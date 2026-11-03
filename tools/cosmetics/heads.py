"""Head items: halos, crowns, ears, hats and more. Anchored to the head (pixels; the head is the box
(-4, -8, -4)..(4, 0, 4), y down, the face at -z), so they follow it as the player looks around."""
import math

import numpy as np

from lib import Model, Bone, Cube, card, rgb, rgba, gradient, bevel, solid, fbm, vnoise, blur, stars, FACES
from paint import gold, silver, ice, wood, fabric, leather, metal, coords, mask_polys, mask_ellipse, edges, snowflake_sprite


# ---------------------------------------------------------------- painting helpers

def M(fn, *a, **k):
    """A material for every face: fn(w, h, ...) -> image."""
    return lambda face, w, h, f: fn(w, h, *a, **k)


def faces(default, **per_face):
    """A per-face painter dict: per_face overrides (front/back/left/right/top/bottom)."""
    return {fc: per_face.get(fc, default) for fc in FACES}


def fur(a, b, seed=1, streak=0.12):
    def p(face, w, h, f):
        x, y = coords(w, h)
        n = vnoise(w, h, max(2, w // 3), seed)
        s = vnoise(max(1, w), h, 1, seed + 5)
        col = gradient(y * 0.55 + n * 0.45, [(0, a), (1, b)]) * (1 - streak + streak * s)[..., None]
        return bevel(rgba(col), 0.1, 0.18, 1)
    return p


def knit(a, b, seed=1):
    def p(face, w, h, f):
        x, y = coords(w, h)
        ix = np.arange(w)[None, :].repeat(h, 0)
        rib = 0.82 + 0.18 * (np.abs((ix % 4) - 1.5) / 1.5)
        n = vnoise(w, h, max(2, w // 4), seed)
        col = gradient(y * 0.4 + n * 0.6, [(0, a), (1, b)]) * rib[..., None]
        return bevel(rgba(col), 0.08, 0.15, 1)
    return p


def felt(a, b, seed=1):
    return lambda face, w, h, f: fabric(w, h, a, b, seed, weave=0.04)


def glowc(colour, alpha=0.8):
    return lambda face, w, h, f: solid(w, h, colour, alpha)


def gem(colour, hi='#ffffff'):
    def p(face, w, h, f):
        x, y = coords(w, h)
        c = gradient(np.clip(x * 0.5 + y * 0.5, 0, 1), [(0, hi), (0.35, colour), (1, '#000000')])
        c = c * 0.7 + rgb(colour) * 0.3
        return bevel(rgba(c), 0.3, 0.3, 1)
    return p


def stack(x, y, z, layers, paint, glow=None, tint=False):
    """Boxes stacked upward from y (smaller as they go), centred on x, z: [(w, h, d), ...]."""
    out = []
    for w, h, d in layers:
        out.append(Cube((x - w / 2, y - h, z - d / 2), (w, h, d), paint, glow=glow, tint=tint))
        y -= h
    return out


def chain(name, pivot, rot, segs, paints, glow=None, tint=False, i=0):
    """A bendy chain of boxes going up (-y): segs = [((w, h, d), rot_of_next), ...]."""
    (w, h, d), nxt = segs[0]
    paint = paints[min(i, len(paints) - 1)] if isinstance(paints, list) else paints
    g = (glow[min(i, len(glow) - 1)] if isinstance(glow, list) else glow)
    cube = Cube((-w / 2, -h, -d / 2), (w, h, d), paint, glow=g, tint=tint)
    children = [chain(name, (0, -h + 0.05, 0), nxt, segs[1:], paints, glow, tint, i + 1)] if len(segs) > 1 else []
    return Bone(f'{name}{i}', pivot, rot, [cube], children)


def square_band(name, y, h, paint, glow=None, out=4.35, t=0.5, tint=False):
    """A band around the (square) head at height y..y-h, `out` pixels from the centre."""
    s = out * 2
    cubes = [
        Cube((-out, y - h, -out - t / 2), (s, h, t), paint, glow, tint),
        Cube((-out, y - h, out - t / 2), (s, h, t), paint, glow, tint),
        Cube((-out - t / 2, y - h, -out + t / 2), (t, h, s - t), paint, glow, tint),
        Cube((out - t / 2, y - h, -out + t / 2), (t, h, s - t), paint, glow, tint),
    ]
    return Bone(name, (0, 0, 0), (0, 0, 0), cubes)


def around(out=4.35, n=3, gap=2.8):
    """Points on the four sides of the head, n per side: (x, z, outward angle in degrees)."""
    pts = []
    offs = [(i - (n - 1) / 2) * gap for i in range(n)]
    for o in offs:
        pts += [(o, -out, 180), (o, out, 0), (-out, o, 270), (out, o, 90)]
    return pts


def ring(name, radius, y, segments, size, paint, glow=None, tint=False):
    """A flat ring of small boxes around the head's vertical axis."""
    children = []
    sx, sy, sz = size
    for i in range(segments):
        children.append(Bone(f'{name}_{i}', pivot=(0, 0, 0), rot=(0, 360 * i / segments, 0),
                             cubes=[Cube((-sx / 2, -sy / 2, -radius - sz / 2), (sx, sy, sz), paint, glow=glow, tint=tint)]))
    return Bone(name, pivot=(0, y, 0), children=children)


GOLD = M(gold, 3)
SILVER = M(silver, 2)
ICE = M(ice, 5, 0.92)


def _gold_glow(face, w, h, f):
    x, y = coords(w, h)
    a = np.clip(0.55 - np.abs(y - 0.5) * 0.6, 0, 1)
    return rgba(np.broadcast_to(rgb('#ffe9a0'), (h, w, 3)).copy(), a)


def _ice_glow(face, w, h, f):
    x, y = coords(w, h)
    return rgba(np.broadcast_to(rgb('#9ff6ff'), (h, w, 3)).copy(), np.clip(0.55 - np.abs(x - 0.5), 0, 1) * 0.8)


# ---------------------------------------------------------------- halos

def halo():
    root = ring('halo', 4.6, -11.2, 18, (1.8, 0.8, 0.9), GOLD, _gold_glow)
    return Model('head', [root], density=4, anims=[
        {'bone': 'halo', 'type': 'spin', 'rot': [0, 40, 0]},
        {'bone': 'halo', 'type': 'wave', 'pos': [0, 0.35, 0], 'speed': 0.5, 'speedMove': 0.5},
    ])


def fallen_halo():
    paint = M(metal, '#5a1010', '#2a0608', '#100204', 4)
    glow = lambda face, w, h, f: rgba(np.broadcast_to(rgb('#ff3030'), (h, w, 3)).copy(), np.full((h, w), 0.75 if face in ('front', 'back') else 0.4, np.float32))
    root = ring('halo', 4.8, 0, 20, (1.7, 0.7, 0.8), paint, glow)
    tilt = Bone('tilt', (0, -11.4, 0), (14, 0, -10), children=[root])
    return Model('head', [tilt], density=4, anims=[
        {'bone': 'halo', 'type': 'spin', 'rot': [0, -30, 0]},
        {'bone': 'tilt', 'type': 'wave', 'pos': [0, 0.3, 0], 'rot': [3, 0, 3], 'speed': 0.4, 'speedMove': 0.4},
    ])


# ---------------------------------------------------------------- crowns

def frost_crown():
    bones = [square_band('band', -7.6, 1.6, ICE, _ice_glow)]
    cubes = []
    for x, z, ang in around(4.35, 3, 2.9):
        tall = abs(x) < 0.1 and abs(z) > 4 or abs(z) < 0.1 and abs(x) > 4
        layers = [(1.3, 1.2, 0.5), (0.9, 1.2 if not tall else 1.6, 0.5), (0.5, 1.0 if not tall else 1.4, 0.5)]
        if not tall:
            layers = layers[:2] + [(0.4, 0.8, 0.4)]
        cubes += stack(x, -9.15, z, layers, ICE, _ice_glow)
    bones.append(Bone('spikes', (0, 0, 0), (0, 0, 0), cubes))
    return Model('head', bones, density=6, translucent=True)


def royal_crown():
    velvet = felt('#c81e3a', '#7a0c1e')
    bones = [square_band('band', -7.4, 2.0, GOLD, None, 4.4, 0.6)]
    cap = Cube((-4.0, -11.0, -4.0), (8.0, 1.8, 8.0), velvet, inflate=0)
    gems = []
    for x, z, ang in around(4.72, 1, 0):
        w, d = (1.4, 0.3) if abs(z) > 4 else (0.3, 1.4)
        gems.append(Cube((x - w / 2, -8.9, z - d / 2), (w, 1.1, d), gem('#e11d48' if abs(z) > 4 else '#2563eb')))
    pts = []
    for x, z, ang in around(4.4, 3, 2.9):
        pts += stack(x, -9.3, z, [(1.2, 1.0, 0.6), (0.8, 1.0, 0.6)], GOLD)
        pts.append(Cube((x - 0.45, -11.8, z - 0.45), (0.9, 0.9, 0.9), GOLD))
    orb = stack(0, -11.0, 0, [(1.0, 0.4, 1.0), (1.6, 1.6, 1.6)], GOLD) + [Cube((-0.35, -13.6, -0.35), (0.7, 0.6, 0.7), gem('#38bdf8'))]
    bones.append(Bone('top', (0, 0, 0), (0, 0, 0), [cap] + gems + pts + orb))
    return Model('head', bones, density=6)


def flower_crown():
    leaf = M(fabric, '#5fbf5a', '#2f7a33', 3, 0.05)
    bones = [square_band('vine', -7.7, 0.9, leaf, None, 4.3, 0.7)]
    cols = ['#ff7eb6', '#ffd23f', '#ffffff', '#c084fc', '#ff9b54']
    cubes = []
    rng = np.random.default_rng(4)
    for k, (x, z, ang) in enumerate(around(4.45, 4, 2.2)):
        c = cols[k % len(cols)]
        s = 1.3 + rng.random() * 0.5
        y = -8.0 - rng.random() * 0.4
        cubes.append(Cube((x - s / 2, y - s / 2, z - s / 2), (s, s, s), M(fabric, c, c, k, 0.0)))
        cubes.append(Cube((x - 0.3, y - s / 2 - 0.15, z - 0.3), (0.6, 0.3, 0.6), M(fabric, '#ffcf3f', '#e09a12', k, 0.0)))
        lx = x + (0.9 if k % 2 else -0.9) * (1 if abs(z) > 4 else 0)
        lz = z + (0.9 if k % 2 else -0.9) * (1 if abs(x) > 4 else 0)
        cubes.append(Cube((lx - 0.5, y + 0.1, lz - 0.5), (1.0, 0.5, 1.0), leaf))
    bones.append(Bone('flowers', (0, 0, 0), (0, 0, 0), cubes))
    return Model('head', bones, density=6)


# ---------------------------------------------------------------- ears (tinted ones are painted in light tones)

def _inner(colour):
    def p(face, w, h, f):
        x, y = coords(w, h)
        tri = mask_polys(w, h, [[(w * 0.5, h * 0.12), (w * 0.88, h * 0.98), (w * 0.12, h * 0.98)]])
        return rgba(np.broadcast_to(rgb(colour), (h, w, 3)).copy() * (0.85 + 0.15 * y[..., None]), tri)
    return p


def ears(kind):
    if kind == 'cat':
        outer, inner, tip, tint = fur('#f6f6f6', '#c9c9c9', 2), '#ff9fbf', None, True
        layers = [(3.0, 1.2, 1.0), (2.3, 1.2, 1.0), (1.5, 1.0, 1.0), (0.7, 0.8, 0.8)]
        x, spread = 2.4, 14
    elif kind == 'fox':
        outer, inner, tip, tint = fur('#ff9a3c', '#d9611c', 3), '#fff1e2', fur('#2a1a12', '#140c08', 3), False
        layers = [(3.2, 1.4, 1.0), (2.4, 1.4, 1.0), (1.6, 1.2, 1.0), (0.8, 1.0, 0.8)]
        x, spread = 2.4, 10
    else:  # polar bear
        outer, inner, tip, tint = fur('#ffffff', '#e2e9f0', 4), '#9aa6b4', None, False
        layers = [(3.2, 1.6, 1.4), (2.2, 0.8, 1.2)]
        x, spread = 2.7, 8
    bones = []
    for sign in (1, -1):
        cubes = []
        y = 0
        for i, (w, h, d) in enumerate(layers):
            p = tip if (tip is not None and i == len(layers) - 1) else outer
            cubes.append(Cube((-w / 2, y - h, -d / 2), (w, h, d), p, tint=tint))
            y -= h
        hh = -y
        cubes.append(Cube((-layers[0][0] * 0.32, -hh * 0.9, -layers[0][2] / 2 - 0.02), (layers[0][0] * 0.64, hh * 0.85, 0), card(_inner(inner))))
        bones.append(Bone(f'ear_{"l" if sign > 0 else "r"}', (sign * x, -8, -0.8), (0, 0, sign * spread), cubes))
    anims = [{'bone': 'ear_l', 'type': 'wave', 'rot': [6, 0, 4], 'speed': 0.25, 'speedMove': 0.6},
             {'bone': 'ear_r', 'type': 'wave', 'rot': [6, 0, -4], 'speed': 0.25, 'speedMove': 0.6, 'phase': 1.3}]
    return Model('head', bones, density=6, anims=anims)


def bunny_ears():
    outer = fur('#ffffff', '#e6e6ea', 6)
    bones = []
    for sign, name in ((1, 'l'), (-1, 'r')):
        lower = [Cube((-0.9, -4.5, -0.45), (1.8, 4.5, 0.9), outer),
                 Cube((-0.5, -4.2, -0.47), (1.0, 3.9, 0), card(_inner('#ffb3c8')))]
        upper = [Cube((-0.85, -3.6, -0.45), (1.7, 3.6, 0.9), outer),
                 Cube((-0.45, -3.4, -0.47), (0.9, 3.0, 0), card(_inner('#ffb3c8')))]
        tip = Bone(f'bunny_tip_{name}', (0, -4.4, 0), (-18 if name == 'l' else -8, 0, sign * 6), upper)
        bones.append(Bone(f'bunny_{name}', (sign * 1.9, -8, -0.4), (-6, 0, sign * 10), lower, [tip]))
    anims = [{'bone': 'bunny_tip_l', 'type': 'wave', 'rot': [10, 0, 0], 'speed': 0.4, 'speedMove': 1.4},
             {'bone': 'bunny_tip_r', 'type': 'wave', 'rot': [8, 0, 0], 'speed': 0.4, 'speedMove': 1.4, 'phase': 1.0}]
    return Model('head', bones, density=6, anims=anims)


def devil_horns():
    shades = [M(metal, '#7a1414', '#4a0a0a', '#2a0505', 1), M(metal, '#5a0e0e', '#350707', '#1c0303', 2),
              M(metal, '#3a0808', '#220404', '#120202', 3), M(metal, '#1c1414', '#120c0c', '#080606', 4)]
    tip_glow = [None, None, None, lambda face, w, h, f: solid(w, h, '#ff3a2a', 0.55)]
    bones = []
    for sign, name in ((1, 'l'), (-1, 'r')):
        segs = [((1.7, 1.4, 1.7), (-14, 0, -sign * 16)), ((1.35, 1.4, 1.35), (-16, 0, -sign * 18)),
                ((1.0, 1.3, 1.0), (-18, 0, -sign * 22)), ((0.55, 1.2, 0.55), (0, 0, 0))]
        bones.append(chain(f'horn_{name}_', (sign * 2.6, -7.6, -1.6), (-8, 0, sign * 30), segs, shades, tip_glow))
    return Model('head', bones, density=6)


def antlers():
    bark = [M(wood, i, '#b98a5a', '#6b4526') for i in range(4)]
    bones = []
    for sign, name in ((1, 'l'), (-1, 'r')):
        tine1 = chain(f'tine1_{name}_', (0, -1.8, 0), (0, 0, -sign * 45), [((0.8, 2.6, 0.8), (0, 0, sign * 10)), ((0.6, 1.8, 0.6), (0, 0, 0))], bark)
        tine2 = chain(f'tine2_{name}_', (0, -1.8, 0), (-10, 0, -sign * 40), [((0.7, 2.4, 0.7), (0, 0, 0))], bark)
        tine3 = chain(f'tine3_{name}_', (0, -3.4, 0), (0, 0, sign * 10), [((0.6, 1.8, 0.6), (0, 0, 0))], bark)
        beam2 = Bone(f'beam2_{name}', (0, -3.2, 0), (0, 0, -sign * 28), [Cube((-0.5, -3.6, -0.5), (1.0, 3.6, 1.0), bark[2])], [tine2, tine3])
        beam = Bone(f'beam_{name}', (sign * 2.6, -7.8, -0.2), (-8, 0, sign * 40),
                    [Cube((-0.65, -3.2, -0.65), (1.3, 3.2, 1.3), bark[1])], [tine1, beam2])
        bones.append(beam)
    return Model('head', bones, density=6)


def antennae():
    stalk = M(metal, '#9ef2c6', '#5cd69c', '#2e9a68', 2)
    orb_glow = lambda face, w, h, f: solid(w, h, '#7dffb8', 0.9)
    bones = []
    for sign, name in ((1, 'l'), (-1, 'r')):
        orb = Bone(f'orb_{name}', (0, -3.6, 0), (0, 0, 0), [Cube((-0.9, -1.8, -0.9), (1.8, 1.8, 1.8), M(metal, '#e8fff2', '#7dffb8', '#2bd47a', 5), glow=orb_glow)])
        bones.append(Bone(f'ant_{name}', (sign * 1.8, -8, 0), (0, 0, sign * 16), [Cube((-0.25, -3.6, -0.25), (0.5, 3.6, 0.5), stalk)], [orb]))
    anims = [{'bone': 'ant_l', 'type': 'wave', 'rot': [8, 0, 6], 'speed': 0.6, 'speedMove': 1.6},
             {'bone': 'ant_r', 'type': 'wave', 'rot': [8, 0, -6], 'speed': 0.6, 'speedMove': 1.6, 'phase': 1.6}]
    return Model('head', bones, density=6, anims=anims)


# ---------------------------------------------------------------- hats

def top_hat():
    black = felt('#2a2a30', '#111116')
    ribbon = felt('#ffffff', '#d9d9d9')
    cubes = [Cube((-5.6, -8.7, -5.6), (11.2, 0.6, 11.2), black),
             Cube((-3.6, -14.2, -3.6), (7.2, 5.5, 7.2), black),
             Cube((-3.75, -10.2, -3.75), (7.5, 1.4, 7.5), ribbon, tint=True)]
    return Model('head', [Bone('hat', (0, 0, 0), (0, 0, -6), cubes)], density=4)


def witch_hat():
    purple = felt('#4c1d95', '#2e1065')
    segs = [((6.8, 2.0, 6.8), (-10, 0, 6)), ((5.0, 2.0, 5.0), (-14, 0, 8)), ((3.4, 2.0, 3.4), (-18, 0, 10)), ((1.8, 2.0, 1.8), (-22, 0, 10)), ((0.8, 1.6, 0.8), (0, 0, 0))]
    cone = chain('cone', (0, -8.6, 0), (0, 0, 0), segs, purple)
    brim = Cube((-6.5, -8.9, -6.5), (13, 0.5, 13), purple)
    band = Cube((-3.55, -10.0, -3.55), (7.1, 1.1, 7.1), felt('#22c55e', '#15803d'))
    buckle = Cube((-1.0, -10.2, -3.8), (2.0, 1.5, 0.3), GOLD)
    return Model('head', [Bone('brim', (0, 0, 0), (0, 0, 0), [brim, band, buckle], [cone])], density=4,
                 anims=[{'bone': 'cone3', 'type': 'wave', 'rot': [6, 0, 4], 'speed': 0.3, 'speedMove': 1.0}])


def beanie():
    k = knit('#f2f2f2', '#bdbdbd', 3)
    cubes = [Cube((-4.4, -9.1, -4.4), (8.8, 3.6, 8.8), k, tint=True),
             Cube((-4.6, -6.0, -4.6), (9.2, 1.5, 9.2), knit('#e4e4e4', '#b0b0b0', 4), tint=True),
             Cube((-1.5, -10.8, -1.5), (3.0, 1.8, 3.0), fur('#ffffff', '#e8e8e8', 2, 0.25))]
    return Model('head', [Bone('beanie', (0, 0, 0), (0, 0, 0), cubes)], density=4)


def santa_hat():
    red = felt('#e11d2e', '#9f1020')
    white = fur('#ffffff', '#e9eef3', 5, 0.2)
    segs = [((7.0, 2.0, 7.0), (-14, 0, 10)), ((5.4, 2.0, 5.4), (-20, 0, 14)), ((3.8, 2.0, 3.8), (-26, 0, 20)), ((2.2, 2.0, 2.2), (0, 0, 0))]
    body = chain('santa', (0, -8.9, 0.6), (0, 0, 0), segs, red)
    # pompom on the tip
    tip = body
    while tip.children:
        tip = tip.children[0]
    tip.children.append(Bone('pom', (0, -1.9, 0), (0, 0, 0), [Cube((-1.3, -2.2, -1.3), (2.6, 2.6, 2.6), white)]))
    base = Cube((-4.4, -9.6, -4.4), (8.8, 1.2, 8.8), red)
    rim = Cube((-4.75, -8.6, -4.75), (9.5, 1.7, 9.5), white)
    return Model('head', [Bone('hat', (0, 0, 0), (0, 0, 0), [base, rim], [body])], density=4,
                 anims=[{'bone': 'santa2', 'type': 'wave', 'rot': [8, 0, 6], 'speed': 0.35, 'speedMove': 1.4}])


def party_hat():
    def stripes(face, w, h, f):
        x, y = coords(w, h)
        band = ((y * h / 4 + x * 0.0).astype(int) % 2).astype(np.float32)
        col = gradient(band, [(0, '#ff5fa2'), (1, '#ffd23f')])
        dots = (np.hypot((x * w) % 3 - 1.5, (y * h) % 3 - 1.5) < 0.7) * 0.6
        col = col * (1 - dots[..., None]) + rgb('#ffffff') * dots[..., None]
        return bevel(rgba(col), 0.1, 0.2, 1)
    segs = [((3.6, 1.5, 3.6), (0, 0, 0)), ((2.6, 1.5, 2.6), (0, 0, 0)), ((1.6, 1.5, 1.6), (0, 0, 0)), ((0.8, 1.3, 0.8), (0, 0, 0))]
    cone = chain('party', (1.4, -8, -0.6), (0, 0, -16), segs, stripes)
    top = cone
    while top.children:
        top = top.children[0]
    top.children.append(Bone('pom', (0, -1.3, 0), (0, 0, 0), [Cube((-0.8, -1.4, -0.8), (1.6, 1.4, 1.6), fur('#fff7a8', '#ffd23f', 3, 0.3))]))
    return Model('head', [cone], density=6)


def headphones():
    shell = M(metal, '#ffffff', '#e6e6e6', '#b3b3b3', 3, 0.03, 0.15, 0.25)
    pad = felt('#2b2b30', '#18181c')
    N = 12

    def light(face, w, h, f):
        x, y = coords(w, h)
        r = np.hypot(x - 0.5, y - 0.5)
        ringm = (np.abs(r - 0.32) < 0.09).astype(np.float32)
        from capes import _hsv
        col = _hsv(np.arctan2(y - 0.5, x - 0.5) / (2 * math.pi) + f / N, 0.7, 1.0)
        return rgba(col, ringm * (1.0 if face in ('left', 'right') else 0.0))
    band = []
    for i in range(11):
        a = math.radians(-90 + 180 * i / 10)
        band.append(Bone(f'band_{i}', (0, -4.2, 0), (0, 0, math.degrees(a)), [Cube((-0.75, -5.3, -0.9), (1.5, 0.7, 1.8), shell, tint=True)]))
    cups = []
    for sign in (1, -1):
        cups.append(Cube((sign * 4.15 - (0 if sign > 0 else 1.6), -5.6, -1.9), (1.6, 3.8, 3.8), shell, glow=light, tint=True))
        cups.append(Cube((sign * 4.0 - (0 if sign > 0 else 0.3), -5.3, -1.6), (0.3, 3.2, 3.2), pad))
    return Model('head', [Bone('phones', (0, 0, 0), (0, 0, 0), cups, band)], density=6, frames=N, frame_time=100)


def snowflake_orbit():
    def flake(face, w, h, f):
        s = snowflake_sprite(max(w, h))
        s = s[:h, :w].copy()
        s[..., :3] = rgb('#e8f7ff')
        return s
    glow = lambda face, w, h, f: (lambda s: rgba(np.broadcast_to(rgb('#7fe6ff'), (h, w, 3)).copy(), s[..., 3] * 0.8))(snowflake_sprite(max(w, h))[:h, :w])
    kids = []
    for i in range(3):
        kids.append(Bone(f'flake_{i}', (0, 0, 0), (0, 120 * i, 0),
                         [Cube((-2.25, -2.25, -6.8), (4.5, 4.5, 0), card(flake), glow=card(glow))]))
    root = Bone('orbit', (0, -6.5, 0), (0, 0, 0), children=kids)
    anims = [{'bone': 'orbit', 'type': 'spin', 'rot': [0, 70, 0]},
             {'bone': 'orbit', 'type': 'wave', 'pos': [0, 0.8, 0], 'rot': [6, 0, 6], 'speed': 0.4, 'speedMove': 0.6}]
    return Model('head', [root], density=8, anims=anims, translucent=True)


def build(out, item):
    item['model']().build(out, item['id'])
    return {'model': f"models/{item['id']}.json"}


ITEMS = [
    dict(id='head_halo', name='Golden Halo', collection='celestial', rarity='epic', model=halo,
         description='A glowing golden halo that slowly turns.'),
    dict(id='head_fallen_halo', name='Fallen Halo', collection='inferno', rarity='rare', model=fallen_halo,
         description='A crooked halo burning a deep red.'),
    dict(id='head_frost_crown', name='Frost Crown', collection='polar', rarity='legendary', model=frost_crown,
         description='A crown of glowing ice spikes.'),
    dict(id='head_royal_crown', name='Royal Crown', collection='royal', rarity='epic', model=royal_crown,
         description='Gold, jewels and red velvet.'),
    dict(id='head_flower_crown', name='Flower Crown', collection='nature', rarity='rare', model=flower_crown,
         description='A ring of bright little flowers.'),
    dict(id='head_polar_ears', name='Polar Bear Ears', collection='polar', rarity='common', model=lambda: ears('polar'),
         description='Round, fluffy polar bear ears.'),
    dict(id='head_cat_ears', name='Cat Ears', collection='cute', rarity='common', model=lambda: ears('cat'), tint=True, defaultColor='#3a3a3a',
         description='Twitchy cat ears in any colour you like.'),
    dict(id='head_fox_ears', name='Fox Ears', collection='cute', rarity='rare', model=lambda: ears('fox'),
         description='Orange fox ears with dark tips.'),
    dict(id='head_bunny_ears', name='Bunny Ears', collection='cute', rarity='rare', model=bunny_ears,
         description='Long, floppy bunny ears that bounce as you move.'),
    dict(id='head_devil_horns', name='Devil Horns', collection='inferno', rarity='epic', model=devil_horns,
         description='Curling horns with smouldering tips.'),
    dict(id='head_antlers', name='Reindeer Antlers', collection='polar', rarity='rare', model=antlers,
         description='Branching antlers for the snowy season.'),
    dict(id='head_antennae', name='Cosmic Antennae', collection='cosmic', rarity='rare', model=antennae,
         description='Bouncy antennae with glowing tips.'),
    dict(id='head_top_hat', name='Top Hat', collection='classic', rarity='common', model=top_hat, tint=True, defaultColor='#c81e3a',
         description='A dapper top hat; pick the ribbon colour.'),
    dict(id='head_witch_hat', name='Witch Hat', collection='royal', rarity='rare', model=witch_hat,
         description='A tall, crooked hat with a golden buckle.'),
    dict(id='head_beanie', name='Beanie', collection='classic', rarity='common', model=beanie, tint=True, defaultColor='#2f6fed',
         description='A cosy knit beanie with a pompom.'),
    dict(id='head_santa_hat', name='Santa Hat', collection='polar', rarity='rare', model=santa_hat,
         description='A floppy red hat with a fluffy pompom.'),
    dict(id='head_party_hat', name='Party Hat', collection='cute', rarity='common', model=party_hat,
         description='Stripes, dots and a pompom. Party time.'),
    dict(id='head_headphones', name='Headphones', collection='neon', rarity='rare', model=headphones, tint=True, defaultColor='#f2f2f2',
         description='Studio headphones with rainbow lights.'),
    dict(id='head_snowflake_orbit', name='Snowflake Orbit', collection='polar', rarity='epic', model=snowflake_orbit,
         description='Three glowing snowflakes circling your head.'),
]
