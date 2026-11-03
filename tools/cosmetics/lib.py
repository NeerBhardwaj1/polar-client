"""Polar cosmetics toolkit: models made of boxes (like Minecraft's own and Blockbench's), painted procedurally.

Coordinates are Minecraft model pixels (1/16 block) in the space of the player part a cosmetic follows ("body" or
"head"), with Minecraft's model conventions: y points down, -z is the front of the player, +x is the player's left.
Body space: shoulders at y=0, waist at y=12, back surface at z=+2. Head space: neck at y=0, top of the head at y=-8.

Each box face is painted as an image seen from outside, upright, the way skins are laid out:
  front = the -z face, back = +z, right = -x (the player's right), left = +x, top = -y, bottom = +y.
"""
import json
import math
import os

import numpy as np
from PIL import Image
from scipy import ndimage

FACES = ('top', 'bottom', 'right', 'front', 'left', 'back')


# ---------------------------------------------------------------- colour and noise helpers

def rgb(hexstr):
    h = hexstr.lstrip('#')
    return np.array([int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)], np.float32)


def gradient(t, stops):
    """t: array 0..1; stops: [(pos, '#rrggbb'), ...] -> (..., 3) colours."""
    t = np.clip(np.asarray(t, np.float32), 0, 1)
    pos = np.array([p for p, _ in stops], np.float32)
    cols = np.stack([rgb(c) for _, c in stops])
    out = np.empty(t.shape + (3,), np.float32)
    for ch in range(3):
        out[..., ch] = np.interp(t, pos, cols[:, ch])
    return out


def smoothstep(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def vnoise(w, h, cell, seed, wrap_x=False):
    """Smooth value noise in 0..1, features about `cell` texels apart."""
    rng = np.random.default_rng(seed)
    gw, gh = max(2, int(math.ceil(w / cell)) + 3), max(2, int(math.ceil(h / cell)) + 3)
    grid = rng.random((gh, gw)).astype(np.float32)
    if wrap_x:
        grid[:, -3:] = grid[:, :3]
    big = ndimage.zoom(grid, (cell, cell), order=3, mode='grid-wrap' if wrap_x else 'reflect')
    return np.clip(big[cell:cell + h, cell:cell + w], 0, 1)


def pnoise(w, h, cell, seed):
    """Value noise that tiles seamlessly in x (w must be a multiple of cell); 0..1."""
    rng = np.random.default_rng(seed)
    gw, gh = max(1, w // cell), max(2, int(math.ceil(h / cell)) + 1)
    grid = rng.random((gh, gw)).astype(np.float32)
    big = ndimage.zoom(grid, (cell, cell), order=3, mode='grid-wrap', grid_mode=True)
    return np.clip(big[:h, :w], 0, 1)


def pfbm(w, h, cell, seed, octaves=3):
    out = np.zeros((h, w), np.float32)
    amp, total, c = 1.0, 0.0, cell
    for o in range(octaves):
        if c < 1 or w % c:
            break
        out += amp * pnoise(w, h, c, seed + o * 97)
        total += amp
        amp *= 0.5
        c //= 2
    return out / total


def fbm(w, h, cell, seed, octaves=4, wrap_x=False):
    out = np.zeros((h, w), np.float32)
    amp, total = 1.0, 0.0
    for o in range(octaves):
        c = max(1, int(cell / (2 ** o)))
        out += amp * vnoise(w, h, c, seed + o * 101, wrap_x)
        total += amp
        amp *= 0.5
    return out / total


def blur(a, sigma):
    if a.ndim == 2:
        return ndimage.gaussian_filter(a, sigma)
    return np.stack([ndimage.gaussian_filter(a[..., c], sigma) for c in range(a.shape[-1])], -1)


def rgba(colour, alpha=1.0):
    a = np.asarray(alpha, np.float32)
    colour = np.asarray(colour, np.float32)
    if a.ndim == 0:
        a = np.full(colour.shape[:-1], float(a), np.float32)
    return np.concatenate([colour, a[..., None]], -1)


def solid(w, h, hexstr, alpha=1.0):
    return rgba(np.broadcast_to(rgb(hexstr), (h, w, 3)).copy(), alpha)


def over(dst, src):
    """Paints src (RGBA) over dst (RGBA), both 0..1."""
    sa = src[..., 3:4]
    da = dst[..., 3:4]
    oa = sa + da * (1 - sa)
    oc = np.where(oa > 0, (src[..., :3] * sa + dst[..., :3] * da * (1 - sa)) / np.maximum(oa, 1e-6), 0)
    return np.concatenate([oc, oa], -1)


def add_light(img, glow_rgb, amount):
    """Adds light (screen-like), keeping alpha."""
    out = img.copy()
    out[..., :3] = 1 - (1 - out[..., :3]) * (1 - np.clip(glow_rgb * amount[..., None], 0, 1))
    return out


def bevel(img, light=0.18, dark=0.28, width=1):
    """Lighter top/left edge and darker bottom/right edge, so each face reads as a solid piece."""
    out = img.copy()
    h, w = img.shape[:2]
    for i in range(width):
        f = 1 - i / max(1, width)
        out[i, :, :3] = np.clip(out[i, :, :3] + light * f, 0, 1)
        out[:, i, :3] = np.clip(out[:, i, :3] + light * f * 0.6, 0, 1)
        out[h - 1 - i, :, :3] *= 1 - dark * f
        out[:, w - 1 - i, :3] *= 1 - dark * f * 0.7
    return out


def stars(w, h, n, seed, phase=0.0, colour='#ffffff', big=0.15):
    """A transparent layer of stars that twinkle with `phase` (0..1 loops)."""
    rng = np.random.default_rng(seed)
    layer = np.zeros((h, w, 4), np.float32)
    col = rgb(colour)
    for _ in range(n):
        x, y = int(rng.integers(0, w)), int(rng.integers(0, h))
        tw = 0.55 + 0.45 * math.sin(2 * math.pi * (phase + rng.random()))
        b = tw * (0.5 + 0.5 * rng.random())
        layer[y, x, :3] = col
        layer[y, x, 3] = max(layer[y, x, 3], b)
        if rng.random() < big:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                xx, yy = x + dx, y + dy
                if 0 <= xx < w and 0 <= yy < h:
                    layer[yy, xx, :3] = col
                    layer[yy, xx, 3] = max(layer[yy, xx, 3], b * 0.45)
    return layer


def card(paint):
    """Painters for a flat card: the back is the front mirrored, so both sides have the same outline."""
    def back(face, w, h, f):
        return np.ascontiguousarray(paint(face, w, h, f)[:, ::-1])
    return {'front': paint, 'back': back}


def card_x(paint):
    """The same for a card lying in the y-z plane (right/left faces)."""
    def other(face, w, h, f):
        return np.ascontiguousarray(paint(face, w, h, f)[:, ::-1])
    return {'right': paint, 'left': other}


def to_u8(img):
    return (np.clip(img, 0, 1) * 255 + 0.5).astype(np.uint8)


# ---------------------------------------------------------------- models

class Cube:
    """A box from `frm` with `size` (x, y, z). paint(face, W, H, frame) -> HxWx4 image, or a dict of them per face.
    A size of 0 on an axis makes a flat card (painted on both sides). glow: the same, for the light-emitting layer."""

    def __init__(self, frm, size, paint, glow=None, tint=False, inflate=0.0, faces=None):
        self.frm = tuple(float(v) for v in frm)
        self.size = tuple(float(v) for v in size)
        self.paint = paint
        self.glow = glow
        self.tint = tint
        self.inflate = inflate
        sx, sy, sz = self.size
        if faces is None:
            if sz == 0:
                faces = ('front', 'back')
            elif sx == 0:
                faces = ('right', 'left')
            elif sy == 0:
                faces = ('top', 'bottom')
            else:
                faces = FACES
        self.faces = faces

    def face_dims(self, face, density):
        sx, sy, sz = self.size
        w, h = {'front': (sx, sy), 'back': (sx, sy), 'right': (sz, sy), 'left': (sz, sy),
                'top': (sx, sz), 'bottom': (sx, sz)}[face]
        return max(1, int(round(w * density))), max(1, int(round(h * density)))


class Bone:
    def __init__(self, name, pivot=(0, 0, 0), rot=(0, 0, 0), cubes=(), children=()):
        self.name, self.pivot, self.rot = name, tuple(pivot), tuple(rot)
        self.cubes, self.children = list(cubes), list(children)

    def walk(self):
        yield self
        for c in self.children:
            yield from c.walk()


def _call(painter, face, w, h, frame):
    p = painter.get(face) if isinstance(painter, dict) else painter
    if p is None:
        return None
    img = np.asarray(p(face, w, h, frame), np.float32)
    assert img.shape == (h, w, 4), (face, img.shape, (h, w))
    return img


class Model:
    """A cosmetic model: bones of boxes, painted into one texture (an animated strip when frames > 1)."""

    def __init__(self, anchor, bones, density=4, anims=(), frames=1, frame_time=100, translucent=False):
        self.anchor, self.bones, self.density = anchor, list(bones), density
        self.anims, self.frames, self.frame_time, self.translucent = list(anims), frames, frame_time, translucent

    def build(self, out_dir, name):
        """Writes models/<name>.json and textures/<name>.png (+ _glow.png). Returns the files written."""
        D = self.density
        rects = []
        for bone in self._all_bones():
            for cube in bone.cubes:
                for face in cube.faces:
                    w, h = cube.face_dims(face, D)
                    rects.append((cube, face, w, h))
        # shelf packing with a 1-texel border (repeated edge colours stop seams from showing)
        rects.sort(key=lambda r: -r[3])
        area = sum((w + 2) * (h + 2) for _, _, w, h in rects)
        width = 64
        while width * width < area * 1.3 or width < max(w + 2 for _, _, w, _ in rects):
            width *= 2
        x = y = shelf = 0
        place = {}
        for cube, face, w, h in rects:
            if x + w + 2 > width:
                x, y, shelf = 0, y + shelf, 0
            place[(id(cube), face)] = (x + 1, y + 1, w, h)
            x += w + 2
            shelf = max(shelf, h + 2)
        height = 1
        while height < y + shelf:
            height *= 2
        tex = np.zeros((height * self.frames, width, 4), np.float32)
        glow = np.zeros_like(tex)
        has_glow = False
        for bone in self._all_bones():
            for cube in bone.cubes:
                for face in cube.faces:
                    px, py, w, h = place[(id(cube), face)]
                    for f in range(self.frames):
                        oy = f * height
                        img = _call(cube.paint, face, w, h, f)
                        if img is not None:
                            _blit_padded(tex, px, py + oy, img)
                        if cube.glow is not None:
                            g = _call(cube.glow, face, w, h, f)
                            if g is not None:
                                _blit_padded(glow, px, py + oy, g)
                                has_glow = True
        os.makedirs(os.path.join(out_dir, 'models'), exist_ok=True)
        os.makedirs(os.path.join(out_dir, 'textures'), exist_ok=True)
        Image.fromarray(to_u8(tex), 'RGBA').save(os.path.join(out_dir, 'textures', f'{name}.png'), optimize=True)
        if has_glow:
            Image.fromarray(to_u8(glow), 'RGBA').save(os.path.join(out_dir, 'textures', f'{name}_glow.png'), optimize=True)

        def bone_json(b):
            cubes = []
            for c in b.cubes:
                x0, y0, z0 = c.frm
                sx, sy, sz = c.size
                faces = {}
                for face in c.faces:
                    px, py, w, h = place[(id(c), face)]
                    faces[face] = [px, py, px + w, py + h]
                cube = {'from': [x0, y0, z0], 'to': [x0 + sx, y0 + sy, z0 + sz], 'faces': faces}
                if c.inflate:
                    cube['inflate'] = c.inflate
                if c.tint:
                    cube['tint'] = True
                cubes.append(cube)
            return {'name': b.name, 'pivot': list(b.pivot), 'rot': list(b.rot), 'cubes': cubes, 'children': [bone_json(ch) for ch in b.children]}

        model = {
            'format': 1, 'anchor': self.anchor,
            'texture': f'textures/{name}.png', 'size': [width, height], 'frames': self.frames, 'frameTime': self.frame_time,
            'glow': f'textures/{name}_glow.png' if has_glow else None,
            'translucent': self.translucent,
            'bones': [bone_json(b) for b in self.bones],
            'anims': self.anims,
        }
        with open(os.path.join(out_dir, 'models', f'{name}.json'), 'w') as fh:
            json.dump(model, fh, separators=(',', ':'))
        return model

    def _all_bones(self):
        for b in self.bones:
            yield from b.walk()


def _blit_padded(dst, x, y, img):
    h, w = img.shape[:2]
    dst[y:y + h, x:x + w] = img
    dst[y - 1, x:x + w] = img[0]
    dst[y + h, x:x + w] = img[-1]
    dst[y:y + h, x - 1] = img[:, 0]
    dst[y:y + h, x + w] = img[:, -1]
    dst[y - 1, x - 1], dst[y - 1, x + w], dst[y + h, x - 1], dst[y + h, x + w] = img[0, 0], img[0, -1], img[-1, 0], img[-1, -1]


# ---------------------------------------------------------------- capes

CAPE_SCALE = 4  # HD capes: 4 texels per pixel (256 x 128, a 4x vanilla cape)


def cape_texture(front, back=None, edge=None, frames=1):
    """Builds an HD cape strip. front(W, H, frame) paints the outside (what people see from behind, 10x16 px);
    back(W, H, frame) the lining (defaults to a darker front); edge: '#rrggbb' for the sides."""
    S = CAPE_SCALE
    W, H = 64 * S, 32 * S
    strip = np.zeros((H * frames, W, 4), np.float32)
    for f in range(frames):
        oy = f * H
        out = front(10 * S, 16 * S, f)
        lining = back(10 * S, 16 * S, f) if back else _lining(out)
        side_col = rgb(edge) if edge else np.clip(out[..., :3].mean((0, 1)) * 0.55, 0, 1)
        side_v = rgba(np.broadcast_to(side_col, (16 * S, 1 * S, 3)).copy())
        side_h = rgba(np.broadcast_to(side_col, (1 * S, 10 * S, 3)).copy())
        # the vanilla cape layout: box at (0, 0), 10 x 16 x 1
        strip[oy + 1 * S:oy + 17 * S, 1 * S:11 * S] = out           # outside (shown behind the player)
        strip[oy + 1 * S:oy + 17 * S, 12 * S:22 * S] = lining       # inside
        strip[oy + 1 * S:oy + 17 * S, 0:1 * S] = side_v
        strip[oy + 1 * S:oy + 17 * S, 11 * S:12 * S] = side_v
        strip[oy:oy + 1 * S, 1 * S:11 * S] = side_h
        strip[oy:oy + 1 * S, 11 * S:21 * S] = side_h
        # elytra (vanilla uses the cape texture for it): reuse the outside, squeezed into its region
        ely = np.asarray(Image.fromarray(to_u8(out)).resize((10 * S, 20 * S), Image.BILINEAR), np.float32) / 255
        strip[oy + 2 * S:oy + 22 * S, 22 * S:32 * S] = ely
        strip[oy + 2 * S:oy + 22 * S, 34 * S:44 * S] = ely[:, ::-1]
    return strip


def _lining(front):
    lin = front.copy()
    lin[..., :3] = lin[..., :3] * 0.45 + 0.03
    return lin


def save_png(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    Image.fromarray(to_u8(img), 'RGBA').save(path, optimize=True)
