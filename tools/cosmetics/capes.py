"""Capes: HD (4x) cape textures, most of them animated (a strip of frames that loops seamlessly).

Every painter is paint(W, H, f, N) -> H x W x 4 (0..1): the outside of the cape as seen from behind, frame f of N.
"""
import colorsys
import math

import numpy as np
from PIL import Image, ImageDraw

from lib import (rgb, rgba, gradient, smoothstep, pnoise, pfbm, fbm, vnoise, stars, over, add_light, bevel, blur,
                 cape_texture, save_png)
from paint import mask_polys, mask_ellipse


# ---------------------------------------------------------------- helpers

def _xy(W, H):
    y, x = np.mgrid[0:H, 0:W].astype(np.float32)
    return x, y, (x + 0.5) / W, (y + 0.5) / H


def _frame_border(img, col='#8cc8ff', alpha=0.85, inner='#ffffff'):
    out = img.copy()
    c, ci = rgb(col), rgb(inner)
    for (sl, a) in (((slice(0, 2), slice(None)), alpha), ((slice(-2, None), slice(None)), alpha),
                    ((slice(None), slice(0, 2)), alpha), ((slice(None), slice(-2, None)), alpha)):
        out[sl][..., :3] = out[sl][..., :3] * (1 - a) + c * a
    for sl in ((slice(2, 3), slice(2, -2)), (slice(-3, -2), slice(2, -2)), (slice(2, -2), slice(2, 3)), (slice(2, -2), slice(-3, -2))):
        out[sl][..., :3] = out[sl][..., :3] * 0.7 + ci * 0.3
    return out


def _paint(img, mask, colour, amount=1.0):
    """Paints a colour (hex or HxWx3) through a 0..1 mask, in place, and returns img."""
    c = rgb(colour) if isinstance(colour, str) else colour
    m = (np.clip(mask, 0, 1) * amount)[..., None]
    img[..., :3] = img[..., :3] * (1 - m) + c * m
    return img


def _glow(img, mask, colour, amount=1.0):
    c = rgb(colour) if isinstance(colour, str) else colour
    return add_light(img, np.broadcast_to(c, img.shape[:2] + (3,)) if c.ndim == 1 else c, np.clip(mask * amount, 0, 1))


def _ytile(W, H, cell, seed, octaves=3):
    """fbm that tiles seamlessly in y (H must be a multiple of cell)."""
    return pfbm(H, W, cell, seed, octaves).T.copy()


def _dots(W, H, pts, radius, sharp=2.0):
    """Soft round dots; pts: (x, y[, scale[, alpha]]) in texels. Wraps at the edges so looping particles don't pop."""
    x, y, _, _ = _xy(W, H)
    m = np.zeros((H, W), np.float32)
    for p in pts:
        r = radius * (p[2] if len(p) > 2 else 1)
        a = p[3] if len(p) > 3 else 1
        for ox in (-W, 0, W):
            for oy in (-H, 0, H):
                px, py = p[0] + ox, p[1] + oy
                if px < -r - 1 or px > W + r + 1 or py < -r - 1 or py > H + r + 1:
                    continue
                d = np.hypot(x + 0.5 - px, y + 0.5 - py)
                m = np.maximum(m, np.clip(1 - d / r, 0, 1) ** sharp * a)
    return m


def _loop(n, seed, W, H, f, N, speeds=(1, 2), dir=1, drift=0, sway=0.0, sway_k=1):
    """Particles that loop: each moves `speed` cape-heights per loop (whole numbers, so frame N == frame 0).
    Returns (x, y, rnd, speed) tuples."""
    rng = np.random.default_rng(seed)
    t = f / N
    out = []
    for i in range(n):
        x0, y0, ph, r = rng.random() * W, rng.random() * H, rng.random(), rng.random()
        k = speeds[i % len(speeds)]
        y = (y0 + dir * k * H * t) % H
        x = (x0 + drift * W * t + sway * math.sin(2 * math.pi * (t * sway_k + ph))) % W
        out.append((x, y, r, k))
    return out


def _lines_mask(W, H, segs, width, scale=4):
    """Antialiased lines; segs: [((x0, y0), (x1, y1)), ...] or polylines [[(x, y), ...]] in texels."""
    im = Image.new('L', (W * scale, H * scale), 0)
    d = ImageDraw.Draw(im)
    for s in segs:
        d.line([(x * scale, y * scale) for x, y in s], fill=255, width=max(1, int(round(width * scale))), joint='curve')
    return np.asarray(im.resize((W, H), Image.LANCZOS), np.float32) / 255


def snowflake_mask(W, H, cx, cy, R, width=1.2, rot=0.0, branches=True):
    """The Polar snowflake: six arms with two pairs of branches each."""
    segs = []
    for k in range(6):
        a = math.radians(k * 60 + rot)
        ex, ey = cx + math.cos(a) * R, cy + math.sin(a) * R
        segs.append(((cx, cy), (ex, ey)))
        if branches:
            for t, bl in ((0.5, 0.36), (0.76, 0.24)):
                mx, my = cx + math.cos(a) * R * t, cy + math.sin(a) * R * t
                for s in (-1, 1):
                    b = a + s * math.radians(45)
                    segs.append(((mx, my), (mx + math.cos(b) * R * bl, my + math.sin(b) * R * bl)))
    return _lines_mask(W, H, segs, width)


def _pines(W, H, trees, scale=4):
    """Pine silhouettes; trees: (cx, base_y, height, width). Returns (mask, snow-on-branches mask)."""
    polys, snow = [], []
    for cx, base, h, w in trees:
        for i in range(3):
            top = base - h + i * h * 0.24
            bot = top + h * (0.42 + 0.06 * i)
            hw = w * (0.32 + 0.18 * i)
            polys.append([(cx, top), (cx + hw, bot), (cx - hw, bot)])
            snow.append([(cx, top), (cx + hw * 0.55, top + (bot - top) * 0.55), (cx, top + (bot - top) * 0.4),
                         (cx - hw * 0.55, top + (bot - top) * 0.55)])
        polys.append([(cx - w * 0.07, base - h * 0.12), (cx + w * 0.07, base - h * 0.12), (cx + w * 0.07, base), (cx - w * 0.07, base)])
    return mask_polys(W, H, polys, scale), mask_polys(W, H, snow, scale)


def heart_mask(W, H, cx, cy, s, ss=4):
    """A heart about 2s wide centred on (cx, cy), antialiased by supersampling."""
    y, x = np.mgrid[0:H * ss, 0:W * ss].astype(np.float32)
    X = ((x + 0.5) / ss - cx) / s * 1.15
    Y = (cy - (y + 0.5) / ss) / s * 1.15 + 0.1
    inside = ((X ** 2 + Y ** 2 - 1) ** 3 - X ** 2 * Y ** 3 < 0).astype(np.float32)
    return inside.reshape(H, ss, W, ss).mean((1, 3))


def _hsv(h, s, v):
    hh = np.asarray(h, np.float32) % 1.0
    i = np.floor(hh * 6).astype(int) % 6
    fr = hh * 6 - np.floor(hh * 6)
    p, q, t = v * (1 - s), v * (1 - s * fr), v * (1 - s * (1 - fr))
    r = np.choose(i, [v, q, p, p, t, v])
    g = np.choose(i, [t, v, v, q, p, p])
    b = np.choose(i, [p, p, t, v, v, q])
    return np.stack([r, g, b], -1).astype(np.float32)


def _voronoi(W, H, n, seed):
    rng = np.random.default_rng(seed)
    x, y, _, _ = _xy(W, H)
    pts = rng.random((n, 2)) * [W, H]
    d = np.stack([np.hypot(x + 0.5 - px, y + 0.5 - py) for px, py in pts], -1)
    idx = np.argsort(d, -1)
    d1 = np.take_along_axis(d, idx[..., :1], -1)[..., 0]
    d2 = np.take_along_axis(d, idx[..., 1:2], -1)[..., 0]
    return idx[..., 0], d1, d2, pts, rng


def _sweep(W, H, f, N, width=0.06, start=-0.5, end=1.9):
    """A diagonal shine that crosses the cape once per loop (off-cape for the rest, so it loops cleanly)."""
    _, _, u, v = _xy(W, H)
    p = start + (end - start) * f / N
    d = (u * 0.55 + v * 0.45) - p
    return np.exp(-(d / width) ** 2)


# ---------------------------------------------------------------- Polar collection

def aurora(W, H, f, N):
    x, y, u, v = _xy(W, H)
    sky = gradient(v, [(0, '#030714'), (0.5, '#081433'), (1, '#0f2556')])
    img = rgba(sky)
    st = stars(W, H, 46, 11, phase=f / N)
    st[..., 3] *= 1 - smoothstep(0.45, 0.72, v)
    img = over(img, st)
    step = W // N  # whole texels per frame, so the loop is seamless
    for seed, centre, amp, up, down, sh, cols, power in (
        (5, 0.42, 0.22, 0.22, 0.03, f * step, [(0, '#3dffb0'), (0.35, '#2fe0c8'), (0.75, '#7a7cff'), (1, '#c08bff')], 1.0),
        (8, 0.30, 0.16, 0.16, 0.025, -f * step, [(0, '#7dffcf'), (0.5, '#5cf2d6'), (1, '#b197fc')], 0.65),
    ):
        edge = centre + amp * (pnoise(W, 8, 10, seed)[0] - 0.5)
        edge = np.roll(edge, sh)[None, :]
        rays = np.roll(0.35 + 0.65 * pnoise(W, 8, 2, seed + 3)[0] ** 1.5, -sh)[None, :]
        d = v - edge
        inten = np.where(d < 0, np.exp(d / up), np.exp(-(d / down) ** 2)) * rays * power
        col = gradient(np.clip(-d / (up * 2.2), 0, 1), cols)
        img = add_light(img, col, np.clip(inten, 0, 1))
    m, snow = _mountains(W, H)
    mount = gradient(np.clip((v - 0.74) / 0.26, 0, 1), [(0, '#1b2f5c'), (1, '#0a1530')])
    mount = mount * (1 - 0.12 * vnoise(W, H, 6, 4)[..., None])
    mount = mount + snow[..., None] * (rgb('#d8ecff') - mount) * 0.85
    img[..., :3] = img[..., :3] * (1 - m[..., None]) + mount * m[..., None]
    return _frame_border(img)


def _mountains(W, H, top=0.74, seed=3):
    """Snowy peaks along the bottom: (alpha mask, snow highlight)."""
    x, y, u, v = _xy(W, H)
    ridge = top + 0.1 * pnoise(W, 4, 8, seed)[0] + 0.06 * np.abs(np.sin(u[0] * math.pi * 3.2 + 0.7))
    ridge = ridge[None, :]
    m = smoothstep(ridge - 0.004, ridge + 0.004, v)
    snow = np.clip(1 - (v - ridge) / 0.05, 0, 1) * m
    return m, snow


def glacier(W, H, f, N):
    x, y, u, v = _xy(W, H)
    cell, d1, d2, pts, rng = _voronoi(W, H, 24, 21)
    shade = rng.random(24)[cell]
    ang = (rng.random(24) * 2 * math.pi)[cell]
    gx = (x - pts[cell, 0]) * np.cos(ang) + (y - pts[cell, 1]) * np.sin(ang)
    t = np.clip(0.18 + 0.45 * shade + gx / 34 + v * 0.32, 0, 1)
    col = gradient(t, [(0, '#f2fbff'), (0.3, '#b4e4ff'), (0.62, '#5aa6ee'), (1, '#1b468f')])
    crack = np.clip(1 - (d2 - d1) / 1.4, 0, 1)
    col = col * (1 - crack[..., None] * 0.25) + rgb('#ffffff') * crack[..., None] * 0.6
    img = rgba(col)
    img = _glow(img, _sweep(W, H, f, N, 0.07) * (0.5 + 0.5 * shade), '#ffffff', 0.85)
    img = over(img, stars(W, H, 18, 4, phase=f / N, colour='#ffffff', big=0.5))
    return _frame_border(img, '#e6f6ff', 0.9, '#7fc4ff')


def polar_night(W, H, f, N):
    x, y, u, v = _xy(W, H)
    img = rgba(gradient(v, [(0, '#01030b'), (0.55, '#0a1838'), (1, '#18346a')]))
    st = stars(W, H, 40, 31, phase=f / N)
    st[..., 3] *= 1 - smoothstep(0.5, 0.75, v)
    img = over(img, st)
    # crescent moon with a soft halo
    mx, my, r = W * 0.7, H * 0.18, W * 0.13
    disk = mask_ellipse(W, H, mx, my, r, r)
    bite = mask_ellipse(W, H, mx - r * 0.45, my - r * 0.2, r * 0.9, r * 0.9)
    halo = np.exp(-np.hypot(x + 0.5 - mx, y + 0.5 - my) / (r * 1.6)) * 0.55
    img = _glow(img, halo, '#9cc8ff', 0.6)
    img = _paint(img, disk * (1 - bite), '#f4f8ff')
    # trees and snowy ground
    trees = [(W * 0.12, H * 0.93, H * 0.36, W * 0.36), (W * 0.32, H * 0.95, H * 0.26, W * 0.3),
             (W * 0.86, H * 0.94, H * 0.42, W * 0.4), (W * 0.62, H * 0.97, H * 0.22, W * 0.26)]
    tm, ts = _pines(W, H, trees)
    img = _paint(img, tm, '#071226')
    img = _paint(img, ts * tm, '#cfe6ff', 0.85)
    ground = smoothstep(0.88, 0.9, v + 0.02 * np.sin(u * 7))
    img = _paint(img, ground, gradient(v, [(0.85, '#dceeff'), (1, '#9cc0ea')]))
    # falling snow: two depths
    far = _loop(30, 3, W, H, f, N, speeds=(1,), sway=1.2)
    near = _loop(14, 4, W, H, f, N, speeds=(2,), sway=2.0)
    img = _paint(img, _dots(W, H, far, 0.9, 1.0) * 0.7, '#e8f3ff')
    img = _paint(img, _dots(W, H, near, 1.4, 1.2), '#ffffff')
    return _frame_border(img, '#6f9ee8', 0.8)


def cozy_cabin(W, H, f, N):
    x, y, u, v = _xy(W, H)
    img = rgba(gradient(v, [(0, '#1d2b55'), (0.45, '#4e6aa8'), (0.7, '#a8c3ec')]))
    hills = smoothstep(0.0, 0.01, v - (0.62 + 0.05 * np.sin(u * 5 + 1) + 0.03 * np.sin(u * 13)))
    img = _paint(img, hills, '#c9dcf5')
    # cabin
    cx, base, cw, ch = W * 0.42, H * 0.86, W * 0.42, H * 0.17
    wall = mask_polys(W, H, [[(cx - cw / 2, base - ch), (cx + cw / 2, base - ch), (cx + cw / 2, base), (cx - cw / 2, base)]])
    planks = 0.85 + 0.15 * (np.floor((y - (base - ch)) / 3) % 2)
    img = _paint(img, wall, gradient(u, [(0, '#6b3f22'), (1, '#4a2914')]) * planks[..., None])
    roof = mask_polys(W, H, [[(cx - cw * 0.66, base - ch), (cx, base - ch * 2.0), (cx + cw * 0.66, base - ch)]])
    img = _paint(img, roof, '#3a2216')
    roof_snow = mask_polys(W, H, [[(cx - cw * 0.7, base - ch + 1), (cx, base - ch * 2.0 - 1.5), (cx + cw * 0.7, base - ch + 1),
                                   (cx + cw * 0.6, base - ch - 1.5), (cx, base - ch * 1.9 + 1.5), (cx - cw * 0.6, base - ch - 1.5)]])
    img = _paint(img, roof_snow, '#f4f9ff')
    chimney = mask_polys(W, H, [[(cx + cw * 0.22, base - ch * 1.75), (cx + cw * 0.34, base - ch * 1.75), (cx + cw * 0.34, base - ch * 1.25), (cx + cw * 0.22, base - ch * 1.45)]])
    img = _paint(img, chimney, '#5b3b2e')
    flick = 0.85 + 0.15 * math.sin(2 * math.pi * f / N * 3) * math.sin(2 * math.pi * f / N * 2 + 1)
    win = mask_polys(W, H, [[(cx - cw * 0.3, base - ch * 0.75), (cx - cw * 0.08, base - ch * 0.75), (cx - cw * 0.08, base - ch * 0.35), (cx - cw * 0.3, base - ch * 0.35)]])
    door = mask_polys(W, H, [[(cx + cw * 0.08, base - ch * 0.8), (cx + cw * 0.3, base - ch * 0.8), (cx + cw * 0.3, base), (cx + cw * 0.08, base)]])
    img = _paint(img, door, '#2c170b')
    wx, wy = cx - cw * 0.19, base - ch * 0.55
    img = _glow(img, np.exp(-np.hypot(x + 0.5 - wx, y + 0.5 - wy) / 5) * flick, '#ffb34d', 0.8)
    img = _paint(img, win, gradient(v, [(0, '#ffe7a3'), (1, '#ffae3d')]) * flick)
    # chimney smoke rising (loops)
    smoke = [(cx + cw * 0.28 + 1.5 * math.sin(2 * math.pi * (f / N) + i) + i * 0.6, (base - ch * 1.8 - (i * 3 + f * 6 / N * 3) % 18), 1 + i * 0.15, 0.5 - i * 0.07)
             for i in range(6)]
    img = _paint(img, _dots(W, H, smoke, 2.0, 1.2), '#dfe8f5', 0.6)
    trees = [(W * 0.08, H * 0.9, H * 0.3, W * 0.3), (W * 0.9, H * 0.92, H * 0.38, W * 0.34)]
    tm, ts = _pines(W, H, trees)
    img = _paint(img, tm, '#173a3a')
    img = _paint(img, ts * tm, '#f4f9ff', 0.9)
    ground = smoothstep(0.0, 0.01, v - (0.86 + 0.015 * np.sin(u * 9)))
    img = _paint(img, ground, gradient(v, [(0.86, '#ffffff'), (1, '#c3d9f2')]))
    snow = _loop(34, 8, W, H, f, N, speeds=(1, 2), sway=1.5)
    img = _paint(img, _dots(W, H, snow, 1.0, 1.0), '#ffffff', 0.9)
    return _frame_border(img, '#f0f7ff', 0.9, '#9cc0ea')


def frostbite(W, H, f, N):
    x, y, u, v = _xy(W, H)
    img = rgba(gradient(v, [(0, '#071026'), (1, '#0c1d40')]))
    rng = np.random.default_rng(12)
    segs = []

    def branch(px, py, ang, length, depth):
        if depth == 0 or length < 1.2:
            return
        ex, ey = px + math.cos(ang) * length, py + math.sin(ang) * length
        segs.append(((px, py), (ex, ey)))
        for t in (0.35, 0.65, 1.0):
            bx, by = px + (ex - px) * t, py + (ey - py) * t
            for s in (-1, 1):
                branch(bx, by, ang + s * math.radians(55 + rng.random() * 15), length * 0.45 * (1.15 - t * 0.5), depth - 1)

    for cx, cy, a0 in ((0, 0, 45), (W, 0, 135), (0, H, -45), (W, H, -135)):
        for da in (-22, 22):
            branch(cx, cy, math.radians(a0 + da + rng.random() * 8 - 4), 11 + rng.random() * 4, 2)
    frost = _lines_mask(W, H, segs, 0.6)
    pulse = 0.65 + 0.35 * math.sin(2 * math.pi * f / N)
    img = _glow(img, blur(frost, 1.2) * 0.9, '#3fc9ff', pulse)
    img = _paint(img, frost, '#dff7ff', 0.8)
    flake = snowflake_mask(W, H, W / 2, H * 0.48, W * 0.3, 1.1)
    img = _glow(img, blur(flake, 2.0) * 2, '#55e0ff', 0.7 + 0.3 * pulse)
    img = _paint(img, flake, '#ffffff')
    img = over(img, stars(W, H, 16, 9, phase=f / N, colour='#bff3ff', big=0.4))
    return _frame_border(img, '#7fdcff', 0.8)


# ---------------------------------------------------------------- Inferno

def inferno(W, H, f, N):
    x, y, u, v = _xy(W, H)
    s = f * H // N
    n1 = np.roll(_ytile(W, H, 16, 41), -s, 0)
    n2 = np.roll(_ytile(W, H, 8, 47), -2 * s, 0)
    n = n1 * 0.6 + n2 * 0.4
    side = 1 - 0.35 * np.abs(u - 0.5) * 2
    heat = n * 1.35 + v * 1.05 * side - 0.82
    img = rgba(gradient(heat, [(0, '#0b0203'), (0.25, '#3a0806'), (0.45, '#a3170a'), (0.62, '#ff5a14'), (0.8, '#ffb43a'), (0.94, '#fff0a8'), (1, '#ffffff')]))
    embers = _loop(22, 5, W, H, f, N, speeds=(1, 2), dir=-1, sway=1.2)
    img = _glow(img, _dots(W, H, embers, 1.1, 1.5), '#ffcf6b', 1.0)
    return _frame_border(img, '#ff8a3d', 0.85, '#ffe08a')


def molten(W, H, f, N):
    x, y, u, v = _xy(W, H)
    cell, d1, d2, pts, rng = _voronoi(W, H, 22, 77)
    rock = gradient(fbm(W, H, 8, 3, 3) * 0.7 + rng.random(22)[cell] * 0.3, [(0, '#120c0c'), (1, '#3b2a24')])
    rock = rock * (0.75 + 0.25 * np.clip(d1 / 8, 0, 1))[..., None]
    crack = np.clip(1 - (d2 - d1) / 2.4, 0, 1) ** 1.5
    s = f * H // N
    flow = np.roll(_ytile(W, H, 16, 9), s, 0) * 0.6 + np.roll(_ytile(W, H, 8, 13), 2 * s, 0) * 0.4
    hot = crack * (0.55 + 0.6 * flow)
    img = rgba(rock)
    img = _glow(img, blur(crack, 2.0) * 1.3, '#ff4a0a', 0.55 + 0.25 * math.sin(2 * math.pi * f / N))
    img[..., :3] = img[..., :3] * (1 - crack[..., None]) + gradient(hot, [(0, '#5a0d02'), (0.4, '#ff3d00'), (0.75, '#ffa000'), (1, '#fff3b0')]) * crack[..., None]
    return _frame_border(img, '#ff6a1a', 0.8, '#ffd27a')


def ember(W, H, f, N):
    x, y, u, v = _xy(W, H)
    img = rgba(gradient(v, [(0, '#0d0a0c'), (0.6, '#1d1213'), (1, '#4a1a0e')]))
    pulse = 0.8 + 0.2 * math.sin(2 * math.pi * f / N * 2)
    coal = smoothstep(0.62, 1.0, v) * (0.45 + 0.55 * fbm(W, H, 5, 6, 3))
    img = _glow(img, coal * pulse * 1.2, '#ff5a1a', 1.0)
    cracks = (np.abs(fbm(W, H, 6, 14, 2) - 0.5) < 0.05) * smoothstep(0.8, 0.95, v)
    img = _glow(img, cracks * pulse, '#ffd27a', 1.0)
    big = _loop(18, 21, W, H, f, N, speeds=(1,), dir=-1, sway=2.5)
    small = _loop(34, 22, W, H, f, N, speeds=(2, 3), dir=-1, sway=1.5, sway_k=2)
    fade = lambda pts: [(p[0], p[1], 0.9 + 0.7 * p[2], 0.25 + 0.75 * (p[1] / H) ** 0.7) for p in pts]
    img = _glow(img, blur(_dots(W, H, fade(big), 1.8, 1.0), 1.4) * 2.0, '#ff6a10', 1.0)
    img = _glow(img, _dots(W, H, fade(big), 1.5, 1.2), '#ffe08a', 1.0)
    img = _glow(img, blur(_dots(W, H, fade(small), 1.0, 1.0), 0.8) * 1.5, '#ff9c4a', 1.0)
    return _frame_border(img, '#c2410c', 0.85, '#fb923c')


# ---------------------------------------------------------------- Cosmic

def galaxy(W, H, f, N):
    x, y, u, v = _xy(W, H)
    img = rgba(gradient(v, [(0, '#03030c'), (1, '#0a0820')]))
    neb = fbm(W, H, 16, 5, 4)
    img = _glow(img, smoothstep(0.45, 0.85, neb) * 0.5, '#4b2a8a', 1.0)
    img = over(img, stars(W, H, 50, 17, phase=f / N, big=0.12))
    cx, cy = W * 0.5, H * 0.46
    dx, dy = (x + 0.5 - cx) / (W * 0.5), (y + 0.5 - cy) / (W * 0.5) * 1.55
    r = np.hypot(dx, dy) + 1e-3
    th = np.arctan2(dy, dx)
    rot = math.pi * f / N  # two arms: half a turn per loop
    arm = ((np.cos(2 * (th - rot) - 4.4 * np.log(r + 0.04)) + 1) / 2) ** 2.4
    dust = fbm(W, H, 6, 8, 3)
    b = arm * np.exp(-r * 1.9) * (0.55 + 0.6 * dust) * 1.8
    core = np.exp(-(r / 0.16) ** 2)
    col = gradient(np.clip(r * 1.3, 0, 1), [(0, '#ffe9c9'), (0.25, '#d6a8ff'), (0.6, '#6f7bff'), (1, '#3ec5ff')])
    img = add_light(img, col, np.clip(b, 0, 1))
    img = _glow(img, core, '#fff4dc', 1.0)
    return _frame_border(img, '#8f7bff', 0.8, '#d6c8ff')


def nebula(W, H, f, N):
    x, y, u, v = _xy(W, H)
    s = f * W // N
    a = np.roll(pfbm(W, H, 20, 3, 3), s, 1)
    b = np.roll(pfbm(W, H, 20, 9, 3), -s, 1)
    img = rgba(gradient(v, [(0, '#05030f'), (1, '#0b0522')]))
    img = add_light(img, gradient(a, [(0, '#000000'), (0.5, '#3a0f5c'), (0.75, '#d43f8d'), (1, '#ffb3d9')]), np.clip((a - 0.35) * 2.2, 0, 1))
    img = add_light(img, gradient(b, [(0, '#000000'), (0.5, '#0d2a6b'), (0.8, '#2fb5ff'), (1, '#c3f4ff')]), np.clip((b - 0.4) * 2.2, 0, 1))
    img = over(img, stars(W, H, 48, 23, phase=f / N, big=0.25))
    for sx, sy in ((W * 0.28, H * 0.3), (W * 0.74, H * 0.68)):
        flare = _lines_mask(W, H, [((sx - 4, sy), (sx + 4, sy)), ((sx, sy - 4), (sx, sy + 4))], 0.6)
        img = _glow(img, flare * (0.7 + 0.3 * math.sin(2 * math.pi * f / N + sx)), '#ffffff', 1.0)
    return _frame_border(img, '#c26bff', 0.8, '#ffb3d9')


def event_horizon(W, H, f, N):
    x, y, u, v = _xy(W, H)
    img = rgba(gradient(v, [(0, '#020107'), (1, '#07031a')]))
    img = over(img, stars(W, H, 44, 51, phase=f / N, big=0.1))
    cx, cy = W * 0.5, H * 0.44
    dx, dy = (x + 0.5 - cx) / (W * 0.5), (y + 0.5 - cy) / (W * 0.5)
    rc = np.hypot(dx, dy)
    re = np.hypot(dx, dy * 3.6)
    th = np.arctan2(dy * 3.6, dx)
    shift = 2 * math.pi * f / N
    swirl = 0.55 + 0.45 * np.cos(3 * (th - shift) + 9 * re) * (0.6 + 0.4 * np.cos(5 * (th - shift) - 4 * re))
    disk = smoothstep(0.38, 0.48, re) * (1 - smoothstep(0.82, 0.98, re))
    hot = gradient(np.clip((re - 0.38) / 0.6, 0, 1), [(0, '#fff6e0'), (0.25, '#ffb057'), (0.6, '#ff4fa3'), (1, '#6c2bd9')])
    halo = np.exp(-np.abs(rc - 0.34) / 0.05)
    img = _glow(img, np.exp(-rc / 0.6) * 0.6, '#5a1fa8', 1.0)
    behind = disk * (dy < 0)
    img = add_light(img, hot, np.clip(behind * swirl * 1.2, 0, 1))
    img = _glow(img, halo, '#ffd9a0', 0.9)
    shadow = 1 - smoothstep(0.27, 0.31, rc)
    img = _paint(img, shadow, '#000000')
    front = disk * (dy >= 0)
    img = add_light(img, hot, np.clip(front * swirl * 1.3, 0, 1))
    return _frame_border(img, '#7c3aed', 0.85, '#f0abfc')


# ---------------------------------------------------------------- Nature

def ocean_depths(W, H, f, N):
    x, y, u, v = _xy(W, H)
    img = rgba(gradient(v, [(0, '#3fd0e8'), (0.35, '#1683b5'), (0.75, '#0a3d73'), (1, '#05213f')]))
    s = f * W // N
    rays = np.roll(pnoise(W, 4, 5, 7)[0], s)
    xs = ((x + y * 0.35).astype(int)) % W
    ray = rays[xs] ** 3 * np.exp(-v * 2.2)
    img = _glow(img, ray, '#bff6ff', 0.55)
    caust = np.abs(np.sin((x * 0.6 + np.roll(pfbm(W, H, 10, 3, 2), s, 1) * 9))) ** 8 * np.exp(-v * 5)
    img = _glow(img, caust, '#e6fdff', 0.5)
    # kelp swaying and a reef at the bottom
    segs = []
    for i, kx in enumerate((W * 0.12, W * 0.24, W * 0.8, W * 0.9)):
        pts = []
        for j in range(12):
            t = j / 11
            pts.append((kx + 2.2 * t * math.sin(2 * math.pi * (f / N) + t * 3 + i), H - t * H * (0.42 + 0.08 * (i % 2))))
        segs.append(pts)
    img = _paint(img, _lines_mask(W, H, segs, 1.6), '#0f6b4a')
    reef = smoothstep(0.0, 0.01, v - (0.9 + 0.04 * np.sin(u * 11) + 0.02 * np.sin(u * 27)))
    img = _paint(img, reef, gradient(u, [(0, '#e86a7a'), (0.5, '#f29f5c'), (1, '#c35bd1')]) * 0.8)
    # a small school of fish crossing (one cape-width per loop)
    for i in range(5):
        fx = (W * (f / N) * 1 + i * 3.1 + 4) % (W + 8) - 4
        fy = H * 0.42 + i * 2.2 + 1.2 * math.sin(i * 1.7)
        body = mask_ellipse(W, H, fx, fy, 1.7, 0.8)
        tail = mask_polys(W, H, [[(fx - 1.4, fy), (fx - 2.8, fy - 1.0), (fx - 2.8, fy + 1.0)]])
        img = _paint(img, np.clip(body + tail, 0, 1), '#ffd36b' if i % 2 else '#ff9b54')
    bubbles = _loop(14, 31, W, H, f, N, speeds=(1, 2), dir=-1, sway=0.8, sway_k=2)
    ring = np.clip(_dots(W, H, bubbles, 1.6, 0.6) - _dots(W, H, bubbles, 1.0, 0.6) * 0.9, 0, 1)
    img = _paint(img, ring, '#e9fdff', 0.8)
    return _frame_border(img, '#7fe3f2', 0.8)


def sakura(W, H, f, N):
    x, y, u, v = _xy(W, H)
    img = rgba(gradient(v, [(0, '#ffd9e6'), (0.5, '#ffeef3'), (1, '#dbe9ff')]))
    img = _glow(img, np.exp(-np.hypot(x + 0.5 - W * 0.72, y + 0.5 - H * 0.6) / 9), '#fff2d6', 0.6)
    mount = smoothstep(0, 0.01, v - (0.72 + 0.16 * np.abs(u - 0.62) * 2))
    img = _paint(img, mount, gradient(v, [(0.7, '#b7c7ee'), (1, '#93a6db')]), 0.7)
    # branch from the top left, with blossoms
    rng = np.random.default_rng(4)
    main = [(-1, H * 0.1), (W * 0.25, H * 0.2), (W * 0.45, H * 0.17), (W * 0.7, H * 0.3), (W * 0.82, H * 0.28)]
    twigs = [[(W * 0.25, H * 0.2), (W * 0.32, H * 0.36), (W * 0.3, H * 0.45)], [(W * 0.45, H * 0.17), (W * 0.52, H * 0.06)],
             [(W * 0.7, H * 0.3), (W * 0.66, H * 0.42)], [(W * 0.1, H * 0.15), (W * 0.15, H * 0.03)]]
    img = _paint(img, _lines_mask(W, H, [main], 2.2) + _lines_mask(W, H, twigs, 1.2), '#4a2a2a')
    blossoms = []
    for path in [main] + twigs:
        for (ax, ay), (bx, by) in zip(path, path[1:]):
            for t in np.linspace(0, 1, 5):
                blossoms.append((ax + (bx - ax) * t + rng.normal(0, 2.2), ay + (by - ay) * t + rng.normal(0, 2.2), 0.8 + rng.random() * 0.6))
    img = _paint(img, _dots(W, H, blossoms, 2.2, 0.5), '#f58fb5')
    img = _paint(img, _dots(W, H, blossoms, 1.4, 0.7), '#ffc2d8')
    img = _paint(img, _dots(W, H, [(b[0], b[1], 0.35) for b in blossoms], 1.6, 1.0), '#fff3f7')
    petals = _loop(24, 6, W, H, f, N, speeds=(1,), drift=1, sway=2.0, sway_k=2)
    img = _paint(img, _dots(W, H, [(p[0], p[1], 0.9 + 0.5 * p[2]) for p in petals], 1.2, 0.8), '#ff9ec4')
    return _frame_border(img, '#f9a8d4', 0.9, '#ffffff')


def firefly_forest(W, H, f, N):
    x, y, u, v = _xy(W, H)
    img = rgba(gradient(v, [(0, '#04121a'), (0.6, '#0b2a33'), (1, '#0f3a3d')]))
    img = over(img, stars(W, H, 20, 2, phase=f / N, big=0.1))
    for depth, (col, fog, seed) in enumerate((('#123a40', 0.0, 1), ('#0c2a2f', 0.15, 2), ('#06171b', 0.0, 3))):
        rng = np.random.default_rng(seed)
        trees = [(rng.random() * W, H * (0.78 + 0.08 * depth + rng.random() * 0.06), H * (0.32 + 0.12 * depth + rng.random() * 0.1),
                  W * (0.22 + 0.06 * depth)) for _ in range(5 - depth)]
        tm, _ = _pines(W, H, trees)
        img = _paint(img, tm, col)
        img = _paint(img, smoothstep(0.5, 1.0, v) * (0.25 - depth * 0.07), '#4fa3a0', 1.0)
    ground = smoothstep(0, 0.01, v - 0.93)
    img = _paint(img, ground, '#04110f')
    rng = np.random.default_rng(91)
    flies = []
    for i in range(14):
        bx, by, ph = rng.random() * W, H * (0.35 + rng.random() * 0.55), rng.random()
        t = 2 * math.pi * (f / N + ph)
        flies.append((bx + 2.2 * math.cos(t), by + 1.6 * math.sin(2 * t), 1.0, 0.45 + 0.55 * max(0.0, math.sin(t * 2 + ph * 9))))
    img = _glow(img, blur(_dots(W, H, flies, 1.6, 1.0), 1.8) * 3.0, '#c8ff5a', 1.0)
    img = _glow(img, _dots(W, H, flies, 1.1, 0.8), '#f6ffb0', 1.0)
    return _frame_border(img, '#5fbf8f', 0.8, '#d9ff9c')


def golden_hour(W, H, f, N):
    x, y, u, v = _xy(W, H)
    hz = 0.62
    img = rgba(gradient(v, [(0, '#2a1650'), (0.25, '#7a2b78'), (0.45, '#e2557a'), (0.56, '#ff9f45'), (hz, '#ffd27a'), (hz + 0.001, '#1d2f5c'), (1, '#0d1838')]))
    s = f * W // N
    clouds = np.roll(pfbm(W, H, 20, 15, 3), s, 1)
    band = np.exp(-((v - 0.3) / 0.12) ** 2) + np.exp(-((v - 0.48) / 0.05) ** 2) * 0.6
    img = _paint(img, smoothstep(0.5, 0.75, clouds) * band, '#ffb38a', 0.7)
    sx, sy, sr = W * 0.5, H * hz, W * 0.2
    img = _glow(img, np.exp(-np.hypot(x + 0.5 - sx, y + 0.5 - sy) / 8) * (v < hz), '#ffcf7a', 0.9)
    sun = mask_ellipse(W, H, sx, sy, sr, sr) * (v < hz)
    img = _paint(img, sun, gradient(v, [(0.45, '#fff3c4'), (hz, '#ffb347')]))
    sea = (v >= hz).astype(np.float32)
    wob = np.roll(pnoise(W, H, 10, 33), s, 1)
    ripple = np.sin(y * 2.3 + wob * 7 + 2 * math.pi * f / N * 2)
    refl = np.exp(-np.abs(x + 0.5 - sx) / (2.5 + np.maximum(v - hz, 0) * 26)) * (ripple > 0.1) * sea
    img = _glow(img, refl, '#ffd48a', 0.95)
    img = _glow(img, (ripple > 0.85) * sea * 0.22 * (1 - v), '#ff9f6b', 1.0)
    hills = smoothstep(0, 0.01, v - (0.86 + 0.06 * np.sin(u * 4 + 2)))
    img = _paint(img, hills, '#0a1024')
    return _frame_border(img, '#ffb36b', 0.85, '#ffe3a3')


def stormbringer(W, H, f, N):
    x, y, u, v = _xy(W, H)
    s = f * W // N
    clouds = np.roll(pfbm(W, H, 20, 61, 3), s, 1)
    flash = 0.0
    bolts = []
    rng = np.random.default_rng(5)
    for start, length, bx in ((4, 3, W * 0.35), (15, 2, W * 0.7)):
        k = f - start
        if 0 <= k < length:
            flash = max(flash, (1.0, 0.55, 0.25)[k])
        r2 = np.random.default_rng(int(bx))
        px, py = bx, H * 0.18
        path = [(px, py)]
        while py < H * 0.95:
            py += 3 + r2.random() * 3
            px += r2.normal(0, 2.4)
            path.append((px, py))
        bolts.append((path, flash if 0 <= k < length else 0.0))
    base = gradient(clouds * 0.85 + (1 - v) * 0.25 - 0.1, [(0, '#03040a'), (0.45, '#141a2e'), (0.75, '#3a4568'), (1, '#8a96bd')])
    img = rgba(base)
    img = _glow(img, smoothstep(0.35, 0.8, clouds) * flash, '#c9d7ff', 0.8)
    for path, b in bolts:
        if b > 0:
            m = _lines_mask(W, H, [path], 1.1)
            img = _glow(img, blur(m, 2.5) * 3.5, '#7da7ff', b)
            img = _paint(img, m, '#ffffff', b)
    rain = np.zeros((H, W), np.float32)
    for (rx, ry, r, k) in _loop(40, 9, W, H, f, N, speeds=(2, 3), drift=-1):
        rain = np.maximum(rain, _lines_mask(W, H, [((rx, ry), (rx - 1.0, ry + 3.5))], 0.5) * 0.5)
    img = _paint(img, rain, '#9fb4dd', 0.6)
    return _frame_border(img, '#60a5fa', 0.85, '#e0e7ff')


# ---------------------------------------------------------------- Neon

def synthwave(W, H, f, N):
    x, y, u, v = _xy(W, H)
    hz = 0.56
    img = rgba(gradient(v, [(0, '#0b0020'), (0.3, '#2a0a52'), (hz, '#ff3d8b'), (hz + 0.001, '#0d0221'), (1, '#1a0536')]))
    st = stars(W, H, 26, 3, phase=f / N)
    st[..., 3] *= v < hz * 0.7
    img = over(img, st)
    sx, sy, sr = W * 0.5, H * 0.4, W * 0.3
    sun = mask_ellipse(W, H, sx, sy, sr, sr) * (v < hz)
    cut = ((y.astype(int) - int(sy)) % 5 < (1 + (y - sy) / 6).astype(int)) & (y > sy)
    sun = sun * (~cut)
    img = _glow(img, np.exp(-np.hypot(x + 0.5 - sx, y + 0.5 - sy) / 10) * (v < hz), '#ff4fa3', 0.6)
    img = _paint(img, sun, gradient((y - (sy - sr)) / (2 * sr), [(0, '#fff07a'), (0.5, '#ff9f45'), (1, '#ff3d8b')]))
    ridge = hz - 0.07 * np.abs(np.sin(u * 7.0 + 1.0)) * np.abs(np.sin(u * 3 + 0.4))
    mount = (v > ridge) & (v < hz)
    img = _paint(img, mount, '#140530')
    img = _glow(img, np.exp(-np.abs(v - ridge) * H / 0.8) * (u > -1), '#ff4fd8', 0.8)
    floor = (y / H) > hz
    zt = 1.0 / np.maximum(y / H - hz, 1e-3)
    zb = 1.0 / np.maximum((y + 1) / H - hz, 1e-3)
    hl = np.floor(zt * 0.5 - f / N) != np.floor(zb * 0.5 - f / N)
    zc = (zt + zb) / 2
    vl = np.floor((x / W - 0.5) * zc * 0.9) != np.floor(((x + 1) / W - 0.5) * zc * 0.9)
    grid = (hl | vl) & floor
    gm = grid.astype(np.float32) * np.clip((v - hz) * 6, 0, 1)
    img = _glow(img, blur(gm, 0.8) * 1.6, '#ff2bd6', 0.7)
    img = _paint(img, gm, '#5ff7ff', 0.9)
    return _frame_border(img, '#ff2bd6', 0.9, '#5ff7ff')


GLYPHS = None


def code_rain(W, H, f, N):
    x, y, u, v = _xy(W, H)
    img = rgba(gradient(v, [(0, '#000a03'), (1, '#00140a')]))
    rng = np.random.default_rng(7)
    cols, rows, P = 10, 10, 20
    lit = np.zeros((H, W), np.float32)
    head = np.zeros((H, W), np.float32)
    for c in range(cols):
        p0, speed, trail = rng.integers(0, P), (1 if c % 3 else 2), int(rng.integers(5, 9))
        p = (p0 + f * speed) % P
        for r in range(rows):
            dist = (p - r) % P
            if dist >= trail:
                continue
            b = 1 - dist / trail
            g = np.random.default_rng(c * 131 + r * 17 + (f // 4) * 7).random((5, 3)) > 0.45
            g[2, 1] = g[2, 1] or dist == 0
            ox, oy = c * 4 + 1, r * 6 + 2
            sub = lit[oy:oy + 5, ox:ox + 3]
            np.maximum(sub, g[:sub.shape[0], :sub.shape[1]] * b, out=sub)
            if dist == 0:
                hs = head[oy:oy + 5, ox:ox + 3]
                np.maximum(hs, g[:hs.shape[0], :hs.shape[1]] * 1.0, out=hs)
    img = _glow(img, blur(lit, 1.0) * 0.9, '#00ff66', 0.6)
    img = _paint(img, lit, '#22ff77')
    img = _paint(img, head, '#e6ffe9')
    return _frame_border(img, '#00c853', 0.85, '#b9f6ca')


def prism(W, H, f, N):
    x, y, u, v = _xy(W, H)
    hue = (u * 0.55 + v * 0.45) * 1.2 - f / N
    img = rgba(_hsv(hue, 0.62, 1.0))
    shine = (np.sin((u * 0.55 + v * 0.45) * 40 - 2 * math.pi * f / N * 4) > 0.92) * 0.25
    img = _glow(img, shine, '#ffffff', 1.0)
    flake = snowflake_mask(W, H, W / 2, H * 0.47, W * 0.27, 1.8)
    img = _paint(img, blur(flake, 1.2) * 1.2, '#2b1a4a', 0.35)
    img = _paint(img, flake, '#ffffff')
    img = over(img, stars(W, H, 16, 13, phase=f / N, big=0.6))
    return _frame_border(img, '#ffffff', 0.9, '#f0abfc')


# ---------------------------------------------------------------- Royal / arcane

def arcane(W, H, f, N):
    x, y, u, v = _xy(W, H)
    base = gradient(v + 0.15 * fbm(W, H, 8, 2, 3), [(0, '#1a0633'), (0.6, '#2d0b52'), (1, '#140426')])
    img = rgba(base)
    cx, cy, R = W * 0.5, H * 0.46, W * 0.42
    a_out = 360 * f / N
    a_in = -60 * f / N
    segs = []
    ring = lambda r, n=64: [(cx + math.cos(2 * math.pi * i / n) * r, cy + math.sin(2 * math.pi * i / n) * r) for i in range(n + 1)]
    segs += [ring(R), ring(R * 0.8), ring(R * 0.38)]
    rng = np.random.default_rng(3)
    for k in range(12):
        a = math.radians(a_out + k * 30)
        r0, r1 = R * 0.83, R * 0.97
        mx, my = cx + math.cos(a) * (r0 + r1) / 2, cy + math.sin(a) * (r0 + r1) / 2
        tx, ty = -math.sin(a), math.cos(a)
        g = rng.integers(0, 4)
        if g == 0:
            segs.append([(mx - tx * 1.2, my - ty * 1.2), (mx + tx * 1.2, my + ty * 1.2)])
        elif g == 1:
            segs.append([(cx + math.cos(a) * r0, cy + math.sin(a) * r0), (cx + math.cos(a) * r1, cy + math.sin(a) * r1)])
        elif g == 2:
            segs.append([(mx - tx * 1.0 - math.cos(a), my - ty * 1.0 - math.sin(a)), (mx + math.cos(a), my + math.sin(a)), (mx + tx * 1.0 - math.cos(a), my + ty * 1.0 - math.sin(a))])
        else:
            segs.append(ring(0.0)[:0] + [(mx + math.cos(t) * 0.9, my + math.sin(t) * 0.9) for t in np.linspace(0, 2 * math.pi, 9)])
    for flip in (0, 45):
        pts = [(cx + math.cos(math.radians(a_in * 1.5 + flip + 90 * i)) * R * 0.78, cy + math.sin(math.radians(a_in * 1.5 + flip + 90 * i)) * R * 0.78) for i in range(5)]
        segs.append(pts)
    circle = _lines_mask(W, H, segs, 0.8)
    pulse = 0.75 + 0.25 * math.sin(2 * math.pi * f / N * 2)
    img = _glow(img, blur(circle, 1.8) * 2.0, '#b25cff', pulse)
    img = _paint(img, circle, '#f3d9ff')
    glint = np.abs(np.sin((u * 0.8 - v * 0.6) * 9 + 2 * math.pi * f / N * 2)) ** 30 * 0.6
    img = _glow(img, glint, '#c58bff', 1.0)
    img = over(img, stars(W, H, 18, 40, phase=f / N, colour='#f5d0fe', big=0.5))
    return _frame_border(img, '#a855f7', 0.9, '#f5d0fe')


def _crest(W, H, cx, cy, s):
    shield = mask_polys(W, H, [[(cx - s, cy - s * 1.1), (cx + s, cy - s * 1.1), (cx + s, cy + s * 0.2), (cx, cy + s * 1.35), (cx - s, cy + s * 0.2)]])
    inner = mask_polys(W, H, [[(cx - s * 0.78, cy - s * 0.9), (cx + s * 0.78, cy - s * 0.9), (cx + s * 0.78, cy + s * 0.12), (cx, cy + s * 1.05), (cx - s * 0.78, cy + s * 0.12)]])
    crown = mask_polys(W, H, [[(cx - s * 0.8, cy - s * 1.25), (cx - s * 0.8, cy - s * 1.9), (cx - s * 0.4, cy - s * 1.5), (cx, cy - s * 2.1),
                               (cx + s * 0.4, cy - s * 1.5), (cx + s * 0.8, cy - s * 1.9), (cx + s * 0.8, cy - s * 1.25)]])
    return shield, inner, crown


def regal(W, H, f, N):
    x, y, u, v = _xy(W, H)
    folds = 0.82 + 0.18 * np.sin(u * math.pi * 5 + 0.3 * np.sin(v * 6))
    img = rgba(gradient(v, [(0, '#8c0f1f'), (1, '#4a0610')]) * folds[..., None])
    border = ((x < 4) | (x >= W - 4) | (y < 4) | (y >= H - 4)).astype(np.float32)
    pattern = ((np.sin(x * 1.6) * np.sin(y * 1.6)) > 0.2) * border * 0.35
    gold_col = gradient(v * 0.6 + 0.4 * u, [(0, '#fff1a8'), (0.5, '#f4c542'), (1, '#b9781c')])
    img = _paint(img, border, gold_col)
    img = _paint(img, pattern, '#8a5a12', 0.8)
    shield, inner, crown = _crest(W, H, W / 2, H * 0.5, W * 0.24)
    img = _paint(img, shield, gold_col)
    img = _paint(img, inner, '#1e2f6e')
    img = _paint(img, crown, gold_col)
    img = _paint(img, snowflake_mask(W, H, W / 2, H * 0.5, W * 0.13, 1.0), '#ffffff')
    for gx in (-0.4, 0, 0.4):
        img = _paint(img, mask_ellipse(W, H, W / 2 + gx * W * 0.24, H * 0.5 - W * 0.24 * 1.42, 0.9, 0.9), '#e11d48' if gx else '#38bdf8')
    gm = np.clip(border + shield + crown, 0, 1)
    img = _glow(img, _sweep(W, H, f, N, 0.06) * gm, '#ffffff', 0.9)
    return img


def gilded(W, H, f, N):
    x, y, u, v = _xy(W, H)
    col = gradient(v * 0.7 + 0.3 * fbm(W, H, 10, 4, 3), [(0, '#fff1a8'), (0.45, '#f4c542'), (1, '#9a5f12')])
    img = rgba(col)
    a, b = (x + 0.5) / 8 + (y + 0.5) / 8, (x + 0.5) / 8 - (y + 0.5) / 8
    eng = ((np.abs(a - np.round(a)) < 0.07) | (np.abs(b - np.round(b)) < 0.07)).astype(np.float32)
    img = _paint(img, eng, '#8a5a12', 0.5)
    dots = (np.abs(a - np.round(a) - 0.5) < 0.1) & (np.abs(b - np.round(b) - 0.5) < 0.1)
    img = _paint(img, dots.astype(np.float32), '#fff3c4', 0.7)
    flake = snowflake_mask(W, H, W / 2, H * 0.47, W * 0.3, 1.8)
    img = _paint(img, blur(flake, 1.0), '#7a4a0c', 0.6)
    img = _paint(img, flake, '#fff8d6')
    img = _glow(img, _sweep(W, H, f, N, 0.08), '#ffffff', 0.9)
    img = over(img, stars(W, H, 10, 3, phase=f / N, colour='#fffbe6', big=0.8))
    return _frame_border(img, '#7a4a0c', 0.7, '#fff1a8')


# ---------------------------------------------------------------- Classic (simple, one colour, the Polar snowflake)

def classic(main, dark, emblem):
    def paint(W, H, f, N):
        x, y, u, v = _xy(W, H)
        n = vnoise(W, H, 6, 2)
        img = rgba(gradient(v * 0.8 + n * 0.2, [(0, main), (1, dark)]))
        iy, ix = np.mgrid[0:H, 0:W]
        img[..., :3] *= (1 - 0.05 * ((ix + iy) % 2))[..., None]
        flake = snowflake_mask(W, H, W / 2, H * 0.42, W * 0.3, 1.3)
        img = _paint(img, blur(flake, 1.0) * 0.8, '#000000', 0.25)
        img = _paint(img, flake, emblem)
        stripe = ((y > H - 9) & (y < H - 6)).astype(np.float32)
        img = _paint(img, stripe, emblem, 0.85)
        return _frame_border(img, emblem, 0.6, emblem)
    return paint


# ---------------------------------------------------------------- Special (handed out, not free)

def founder(W, H, f, N):
    x, y, u, v = _xy(W, H)
    img = rgba(gradient(v, [(0, '#050b1f'), (0.6, '#0b1a40'), (1, '#132a5e')]))
    s = f * W // N
    edge = 0.25 + 0.12 * (pnoise(W, 8, 10, 3)[0] - 0.5)
    edge = np.roll(edge, s)[None, :]
    d = v - edge
    img = add_light(img, gradient(np.clip(-d * 3, 0, 1), [(0, '#5cf2d6'), (1, '#7a7cff')]), np.clip(np.where(d < 0, np.exp(d / 0.15), np.exp(-(d / 0.03) ** 2)) * 0.8, 0, 1))
    img = over(img, stars(W, H, 30, 8, phase=f / N, big=0.3))
    cx, cy = W / 2, H * 0.5
    ring = mask_ellipse(W, H, cx, cy, W * 0.33, W * 0.33) - mask_ellipse(W, H, cx, cy, W * 0.28, W * 0.28)
    gold_col = gradient(v * 0.6 + 0.4 * u, [(0, '#fff1a8'), (0.5, '#f4c542'), (1, '#b9781c')])
    leaves = []
    for side in (-1, 1):
        for i in range(6):
            a = math.radians(110 + i * 22) if side < 0 else math.radians(70 - i * 22)
            lx, ly = cx + math.cos(a) * W * 0.4, cy + math.sin(a) * W * 0.4
            leaves.append((lx, ly, 1.0))
    img = _paint(img, _dots(W, H, leaves, 1.8, 0.4), gold_col)
    img = _paint(img, ring, gold_col)
    flake = snowflake_mask(W, H, cx, cy, W * 0.22, 1.6)
    img = _glow(img, blur(flake, 1.6) * 1.8, '#5cf2ff', 0.8)
    img = _paint(img, flake, '#ffffff')
    crown = _crest(W, H, cx, cy + W * 0.06, W * 0.2)[2]
    crown = np.roll(crown, -int(W * 0.17), 0)
    img = _paint(img, crown, gold_col)
    img = _glow(img, _sweep(W, H, f, N, 0.06) * np.clip(ring + crown, 0, 1), '#ffffff', 1.0)
    return _frame_border(img, '#f4c542', 0.95, '#fff1a8')


def supporter(W, H, f, N):
    x, y, u, v = _xy(W, H)
    img = rgba(gradient(v, [(0, '#3b0a45'), (0.5, '#86198f'), (1, '#db2777')]))
    hearts = _loop(10, 12, W, H, f, N, speeds=(1,), dir=-1, sway=1.5)
    from paint import heart_sprite
    hs = heart_sprite(9)[..., 3]
    small = np.zeros((H, W), np.float32)
    for hx, hy, r, k in hearts:
        ix, iy = int(hx) - 4, int(hy) - 4
        for ox in (0,):
            x0, y0 = max(0, ix), max(0, iy)
            x1, y1 = min(W, ix + 9), min(H, iy + 9)
            if x1 > x0 and y1 > y0:
                small[y0:y1, x0:x1] = np.maximum(small[y0:y1, x0:x1], hs[y0 - iy:y1 - iy, x0 - ix:x1 - ix] * 0.5)
    img = _paint(img, small, '#ffc2e2', 0.6)
    beat = 1 + 0.08 * max(0.0, math.sin(2 * math.pi * f / N * 2)) ** 4
    cx, cy, s = W / 2, H * 0.46, W * 0.36 * beat
    heart = heart_mask(W, H, cx, cy, s)
    img = _glow(img, blur(heart, 2.5) * 1.5, '#ff4fa3', 0.8)
    img = _paint(img, heart, gradient(v, [(0.3, '#ff7ab8'), (0.7, '#e11d74')]))
    img = _glow(img, heart * np.exp(-((x + 0.5 - cx + s * 0.4) ** 2 + (y + 0.5 - cy + s * 0.3) ** 2) / 8), '#ffffff', 0.7)
    return _frame_border(img, '#f9a8d4', 0.9, '#ffffff')


def staff(W, H, f, N):
    x, y, u, v = _xy(W, H)
    img = rgba(gradient(v, [(0, '#05121c'), (1, '#0b2233')]))
    rng = np.random.default_rng(2)
    paths = []
    for i in range(9):
        px, py = int(rng.integers(2, W - 2)), 0 if i % 2 else H
        pts = [(px, py)]
        for _ in range(5):
            if rng.random() < 0.5:
                py = py + (1 if i % 2 else -1) * int(rng.integers(6, 14))
            else:
                px = int(np.clip(px + rng.choice([-1, 1]) * int(rng.integers(4, 10)), 2, W - 2))
            pts.append((px, py))
        paths.append(pts)
    traces = _lines_mask(W, H, paths, 0.9)
    img = _paint(img, traces, '#0f4c5c')
    pulses = []
    for i, pts in enumerate(paths):
        seglen = [math.hypot(b[0] - a[0], b[1] - a[1]) for a, b in zip(pts, pts[1:])]
        total = sum(seglen)
        t = ((f / N) + i * 0.37) % 1.0 * total
        for (a, b), L in zip(zip(pts, pts[1:]), seglen):
            if t <= L and L > 0:
                pulses.append((a[0] + (b[0] - a[0]) * t / L, a[1] + (b[1] - a[1]) * t / L))
                break
            t -= L
        for px, py in pts[1:-1]:
            pulses.append((px, py, 0.6, 0.5))
    img = _glow(img, blur(_dots(W, H, pulses, 1.5, 1.0), 1.2) * 2, '#22d3ee', 0.8)
    img = _paint(img, _dots(W, H, pulses, 1.0, 1.0), '#cffafe')
    cx, cy = W / 2, H * 0.45
    shield, inner, _ = _crest(W, H, cx, cy, W * 0.26)
    img = _paint(img, shield, '#22d3ee')
    img = _paint(img, inner, '#071a26')
    img = _paint(img, snowflake_mask(W, H, cx, cy - 0.5, W * 0.15, 1.2), '#e0fbff')
    img = _glow(img, _sweep(W, H, f, N, 0.05) * shield, '#ffffff', 0.8)
    return _frame_border(img, '#22d3ee', 0.95, '#a5f3fc')


def creator(W, H, f, N):
    x, y, u, v = _xy(W, H)
    img = rgba(gradient(v + 0.1 * np.sin(u * 3), [(0, '#1e0b3d'), (0.55, '#7c1d6f'), (1, '#f97316')]))
    cx, cy = W / 2, H * 0.44
    dx, dy = x + 0.5 - cx, y + 0.5 - cy
    r = np.hypot(dx, dy)
    th = np.arctan2(dy, dx)
    rays = (np.cos(th * 8 - 2 * math.pi * f / N) * 0.5 + 0.5) ** 6 * np.exp(-r / 14)
    img = _glow(img, rays, '#ffd1a3', 0.5)
    spike = np.abs(np.cos(th * 2)) ** 10
    star = np.clip(1 - r / (2.0 + 12 * spike), 0, 1) ** 0.8
    img = _glow(img, blur(star, 1.5) * 1.5, '#ffb3f0', 0.8)
    img = _paint(img, star, '#fff6fb')
    orbit = []
    for i in range(3):
        a = 2 * math.pi * (f / N + i / 3)
        orbit.append((cx + math.cos(a) * W * 0.32, cy + math.sin(a) * W * 0.32 * 0.6, 1.0))
    img = _glow(img, _dots(W, H, orbit, 1.6, 1.0), '#ffe08a', 1.0)
    img = over(img, stars(W, H, 20, 77, phase=f / N, big=0.4))
    return _frame_border(img, '#fb7185', 0.9, '#fde68a')


# ---------------------------------------------------------------- build

def build(out, item):
    frames = item.get('frames', 1)
    tex = cape_texture(lambda W, H, f: item['paint'](W, H, f, frames), frames=frames, edge=item.get('edge'))
    save_png(tex, f"{out}/textures/{item['id']}.png")
    return {'texture': f"textures/{item['id']}.png", 'frames': frames, 'frameTime': item.get('frameTime', 100)}


def _c(id, name, collection, rarity, paint, description, frames=1, frameTime=100, edge=None, **kw):
    return dict(id=id, name=name, collection=collection, rarity=rarity, paint=paint, description=description,
                frames=frames, frameTime=frameTime, edge=edge, **kw)


ITEMS = [
    # Polar
    _c('cape_aurora', 'Aurora Cape', 'polar', 'legendary', aurora, 'Northern lights rolling over snowy peaks.', 20, 90, '#0a1633'),
    _c('cape_glacier', 'Glacier', 'polar', 'epic', glacier, 'Faceted glacier ice with a light that sweeps across it.', 20, 80, '#9fd2ff'),
    _c('cape_polar_night', 'Polar Night', 'polar', 'epic', polar_night, 'Snow drifting past pines under a crescent moon.', 20, 110, '#0a1838'),
    _c('cape_cozy_cabin', 'Cozy Cabin', 'polar', 'rare', cozy_cabin, 'A warm little cabin in the falling snow.', 20, 110, '#4e6aa8'),
    _c('cape_frostbite', 'Frostbite', 'polar', 'rare', frostbite, 'Frost creeps in from every edge and pulses with cold light.', 16, 100, '#0c1d40'),
    # Inferno
    _c('cape_inferno', 'Inferno', 'inferno', 'legendary', inferno, 'A wall of living flame.', 16, 70, '#3a0806'),
    _c('cape_molten', 'Molten Core', 'inferno', 'epic', molten, 'Cracked basalt over rivers of lava.', 16, 100, '#1d1213'),
    _c('cape_ember', 'Ember', 'inferno', 'rare', ember, 'Glowing embers drifting up from the coals.', 20, 90, '#1d1213'),
    # Cosmic
    _c('cape_galaxy', 'Galaxy', 'cosmic', 'legendary', galaxy, 'A spiral galaxy turning slowly among the stars.', 24, 110, '#0a0820'),
    _c('cape_nebula', 'Nebula', 'cosmic', 'epic', nebula, 'Clouds of pink and blue star-stuff drifting by.', 20, 120, '#0b0522'),
    _c('cape_event_horizon', 'Event Horizon', 'cosmic', 'legendary', event_horizon, 'A black hole with a blazing, swirling disk.', 24, 80, '#07031a'),
    # Nature
    _c('cape_ocean', 'Ocean Depths', 'nature', 'epic', ocean_depths, 'Sunbeams, swaying kelp and a passing school of fish.', 20, 110, '#0a3d73'),
    _c('cape_sakura', 'Sakura', 'nature', 'epic', sakura, 'Cherry blossoms with petals on the wind.', 20, 110, '#f9a8d4'),
    _c('cape_firefly_forest', 'Firefly Forest', 'nature', 'rare', firefly_forest, 'Fireflies drifting through a misty forest.', 20, 110, '#0b2a33'),
    _c('cape_golden_hour', 'Golden Hour', 'nature', 'rare', golden_hour, 'The sun sinking into a shimmering sea.', 20, 120, '#7a2b78'),
    _c('cape_storm', 'Stormbringer', 'nature', 'epic', stormbringer, 'Rolling storm clouds, rain and lightning.', 20, 90, '#1b2236'),
    # Neon
    _c('cape_synthwave', 'Synthwave', 'neon', 'legendary', synthwave, 'A neon grid racing toward a striped sun.', 16, 80, '#2a0a52'),
    _c('cape_code_rain', 'Code Rain', 'neon', 'epic', code_rain, 'Falling streams of glowing green code.', 20, 90, '#00140a'),
    _c('cape_prism', 'Prism', 'neon', 'rare', prism, 'Every colour of the rainbow flowing across the Polar snowflake.', 20, 90, '#ffffff'),
    # Royal
    _c('cape_regal', 'Regal', 'royal', 'epic', regal, 'Crimson velvet, gold trim and a royal crest.', 16, 90, '#4a0610'),
    _c('cape_gilded', 'Gilded', 'royal', 'rare', gilded, 'Engraved gold that catches the light.', 16, 90, '#9a5f12'),
    _c('cape_arcane', 'Arcane Circle', 'royal', 'legendary', arcane, 'A glowing magic circle that turns as you move.', 24, 120, '#1a0633'),
    # Classic
    _c('cape_classic_ice', 'Polar Classic: Ice', 'classic', 'common', classic('#3b82f6', '#1e3a8a', '#ffffff'), 'The classic Polar cape in ice blue.', edge='#1e3a8a'),
    _c('cape_classic_midnight', 'Polar Classic: Midnight', 'classic', 'common', classic('#1f2937', '#0b1120', '#5ce1ff'), 'The classic Polar cape in midnight black.', edge='#0b1120'),
    _c('cape_classic_crimson', 'Polar Classic: Crimson', 'classic', 'common', classic('#dc2626', '#7f1d1d', '#ffffff'), 'The classic Polar cape in crimson.', edge='#7f1d1d'),
    _c('cape_classic_snow', 'Polar Classic: Snow', 'classic', 'common', classic('#ffffff', '#cfdcec', '#2563eb'), 'The classic Polar cape in snow white.', edge='#cfdcec'),
    # Special
    _c('cape_founder', 'Founder', 'special', 'legendary', founder, 'For the people who were here first.', 20, 100, '#0b1a40', special=True),
    _c('cape_supporter', 'Supporter', 'special', 'epic', supporter, 'Thank you for supporting Polar.', 20, 90, '#86198f', special=True),
    _c('cape_staff', 'Polar Staff', 'special', 'legendary', staff, 'Worn by the people who build Polar.', 20, 90, '#0b2233', special=True),
    _c('cape_creator', 'Creator', 'special', 'epic', creator, 'For creators who make videos and streams with Polar.', 20, 100, '#7c1d6f', special=True),
]
