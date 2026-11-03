"""Wings: hinged panels (cards) that spread and flap as you move.

Two rigs:
  wing_model       two panels per side (inner and outer, hinged at the "wrist"): feathered and membrane wings
  butterfly_model  a forewing and a hindwing per side, both on the root: butterflies and fairies
Painters get (face, w, h, frame) for the LEFT wing's panels, root on the left; the right wing is mirrored.
"""
import math

import numpy as np

from lib import Model, Bone, Cube, card, rgb, rgba, gradient, smoothstep, blur, fbm, vnoise, over, stars, pnoise
from paint import mask_polys, shard_halves, shard, coords, edges

# panel sizes in model pixels (cards: no thickness)
IN_W, IN_H, OUT_W, OUT_H = 11, 15, 12, 18


def wing_model(inner_paint, outer_paint, inner_glow=None, outer_glow=None, translucent=False, tint=False,
               spread=26, lift=14, flap=18, fold=16, speed=0.35, speed_move=1.1, frames=1, frame_time=100):
    """A pair of wings on the upper back."""
    def side(sign):
        name = 'l' if sign > 0 else 'r'
        x0 = 0 if sign > 0 else -IN_W
        ox0 = 0 if sign > 0 else -OUT_W
        flip = (lambda p: p) if sign > 0 else _mirror
        inner = Cube((x0, -9, 0), (IN_W, IN_H, 0), card(flip(inner_paint)), glow=card(flip(inner_glow)) if inner_glow else None, tint=tint)
        outer = Cube((ox0, -9, 0), (OUT_W, OUT_H, 0), card(flip(outer_paint)), glow=card(flip(outer_glow)) if outer_glow else None, tint=tint)
        outer_bone = Bone(f'outer_{name}', pivot=(sign * IN_W, 0, 0), rot=(0, -sign * fold, sign * 6), cubes=[outer])
        return Bone(f'wing_{name}', pivot=(sign * 1.5, 2.5, 2.3), rot=(0, -sign * spread, -sign * lift), cubes=[inner], children=[outer_bone])

    anims = []
    for sign, name in ((1, 'l'), (-1, 'r')):
        anims.append({'bone': f'wing_{name}', 'type': 'wave', 'rot': [0, sign * flap, -sign * 4], 'speed': speed, 'speedMove': speed_move, 'idle': 0.45, 'move': 1.0})
        anims.append({'bone': f'outer_{name}', 'type': 'wave', 'rot': [0, sign * flap * 0.7, 0], 'speed': speed, 'speedMove': speed_move, 'phase': -0.9, 'idle': 0.45, 'move': 1.0})
    return Model('body', [side(1), side(-1)], density=4, anims=anims, translucent=translucent, frames=frames, frame_time=frame_time)


FORE_W, FORE_H, HIND_W, HIND_H = 13, 14, 10, 11


def butterfly_model(fore_paint, hind_paint, fore_glow=None, hind_glow=None, translucent=False, tint=False,
                    sweep=28, flap=30, speed=0.7, speed_move=1.6, frames=1, frame_time=100, scale=1.0):
    """Butterfly-style wings: a forewing above and a hindwing below, both hinged at the spine."""
    fw, fh, hw, hh = FORE_W * scale, FORE_H * scale, HIND_W * scale, HIND_H * scale

    def side(sign):
        name = 'l' if sign > 0 else 'r'
        flip = (lambda p: p) if sign > 0 else _mirror
        fore = Cube((0 if sign > 0 else -fw, -fh + 1, 0), (fw, fh, 0), card(flip(fore_paint)), glow=card(flip(fore_glow)) if fore_glow else None, tint=tint)
        hind = Cube((0 if sign > 0 else -hw, 0, 0.01), (hw, hh, 0), card(flip(hind_paint)), glow=card(flip(hind_glow)) if hind_glow else None, tint=tint)
        return Bone(f'wing_{name}', pivot=(sign * 1.0, 4.0, 2.25), rot=(0, -sign * sweep, -sign * 6), cubes=[fore, hind])

    anims = [{'bone': f'wing_{n}', 'type': 'wave', 'rot': [0, s * flap, 0], 'speed': speed, 'speedMove': speed_move, 'idle': 0.6, 'move': 1.0}
             for s, n in ((1, 'l'), (-1, 'r'))]
    return Model('body', [side(1), side(-1)], density=4, anims=anims, translucent=translucent, frames=frames, frame_time=frame_time)


def _mirror(paint):
    def p(face, w, h, f):
        return np.ascontiguousarray(paint(face, w, h, f)[:, ::-1])
    return p


def _cache(fn):
    memo = {}

    def get(*key):
        if key not in memo:
            memo[key] = fn(*key)
        return memo[key]
    return get


def _xy(w, h):
    y, x = np.mgrid[0:h, 0:w].astype(np.float32)
    return x + 0.5, y + 0.5


# ---------------------------------------------------------------- feathers

def _feather(X, Y, base, tip, width, blade=False):
    """One feather from base to tip: (alpha, t along it, signed distance to its edge in texels, distance to the shaft)."""
    bx, by = base
    dx, dy = tip[0] - bx, tip[1] - by
    L = math.hypot(dx, dy) or 1.0
    dx, dy = dx / L, dy / L
    px, py = X - bx, Y - by
    t = (px * dx + py * dy) / L
    s = np.abs(-px * dy + py * dx)
    tc = np.clip(t, 0, 1)
    if blade:  # straight-sided steel blade with a sharp point
        prof = np.clip((1 - tc) / 0.4, 0, 1) * np.clip(tc * 6 + 0.5, 0, 1)
    else:
        prof = np.sqrt(np.clip(1 - tc ** 2.6, 0, 1)) * np.clip(tc * 4 + 0.45, 0, 1)
    dist = s - width * prof
    alpha = np.clip(0.5 - dist, 0, 1) * (t >= -0.02) * (t <= 1.0)
    return alpha, t, dist, s


def _feather_layout(w, h, which):
    """Rows of feathers for a panel, back to front: (row, base, tip, width)."""
    X = []
    if which == 'in':
        top = lambda u: h * (0.3 - 0.22 * u)
        trail = lambda u: (w * (0.04 + 0.96 * u), h * (0.84 - 0.12 * u))
        rows = [('long', 7, 6.0, 1.0, 0.8), ('mid', 8, 3.0, 0.5, 0.75), ('small', 10, 0.5, 0.22, 0.8)]
    else:
        top = lambda u: h * (0.08 - 0.04 * u)
        trail = lambda u: (w * (0.1 + 0.88 * u ** 0.85), h * (0.96 - 0.86 * u ** 1.6))
        rows = [('long', 9, 3.0, 1.0, 0.85), ('mid', 8, 2.0, 0.48, 0.75), ('small', 10, 0.5, 0.2, 0.8)]
    for row, n, drop, reach, wf in rows:
        for i in reversed(range(n)):
            u = (i + 0.5) / n
            bx = w * (u * (0.92 if which == 'out' else 1.0))
            by = top(bx / w) + drop
            tx, ty = trail(u)
            tip = (bx + (tx - bx) * reach, by + (ty - by) * reach)
            X.append((row, (bx, by), tip, w / n * wf * (1.1 if row == 'long' else 1.0)))
    return X


def feather_panel(w, h, which, pal, style='soft', seed=1):
    """pal: {'long': (base, tip), 'mid': ..., 'small': ..., 'line': outline colour}. Returns (rgba, info)."""
    X, Y = _xy(w, h)
    img = np.zeros((h, w, 4), np.float32)
    tmap = np.zeros((h, w), np.float32)
    rowmap = np.zeros((h, w), np.float32)
    idmap = np.zeros((h, w), np.float32)
    shaft = np.zeros((h, w), np.float32)
    line = rgb(pal.get('line', '#000000'))
    rng = np.random.default_rng(seed)
    for k, (row, base, tip, width) in enumerate(_feather_layout(w, h, which)):
        a, t, dist, s = _feather(X, Y, base, tip, width, style == 'metal')
        if a.max() <= 0:
            continue
        c0, c1 = pal[row]
        col = gradient(np.clip(t, 0, 1), [(0, c0), (1, c1)])
        col *= (0.94 + 0.12 * rng.random())
        edge = np.clip(1 + dist / 1.2, 0, 1)
        if style == 'metal':
            lit = (X - base[0]) * 0.0 + (s < width * 0.25)
            col = col * (1 - edge[..., None] * 0.45) + rgb('#ffffff') * (lit * (1 - edge) * 0.18)[..., None]
        else:
            col = col * (1 - edge[..., None] * 0.35) + line * (edge[..., None] * 0.15)
            barbs = 0.5 + 0.5 * np.sin(s * 2.4 + t * 9)
            col = col * (0.95 + 0.05 * barbs[..., None])
            sh = (s < 0.45) * (t < 0.92) * (t > 0)
            col = col * (1 - sh[..., None] * 0.12)
        img = over(img, rgba(col, a))
        on = a > 0.5
        tmap[on] = np.clip(t[on], 0, 1)
        rowmap[on] = {'long': 1, 'mid': 2, 'small': 3}[row]
        idmap[on] = k
        shaft[on] = ((s < (width * 0.16 if style == 'metal' else 0.5)) * (t < 0.95))[on]
    return img, {'t': tmap, 'row': rowmap, 'id': idmap, 'shaft': shaft}


def _feathered(pal, style='soft', seed=1):
    get = _cache(lambda w, h, which: feather_panel(w, h, which, pal, style, seed))
    return (lambda f, w, h, i: get(w, h, 'in')[0]), (lambda f, w, h, i: get(w, h, 'out')[0]), get


ANGEL = {'long': ('#ffffff', '#e3ebf7'), 'mid': ('#ffffff', '#eef3fb'), 'small': ('#ffffff', '#f7f9fd'), 'line': '#8da2c4'}
PHOENIX = {'long': ('#ff7a1a', '#a3120a'), 'mid': ('#ffb13b', '#ff5a14'), 'small': ('#ffe08a', '#ffb13b'), 'line': '#5a0b05'}
AURORA = {'long': ('#4de8c9', '#8a6cff'), 'mid': ('#9ff7e2', '#40d8c4'), 'small': ('#f0fffb', '#bff8ec'), 'line': '#1d3b66'}
MECH = {'long': ('#6b7684', '#262c35'), 'mid': ('#8a95a3', '#3a424e'), 'small': ('#a9b3bf', '#56606c'), 'line': '#0b0f14'}


def angel_wings():
    pin, pout, get = _feathered(ANGEL, seed=3)

    def glow(which):
        def g(face, w, h, i):
            img, info = get(w, h, which)
            a = img[..., 3] * ((info['row'] == 3) * 0.35 + (info['t'] > 0.85) * (info['row'] == 1) * 0.5)
            return rgba(np.broadcast_to(rgb('#ffe9a8'), (h, w, 3)).copy(), np.clip(a, 0, 1))
        return g
    return wing_model(pin, pout, glow('in'), glow('out'), spread=24, lift=16)


def phoenix_wings():
    pin, pout, get = _feathered(PHOENIX, seed=5)
    N = 8

    def glow(which):
        def g(face, w, h, f):
            img, info = get(w, h, which)
            fl = 0.65 + 0.35 * np.sin(2 * math.pi * f / N + info['id'] * 1.7)
            a = img[..., 3] * (0.25 + 0.75 * info['t'] ** 1.5) * fl
            col = gradient(info['t'], [(0, '#ffe08a'), (0.6, '#ff8a1a'), (1, '#ff3d0a')])
            return rgba(col, np.clip(a * 0.85, 0, 1))
        return g
    return wing_model(pin, pout, glow('in'), glow('out'), frames=N, frame_time=90, flap=22, speed=0.45)


def aurora_wings():
    pin, pout, get = _feathered(AURORA, seed=8)
    N = 12

    def glow(which):
        def g(face, w, h, f):
            img, info = get(w, h, which)
            X, Y = _xy(w, h)
            wave = 0.5 + 0.5 * np.sin((X / w + Y / h * 0.6) * 7 - 2 * math.pi * f / N)
            a = img[..., 3] * (0.15 + 0.6 * wave * (info['row'] == 1)) + info['shaft'] * 0.3
            col = gradient(np.clip(info['t'] * 0.7 + wave * 0.3, 0, 1), [(0, '#7dffcf'), (0.5, '#5cf2d6'), (1, '#b197fc')])
            return rgba(col, np.clip(a, 0, 1))
        return g
    return wing_model(pin, pout, glow('in'), glow('out'), frames=N, frame_time=110)


def mech_wings():
    pin, pout, get = _feathered(MECH, style='metal', seed=2)
    N = 10

    def glow(which):
        def g(face, w, h, f):
            img, info = get(w, h, which)
            run = np.abs(((info['t'] - f / N) % 1.0) - 0.5) < 0.1
            a = info['shaft'] * (info['row'] < 3) * (0.7 + 0.3 * run) + run * (info['row'] == 1) * img[..., 3] * 0.25
            return rgba(np.broadcast_to(rgb('#38e8ff'), (h, w, 3)).copy(), np.clip(a, 0, 1))
        return g
    return wing_model(pin, pout, glow('in'), glow('out'), frames=N, frame_time=80, flap=12, speed=0.3, fold=10)


# ---------------------------------------------------------------- membranes (bat/dragon style)

def _bow(p, q, toward, amount, n=8):
    """Points from p to q, bowed toward a point (a scalloped membrane edge)."""
    out = []
    mx, my = (p[0] + q[0]) / 2, (p[1] + q[1]) / 2
    dx, dy = toward[0] - mx, toward[1] - my
    d = math.hypot(dx, dy) or 1
    L = math.hypot(q[0] - p[0], q[1] - p[1])
    for i in range(1, n):
        s = i / n
        k = math.sin(math.pi * s) * amount * L
        out.append((p[0] + (q[0] - p[0]) * s + dx / d * k, p[1] + (q[1] - p[1]) * s + dy / d * k))
    return out


def _membrane_shapes(w, h, which):
    if which == 'in':
        root, joint = (0, h * 0.3), (w, h * 0.08)
        p0, p1, p2 = (w, h * 0.7), (w * 0.45, h * 0.86), (0, h * 0.64)
        poly = [root, joint, p0] + _bow(p0, p1, joint, 0.18) + [p1] + _bow(p1, p2, root, 0.18) + [p2]
        bones = [([root, joint], 2.6), ([(w * 0.5, h * 0.19), p1], 1.2)]
        return poly, bones, joint
    J = (0, h * 0.07)
    tips = [(w * 0.97, h * 0.02), (w * 0.9, h * 0.44), (w * 0.62, h * 0.78), (w * 0.26, h * 0.95)]
    R = (0, h * 0.58)
    poly = [J, tips[0]]
    for a, b in zip(tips, tips[1:] + [R]):
        poly += _bow(a, b, J, 0.16) + [b]
    bones = [([J, tips[0]], 2.4), ([J, tips[1]], 1.5), ([J, tips[2]], 1.3), ([J, tips[3]], 1.1)]
    return poly, bones, J


def membrane_panel(w, h, which, mem, bone_col, vein=None, bone_w=1.0):
    """mem(X, Y, dist_from_joint) -> rgb; returns (rgba, info with membrane/bone masks)."""
    from capes import _lines_mask
    poly, bones, J = _membrane_shapes(w, h, which)
    X, Y = _xy(w, h)
    m = mask_polys(w, h, [poly])
    d = np.hypot(X - J[0], Y - J[1]) / max(w, h)
    col = mem(X, Y, d)
    e = edges(m, 1.0)
    col = col * (1 - e[..., None] * 0.45)
    img = rgba(col, m * 0.96)
    veins = np.zeros((h, w), np.float32)
    if vein:
        segs = []
        for (pts, _) in bones[1:]:
            a, b = pts
            for s in (0.3, 0.6):
                mx, my = a[0] + (b[0] - a[0]) * s, a[1] + (b[1] - a[1]) * s
                segs.append([(mx, my), (mx + (b[0] - a[0]) * 0.25 + 3, my + (b[1] - a[1]) * 0.25 + 4)])
        veins = _lines_mask(w, h, segs, 0.5) * m
        img[..., :3] = img[..., :3] * (1 - veins[..., None] * 0.5) + rgb(vein) * veins[..., None] * 0.5
    bm = np.zeros((h, w), np.float32)
    for pts, width in bones:
        bm = np.maximum(bm, _lines_mask(w, h, [pts], width * bone_w))
    bc = gradient(np.clip(Y / h, 0, 1), [(0, bone_col[0]), (1, bone_col[1])])
    bcol = bc * (1 - edges(np.clip(bm * 1.5, 0, 1), 0.7)[..., None] * 0.4)
    img = over(img, rgba(bcol, bm))
    return img, {'mem': m, 'bone': bm, 'veins': veins, 'd': d, 'edge': e}


def _membraned(mem, bone_col, vein=None):
    get = _cache(lambda w, h, which: membrane_panel(w, h, which, mem, bone_col, vein))
    return (lambda f, w, h, i: get(w, h, 'in')[0]), (lambda f, w, h, i: get(w, h, 'out')[0]), get


def demon_wings():
    mem = lambda X, Y, d: gradient(np.clip(d * 1.3, 0, 1), [(0, '#5c0a12'), (0.6, '#360610'), (1, '#1a0309')]) * (0.85 + 0.15 * fbm(X.shape[1], X.shape[0], 6, 3, 2))[..., None]
    pin, pout, get = _membraned(mem, ('#2a2224', '#120c0e'), vein='#ff3d3d')
    N = 8

    def glow(which):
        def g(face, w, h, f):
            img, info = get(w, h, which)
            p = 0.6 + 0.4 * math.sin(2 * math.pi * f / N)
            a = info['veins'] * p + info['edge'] * info['mem'] * 0.35
            return rgba(np.broadcast_to(rgb('#ff2a2a'), (h, w, 3)).copy(), np.clip(a, 0, 1))
        return g
    return wing_model(pin, pout, glow('in'), glow('out'), frames=N, frame_time=110, spread=22, lift=10, flap=16, fold=22)


def dragon_wings():
    """Tinted: painted in light neutral tones so the player's colour shows through."""
    def mem(X, Y, d):
        n = fbm(X.shape[1], X.shape[0], 5, 9, 3)
        return gradient(np.clip(d * 1.2 + n * 0.2, 0, 1), [(0, '#e9eef0'), (0.6, '#b8c3c8'), (1, '#8b979d')])

    pin_base, pout_base, get = _membraned(mem, ('#f4f4f2', '#a7a8a4'), vein='#5d676b')

    def scaled(which):
        def p(f, w, h, i):
            img = get(w, h, which)[0].copy()
            X, Y = _xy(w, h)
            sc = ((np.floor(X / 2) + np.floor(Y / 2)) % 2) * get(w, h, which)[1]['bone'] * 0.12
            img[..., :3] *= (1 - sc[..., None])
            if which == 'in':
                spikes = []
                for k in range(4):
                    u = 0.15 + 0.22 * k
                    bx, by = w * u, h * (0.3 - 0.22 * u)
                    spikes.append([(bx - 1.6, by - 0.5), (bx + 0.6, by - 4.5), (bx + 1.6, by - 0.5)])
                sm = mask_polys(w, h, spikes)
                img = over(img, rgba(gradient(Y / h, [(0, '#ffffff'), (0.3, '#cfd5d8')]), sm))
            return img
        return p
    return wing_model(scaled('in'), scaled('out'), tint=True, spread=22, lift=12, flap=14, fold=20, speed=0.3)


def galaxy_wings():
    N = 10

    def mem_frame(f):
        def mem(X, Y, d):
            w, h = X.shape[1], X.shape[0]
            n1, n2 = fbm(w, h, 12, 4, 3), fbm(w, h, 10, 7, 3)
            col = gradient(d, [(0, '#140a2e'), (1, '#05030f')])
            col = col + gradient(n1, [(0, '#000000'), (0.6, '#3a0f5c'), (1, '#d43f8d')]) * smoothstep(0.45, 0.9, n1)[..., None]
            col = col + gradient(n2, [(0, '#000000'), (0.6, '#0d2a6b'), (1, '#2fb5ff')]) * smoothstep(0.5, 0.95, n2)[..., None]
            st = stars(w, h, int(w * h / 40), 3 + w, phase=f / N, big=0.2)
            return np.clip(col * (1 - st[..., 3:4]) + st[..., :3] * st[..., 3:4], 0, 1)
        return mem
    gets = [_cache(lambda w, h, which, f=f: membrane_panel(w, h, which, mem_frame(f), ('#2b1650', '#120a26'))) for f in range(N)]
    pin = lambda face, w, h, f: gets[f](w, h, 'in')[0]
    pout = lambda face, w, h, f: gets[f](w, h, 'out')[0]

    def glow(which):
        def g(face, w, h, f):
            img, info = gets[f](w, h, which)
            st = stars(w, h, int(w * h / 40), 3 + w, phase=f / N, big=0.2)[..., 3]
            a = st * info['mem'] * 0.8 + info['edge'] * info['mem'] * 0.5 + edges(np.clip(info['bone'] * 1.5, 0, 1), 0.7) * 0.6
            col = np.broadcast_to(rgb('#b9a8ff'), (h, w, 3)).copy()
            return rgba(col, np.clip(a, 0, 1))
        return g
    return wing_model(pin, pout, glow('in'), glow('out'), frames=N, frame_time=120, spread=24, lift=14, flap=16, fold=18)


# ---------------------------------------------------------------- butterflies and fairies

def _chaikin(pts, n=3):
    for _ in range(n):
        out = []
        for a, b in zip(pts, pts[1:] + pts[:1]):
            out += [(a[0] * 0.75 + b[0] * 0.25, a[1] * 0.75 + b[1] * 0.25), (a[0] * 0.25 + b[0] * 0.75, a[1] * 0.25 + b[1] * 0.75)]
        pts = out
    return pts


def _bfly_shape(w, h, part, slim=False):
    if part == 'fore':
        if slim:
            pts = [(w * 0.02, h * 0.95), (w * 0.3, h * 0.45), (w * 0.7, h * 0.06), (w * 0.98, h * 0.02), (w * 0.9, h * 0.25), (w * 0.45, h * 0.8), (w * 0.15, h * 0.99)]
        else:
            pts = [(w * 0.02, h * 0.95), (w * 0.22, h * 0.3), (w * 0.55, h * 0.03), (w * 0.95, h * 0.06), (w * 0.99, h * 0.38), (w * 0.72, h * 0.78), (w * 0.3, h * 0.99)]
        root = (w * 0.02, h * 0.97)
    else:
        if slim:
            pts = [(w * 0.02, h * 0.04), (w * 0.5, h * 0.12), (w * 0.95, h * 0.6), (w * 0.85, h * 0.95), (w * 0.45, h * 0.6), (w * 0.12, h * 0.3)]
        else:
            pts = [(w * 0.02, h * 0.03), (w * 0.62, h * 0.02), (w * 0.98, h * 0.35), (w * 0.85, h * 0.78), (w * 0.5, h * 0.98), (w * 0.18, h * 0.82)]
        root = (w * 0.02, h * 0.03)
    return _chaikin(pts), root


def butterfly_panel(w, h, part, slim=False):
    from capes import _lines_mask
    poly, root = _bfly_shape(w, h, part, slim)
    X, Y = _xy(w, h)
    m = mask_polys(w, h, [poly])
    d = np.hypot(X - root[0], Y - root[1]) / max(w, h)
    rim = np.clip(m - blur(m, 3.4) * 1.0, 0, 1) * 2.6
    border = np.clip(rim, 0, 1)
    veins = _lines_mask(w, h, [[root, p] for p in poly[::max(1, len(poly) // 7)]], 0.55) * m
    return m, d, border, veins, poly, root


def butterfly_wings():
    """Tinted monarch-like pattern: light areas take the player's colour; dark border with white spots."""
    def paint(part):
        get = _cache(lambda w, h: butterfly_panel(w, h, part))

        def p(face, w, h, f):
            m, d, border, veins, poly, root = get(w, h)
            col = gradient(np.clip(d * 1.2, 0, 1), [(0, '#ffffff'), (0.7, '#e8e8e8'), (1, '#c9c9c9')])
            col = col * (1 - veins[..., None] * 0.75)
            col = col * (1 - border[..., None] * 0.88)
            X, Y = _xy(w, h)
            dots = np.zeros((h, w), np.float32)
            for i, (px, py) in enumerate(poly[::3]):
                cx, cy = px + (root[0] - px) * 0.07, py + (root[1] - py) * 0.07
                if math.hypot(px - root[0], py - root[1]) > max(w, h) * 0.35:
                    dots = np.maximum(dots, np.clip(1.8 - np.hypot(X - cx, Y - cy), 0, 1))
            col = col * (1 - dots[..., None] * border[..., None]) + dots[..., None] * border[..., None] * 0.95
            if part == 'hind':
                ex, ey, er = w * 0.55, h * 0.52, min(w, h) * 0.2
                r = np.hypot(X - ex, Y - ey)
                col = np.where((r < er)[..., None], gradient(r / er, [(0, '#111111'), (0.45, '#111111'), (0.5, '#ffffff'), (0.8, '#ffffff'), (0.85, '#222222'), (1, '#222222')]), col)
            return rgba(col, m)
        return p
    return butterfly_model(paint('fore'), paint('hind'), tint=True)


def fairy_wings():
    N = 12

    def paint(part, glow=False):
        get = _cache(lambda w, h: butterfly_panel(w, h, part, slim=True))

        def p(face, w, h, f):
            m, d, border, veins, poly, root = get(w, h)
            X, Y = _xy(w, h)
            hue = (0.48 + d * 0.35 + 0.08 * np.sin((X + Y) / 6 - 2 * math.pi * f / N)) % 1.0
            from capes import _hsv
            col = _hsv(hue, 0.45, 1.0)
            if glow:
                a = (veins * 0.8 + border * 0.6) * m * (0.7 + 0.3 * math.sin(2 * math.pi * f / N))
                sp = stars(w, h, 6, 5 + w, phase=f / N, big=0.6)[..., 3] * m
                return rgba(np.clip(col * 0.4 + 0.6, 0, 1), np.clip(a + sp, 0, 1))
            a = m * (0.42 + 0.35 * d) + border * 0.4 + veins * 0.4
            col = col * (1 - veins[..., None] * 0.2) + veins[..., None] * 0.2
            return rgba(col, np.clip(a, 0, 1) * m)
        return p
    return butterfly_model(paint('fore'), paint('hind'), paint('fore', True), paint('hind', True), translucent=True,
                           frames=N, frame_time=90, sweep=24, flap=26, speed=1.2, speed_move=2.2, scale=1.08)


# ---------------------------------------------------------------- frost wings: crystal shards

def _frost_panel(w, h, shards, seed):
    img = np.zeros((h, w, 4), np.float32)
    glow = np.zeros_like(img)
    for i, (base, ang, length, width) in enumerate(shards):
        b = (base[0] * w, base[1] * h)
        L, Wd = length * w, width * w
        left, right = shard_halves(b, ang, L, Wd)
        mL, mR = mask_polys(w, h, [left]), mask_polys(w, h, [right])
        m = np.clip(mL + mR, 0, 1)
        x, y = coords(w, h)
        along = np.clip(((x * w - b[0]) * math.cos(math.radians(ang)) + (y * h - b[1]) * math.sin(math.radians(ang))) / L, 0, 1)
        lightc = gradient(along, [(0, '#f4fcff'), (0.4, '#b8e6ff'), (1, '#6fb8ff')])
        darkc = gradient(along, [(0, '#bfe8ff'), (0.5, '#6aaef5'), (1, '#2f6fcf')])
        col = lightc * mL[..., None] + darkc * mR[..., None]
        e = edges(m, 0.8)
        col = col * (1 - e[..., None] * 0.55) + rgb('#ffffff') * (e[..., None] * 0.25)
        layer = np.concatenate([col / np.maximum(m[..., None], 1e-3), (m * (0.82 + 0.18 * e))[..., None]], -1)
        img = over(img, layer)
        axis = mask_polys(w, h, [shard(b, ang, L * 0.92, Wd * 0.16, 0.5)])
        g = np.concatenate([np.broadcast_to(rgb('#8ff3ff'), (h, w, 3)), (axis * 0.75 * (1 - along * 0.5))[..., None]], -1)
        glow = over(glow, g)
    return img, glow


FROST_INNER = [((0.04, 0.06), 8, 0.98, 0.10), ((0.04, 0.2), 22, 0.92, 0.10), ((0.05, 0.34), 38, 0.85, 0.10),
               ((0.06, 0.48), 55, 0.7, 0.10), ((0.08, 0.6), 70, 0.55, 0.09)]
FROST_OUTER = [((0.0, 0.04), 18, 1.0, 0.11), ((0.0, 0.16), 34, 1.0, 0.10), ((0.02, 0.3), 50, 0.95, 0.10),
               ((0.04, 0.42), 64, 0.85, 0.09), ((0.06, 0.52), 76, 0.66, 0.09)]

_frost = _cache(lambda w, h, which: _frost_panel(w, h, FROST_INNER if which == 'in' else FROST_OUTER, 7))


def frost_wings():
    return wing_model(lambda f, w, h, i: _frost(w, h, 'in')[0], lambda f, w, h, i: _frost(w, h, 'out')[0],
                      lambda f, w, h, i: _frost(w, h, 'in')[1], lambda f, w, h, i: _frost(w, h, 'out')[1], translucent=True)


def build(out, item):
    model = item['model']()
    model.build(out, item['id'])
    return {'model': f"models/{item['id']}.json"}


ITEMS = [
    dict(id='wings_frost', name='Frost Wings', collection='polar', rarity='legendary', model=frost_wings,
         description='Wings of glittering ice crystals.'),
    dict(id='wings_aurora', name='Aurora Wings', collection='polar', rarity='legendary', model=aurora_wings,
         description='Feathers that shimmer like the northern lights.'),
    dict(id='wings_angel', name='Angel Wings', collection='celestial', rarity='epic', model=angel_wings,
         description='Soft white feathers with a golden glow.'),
    dict(id='wings_phoenix', name='Phoenix Wings', collection='inferno', rarity='legendary', model=phoenix_wings,
         description='Blazing feathers that flicker like fire.'),
    dict(id='wings_demon', name='Demon Wings', collection='inferno', rarity='epic', model=demon_wings,
         description='Leathery wings with smouldering veins.'),
    dict(id='wings_dragon', name='Dragon Wings', collection='nature', rarity='epic', model=dragon_wings, tint=True, defaultColor='#3fbf74',
         description='Scaled dragon wings in any colour you like.'),
    dict(id='wings_galaxy', name='Galaxy Wings', collection='cosmic', rarity='legendary', model=galaxy_wings,
         description='Wings cut from the night sky, full of twinkling stars.'),
    dict(id='wings_butterfly', name='Butterfly Wings', collection='cute', rarity='rare', model=butterfly_wings, tint=True, defaultColor='#ff9a2e',
         description='Patterned butterfly wings in any colour you like.'),
    dict(id='wings_fairy', name='Fairy Wings', collection='cute', rarity='epic', model=fairy_wings,
         description='Glassy, glimmering fairy wings that flutter fast.'),
    dict(id='wings_mech', name='Mech Wings', collection='neon', rarity='epic', model=mech_wings,
         description='Steel blades with energy running through them.'),
]
