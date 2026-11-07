"""Painting for second-generation wings: geometry passes that record what is where (maps), and shaders that colour
those maps (per animation frame). Everything is in texels of the LEFT wing's picture: x from the root outwards,
y down, the shoulder pivot at model y = 0 (texel row -top * DENSITY).
"""
import math

import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage

from lib import rgb, rgba, gradient, smoothstep, blur, fbm, vnoise
from wingkit import DENSITY as D


# ---------------------------------------------------------------- canvas

class Canvas:
    """W x H model px, top = model y of the top row."""

    def __init__(self, W, H, top):
        self.W, self.H, self.top = W, H, top
        self.w, self.h = int(round(W * D)), int(round(H * D))
        y, x = np.mgrid[0:self.h, 0:self.w].astype(np.float32)
        self.X, self.Y = x + 0.5, y + 0.5

    def p(self, x, y):
        """Model px -> texel coordinates."""
        return (x * D, (y - self.top) * D)

    def zeros(self, ch=None):
        return np.zeros((self.h, self.w) if ch is None else (self.h, self.w, ch), np.float32)

    def mx(self):
        """Model x of every texel."""
        return self.X / D

    def my(self):
        return self.Y / D + self.top


def polys_mask(cv, polys, scale=4):
    im = Image.new('L', (cv.w * scale, cv.h * scale), 0)
    d = ImageDraw.Draw(im)
    for poly in polys:
        d.polygon([(x * scale, y * scale) for x, y in poly], fill=255)
    return np.asarray(im.resize((cv.w, cv.h), Image.LANCZOS), np.float32) / 255


def lines_mask(cv, lines, width, scale=4):
    im = Image.new('L', (cv.w * scale, cv.h * scale), 0)
    d = ImageDraw.Draw(im)
    for ln in lines:
        d.line([(x * scale, y * scale) for x, y in ln], fill=255, width=max(1, int(round(width * scale))), joint='curve')
    return np.asarray(im.resize((cv.w, cv.h), Image.LANCZOS), np.float32) / 255


def chaikin(pts, n=3, closed=True):
    for _ in range(n):
        out = []
        m = len(pts)
        rng = range(m) if closed else range(m - 1)
        for i in rng:
            a, b = pts[i], pts[(i + 1) % m]
            out.append((a[0] * 0.75 + b[0] * 0.25, a[1] * 0.75 + b[1] * 0.25))
            out.append((a[0] * 0.25 + b[0] * 0.75, a[1] * 0.25 + b[1] * 0.75))
        if not closed:
            out = [pts[0]] + out + [pts[-1]]
        pts = out
    return pts


def bezier(p0, p1, p2, n=12):
    return [((1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t * t * p2[0], (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t * t * p2[1])
            for t in np.linspace(0, 1, n)]


def shift(a, dx, dy):
    return ndimage.shift(a, (dy, dx), order=1, mode='constant')


def edge_of(mask, width=1.0):
    inner = blur(mask, width)
    return np.clip((mask - inner) * 3 + (mask > 0.5) * (inner < 0.75) * 0.6, 0, 1) * (mask > 0.05)


def hsv2rgb(h, s, v):
    h = np.asarray(h, np.float32) % 1.0
    i = np.floor(h * 6)
    f = h * 6 - i
    p, q, t = v * (1 - s), v * (1 - f * s), v * (1 - (1 - f) * s)
    i = i.astype(int) % 6
    r = np.choose(i, [v, q, p, p, t, v])
    g = np.choose(i, [t, v, v, q, p, p])
    b = np.choose(i, [p, p, t, v, v, q])
    return np.stack([r, g, b], -1).astype(np.float32)


# ---------------------------------------------------------------- feathers

def feather_geom(X, Y, base, tip, hw, shape='round', bend=0.0):
    """One feather in texels: (alpha, t along it, signed edge distance, |offset from shaft|, side -1/+1)."""
    bx, by = base
    dx, dy = tip[0] - bx, tip[1] - by
    L = math.hypot(dx, dy) or 1.0
    dx, dy = dx / L, dy / L
    px, py = X - bx, Y - by
    t = (px * dx + py * dy) / L
    off = -px * dy + py * dx
    if bend:
        off = off - bend * L * np.clip(t, 0, 1) ** 2
    s = np.abs(off)
    tc = np.clip(t, 0, 1)
    if shape == 'point':
        prof = (1 - tc) ** 0.75 * np.clip(tc * 5 + 0.45, 0, 1)
    elif shape == 'blade':
        prof = np.clip((1 - tc) / 0.35, 0, 1) * np.clip(tc * 7 + 0.5, 0, 1)
    elif shape == 'leaf':
        prof = np.sin(np.pi * np.clip(tc * 0.92 + 0.04, 0, 1)) ** 0.85
    elif shape == 'petal':
        prof = np.clip(tc, 0.12, 1) ** 0.55 * np.sqrt(np.clip(1 - tc ** 3, 0, 1))
    elif shape == 'scale':
        prof = np.sqrt(np.clip(1 - (tc * 2 - 1) ** 2, 0, 1))
    else:  # round: a classic flight feather
        prof = np.sqrt(np.clip(1 - tc ** 2.4, 0, 1)) * np.clip(tc * 4 + 0.45, 0, 1)
    dist = s - hw * prof
    alpha = np.clip(0.5 - dist, 0, 1) * (t >= -0.03) * (t <= 1.0)
    return alpha, t, dist, s, np.sign(off)


class Maps:
    """What a feather/plate layer looks like, before colour: coverage and per-texel facts about the top-most piece."""

    def __init__(self, cv):
        self.cv = cv
        self.alpha = cv.zeros()
        self.t = cv.zeros()
        self.u = cv.zeros()
        self.row = cv.zeros()
        self.id = cv.zeros()
        self.edge = cv.zeros()
        self.side = cv.zeros()
        self.shaft = cv.zeros()
        self.shadow = cv.zeros()
        self.across = cv.zeros()  # 0 at the shaft .. 1 at the edge
        self.rnd = cv.zeros()
        self.n = 0

    def add(self, a, t, dist, s, side, hw, u, row, rnd, shadow=0.32, shaft_w=0.55):
        sh = shift(blur(a, 1.3), 1.2, 1.8) * shadow
        self.shadow = np.maximum(self.shadow, sh * (self.alpha > 0.05))
        self.shadow *= (1 - a)
        on = a > 0.45
        self.t[on] = np.clip(t[on], 0, 1)
        self.u[on] = u
        self.row[on] = row
        self.id[on] = self.n
        self.rnd[on] = rnd
        self.edge[on] = np.clip(1 + dist[on] / 1.4, 0, 1)
        self.side[on] = side[on]
        self.shaft[on] = ((s < shaft_w) * (t < 0.94))[on]
        self.across[on] = np.clip(s[on] / max(hw, 1e-3), 0, 1)
        self.alpha = a + self.alpha * (1 - a)
        self.n += 1


def feather_maps(cv, feathers, seed=1, shadow=0.32):
    """feathers: [(base, tip, halfwidth, u, row, shape, bend)] in MODEL px, back to front."""
    rng = np.random.default_rng(seed)
    m = Maps(cv)
    for base, tip, hw, u, row, shape, bend in feathers:
        b, tp = cv.p(*base), cv.p(*tip)
        a, t, dist, s, side = feather_geom(cv.X, cv.Y, b, tp, hw * D, shape, bend)
        if a.max() <= 0:
            continue
        m.add(a, t, dist, s, side, hw * D, u, row, rng.random(), shadow)
    return m


def shade(m, col, line='#000000', edge_dark=0.38, vane=0.07, barbs=0.03, shaft_col=None, shaft_amt=0.35, alpha=1.0,
          shadow=True, gloss=0.0):
    """Colours feather maps: col is (h, w, 3). Adds outlines, the two-tone vane, barbs, the shaft and cast shadows."""
    c = np.asarray(col, np.float32).copy()
    e = m.edge[..., None]
    c = c * (1 - e * edge_dark) + rgb(line) * (e * edge_dark * 0.4)
    c = c * (1 + vane * m.side[..., None] * (m.alpha[..., None] > 0))
    if barbs:
        X, Y = m.cv.X, m.cv.Y
        st = 0.5 + 0.5 * np.sin(m.across * 9 + m.t * 26 + m.id * 1.3)
        c = c * (1 - barbs + barbs * 2 * st[..., None])
    if gloss:
        g = np.clip(1 - np.abs(m.across - 0.35) * 3.5, 0, 1) * (1 - m.edge)
        c = c + gloss * g[..., None]
    if shaft_col is not None:
        sc = rgb(shaft_col)
        c = c * (1 - m.shaft[..., None] * shaft_amt) + sc * (m.shaft[..., None] * shaft_amt)
    if shadow:
        c = c * (1 - m.shadow[..., None])
    return rgba(np.clip(c, 0, 1), m.alpha * alpha)


def stops(spec):
    """'#a:#b:#c' or a list of colours -> gradient stops evenly spaced."""
    cols = spec.split(':') if isinstance(spec, str) else list(spec)
    n = len(cols)
    return [(i / max(1, n - 1), c) for i, c in enumerate(cols)]


def grad(t, spec):
    return gradient(t, stops(spec))


# ---------------------------------------------------------------- feathered wing layouts (model px, left wing)

class FeatherWing:
    """A bird-style wing: primaries on the hand, secondaries on the forearm, tertials by the body, coverts on top.

    style: 'angel' (tall, long primaries pointing down and out), 'hawk' (broad, splayed primaries),
    'swept' (long and pointed, reaching outwards), 'owl' (rounded), 'plume' (long streaming tail feathers).
    """

    def __init__(self, W=26, H=30, top=-15, style='angel', shape='round', n=(9, 8, 4), scale_cov=1.0, seed=1,
                 width=1.0, droop=0.0, reach=1.0):
        self.W, self.H, self.top, self.style, self.shape, self.seed = W, H, top, style, shape, seed
        self.cv = Canvas(W, H, top)
        np_, ns, nt = n
        Wd = W
        bot = top + H
        # skeleton (model px): shoulder, elbow, wrist, hand tip
        if style == 'hawk':
            S, E, Wr, T = (0.4, 0.5), (Wd * 0.33, -3.5), (Wd * 0.62, -7.5), (Wd * 0.86, -7.0)
        elif style == 'swept':
            S, E, Wr, T = (0.4, 0.0), (Wd * 0.3, -4.5), (Wd * 0.56, -9.5), (Wd * 0.8, -12.0)
        elif style == 'owl':
            S, E, Wr, T = (0.4, 0.5), (Wd * 0.34, -4.0), (Wd * 0.6, -7.0), (Wd * 0.8, -6.0)
        elif style == 'plume':
            S, E, Wr, T = (0.4, 0.0), (Wd * 0.32, -5.0), (Wd * 0.58, -10.0), (Wd * 0.78, -10.5)
        else:  # angel
            S, E, Wr, T = (0.4, 0.0), (Wd * 0.34, -6.0), (Wd * 0.62, top + 2.2), (Wd * 0.86, top + 3.6)
        self.S, self.E, self.Wr, self.T = S, E, Wr, T
        lerp = lambda a, b, k: (a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k)
        f_long, f_mid, f_small = [], [], []
        # primaries: from the wrist to the hand tip; the outermost points along the hand
        for i in range(np_):
            k = i / max(1, np_ - 1)
            base = lerp(Wr, T, k ** 0.9)
            base = (base[0], base[1] + 1.2)
            if style == 'hawk':
                ang = 80 - 70 * k ** 1.1
                ln = (H * 0.55) * (0.9 + 0.25 * math.sin(math.pi * k * 0.9)) * reach
            elif style == 'swept':
                ang = 62 - 72 * k ** 0.9
                ln = (H * 0.62) * (0.85 + 0.35 * k) * reach
            elif style == 'owl':
                ang = 84 - 60 * k
                ln = (H * 0.5) * (0.95 + 0.12 * math.sin(math.pi * k)) * reach
            elif style == 'plume':
                ang = 78 - 40 * k
                ln = (H * 0.78) * (0.9 + 0.2 * k) * reach
            else:
                ang = 84 - 58 * k ** 1.15
                ln = (H * 0.66) * (0.88 + 0.22 * math.sin(math.pi * min(1, k * 1.15))) * reach
            ang += droop
            tip = (base[0] + math.cos(math.radians(ang)) * ln, base[1] + math.sin(math.radians(ang)) * ln)
            tip = (min(tip[0], W - 0.6), min(tip[1], bot - 0.6))
            hw = (1.55 if style != 'plume' else 1.35) * width * (1.0 + 0.15 * (1 - k))
            f_long.append((base, tip, hw, 0.62 + 0.38 * k, 1, shape, 0.04 if style == 'angel' else 0.0))
        # secondaries along the forearm (elbow -> wrist), hanging down
        for i in range(ns):
            k = i / max(1, ns - 1)
            base = lerp(E, Wr, k)
            base = (base[0], base[1] + 1.0)
            ang = 96 - 14 * k
            ln = (H * (0.6 if style != 'owl' else 0.55)) * (0.95 + 0.08 * k) * reach
            if style == 'hawk':
                ln *= 0.9
            tip = (base[0] + math.cos(math.radians(ang)) * ln, base[1] + math.sin(math.radians(ang)) * ln)
            tip = (min(tip[0], W - 0.6), min(tip[1], bot - 0.6))
            f_long.append((base, tip, 1.7 * width, 0.3 + 0.32 * k, 1, shape, 0.0))
        # tertials by the body
        for i in range(nt):
            k = i / max(1, nt - 1)
            base = lerp(S, E, 0.15 + 0.85 * k)
            base = (base[0], base[1] + 0.8)
            ang = 100 - 6 * k
            ln = (H * 0.5) * (0.85 + 0.15 * k) * reach
            tip = (base[0] + math.cos(math.radians(ang)) * ln, base[1] + math.sin(math.radians(ang)) * ln)
            tip = (max(0.6, tip[0]), min(tip[1], bot - 0.6))
            f_long.append((base, tip, 1.8 * width, 0.05 + 0.25 * k, 1, shape, 0.0))
        # draw order: outer primaries first (under), then towards the body
        f_long.sort(key=lambda f: -f[3])
        # greater coverts: over the bases of everything
        lead = self.leading(40)
        n_c = int(14 * scale_cov)
        for i in range(n_c):
            k = (i + 0.5) / n_c
            base = _along(lead, k * 0.94)
            base = (base[0], base[1] + 1.4)
            j = _along(lead, min(1, k * 0.94 + 0.03))
            ang = 92 - 55 * k ** 1.3 if style != 'swept' else 80 - 70 * k
            ln = H * 0.3 * (1 - 0.25 * k) * (1.1 if style == 'plume' else 1)
            tip = (base[0] + math.cos(math.radians(ang)) * ln, base[1] + math.sin(math.radians(ang)) * ln)
            f_mid.append((base, tip, 1.5 * width, k, 2, 'round' if shape in ('round', 'point') else shape, 0.0))
        f_mid.sort(key=lambda f: -f[3])
        # lesser coverts / marginals: small scales along the leading edge
        n_s = int(20 * scale_cov)
        for row in range(2):
            for i in range(n_s):
                k = (i + 0.5 + row * 0.5) / n_s
                if k > 0.97:
                    continue
                base = _along(lead, k * 0.96)
                base = (base[0], base[1] - 0.2 + row * 1.6)
                ang = 95 - 50 * k ** 1.4
                ln = (H * 0.13) * (1 - 0.4 * k) + 1.0
                tip = (base[0] + math.cos(math.radians(ang)) * ln, base[1] + math.sin(math.radians(ang)) * ln)
                f_small.append((base, tip, 1.1 * width * (1 - 0.3 * k), k, 3, 'scale' if shape not in ('point', 'blade') else shape, 0.0))
        f_small.sort(key=lambda f: (-f[0][1], -f[3]))
        self.long = feather_maps(self.cv, f_long, seed)
        self.mid = feather_maps(self.cv, f_mid, seed + 1)
        self.small = feather_maps(self.cv, f_small, seed + 2, shadow=0.22)
        # a band along the leading edge on the bottom layer, so the wing reads as solid from the front too
        band = lead + [(p[0], p[1] + 2.4) for p in reversed(lead)]
        self.band = polys_mask(self.cv, [[self.cv.p(*q) for q in band]])
        self.cuts = [E[0], Wr[0]]

    def leading(self, n=40):
        pts = [self.S, self.E, self.Wr, self.T]
        return chaikin(pts, 3, closed=False)

    def u_map(self):
        """0 at the root .. 1 at the tip, for colour sweeps across the whole wing."""
        return np.clip(self.cv.X / self.cv.w, 0, 1)


def _along(pts, k):
    seg = [math.hypot(b[0] - a[0], b[1] - a[1]) for a, b in zip(pts, pts[1:])]
    total = sum(seg)
    d = k * total
    for (a, b), L in zip(zip(pts, pts[1:]), seg):
        if d <= L:
            f = d / L if L else 0
            return (a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f)
        d -= L
    return pts[-1]


# ---------------------------------------------------------------- membranes (bat / dragon)

class MembraneWing:
    """Arm bones along the leading edge, fingers fanning from the wrist, membrane between them with a scalloped edge.

    fingers: tip points (model px) from the outermost (top) to the innermost; bow: how deep the scallops go.
    """

    def __init__(self, W=26, H=28, top=-14, fingers=None, bow=0.22, seed=1, elbow=None, wrist=None, root_bottom=None,
                 spikes=0, tatter=0.0, claw=True):
        self.cv = cv = Canvas(W, H, top)
        S = (0.3, -0.5)
        E = elbow or (W * 0.36, -7.0)
        Wr = wrist or (W * 0.6, top + 2.0)
        if fingers is None:
            fingers = [(W - 0.6, top + 5.5), (W * 0.92, top + H * 0.62), (W * 0.66, top + H * 0.88), (W * 0.36, top + H - 1.5)]
        R = root_bottom or (0.3, top + H * 0.72)
        self.S, self.E, self.Wr, self.fingers, self.R = S, E, Wr, fingers, R
        pts = [S, E, Wr, fingers[0]]
        prev = fingers[0]
        chain = fingers[1:] + [R]
        for nxt in chain:
            pts += _bow(prev, nxt, Wr, bow)
            pts.append(nxt)
            prev = nxt
        self.poly = pts
        P = lambda q: cv.p(*q)
        mem = polys_mask(cv, [[P(q) for q in pts]])
        if tatter:
            rng = np.random.default_rng(seed + 50)
            holes = cv.zeros()
            n = fbm(cv.w, cv.h, 7, seed + 7, 3)
            d_edge = ndimage.distance_transform_edt(mem > 0.5)
            rip = (n < 0.36 + 0.1 * tatter) * (d_edge < 6 * tatter + 3)
            mem = mem * (1 - rip) * (1 - smoothstep(0.6, 0.62, fbm(cv.w, cv.h, 4, seed + 3, 2)) * tatter * 0.0)
            for _ in range(int(6 * tatter)):
                cx, cy = rng.uniform(cv.w * 0.25, cv.w * 0.9), rng.uniform(cv.h * 0.35, cv.h * 0.85)
                r = rng.uniform(1.5, 3.5) * D * 0.5
                holes = np.maximum(holes, (np.hypot(cv.X - cx, cv.Y - cy) < r).astype(np.float32))
            mem = mem * (1 - holes)
        self.mem = mem
        # bones
        self.arm = lines_mask(cv, [[P(S), P(E), P(Wr)]], 2.4 * D * 0.5)
        fl = [[P(Wr), P(f)] for f in fingers]
        self.fing = np.maximum.reduce([lines_mask(cv, [f], (1.5 - 0.15 * i) * D * 0.5) for i, f in enumerate(fl)])
        self.bone = np.maximum(self.arm, self.fing)
        self.joint = np.clip(1 - np.hypot(cv.X - P(Wr)[0], cv.Y - P(Wr)[1]) / (1.5 * D), 0, 1)
        self.joint = np.maximum(self.joint, np.clip(1 - np.hypot(cv.X - P(E)[0], cv.Y - P(E)[1]) / (1.3 * D), 0, 1))
        # claw at the wrist, spikes along the leading edge
        extra = []
        if claw:
            wx, wy = Wr
            extra.append([(wx - 0.6, wy + 0.2), (wx + 0.4, wy - 2.6), (wx + 0.8, wy + 0.4)])
        if spikes:
            lead = chaikin([S, E, Wr], 2, closed=False)
            for i in range(spikes):
                k = (i + 0.7) / (spikes + 0.4)
                bx, by = _along(lead, k)
                extra.append([(bx - 0.8, by + 0.3), (bx + 0.2, by - 1.6 - 0.6 * (1 - abs(k - 0.6))), (bx + 0.9, by + 0.3)])
        self.horn = polys_mask(cv, [[P(q) for q in tri] for tri in extra]) if extra else cv.zeros()
        Jx, Jy = P(Wr)
        self.d = np.hypot(cv.X - Jx, cv.Y - Jy) / max(cv.w, cv.h)
        self.edge = edge_of(mem, 1.0)
        # which finger sector each texel is in (for panels of colour)
        ang = np.arctan2(cv.Y - Jy, cv.X - Jx)
        self.ang = ang
        self.cuts = [E[0], Wr[0]]


def _bow(p, q, toward, amount, n=10):
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


def membrane_layers(mw, mem_col, bone_col=('#3a3236', '#141012'), mem_alpha=0.97, veins=None, vein_amt=0.45,
                    horn_col=('#e8dcc4', '#8a7a62'), seed=1, folds_amt=0.1, edge_col=None, edge_amt=0.45):
    """Base pictures (membrane layer, bone layer) from a MembraneWing and a membrane colour (h, w, 3)."""
    cv = mw.cv
    col = np.asarray(mem_col, np.float32)
    e = smoothstep(0.2, 0.9, mw.edge)[..., None]
    col = col * (1 - e * edge_amt) + (rgb(edge_col) * e * edge_amt if edge_col else 0)
    if folds_amt:
        # soft folds radiating from the wrist
        folds = 0.5 + 0.5 * np.cos(mw.ang * 9 + fbm(cv.w, cv.h, 10, seed, 2) * 3)
        col = col * (1 - folds_amt + folds_amt * folds[..., None])
    if veins:
        v = np.abs(fbm(cv.w, cv.h, 9, seed + 11, 3) - 0.5) < 0.022
        v = blur(v.astype(np.float32), 0.5) * mw.mem
        col = col * (1 - v[..., None] * vein_amt) + rgb(veins) * (v[..., None] * vein_amt)
    mem = rgba(np.clip(col, 0, 1), mw.mem * mem_alpha)
    yy = np.clip(cv.Y / cv.h, 0, 1)
    bc = grad(yy, list(bone_col))
    lit = blur(mw.bone, 1.0)
    bc = bc * (0.75 + 0.45 * np.clip(lit - 0.3, 0, 1)[..., None])
    bc = bc * (1 - edge_of(np.clip(mw.bone * 1.4, 0, 1), 0.8)[..., None] * 0.35)
    bc = np.clip(bc + mw.joint[..., None] * 0.12, 0, 1)
    bone = rgba(bc, np.clip(mw.bone * 1.2, 0, 1))
    hc = grad(np.clip(mw.d * 3, 0, 1), list(horn_col))
    hc = hc * (1 - edge_of(mw.horn, 0.8)[..., None] * 0.4)
    bone = _over(bone, rgba(hc, mw.horn))
    return mem, bone


def _over(dst, src):
    sa = src[..., 3:4]
    da = dst[..., 3:4]
    oa = sa + da * (1 - sa)
    oc = np.where(oa > 0, (src[..., :3] * sa + dst[..., :3] * da * (1 - sa)) / np.maximum(oa, 1e-6), 0)
    return np.concatenate([oc, oa], -1)


over = _over


# ---------------------------------------------------------------- insect wings

def insect_shape(cv, kind, x0=0.0, y0=0.0, w=14, h=12, slim=False):
    """Outline (texels) of a fore/hind wing with its root at (x0, y0) model px."""
    P = lambda x, y: cv.p(x0 + x, y0 + y)
    if kind == 'fore':
        pts = [P(0, 0), P(w * 0.25, -h * 0.55), P(w * 0.62, -h * 0.95), P(w * 0.95, -h * 1.0), P(w * 1.0, -h * 0.7),
               P(w * 0.85, -h * 0.2), P(w * 0.5, h * 0.12), P(w * 0.15, h * 0.12)]
        if slim:
            pts = [P(0, 0), P(w * 0.3, -h * 0.35), P(w * 0.8, -h * 0.6), P(w * 1.0, -h * 0.48), P(w * 0.8, -h * 0.12), P(w * 0.3, h * 0.08)]
    elif kind == 'hind':
        pts = [P(0, 0), P(w * 0.4, -h * 0.05), P(w * 0.9, h * 0.25), P(w * 0.95, h * 0.62), P(w * 0.62, h * 0.98),
               P(w * 0.3, h * 0.95), P(w * 0.06, h * 0.45)]
        if slim:
            pts = [P(0, 0), P(w * 0.4, h * 0.0), P(w * 0.95, h * 0.18), P(w * 1.0, h * 0.42), P(w * 0.65, h * 0.5), P(w * 0.2, h * 0.28)]
    elif kind == 'tail':  # swallowtail hindwing with a long tail
        pts = [P(0, 0), P(w * 0.45, -h * 0.05), P(w * 0.9, h * 0.25), P(w * 0.85, h * 0.55), P(w * 0.62, h * 0.7),
               P(w * 0.6, h * 1.25), P(w * 0.5, h * 1.28), P(w * 0.46, h * 0.78), P(w * 0.2, h * 0.7), P(w * 0.04, h * 0.35)]
    elif kind == 'lobe':  # rounded fairy lobe
        pts = [P(0, 0), P(w * 0.3, -h * 0.75), P(w * 0.75, -h * 1.0), P(w * 1.0, -h * 0.75), P(w * 0.85, -h * 0.3), P(w * 0.35, h * 0.05)]
    else:
        raise ValueError(kind)
    return chaikin(pts, 3)


def veins_mask(cv, root, tips, width=0.5, branch=True, seed=1):
    """Veins from a root (model px) to tips, with a few forks."""
    rng = np.random.default_rng(seed)
    lines = []
    R = cv.p(*root)
    for tx, ty in tips:
        T = cv.p(tx, ty)
        mid = (R[0] + (T[0] - R[0]) * 0.5 + rng.uniform(-1, 1) * D, R[1] + (T[1] - R[1]) * 0.5 + rng.uniform(-1, 1) * D)
        lines.append(bezier(R, mid, T, 14))
        if branch:
            k = rng.uniform(0.45, 0.7)
            bx, by = R[0] + (T[0] - R[0]) * k, R[1] + (T[1] - R[1]) * k
            ang = math.atan2(T[1] - R[1], T[0] - R[0]) + rng.choice([-1, 1]) * rng.uniform(0.25, 0.45)
            L = math.hypot(T[0] - R[0], T[1] - R[1]) * (1 - k) * 0.9
            lines.append([(bx, by), (bx + math.cos(ang) * L, by + math.sin(ang) * L)])
    return lines_mask(cv, lines, width * D * 0.5)


# ---------------------------------------------------------------- shards and plates

def shard_poly(base, ang, length, width, mid=0.3):
    a = math.radians(ang)
    dx, dy = math.cos(a), math.sin(a)
    px, py = -dy, dx
    bx, by = base
    mx, my = bx + dx * length * mid, by + dy * length * mid
    tip = (bx + dx * length, by + dy * length)
    return [(bx, by), (mx + px * width, my + py * width), tip, (mx - px * width, my - py * width)]


def facet_maps(cv, shards):
    """shards: [(poly model px, light 0..1 for the two halves, id)] back to front -> (alpha, light, id, edge, t)."""
    alpha, light, ids, edge = cv.zeros(), cv.zeros(), cv.zeros(), cv.zeros()
    for k, (poly, l1, l2) in enumerate(shards):
        P = [cv.p(*q) for q in poly]
        h1 = polys_mask(cv, [[P[0], P[1], P[2]]])
        h2 = polys_mask(cv, [[P[0], P[2], P[3]]])
        a = np.clip(h1 + h2, 0, 1)
        sh = shift(blur(a, 1.2), 1.0, 1.6) * 0.35 * (alpha > 0.05)
        light = light * (1 - sh)
        on1, on2 = h1 > 0.45, h2 > 0.45
        light[on1] = l1
        light[on2] = l2
        on = a > 0.45
        ids[on] = k
        e = edge_of(a, 0.9)
        edge[on] = e[on]
        alpha = a + alpha * (1 - a)
    return alpha, light, ids, edge


# ---------------------------------------------------------------- glow helpers

def glow_img(col, a):
    col = np.asarray(col, np.float32)
    if col.ndim == 1:
        col = np.broadcast_to(col, a.shape + (3,)).copy()
    return rgba(col, np.clip(a, 0, 1))


def sweep(cv, f, N, width=0.12, angle=0.6, speed=1.0):
    """A band of light crossing the picture once per loop of N frames: 0..1."""
    X, Y = cv.X / cv.w, cv.Y / cv.h
    pos = (X + Y * angle) / (1 + angle)
    c = (f / N * speed) % 1.0 * 1.6 - 0.3
    return np.clip(1 - np.abs(pos - c) / width, 0, 1) ** 1.5


def flicker(cv, f, N, cell=6, seed=1, rise=1.0):
    """Animated flame noise that loops over N frames: 0..1."""
    ph = 2 * math.pi * f / N
    a = fbm(cv.w, cv.h, cell, seed, 3)
    b = fbm(cv.w, cv.h, cell, seed + 31, 3)
    return np.clip(0.5 + 0.5 * (np.sin(ph + a * 6.283) * 0.6 + np.cos(ph * 2 + b * 6.283) * 0.4), 0, 1)


def twinkle_stars(cv, n, seed, f, N, mask, size=0.0):
    rng = np.random.default_rng(seed)
    out = cv.zeros()
    for _ in range(n):
        x, y = int(rng.integers(0, cv.w)), int(rng.integers(0, cv.h))
        if mask[y, x] < 0.5:
            continue
        ph = rng.random()
        b = (0.5 + 0.5 * math.sin(2 * math.pi * (f / N + ph))) ** 2 * (0.6 + 0.4 * rng.random())
        out[y, x] = max(out[y, x], b)
        if rng.random() < 0.3 + size:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                xx, yy = x + dx, y + dy
                if 0 <= xx < cv.w and 0 <= yy < cv.h:
                    out[yy, xx] = max(out[yy, xx], b * 0.45)
    return out


# ---------------------------------------------------------------- clean stylised feathered wings

class CleanWing:
    """A stylised wing with few, large, clean feathers (the Polar look): flight feathers, one row of coverts and a
    smooth arm with a scalloped edge. The picture is fitted to the feathers, so nothing gets squashed at the edges.

    style: 'angel' (tall, primaries fanning down and out), 'hawk' (broad, reaching outwards), 'swept' (long, pointed,
    angled up), 'round' (short and rounded), 'plume' (long streaming feathers).
    """

    def __init__(self, style='angel', size=1.0, n=(6, 5, 2), shape='round', width=1.3, reach=1.0, cov=5, scallops=4,
                 seed=1, droop=0.0, arm=2.6):
        s = size
        st = {
            'angel': dict(S=(0.4, 0.0), E=(8.0, -6.0), Wr=(14.5, -12.0), T=(19.5, -11.0), p_ang=(84, 30), p_len=(15.5, 18.5), s_ang=(98, 84), s_len=15.0, t_len=11.5),
            'hawk': dict(S=(0.4, 0.5), E=(8.0, -4.0), Wr=(15.0, -8.0), T=(20.5, -8.0), p_ang=(76, 8), p_len=(13.0, 15.5), s_ang=(100, 86), s_len=12.5, t_len=10.0),
            'swept': dict(S=(0.4, 0.0), E=(8.0, -5.0), Wr=(14.0, -10.0), T=(19.5, -12.0), p_ang=(66, -6), p_len=(14.0, 18.5), s_ang=(98, 80), s_len=13.0, t_len=10.0),
            'round': dict(S=(0.4, 0.5), E=(8.0, -4.0), Wr=(14.5, -7.5), T=(19.5, -6.5), p_ang=(80, 18), p_len=(11.5, 12.5), s_ang=(100, 88), s_len=11.5, t_len=9.5),
            'plume': dict(S=(0.4, 0.0), E=(8.0, -5.5), Wr=(14.5, -11.0), T=(20.0, -11.5), p_ang=(80, 30), p_len=(19.0, 23.0), s_ang=(98, 88), s_len=17.5, t_len=13.5),
        }[style]
        sc = lambda p: (p[0] * s, p[1] * s)
        S, E, Wr, T = sc(st['S']), sc(st['E']), sc(st['Wr']), sc(st['T'])
        self.S, self.E, self.Wr, self.T = S, E, Wr, T
        lerp = lambda a, b, k: (a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k)
        np_, ns, nt = n
        L, M, A = [], [], []
        pol = lambda b, ang, ln: (b[0] + math.cos(math.radians(ang)) * ln, b[1] + math.sin(math.radians(ang)) * ln)
        a0, a1 = st['p_ang']
        l0, l1 = st['p_len']
        for i in range(np_):
            k = i / max(1, np_ - 1)
            base = lerp(Wr, T, k)
            base = (base[0], base[1] + 1.0 * s)
            ang = a0 + (a1 - a0) * k ** 1.05 + droop
            ln = (l0 + (l1 - l0) * math.sin(math.pi * 0.5 * min(1, k * 1.25)) - 1.5 * max(0, k - 0.8) / 0.2) * s * reach
            L.append((base, pol(base, ang, ln), 1.75 * width * s, 0.6 + 0.4 * k, 1, shape, 0.0))
        b0, b1 = st['s_ang']
        for i in range(ns):
            k = i / max(1, ns - 1)
            base = lerp(E, Wr, 0.1 + 0.85 * k)
            base = (base[0], base[1] + 1.0 * s)
            ang = b0 + (b1 - b0) * k + droop * 0.4
            ln = st['s_len'] * (0.94 + 0.08 * k) * s * reach
            L.append((base, pol(base, ang, ln), 1.9 * width * s, 0.25 + 0.33 * k, 1, shape, 0.0))
        for i in range(nt):
            k = i / max(1, nt - 1) if nt > 1 else 0.5
            base = lerp(S, E, 0.3 + 0.55 * k)
            base = (base[0], base[1] + 0.8 * s)
            ang = 100 - 4 * k
            ln = st['t_len'] * (0.9 + 0.1 * k) * s * reach
            L.append((base, pol(base, ang, ln), 2.0 * width * s, 0.05 + 0.2 * k, 1, shape, 0.0))
        L.sort(key=lambda f: -f[3])
        lead = chaikin([S, E, Wr, T], 3, closed=False)
        self.lead = lead
        for i in range(cov):
            k = (i + 0.5) / cov
            base = _along(lead, k * 0.92)
            base = (base[0], base[1] + 1.2 * s)
            ang = 96 - 62 * k ** 1.25
            ln = (6.2 - 2.0 * k) * s * (reach ** 0.5)
            M.append((base, pol(base, ang, ln), 1.75 * width * s, k, 2, 'round' if shape != 'blade' else 'blade', 0.0))
        M.sort(key=lambda f: -f[3])
        self.feathers = (L, M)
        # the arm: a smooth band along the leading edge with a scalloped lower edge
        pts = []
        for i in range(41):
            pts.append(_along(lead, i / 40))
        self.arm_top = pts
        self.arm_w = arm * s
        self.scallops = scallops
        # fit the canvas around everything
        xs, ys = [], []
        for lst in (L, M):
            for base, tip, hw, *_ in lst:
                for p in (base, tip):
                    xs += [p[0] - hw, p[0] + hw]
                    ys += [p[1] - hw, p[1] + hw]
        for p in pts:
            xs.append(p[0])
            ys += [p[1] - 1.0, p[1] + arm * s + 1.5]
        x1 = max(xs) + 0.8
        y0, y1 = min(ys) - 0.8, max(ys) + 0.8
        self.W, self.top = math.ceil(x1 * 2) / 2, math.floor(y0 * 2) / 2
        self.H = math.ceil((y1 - self.top) * 2) / 2
        self.cv = cv = Canvas(self.W, self.H, self.top)
        self.long = feather_maps(cv, L, seed, shadow=0.26)
        self.mid = feather_maps(cv, M, seed + 1, shadow=0.24)
        # arm shape: a smooth band whose lower edge rolls in a few gentle waves
        P = lambda q: cv.p(*q)
        bottom = []
        for i in range(len(pts)):
            k = i / (len(pts) - 1)
            wv = 0.78 + 0.22 * math.cos(2 * math.pi * k * scallops) - 0.25 * k
            bottom.append((pts[i][0], pts[i][1] + arm * s * wv))
        band = chaikin(pts + list(reversed(bottom)), 2)
        self.arm = polys_mask(cv, [[P(q) for q in band]])
        # arm facts for shading: distance from the leading edge (0 top .. 1 bottom)
        lx = np.array([P(q)[0] for q in pts])
        ly = np.array([P(q)[1] for q in pts])
        top_at = np.interp(cv.X, lx, ly, left=ly[0], right=ly[-1])
        self.arm_v = np.clip((cv.Y - top_at) / (arm * s * D * 1.2), 0, 1)
        self.arm_edge = edge_of(self.arm, 1.0)
        # under the flight feathers (front view): a solid band so there are no gaps
        self.band = np.clip(blur(self.arm, 0.5) * 1.2, 0, 1)
        self.cuts = [E[0], Wr[0]]

    def u_map(self):
        return np.clip(self.cv.X / self.cv.w, 0, 1)


def clean_shade(m, col, line, rim=0.55, gloss=0.1, shadow=True, alpha=1.0, crease=0.05):
    """Flat, clean feather colouring: crisp outline, a soft highlight down the middle, gentle overlap shadows."""
    c = np.asarray(col, np.float32).copy()
    e = smoothstep(0.35, 0.95, m.edge)[..., None]
    c = c * (1 - e * rim) + rgb(line) * (e * rim * 0.55)
    hl = np.clip(1 - np.abs(m.across - 0.28) * 3.0, 0, 1) * (1 - m.edge) * smoothstep(0.05, 0.3, m.t) * (1 - smoothstep(0.85, 1.0, m.t))
    c = c + gloss * hl[..., None]
    c = c * (1 + crease * m.side[..., None] * (m.alpha[..., None] > 0))
    if shadow:
        c = c * (1 - m.shadow[..., None])
    return rgba(np.clip(c, 0, 1), m.alpha * alpha)


def clean_arm(cw, col, line, rim=0.55, alpha=1.0):
    c = np.asarray(col, np.float32).copy()
    e = smoothstep(0.3, 0.9, cw.arm_edge)[..., None]
    c = c * (1 - e * rim) + rgb(line) * (e * rim * 0.55)
    hl = (1 - cw.arm_v) * 0.08
    c = c + hl[..., None]
    return rgba(np.clip(c, 0, 1), cw.arm * alpha)
