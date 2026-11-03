"""Painting helpers shared by the cosmetics: shapes, materials and sprites."""
import math

import numpy as np
from PIL import Image, ImageDraw

from lib import rgb, rgba, gradient, smoothstep, blur, over, bevel, solid, vnoise, fbm, add_light


def mask_polys(w, h, polys, scale=4):
    """Antialiased coverage (0..1) of polygons given in texel coordinates."""
    im = Image.new('L', (w * scale, h * scale), 0)
    d = ImageDraw.Draw(im)
    for poly in polys:
        d.polygon([(x * scale, y * scale) for x, y in poly], fill=255)
    return np.asarray(im.resize((w, h), Image.LANCZOS), np.float32) / 255


def mask_ellipse(w, h, cx, cy, rx, ry, scale=4):
    im = Image.new('L', (w * scale, h * scale), 0)
    ImageDraw.Draw(im).ellipse([(cx - rx) * scale, (cy - ry) * scale, (cx + rx) * scale, (cy + ry) * scale], fill=255)
    return np.asarray(im.resize((w, h), Image.LANCZOS), np.float32) / 255


def edges(mask, width=1.0):
    """A soft outline (0..1) just inside the edge of a mask."""
    inner = blur(mask, width)
    return np.clip((mask - inner) * 3 + (mask > 0.5) * (inner < 0.75) * 0.6, 0, 1) * (mask > 0.05)


def coords(w, h):
    y, x = np.mgrid[0:h, 0:w].astype(np.float32)
    return (x + 0.5) / w, (y + 0.5) / h


def shard(base, angle_deg, length, width, side_ratio=0.3):
    """A crystal/feather diamond from base point, pointing at angle (0 = right, 90 = down)."""
    a = math.radians(angle_deg)
    dx, dy = math.cos(a), math.sin(a)
    px, py = -dy, dx
    bx, by = base
    mx, my = bx + dx * length * side_ratio, by + dy * length * side_ratio
    tip = (bx + dx * length, by + dy * length)
    return [(bx, by), (mx + px * width, my + py * width), tip, (mx - px * width, my - py * width)]


def shard_halves(base, angle_deg, length, width, side_ratio=0.3):
    """The two long halves of a shard, for facet shading."""
    p = shard(base, angle_deg, length, width, side_ratio)
    return [p[0], p[1], p[2]], [p[0], p[2], p[3]]


# ---------------------------------------------------------------- materials (fill a face of size w x h)

def metal(w, h, top, mid, bottom, seed=1, grain=0.06, light=0.22, dark=0.3):
    _, y = coords(w, h)
    col = gradient(y, [(0, top), (0.5, mid), (1, bottom)])
    n = vnoise(w, h, max(2, w // 3), seed)
    col = np.clip(col * (1 - grain + grain * 2 * n[..., None]), 0, 1)
    return bevel(rgba(col), light, dark, max(1, min(w, h) // 8))


def gold(w, h, seed=1):
    return metal(w, h, '#fff1a8', '#f4c542', '#b9781c', seed)


def silver(w, h, seed=1):
    return metal(w, h, '#ffffff', '#c9d6e3', '#7d8ea3', seed)


def ice(w, h, seed=1, alpha=0.88):
    x, y = coords(w, h)
    n = fbm(w, h, max(3, w // 3), seed, 3)
    col = gradient(y * 0.6 + n * 0.4, [(0, '#f2fbff'), (0.35, '#a9e0ff'), (0.7, '#5aa8f0'), (1, '#2f6fcf')])
    # cracks
    c = np.abs(fbm(w, h, max(3, w // 2), seed + 9, 2) - 0.5) < 0.03
    col[c] = col[c] * 0.6 + rgb('#ffffff') * 0.4
    return bevel(rgba(col, alpha), 0.25, 0.25, max(1, min(w, h) // 8))


def wood(w, h, seed=1, light='#a0703f', dark='#5c3a1e'):
    x, y = coords(w, h)
    n = vnoise(w, h, max(2, w // 2), seed)
    rings = (np.sin((y * 6 + n * 3) * math.pi * 2) + 1) / 2
    col = gradient(rings * 0.6 + n * 0.4, [(0, dark), (1, light)])
    return bevel(rgba(col), 0.12, 0.25, max(1, min(w, h) // 8))


def fabric(w, h, a, b, seed=1, weave=0.06):
    x, y = coords(w, h)
    n = vnoise(w, h, max(2, w // 4), seed)
    col = gradient(y * 0.5 + n * 0.5, [(0, a), (1, b)])
    iy, ix = np.mgrid[0:h, 0:w]
    col *= (1 - weave * ((ix + iy) % 2))[..., None]
    return bevel(rgba(col), 0.12, 0.22, 1)


def leather(w, h, seed=1, a='#8a5a35', b='#4f2f19'):
    return fabric(w, h, a, b, seed, weave=0.03)


# ---------------------------------------------------------------- particle sprites (frames side by side)

def sprite_strip(frames, size, draw):
    """draw(i, size) -> size x size x 4 image; returns a strip of `frames` sprites."""
    return np.concatenate([draw(i, size) for i in range(frames)], 1)


def soft_dot(size, sharp=2.5):
    x, y = coords(size, size)
    r = np.hypot(x - 0.5, y - 0.5) * 2
    a = np.clip(1 - r, 0, 1) ** sharp
    return rgba(np.ones((size, size, 3), np.float32), a)


def star_sprite(size, points=4, inner=0.18, sharp=1.6):
    x, y = coords(size, size)
    dx, dy = x - 0.5, y - 0.5
    r = np.hypot(dx, dy) * 2
    ang = np.arctan2(dy, dx)
    spikes = np.abs(np.cos(ang * points / 2)) ** 6
    shape = np.clip(1 - r / (inner + (1 - inner) * spikes), 0, 1) ** sharp
    core = np.clip(1 - r * 3, 0, 1)
    return rgba(np.ones((size, size, 3), np.float32), np.clip(shape + core, 0, 1))


def snowflake_sprite(size, variant=0):
    im = Image.new('L', (size * 4, size * 4), 0)
    d = ImageDraw.Draw(im)
    c = size * 2
    L = size * 1.75
    wid = max(2, size // 5)
    for k in range(6):
        a = math.radians(k * 60 + variant * 15)
        ex, ey = c + math.cos(a) * L, c + math.sin(a) * L
        d.line([(c, c), (ex, ey)], fill=255, width=wid)
        for t, bl in ((0.55, 0.38), (0.8, 0.25)):
            mx, my = c + math.cos(a) * L * t, c + math.sin(a) * L * t
            for s in (-1, 1):
                b = a + s * math.radians(45)
                d.line([(mx, my), (mx + math.cos(b) * L * bl, my + math.sin(b) * L * bl)], fill=255, width=max(2, wid - 1))
    d.ellipse([c - wid * 1.5, c - wid * 1.5, c + wid * 1.5, c + wid * 1.5], fill=255)
    a = np.asarray(im.resize((size, size), Image.LANCZOS), np.float32) / 255
    return rgba(np.ones((size, size, 3), np.float32), np.clip(a * 1.3, 0, 1))


def heart_sprite(size, ss=4):
    y, x = np.mgrid[0:size * ss, 0:size * ss].astype(np.float32)
    X, Y = ((x + 0.5) / (size * ss) - 0.5) * 2.5, (0.47 - (y + 0.5) / (size * ss)) * 2.5
    inside = ((X ** 2 + Y ** 2 - 1) ** 3 - X ** 2 * Y ** 3 < 0).astype(np.float32)
    a = inside.reshape(size, ss, size, ss).mean((1, 3))
    return rgba(np.ones((size, size, 3), np.float32), a)


def note_sprite(size, variant=0):
    im = Image.new('L', (size * 4, size * 4), 0)
    d = ImageDraw.Draw(im)
    s = size * 4
    d.ellipse([s * 0.15, s * 0.62, s * 0.5, s * 0.88], fill=255)
    d.rectangle([s * 0.42, s * 0.12, s * 0.5, s * 0.76], fill=255)
    if variant % 2:
        d.ellipse([s * 0.55, s * 0.5, s * 0.88, s * 0.76], fill=255)
        d.rectangle([s * 0.8, s * 0.06, s * 0.88, s * 0.64], fill=255)
        d.polygon([(s * 0.42, s * 0.12), (s * 0.88, s * 0.06), (s * 0.88, s * 0.18), (s * 0.42, s * 0.24)], fill=255)
    else:
        d.polygon([(s * 0.42, s * 0.12), (s * 0.75, s * 0.3), (s * 0.7, s * 0.4), (s * 0.5, s * 0.28)], fill=255)
    a = np.asarray(im.resize((size, size), Image.LANCZOS), np.float32) / 255
    return rgba(np.ones((size, size, 3), np.float32), a)


def petal_sprite(size, variant=0):
    x, y = coords(size, size)
    a = math.radians(variant * 50)
    dx, dy = x - 0.5, y - 0.5
    u = dx * math.cos(a) + dy * math.sin(a)
    v = -dx * math.sin(a) + dy * math.cos(a)
    shape = (u / 0.42) ** 2 + (v / 0.22) ** 2 * (1 + 0.6 * (u > 0.2))
    al = np.clip((1 - shape) * 4, 0, 1)
    col = gradient(np.clip(u + 0.5, 0, 1), [(0, '#ffffff'), (1, '#ffe1ee')])
    return rgba(col, al)
