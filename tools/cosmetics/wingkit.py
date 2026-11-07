"""Wings, second generation: whole-wing artwork cut into hinged segments, with layers at different depths.

A design paints the LEFT wing as one picture per layer (root on the left, the shoulder pivot at model y = 0), in
texels (DENSITY per model pixel). The kit cuts each layer at the wing's hinges into cards on a chain of bones
(shoulder -> mid -> tip), so the artwork stays continuous across the hinges while the wing bends. The right wing reuses
every picture mirrored (flipped UVs), and so do the back faces, so a texture holds each picture once.

Extra parts (gears, chains, ribbons, separate blades, insect wings) are cards on their own bones.

Output is the same model format the client and the launcher already read (format 1). Animations use the new 'flap'
type (a quick power stroke and a slower recovery); clients that don't know it play it as a plain wave.
"""
import json
import math
import os

import numpy as np
from PIL import Image

from lib import to_u8

DENSITY = 4


class Layer:
    """One depth layer of a wing. paint(frame) -> (H, W, 4) texels; glow(frame) -> the same or None."""

    def __init__(self, z, paint, glow=None):
        self.z, self.paint, self.glow = z, paint, glow


class Part:
    """A card on its own bone. attach: a segment name ('root', 'mid', 'tip') or another part's name.
    rect: (x, y, w, h) model px in the part's own frame; paint(frame) -> (h*D, w*D, 4)."""

    def __init__(self, name, attach, pivot, rot, rect, paint, glow=None, z=0.0, anims=(), children=()):
        self.name, self.attach, self.pivot, self.rot = name, attach, pivot, rot
        self.rect, self.paint, self.glow, self.z = rect, paint, glow, z
        self.anims = list(anims)  # dicts like the model's, with 'rot'/'pos' given for the left side
        self.children = list(children)


class Rig:
    """One pair of wings.

    size: (W, H) model px of the left wing's picture; top: model y of its top edge (the pivot is y = 0).
    cuts: x positions (model px) of the hinges, e.g. [8, 17]. pose: rest angles in degrees.
    flap: animation settings. scale: draws the pair bigger or smaller (the pictures stay the same).
    """

    def __init__(self, layers, size, top, cuts=(), pivot=(1.5, 2.5, 2.3), pose=None, flap=None, parts=(), scale=0.85,
                 prefix='', tint=False):
        self.layers, self.size, self.top, self.cuts = layers, size, top, list(cuts)
        self.pivot, self.scale, self.prefix, self.tint = pivot, scale, prefix, tint
        self.pose = dict(spread=14, lift=10, fold1=9, droop1=3, fold2=10, droop2=6)
        if pose:
            self.pose.update(pose)
        self.flap = dict(amp=16, lift=5, speed=0.42, speedMove=1.05, idle=0.4, move=1.0, kind='flap', lag=0.75, down=0.38)
        if flap:
            self.flap.update(flap)
        self.parts = list(parts)


class WingModel:
    def __init__(self, rigs, frames=1, frame_time=100, translucent=False):
        self.rigs, self.frames, self.frame_time, self.translucent = rigs, frames, frame_time, translucent

    # ------------------------------------------------------------ building
    def build(self, out_dir, name):
        D = DENSITY
        F = self.frames
        images = []  # [{'base': [F images], 'glow': [F images] or None}]
        bones_l = []  # per rig: the left side bone tree (dicts), with cards referencing images
        anims = []
        for ri, rig in enumerate(self.rigs):
            P = rig.prefix
            seg_names = [f'{P}wing', f'{P}mid', f'{P}tip'][:len(rig.cuts) + 1]
            W, H = rig.size
            edges = [0.0] + [float(c) for c in rig.cuts] + [float(W)]
            # paint every layer for every frame once
            layer_px = []
            for L in rig.layers:
                base = [np.asarray(L.paint(f), np.float32) for f in range(F)]
                glow = [np.asarray(L.glow(f), np.float32) for f in range(F)] if L.glow else None
                for b in base:
                    assert b.shape == (int(round(H * D)), int(round(W * D)), 4), (name, b.shape, (H * D, W * D))
                layer_px.append((L, base, glow))
            seg_cards = [[] for _ in seg_names]
            for L, base, glow in layer_px:
                cover = np.zeros(base[0].shape[:2], np.float32)
                for b in base:
                    cover = np.maximum(cover, b[..., 3])
                if glow:
                    for g in glow:
                        cover = np.maximum(cover, g[..., 3])
                for si in range(len(seg_names)):
                    x0 = edges[si] - (0.5 if si > 0 else 0.0)  # a little overlap hides the hinge seam
                    x1 = edges[si + 1]
                    c0, c1 = int(round(x0 * D)), int(round(x1 * D))
                    rows = np.where(cover[:, c0:c1].max(1) > 0.02)[0]
                    cols = np.where(cover[:, c0:c1].max(0) > 0.02)[0]
                    if len(rows) == 0:
                        continue
                    r0, r1 = rows[0], rows[-1] + 1
                    k0, k1 = c0 + cols[0], c0 + cols[-1] + 1
                    img = {'base': [b[r0:r1, k0:k1] for b in base], 'glow': [g[r0:r1, k0:k1] for g in glow] if glow else None}
                    images.append(img)
                    # card position in the segment's own frame (the segment's pivot is at its left edge, x = edges[si])
                    cx0 = k0 / D - edges[si]
                    cy0 = rig.top + r0 / D
                    seg_cards[si].append({'img': len(images) - 1, 'x': cx0, 'y': cy0, 'w': (k1 - k0) / D, 'h': (r1 - r0) / D, 'z': L.z, 'tint': rig.tint})
            # parts
            part_imgs = {}
            for part in _all_parts(rig.parts):
                base = [np.asarray(part.paint(f), np.float32) for f in range(F)]
                glow = [np.asarray(part.glow(f), np.float32) for f in range(F)] if part.glow else None
                images.append({'base': base, 'glow': glow})
                part_imgs[id(part)] = len(images) - 1
            s = rig.scale
            pose = rig.pose
            tree = None
            for si in reversed(range(len(seg_names))):
                if si == 0:
                    pivot, rot = rig.pivot, (0, -pose['spread'], -pose['lift'])
                elif si == 1:
                    pivot, rot = (edges[1] * s, 0, 0), (0, -pose['fold1'], pose['droop1'])
                else:
                    pivot, rot = ((edges[2] - edges[1]) * s, 0, 0), (0, -pose['fold2'], pose['droop2'])
                bone = {'name': seg_names[si], 'pivot': list(pivot), 'rot': list(rot), 'cards': seg_cards[si], 'children': [], 'scale': s}
                if tree is not None:
                    bone['children'].append(tree)
                tree = bone
            # hang parts on their bones
            by_name = {}

            def index(b):
                by_name[b['name']] = b
                for c in b['children']:
                    index(c)
            index(tree)
            seg_alias = {'root': seg_names[0], 'mid': seg_names[min(1, len(seg_names) - 1)], 'tip': seg_names[-1]}

            def add_part(part, parent_name):
                x, y, w, h = part.rect
                pb = {'name': P + part.name, 'pivot': [v * s for v in part.pivot], 'rot': list(part.rot), 'scale': s,
                      'cards': [{'img': part_imgs[id(part)], 'x': x, 'y': y, 'w': w, 'h': h, 'z': part.z, 'tint': rig.tint}],
                      'children': []}
                by_name[parent_name]['children'].append(pb)
                by_name[pb['name']] = pb
                for a in part.anims:
                    anims.append(dict(a, bone=P + part.name))
                for ch in part.children:
                    add_part(ch, pb['name'])
            for part in rig.parts:
                add_part(part, seg_alias.get(part.attach, P + part.attach))
            bones_l.append(tree)
            # wing beat: the shoulder leads, the outer segments follow a little later (a wave down the wing)
            fl = rig.flap
            if fl['amp']:
                common = {k: fl[k] for k in ('speed', 'speedMove', 'idle', 'move')}
                if fl['kind'] == 'flap':
                    common['down'] = fl['down']
                p0 = fl.get('phase0', 0.0)
                a0 = dict(common, bone=seg_names[0], type=fl['kind'], rot=[0, fl['amp'], fl['lift']])
                if p0:
                    a0['phase'] = p0
                anims.append(a0)
                if len(seg_names) > 1:
                    anims.append(dict(common, bone=seg_names[1], type=fl['kind'], rot=[0, fl['amp'] * 0.55, -fl['lift'] * 0.4], phase=p0 - fl['lag']))
                if len(seg_names) > 2:
                    anims.append(dict(common, bone=seg_names[2], type=fl['kind'], rot=[0, fl['amp'] * 0.5, -fl['lift'] * 0.5], phase=p0 - fl['lag'] * 2))
        # ---- pack the atlas (each picture once; a 1-texel border repeats the edges)
        rects = sorted(range(len(images)), key=lambda i: -images[i]['base'][0].shape[0])
        area = sum((images[i]['base'][0].shape[1] + 2) * (images[i]['base'][0].shape[0] + 2) for i in rects)
        width = 64
        maxw = max(images[i]['base'][0].shape[1] for i in rects) + 2
        while width * width < area * 1.25 or width < maxw:
            width *= 2
        x = y = shelf = 0
        place = {}
        for i in rects:
            h, w = images[i]['base'][0].shape[:2]
            if x + w + 2 > width:
                x, y, shelf = 0, y + shelf, 0
            place[i] = (x + 1, y + 1, w, h)
            x += w + 2
            shelf = max(shelf, h + 2)
        height = 1
        while height < y + shelf:
            height *= 2
        tex = np.zeros((height * F, width, 4), np.float32)
        gtex = np.zeros_like(tex)
        has_glow = False
        for i, img in enumerate(images):
            px, py, w, h = place[i]
            for f in range(F):
                _blit(tex, px, py + f * height, img['base'][f])
                if img['glow']:
                    _blit(gtex, px, py + f * height, img['glow'][f])
                    has_glow = True
        os.makedirs(os.path.join(out_dir, 'models'), exist_ok=True)
        os.makedirs(os.path.join(out_dir, 'textures'), exist_ok=True)
        _save(tex, os.path.join(out_dir, 'textures', f'{name}.png'))
        if has_glow:
            _save(gtex, os.path.join(out_dir, 'textures', f'{name}_glow.png'))

        # ---- bones: the left side as built, the right side mirrored
        def emit(b, side):
            sgn = 1 if side == 'l' else -1
            px, py, pz = b['pivot']
            rx, ry, rz = b['rot']
            s = b['scale']
            cubes = []
            for c in b['cards']:
                ix, iy, w, h = place[c['img']]
                x0, x1 = c['x'] * s, (c['x'] + c['w']) * s
                y0, y1 = c['y'] * s, (c['y'] + c['h']) * s
                z = c['z']
                if side == 'r':
                    x0, x1 = -x1, -x0
                plain, flipped = [ix, iy, ix + w, iy + h], [ix + w, iy, ix, iy + h]
                faces = {'front': plain, 'back': flipped} if side == 'l' else {'front': flipped, 'back': plain}
                cube = {'from': [r4(x0), r4(y0), r4(z)], 'to': [r4(x1), r4(y1), r4(z)], 'faces': faces}
                if c['tint']:
                    cube['tint'] = True
                cubes.append(cube)
            return {'name': f"{b['name']}_{side}", 'pivot': [r4(px * sgn), r4(py), r4(pz)], 'rot': [r4(rx), r4(ry * sgn), r4(rz * sgn)],
                    'cubes': cubes, 'children': [emit(c, side) for c in b['children']]}

        bones = []
        for tree in bones_l:
            bones.append(emit(tree, 'l'))
            bones.append(emit(tree, 'r'))
        anim_json = []
        for side, sgn in (('l', 1), ('r', -1)):
            for a in anims:
                a2 = dict(a, bone=f"{a['bone']}_{side}")
                if 'rot' in a2:
                    rx, ry, rz = a2['rot']
                    a2['rot'] = [r4(rx), r4(ry * sgn), r4(rz * sgn)]
                if 'pos' in a2:
                    px, py, pz = a2['pos']
                    a2['pos'] = [r4(px * sgn), r4(py), r4(pz)]
                anim_json.append(a2)
        model = {'format': 1, 'anchor': 'body', 'texture': f'textures/{name}.png', 'size': [width, height], 'frames': F,
                 'frameTime': self.frame_time, 'glow': f'textures/{name}_glow.png' if has_glow else None,
                 'translucent': self.translucent, 'bones': bones, 'anims': anim_json}
        with open(os.path.join(out_dir, 'models', f'{name}.json'), 'w') as fh:
            json.dump(model, fh, separators=(',', ':'))
        return model


def _all_parts(parts):
    for p in parts:
        yield p
        yield from _all_parts(p.children)


def r4(v):
    v = round(float(v), 4)
    return 0.0 if v == 0 else v


def _blit(dst, x, y, img):
    h, w = img.shape[:2]
    dst[y:y + h, x:x + w] = img
    dst[y - 1, x:x + w] = img[0]
    dst[y + h, x:x + w] = img[-1]
    dst[y:y + h, x - 1] = img[:, 0]
    dst[y:y + h, x + w] = img[:, -1]
    dst[y - 1, x - 1], dst[y - 1, x + w], dst[y + h, x - 1], dst[y + h, x + w] = img[0, 0], img[0, -1], img[-1, 0], img[-1, -1]


def _save(img, path):
    Image.fromarray(to_u8(img), 'RGBA').save(path, optimize=True)
