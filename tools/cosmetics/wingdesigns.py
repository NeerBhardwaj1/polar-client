"""The Polar wing designs (all original, in the clean stylised look): each returns (WingModel, extra). Built by wings.py."""
import math

import numpy as np

from lib import rgb, rgba, gradient, smoothstep, blur, fbm, vnoise
from wingkit import WingModel, Rig, Layer, Part, DENSITY as D
from wingpaint import (Canvas, MembraneWing, membrane_layers, grad, glow_img, sweep, twinkle_stars, polys_mask,
                       lines_mask, chaikin, insect_shape, veins_mask, shard_poly, facet_maps, edge_of, hsv2rgb, over,
                       bezier, shift, _along, clean_shade)
from winghelpers import clean, membraned, insect, shard_layout, sharded, part_canvas, const, cached

DESIGNS = []


def design(id, name, family, desc, motion, tint=False, rarity='epic', remake=False, color='#3fbf74'):
    def wrap(fn):
        DESIGNS.append({'id': id, 'name': name, 'family': family, 'desc': desc, 'motion': motion, 'tint': tint,
                        'rarity': rarity, 'remake': remake, 'build': fn, 'color': color})
        return fn
    return wrap


def bc(col, shape):
    return np.broadcast_to(rgb(col), shape + (3,)).copy()


def U(cw):
    return np.clip(cw.cv.X / cw.cv.w, 0, 1)


def wave(cw, f, N, k=1.0, angle=0.5, freq=1.0):
    """A smooth band of light travelling across the wing, looping every N frames: 0..1."""
    X, Y = cw.cv.X / cw.cv.w, cw.cv.Y / cw.cv.h
    return 0.5 + 0.5 * np.cos(2 * math.pi * (freq * (X + Y * angle) - f / N * k))


def pulse(f, N, lo=0.6, hi=1.0):
    return lo + (hi - lo) * (0.5 - 0.5 * math.cos(2 * math.pi * f / N))


def rim_glow(m, amt=0.6, inner=0.3):
    return m.alpha * smoothstep(inner, 1.0, m.edge) * amt


# ================================================================ the current ten, rebuilt

@design('wings_angel', 'Angel Wings', 'Feathered', 'Clean white feathers with soft golden edges.',
        'Slow, graceful beats; a warm glow breathes along the edges', remake=True)
def angel():
    N = 12
    gold = rgb('#ffd98a')

    def g(name, m, cw, f):
        if name == 'arm':
            return None
        p = pulse(f, N, 0.55, 1.0)
        return glow_img(gold, (rim_glow(m, 0.55) + m.alpha * smoothstep(0.86, 1, m.t) * 0.35) * p)
    return clean('angel', cols=('#ffffff:#e4eaf5', '#ffffff:#eef2fa', '#ffffff:#f4f6fb'), line='#8a9cc0', glow=g,
                 frames=N, frame_time=110, flap=dict(amp=15, speed=0.38))


@design('wings_phoenix', 'Phoenix Wings', 'Fire', 'Feathers of living fire, from gold to deep crimson.',
        'Strong beats; flames flow up through every feather', remake=True, rarity='legendary')
def phoenix():
    N = 10

    def col(name, m, cw, f):
        if name == 'arm':
            return grad(cw.arm_v, '#ffe58a:#ffc040')
        if name == 'mid':
            return grad(m.t, '#ffe58a:#ffb030')
        return grad(m.t, '#ffcf4a:#ff7a1a:#d8260a')

    def g(name, m, cw, f):
        if name == 'arm':
            return None
        flow = 0.5 + 0.5 * np.cos(2 * math.pi * (m.t * 1.6 - f / N))
        c = grad(np.clip(m.t, 0, 1), '#fff2b0:#ffa020:#ff4010')
        return glow_img(c, m.alpha * (0.15 + 0.55 * flow * m.t) * (1 - m.edge * 0.3))
    return clean('hawk', shape='point', color=col, line='#6a1205', glow=g, frames=N, frame_time=80,
                 flap=dict(amp=18, speed=0.45))


@design('wings_aurora', 'Aurora Wings', 'Feathered', 'Teal and violet feathers with the northern lights rippling through.',
        'Gentle beats; colour ripples from root to tip', remake=True, rarity='legendary')
def aurora():
    N = 16

    def col(name, m, cw, f):
        u = U(cw)
        if name == 'arm':
            return grad(u, '#c8fff0:#b8c8ff')
        return grad(np.clip(u * 0.7 + m.t * 0.4, 0, 1), '#3fe0c4:#4ab8e8:#8a6cff')

    def g(name, m, cw, f):
        w = wave(cw, f, N, angle=0.4, freq=1.2)
        a = (m.alpha if m is not None else cw.arm) * w * (0.35 if name != 'arm' else 0.2)
        return glow_img(grad(w, '#7dffd6:#c4a8ff'), a)
    return clean('swept', color=col, line='#1d3a66', glow=g, frames=N, frame_time=90)


@design('wings_frost', 'Frost Wings', 'Ice & Water', 'Clean crystal feathers of glittering ice.',
        'Crisp beats; light glints across the crystals', remake=True, rarity='legendary')
def frost():
    N = 12

    def col(name, m, cw, f):
        if name == 'arm':
            return grad(cw.arm_v, '#f4fcff:#d0eeff')
        return grad(m.t, '#eefaff:#a8dcff:#5aa8f0')

    def g(name, m, cw, f):
        s = sweep(cw.cv, f, N, 0.1)
        a = m.alpha if m is not None else cw.arm
        k = (0.5 + 0.5 * m.side) if m is not None else 0.6
        return glow_img(rgb('#f2fcff'), a * s * 0.7 * k + (rim_glow(m, 0.25) if m is not None else 0))
    return clean('swept', shape='point', color=col, line='#2a6aa8', crease=0.22, gloss=0.06, glow=g, frames=N,
                 frame_time=90, flap=dict(amp=13, speed=0.36), alpha=0.95)


@design('wings_dragon', 'Dragon Wings', 'Nature', 'Smooth dragon wings in any colour you like.',
        'Heavy, powerful beats', tint=True, remake=True)
def dragon():
    def mem(mw, f):
        return grad(np.clip(mw.d * 1.6, 0, 1), '#f6f6f6:#cfcfcf:#a0a0a0')
    return membraned(27, 29, -15, seed=4, mem=mem, bone_col=('#e8e8e8', '#9a9a9a'), horn_col=('#ffffff', '#c0c0c0'),
                     spikes=4, tint=True, edge_col='#404040', edge_amt=0.55,
                     flap=dict(amp=17, speed=0.32, speedMove=0.9, down=0.32))


@design('wings_galaxy', 'Galaxy Wings', 'Cosmic', 'Wings cut from a deep violet sky, full of twinkling stars.',
        'Calm beats; stars twinkle and a nebula glow drifts across', remake=True, rarity='legendary')
def galaxy():
    N = 12

    def mem(mw, f):
        return grad(np.clip(mw.d * 1.5, 0, 1), '#6a3ad8:#3a1a8c:#140838')

    def g(mw, f):
        s = twinkle_stars(mw.cv, 110, 7, f, N, mw.mem)
        X, Y = mw.cv.X / mw.cv.w, mw.cv.Y / mw.cv.h
        neb = (0.5 + 0.5 * np.cos(2 * math.pi * (X * 0.8 + Y * 0.5 - f / N))) * mw.mem * 0.25
        return glow_img(np.clip(rgb('#ffffff') * s[..., None] + rgb('#ff6ad8') * neb[..., None] * 2, 0, 1), np.clip(s + neb, 0, 1))
    return membraned(27, 28, -14, seed=6, mem=mem, bone_col=('#3a2a6a', '#140c30'), horn_col=('#c9b8ff', '#5a4a9a'),
                     glow=g, frames=N, frame_time=120, flap=dict(amp=14, speed=0.3), bow=0.2, claw=False,
                     edge_col='#c8a8ff', edge_amt=0.5)


@design('wings_mech', 'Mech Wings', 'Tech', 'Polished steel blades with an energy core in each one.',
        'Precise beats; energy pulses out along every blade', remake=True)
def mech():
    N = 12

    def col(name, m, cw, f):
        if name == 'arm':
            return grad(cw.arm_v, '#c8d0da:#8a95a3')
        return grad(m.t, '#b8c2ce:#6a7584:#3a424e')

    def g(name, m, cw, f):
        if name != 'long':
            return None
        core = smoothstep(0.32, 0.08, m.across) * (m.t > 0.06) * (m.t < 0.86)
        run = 0.35 + 0.65 * (0.5 + 0.5 * np.cos(2 * math.pi * (m.t * 1.5 - f / N)))
        return glow_img(rgb('#38e8ff'), m.alpha * core * run)
    return clean('swept', shape='blade', n=(6, 4, 2), width=1.15, color=col, line='#141a22', gloss=0.2, glow=g, frames=N,
                 frame_time=70, flap=dict(amp=11, speed=0.32, down=0.45))


@design('wings_demon', 'Demon Wings', 'Dark', 'Deep crimson wings with a smouldering heartbeat glow.',
        'Heavy beats; a red glow pulses out from the bones like a heartbeat', remake=True)
def demon():
    N = 12

    def mem(mw, f):
        return grad(np.clip(mw.d * 1.5, 0, 1), '#8a1420:#4a0a14:#1e0408')

    def g(mw, f):
        near = blur(mw.bone, 3.0) * mw.mem
        beat = max(0.0, math.sin(2 * math.pi * f / N)) ** 3 + 0.35 * max(0.0, math.sin(2 * math.pi * f / N - 1.2)) ** 3
        return glow_img(rgb('#ff3a2a'), np.clip(near * (0.25 + 0.9 * beat) + mw.edge * 0.25 * mw.mem, 0, 1))
    return membraned(27, 28, -14, seed=3, mem=mem, bone_col=('#3a2a2c', '#140c0e'), horn_col=('#efe2cc', '#7a6a58'),
                     spikes=3, glow=g, frames=N, frame_time=90, edge_col='#ff5a4a', edge_amt=0.35,
                     flap=dict(amp=16, speed=0.34, down=0.32))


def clean_insect(cv, masks, fills, border='#1a1a1a', border_w=1.6, dots='#ffffff', n_dots=7, vein=None, alpha=1.0,
                 vein_amt=0.45, root=(0.6, 0.0), tips=None):
    img = np.zeros((cv.h, cv.w, 4), np.float32)
    for k, m in enumerate(masks):
        col = np.asarray(fills(cv, k), np.float32)
        inner = blur(m, border_w)
        band = np.clip((m - inner) * 3.2 + (inner < 0.6) * (m > 0.5) * 1.0, 0, 1) * (m > 0.05)
        band = smoothstep(0.25, 0.75, band)
        col = col * (1 - band[..., None]) + rgb(border) * band[..., None]
        if n_dots:
            ys, xs = np.where((band > 0.8) & (blur(m, border_w * 2.2) < 0.75) & (m > 0.9))
            if len(xs):
                rng = np.random.default_rng(5 + k)
                pick = np.linspace(0, len(xs) - 1, n_dots).astype(int)
                order = np.argsort(np.arctan2(ys - ys.mean(), xs - xs.mean()))
                dot = np.zeros((cv.h, cv.w), np.float32)
                for i in order[pick]:
                    dot = np.maximum(dot, np.clip(1.6 - np.hypot(cv.X - xs[i], cv.Y - ys[i]) / 1.4, 0, 1))
                col = col * (1 - dot[..., None] * m[..., None]) + rgb(dots) * (dot * m)[..., None]
        img = over(img, rgba(np.clip(col, 0, 1), m * alpha))
    if vein and tips:
        v = veins_mask(cv, root, tips, 0.4, branch=False, seed=2) * np.clip(sum(masks), 0, 1)
        img[..., :3] = img[..., :3] * (1 - v[..., None] * vein_amt) + rgb(vein) * (v[..., None] * vein_amt)
    return img


@design('wings_butterfly', 'Butterfly Wings', 'Nature', 'Bold butterfly wings in any colour you like.',
        'Quick, fluttering beats', tint=True, remake=True, rarity='rare', color='#ff9a2e')
def butterfly():
    shapes = [('fore', 0.5, -1.0, 15, 13, False), ('hind', 0.5, 0.0, 11, 12, False)]

    def fills(cv, k):
        X, Y = cv.X / cv.w, cv.Y / cv.h
        col = grad(np.clip(np.hypot(X, Y - 0.55) * 1.2, 0, 1), '#ffffff:#ececec:#c8c8c8')
        cx, cy = (cv.p(9.0, -7.0) if k == 0 else cv.p(6.0, 6.2))
        r = np.hypot(cv.X - cx, cv.Y - cy) / (2.3 * D)
        col = np.where((r < 0.42)[..., None], rgb('#202020'), np.where((r < 0.66)[..., None], rgb('#ffffff'),
                       np.where((r < 1.0)[..., None], rgb('#5a5a5a'), col)))
        return col

    def paint(cv, masks, f):
        return clean_insect(cv, masks, fills, vein='#3a3a3a', root=(0.6, -0.3),
                            tips=[(14, -12), (15, -7), (12, -2), (9, 9), (5, 11.5)])
    return insect(16, 26, -14, shapes, paint, tint=True)


@design('wings_fairy', 'Fairy Wings', 'Nature', 'Glassy pastel fairy wings with a sparkling white rim.',
        'Very fast flutter; sparkles twinkle along the edges', remake=True)
def fairy():
    N = 12
    shapes = [('lobe', 0.5, -0.5, 17, 18, False), ('lobe', 0.5, 0.8, 12, -13, False)]

    def paint(cv, masks, f):
        def fills(cv, k):
            X, Y = cv.X / cv.w, cv.Y / cv.h
            return hsv2rgb(0.8 + X * 0.3 - Y * 0.12, 0.45, 1.0)
        return clean_insect(cv, masks, fills, border='#ffffff', border_w=1.0, n_dots=0, alpha=0.72, vein='#ffffff',
                            vein_amt=0.5, root=(0.6, 0.2), tips=[(12, -13), (13, -7), (9, 8)])

    def g(cv, masks, f):
        m = np.clip(masks[0] + masks[1], 0, 1)
        e = edge_of(m, 1.2)
        tw = twinkle_stars(cv, 70, 12, f, N, smoothstep(0.3, 0.8, e) + 0.0)
        return glow_img(rgb('#f6e8ff'), e * 0.45 + tw)
    return insect(18, 34, -19, shapes, paint, glow=g, frames=N, frame_time=90, translucent=True,
                  flap=dict(amp=30, speed=1.4, speedMove=2.6))


# ================================================================ new: feathered

@design('wings_seraph_plume', 'Seraph Plume', 'Feathered', 'Long, elegant plumes of white and champagne gold.',
        'Slow, sweeping beats; a golden shine glides across the plumes', rarity='legendary')
def seraph_plume():
    N = 14

    def g(name, m, cw, f):
        if name == 'arm':
            return None
        s = sweep(cw.cv, f, N, 0.1)
        return glow_img(rgb('#ffd978'), m.alpha * (s * 0.5 + smoothstep(0.88, 1, m.t) * 0.35) + rim_glow(m, 0.35))
    return clean('plume', cols=('#ffffff:#f0e4c4', '#ffffff:#f6eedb', '#fffdf6:#f3ead2'), line='#b8913f', glow=g,
                 frames=N, frame_time=90, flap=dict(amp=13, speed=0.34))


@design('wings_dawnbreaker', 'Dawnbreaker', 'Feathered', 'A sunrise in feathers: violet at the root, burning gold at the tips.',
        'Graceful beats; the tips glow warm and breathe')
def dawnbreaker():
    N = 12

    def col(name, m, cw, f):
        u = U(cw)
        if name == 'arm':
            return grad(u, '#4a2478:#a3367d')
        return grad(np.clip(u * 0.6 + m.t * 0.5, 0, 1), '#4a2478:#a3367d:#ff6a5a:#ffb347:#ffe9a8')

    def g(name, m, cw, f):
        if name != 'long':
            return None
        return glow_img(rgb('#ffcf6a'), m.alpha * smoothstep(0.75, 1.0, m.t) * pulse(f, N, 0.35, 0.75))
    return clean('angel', color=col, line='#2a1240', glow=g, frames=N, frame_time=110)


@design('wings_raven_shroud', 'Raven Shroud', 'Feathered', 'Blue-black feathers with a purple and green sheen.',
        'Sharp beats; the sheen slides across the feathers')
def raven_shroud():
    N = 14

    def col(name, m, cw, f):
        if name == 'arm':
            return grad(cw.arm_v, '#3e3a58:#24223a')
        return grad(m.t, '#3e3a58:#22203a:#16142a')

    def g(name, m, cw, f):
        a0 = m.alpha if m is not None else cw.arm
        X, Y = cw.cv.X / cw.cv.w, cw.cv.Y / cw.cv.h
        sheen = 0.5 + 0.5 * np.cos(2 * math.pi * (X * 1.1 - Y * 0.5 - f / N))
        c = grad(sheen, '#5a2aa8:#1a8a6e')
        return glow_img(c, a0 * (0.14 + 0.45 * sheen ** 2))
    return clean('hawk', shape='point', color=col, line='#6a5a9a', rim=0.45, glow=g, frames=N, frame_time=90,
                 flap=dict(amp=17, speed=0.44, down=0.34), gloss=0.08)


@design('wings_snowy_owl', 'Snowy Owl', 'Feathered', 'Soft, rounded white feathers with neat dark bars.',
        'Silent, soft beats', rarity='rare')
def snowy_owl():
    def col(name, m, cw, f):
        if name == 'arm':
            return grad(cw.arm_v, '#ffffff:#f4f2ec')
        base = grad(m.t, '#ffffff:#f3f1ea')
        bar = smoothstep(0.6, 0.64, m.t) * (1 - smoothstep(0.72, 0.76, m.t)) * smoothstep(0.85, 0.6, m.across)
        bar2 = smoothstep(0.82, 0.86, m.t) * (1 - smoothstep(0.9, 0.94, m.t)) * smoothstep(0.85, 0.6, m.across)
        dark = np.clip((bar + bar2) * (0.8 if name == 'long' else 0.5), 0, 1)
        return base * (1 - dark[..., None]) + rgb('#4a443c') * dark[..., None]
    return clean('round', color=col, line='#9a9284', flap=dict(amp=14, speed=0.36, down=0.42))


@design('wings_sunhawk', 'Sunhawk', 'Feathered', 'Polished gold and bronze feathers that catch the light.',
        'Proud, powerful beats; a gleam runs along the gold')
def sunhawk():
    N = 12

    def col(name, m, cw, f):
        if name == 'arm':
            return grad(cw.arm_v, '#ffeb9a:#e8b84a')
        return grad(np.clip(m.t * 0.8 + U(cw) * 0.25, 0, 1), '#fff1a8:#f4c542:#c98a1c:#8a5214')

    def g(name, m, cw, f):
        a0 = m.alpha if m is not None else cw.arm
        s = sweep(cw.cv, f, N, 0.08, angle=-0.4)
        return glow_img(rgb('#fff6d0'), a0 * s * 0.75)
    return clean('hawk', color=col, line='#5a3208', gloss=0.22, glow=g, frames=N, frame_time=80,
                 flap=dict(amp=17, speed=0.42))


# ================================================================ new: fire

@design('wings_ember_drift', 'Ember Drift', 'Fire', 'Charcoal feathers with glowing ember edges.',
        'Steady beats; the edges smoulder and glow (in game, embers drift off)')
def ember_drift():
    N = 12

    def col(name, m, cw, f):
        if name == 'arm':
            return grad(cw.arm_v, '#4a423e:#2a2422')
        return grad(m.t, '#4a423e:#221c1a')

    def g(name, m, cw, f):
        if m is None:
            return None
        flow = 0.5 + 0.5 * np.cos(2 * math.pi * (m.t * 1.2 + m.id * 0.13 - f / N))
        a = rim_glow(m, 0.75, 0.2) * (0.45 + 0.55 * flow) + m.alpha * smoothstep(0.85, 1, m.t) * (0.4 + 0.5 * flow)
        return glow_img(grad(flow, '#ff3a0a:#ff9a2a'), a * (1.0 if name == 'long' else 0.6))
    return clean('swept', shape='point', color=col, line='#0a0806', glow=g, frames=N, frame_time=90)


@design('wings_magma_veil', 'Magma Veil', 'Fire', 'Black basalt wings with lava flowing out along the bones.',
        'Slow, heavy beats; lava pulses outward through the wing')
def magma_veil():
    N = 12

    def mem(mw, f):
        return grad(np.clip(mw.d * 1.4, 0, 1), '#3a3230:#1a1614:#0c0a0a')

    def g(mw, f):
        near = blur(mw.bone, 2.2) * mw.mem
        flow = 0.5 + 0.5 * np.cos(2 * math.pi * (mw.d * 3.0 - f / N))
        a = np.clip(near * (0.35 + 0.75 * flow) + mw.edge * mw.mem * 0.3 * flow, 0, 1)
        return glow_img(grad(flow, '#c81e05:#ff7a1a:#ffc850'), a)
    return membraned(27, 28, -14, seed=7, mem=mem, bone_col=('#4a3e38', '#1a1412'), horn_col=('#ffb060', '#8a3a0a'),
                     spikes=3, glow=g, bone_glow=lambda mw, f: glow_img(rgb('#ff7a1a'), mw.horn * pulse(f, N, 0.5, 0.9)),
                     frames=N, frame_time=100, edge_col='#ff7a2a', edge_amt=0.3, flap=dict(amp=14, speed=0.28, down=0.3))


@design('wings_soulfire', 'Soulfire', 'Fire', 'Feathers of cold blue flame.',
        'Light beats; blue fire flows up through each feather', rarity='legendary')
def soulfire():
    N = 12

    def col(name, m, cw, f):
        if name == 'arm':
            return grad(cw.arm_v, '#1a3a9a:#0e1e5a')
        return grad(m.t, '#1a3a9a:#0a1648')

    def g(name, m, cw, f):
        a0 = m.alpha if m is not None else cw.arm
        t = m.t if m is not None else cw.arm_v
        flow = 0.5 + 0.5 * np.cos(2 * math.pi * (t * 1.4 - f / N))
        core = (1 - m.across) if m is not None else 0.6
        c = grad(np.clip(core * 0.6 + flow * 0.4, 0, 1), '#2a4aff:#4ab8ff:#d8f8ff')
        return glow_img(c, a0 * np.clip(0.25 + 0.55 * flow * core + (rim_glow(m, 0.4) if m is not None else 0), 0, 1))
    return clean('swept', shape='point', color=col, line='#00051a', glow=g, frames=N, frame_time=75)


@design('wings_solar_flare', 'Solar Flare', 'Fire', 'White-hot feathers blazing out to orange tips.',
        'Fast beats; the wings flare brighter with every beat', rarity='legendary')
def solar_flare():
    N = 10

    def col(name, m, cw, f):
        if name == 'arm':
            return grad(cw.arm_v, '#fffbe6:#ffe08a')
        return grad(np.clip(m.t * 0.75 + U(cw) * 0.3, 0, 1), '#fff8e0:#ffd24a:#ff8a1a:#e8400a')

    def g(name, m, cw, f):
        a0 = m.alpha if m is not None else cw.arm
        p = pulse(f, N, 0.25, 0.8)
        X, Y = cw.cv.X / cw.cv.w, cw.cv.Y / cw.cv.h
        ring = 0.5 + 0.5 * np.cos(2 * math.pi * (np.hypot(X, Y * 0.7) * 1.5 - f / N))
        return glow_img(rgb('#ffd860'), a0 * (p * 0.5 + ring * 0.3))
    return clean('angel', shape='point', color=col, line='#9a3a08', glow=g, frames=N, frame_time=70,
                 flap=dict(amp=17, speed=0.5))


@design('wings_cinder_crest', 'Cinder Crest', 'Fire', 'Ash-grey feathers with red-hot tips that never cool.',
        'Calm beats; the tips glow and fade like coals')
def cinder_crest():
    N = 14

    def col(name, m, cw, f):
        if name == 'arm':
            return grad(cw.arm_v, '#8a847e:#5a5450')
        return grad(m.t, '#8a847e:#5a5450:#3a3432')

    def g(name, m, cw, f):
        if name != 'long':
            return None
        p = 0.45 + 0.55 * (0.5 + 0.5 * np.cos(2 * math.pi * (f / N + m.id * 0.17)))
        return glow_img(grad(m.t, '#ff2a0a:#ff7a1a:#ffc050'), m.alpha * smoothstep(0.68, 1.0, m.t) * p)
    return clean('round', color=col, line='#1a1614', glow=g, frames=N, frame_time=110)


# ================================================================ new: ice & water

@design('wings_glacier_shard', 'Glacier Shard', 'Ice & Water', 'Thick, faceted slabs of deep glacier ice.',
        'Slow, solid beats; a cold light slides over the facets')
def glacier_shard():
    N = 14

    def col(name, m, cw, f):
        if name == 'arm':
            return grad(cw.arm_v, '#e6f6ff:#9ad4f4')
        return grad(m.t, '#d8f2ff:#7cc0ec:#2a74c4')

    def g(name, m, cw, f):
        a0 = m.alpha if m is not None else cw.arm
        s = sweep(cw.cv, f, N, 0.14, angle=0.8)
        k = (0.5 + 0.5 * m.side) if m is not None else 0.5
        return glow_img(rgb('#e8faff'), a0 * s * 0.55 * k)
    return clean('hawk', shape='blade', n=(5, 4, 2), width=1.55, color=col, line='#16447a', crease=0.28, gloss=0.05,
                 glow=g, frames=N, frame_time=90, flap=dict(amp=12, speed=0.3, down=0.42))


@design('wings_blizzard', 'Blizzard', 'Ice & Water', 'Frosted white feathers dusted with sparkling snow.',
        'Brisk beats; snow sparkles across the feathers (in game, snowflakes fall)')
def blizzard():
    N = 12

    def col(name, m, cw, f):
        if name == 'arm':
            return grad(cw.arm_v, '#ffffff:#e4f2fc')
        return grad(m.t, '#ffffff:#d8ecfa:#a8d0f0')

    def g(name, m, cw, f):
        a0 = m.alpha if m is not None else cw.arm
        tw = twinkle_stars(cw.cv, 60 if name == 'long' else 30, 40 + len(name), f, N, a0)
        return glow_img(rgb('#ffffff'), tw + (rim_glow(m, 0.25) if m is not None else 0))
    return clean('swept', color=col, line='#6a9ac8', glow=g, frames=N, frame_time=100)


@design('wings_tidecaller', 'Tidecaller', 'Ice & Water', 'Glassy teal fins with water flowing through them.',
        'Smooth, swimming beats; water ripples out along the fins')
def tidecaller():
    N = 14

    def mem(mw, f):
        return grad(np.clip(mw.d * 1.6, 0, 1), '#7af2e4:#2ab8c8:#1a6a9a')
    fingers = [(26.4, -9.5), (25.5, 0.0), (21.0, 7.5), (14.5, 11.5), (8.0, 12.5)]

    def g(mw, f):
        flow = 0.5 + 0.5 * np.cos(2 * math.pi * (mw.d * 4.0 - f / N))
        return glow_img(rgb('#c8fff8'), mw.mem * flow ** 4 * 0.28 + mw.edge * mw.mem * 0.3)
    return membraned(27, 28, -14, fingers=fingers, bow=0.12, seed=8, mem=mem, bone_col=('#d8fff8', '#6ad8d0'),
                     horn_col=('#ffffff', '#8ae8e0'), claw=False, mem_alpha=0.78, translucent=True, glow=g, frames=N,
                     frame_time=90, edge_col='#e8fffc', edge_amt=0.4, flap=dict(amp=15, speed=0.36, kind='wave'))


@design('wings_abyssal_fin', 'Abyssal Fin', 'Ice & Water', 'Deep-sea fins dotted with glowing lights.',
        'Slow, drifting beats; lights pulse in sequence from root to tip')
def abyssal_fin():
    N = 16
    fingers = [(26.4, -9.0), (25.0, 1.0), (19.5, 8.5), (12.0, 12.0)]

    def mem(mw, f):
        return grad(np.clip(mw.d * 1.5, 0, 1), '#1a3a6a:#0e2048:#060e24')

    def g(mw, f):
        cv = mw.cv
        out = cv.zeros()
        Jx, Jy = cv.p(*mw.Wr)
        for i, (fx, fy) in enumerate(mw.fingers):
            Fx, Fy = cv.p(fx, fy)
            for k in range(1, 5):
                t = k / 5
                px, py = Jx + (Fx - Jx) * t, Jy + (Fy - Jy) * t
                ph = 0.5 + 0.5 * math.cos(2 * math.pi * (t * 0.8 - f / N))
                d = np.hypot(cv.X - px - 3, cv.Y - py + 1)
                out = np.maximum(out, np.clip(1 - d / 3.2, 0, 1) ** 1.5 * (0.25 + 0.75 * ph ** 2))
        return glow_img(rgb('#5afff0'), out * mw.mem + mw.edge * mw.mem * 0.18)
    return membraned(27, 28, -14, fingers=fingers, bow=0.14, seed=9, mem=mem, bone_col=('#2a5a8a', '#0e2a4a'),
                     claw=False, glow=g, frames=N, frame_time=100, edge_col='#5ae8ff', edge_amt=0.35,
                     flap=dict(amp=14, speed=0.3, kind='wave'))


@design('wings_frostbite', 'Frostbite', 'Ice & Water', 'Pale ice-blue bat wings with frosted white bones.',
        'Quick, chilly beats; frost shimmers along the edges')
def frostbite():
    N = 12

    def mem(mw, f):
        return grad(np.clip(mw.d * 1.5, 0, 1), '#e2f6ff:#9ad2f4:#5a9ad8')

    def g(mw, f):
        s = sweep(mw.cv, f, N, 0.12)
        return glow_img(rgb('#f4fcff'), mw.mem * s * 0.35 + mw.edge * mw.mem * 0.35)
    return membraned(26, 27, -14, seed=10, mem=mem, bone_col=('#ffffff', '#c8e4f8'), horn_col=('#ffffff', '#a8d8ff'),
                     spikes=4, mem_alpha=0.92, glow=g, frames=N, frame_time=90, edge_col='#ffffff', edge_amt=0.45,
                     flap=dict(amp=17, speed=0.4))


# ================================================================ new: cosmic

@design('wings_nebula_veil', 'Nebula Veil', 'Cosmic', 'Soft wings swirling with pink and blue nebula clouds.',
        'Dreamy, slow beats; the nebula drifts and stars twinkle', rarity='legendary')
def nebula_veil():
    N = 16
    fingers = [(26.4, -8.0), (24.0, 3.0), (16.0, 11.0)]

    def mem(mw, f):
        X, Y = mw.cv.X / mw.cv.w, mw.cv.Y / mw.cv.h
        k = np.clip(X * 0.6 + Y * 0.5, 0, 1)
        return grad(k, '#ff7ad8:#9a4ae8:#3a3ad8:#1a1a5a')

    def g(mw, f):
        X, Y = mw.cv.X / mw.cv.w, mw.cv.Y / mw.cv.h
        cloud = 0.5 + 0.5 * np.cos(2 * math.pi * (X * 1.3 - Y * 0.6 - f / N)) * np.cos(2 * math.pi * (Y * 1.1 + X * 0.3))
        s = twinkle_stars(mw.cv, 80, 3, f, N, mw.mem)
        c = np.clip(rgb('#ffb8f0') * (cloud * 0.6)[..., None] + rgb('#ffffff') * s[..., None], 0, 1)
        return glow_img(c, np.clip(mw.mem * cloud ** 2 * 0.4 + s, 0, 1))
    return membraned(27, 28, -14, fingers=fingers, bow=0.12, seed=11, mem=mem, bone_col=('#f0d8ff', '#9a7ad8'),
                     claw=False, glow=g, frames=N, frame_time=100, edge_col='#ffd8f8', edge_amt=0.4,
                     flap=dict(amp=13, speed=0.3, kind='wave'))


@design('wings_event_horizon', 'Event Horizon', 'Cosmic', 'Pitch-black wings ringed by a blazing band of light.',
        'Slow, heavy beats; the ring of light spins around the dark core', rarity='legendary')
def event_horizon():
    N = 16

    def mem(mw, f):
        return grad(np.clip(mw.d * 1.5, 0, 1), '#14101e:#08060c:#020203')

    def g(mw, f):
        cv = mw.cv
        cx, cy = cv.p(mw.Wr[0] + 4.5, mw.Wr[1] + 12.0)
        dx, dy = (cv.X - cx) / D, (cv.Y - cy) / D
        r = np.hypot(dx, dy * 1.35)
        ang = np.arctan2(dy, dx)
        ring = np.clip(1 - np.abs(r - 6.2) / 1.6, 0, 1) ** 1.2
        spin = 0.45 + 0.55 * (0.5 + 0.5 * np.cos(ang - 2 * math.pi * f / N)) ** 2
        c = grad(np.clip(ring * spin, 0, 1), '#ff4a1a:#ffa040:#fff0c0')
        halo = np.clip(1 - np.abs(r - 6.2) / 3.6, 0, 1) ** 2 * 0.4
        return glow_img(c, np.clip((ring * spin + halo) * mw.mem + mw.edge * mw.mem * 0.3, 0, 1))
    return membraned(27, 28, -14, seed=12, mem=mem, bone_col=('#2a2238', '#0a0810'), horn_col=('#9a7ae8', '#3a2a6a'),
                     claw=False, bow=0.18, glow=g, frames=N, frame_time=80, edge_col='#9a6aff', edge_amt=0.45,
                     flap=dict(amp=13, speed=0.28, down=0.32))


@design('wings_starforged', 'Starforged', 'Cosmic', 'Midnight-blue feathers mapped with glowing constellations.',
        'Calm beats; the constellations twinkle and light up in turn')
def starforged():
    N = 16

    def col(name, m, cw, f):
        if name == 'arm':
            return grad(cw.arm_v, '#2a3a8a:#1a2468')
        return grad(np.clip(m.t * 0.8 + U(cw) * 0.2, 0, 1), '#2a3a8a:#141a52:#0a0c2e')

    def g(name, m, cw, f):
        if name != 'long':
            return None
        cv = cw.cv
        rng = np.random.default_rng(31)
        pts = []
        ys, xs = np.where(m.alpha > 0.9)
        idx = rng.choice(len(xs), 14, replace=False)
        pts = sorted([(xs[i], ys[i]) for i in idx])
        lines = [[pts[i], pts[i + 1]] for i in range(len(pts) - 1) if math.hypot(pts[i][0] - pts[i + 1][0], pts[i][1] - pts[i + 1][1]) < 40]
        ln = lines_mask(Canvas(cv.W, cv.H, cv.top), [[(x / D, y / D) for x, y in l] for l in lines], 0.18) if lines else cv.zeros()
        stars = cv.zeros()
        for k, (x, y) in enumerate(pts):
            b = 0.5 + 0.5 * math.cos(2 * math.pi * (f / N - k / len(pts)))
            stars = np.maximum(stars, np.clip(1 - np.hypot(cv.X - x, cv.Y - y) / 2.2, 0, 1) * (0.5 + 0.5 * b))
        tw = twinkle_stars(cv, 70, 33, f, N, m.alpha) * 0.7
        return glow_img(rgb('#dfe8ff'), np.clip(ln * 0.45 + stars + tw, 0, 1) * m.alpha)
    return clean('angel', color=col, line='#7a8ae8', rim=0.4, glow=g, frames=N, frame_time=100)


@design('wings_comet_tail', 'Comet Tail', 'Cosmic', 'Long streaming plumes of ice-white light.',
        'Sweeping beats; light streams out along each plume')
def comet_tail():
    N = 12

    def col(name, m, cw, f):
        if name == 'arm':
            return grad(cw.arm_v, '#ffffff:#d8f0ff')
        return grad(m.t, '#ffffff:#c8ecff:#6ab0ff')

    def g(name, m, cw, f):
        if m is None:
            return glow_img(rgb('#e8f6ff'), cw.arm * 0.35)
        stream = 0.5 + 0.5 * np.cos(2 * math.pi * (m.t * 2.0 - f / N))
        core = smoothstep(0.55, 0.0, m.across)
        return glow_img(rgb('#e8f6ff'), m.alpha * (0.2 + 0.6 * stream * core) * (0.5 + 0.5 * m.t))
    return clean('plume', reach=1.05, width=1.15, color=col, line='#4a8ad8', glow=g, frames=N, frame_time=80,
                 flap=dict(amp=13, speed=0.36))


@design('wings_supernova', 'Supernova', 'Cosmic', 'A burst of light: white-hot at the root, magenta and violet at the tips.',
        'Strong beats; shockwaves of light ripple outward', rarity='legendary')
def supernova():
    N = 12

    def col(name, m, cw, f):
        X, Y = cw.cv.X / cw.cv.w, cw.cv.Y / cw.cv.h
        r = np.clip(np.hypot(X, (Y - 0.35) * 0.9) * 1.05, 0, 1)
        return grad(r, '#fffaf0:#ffd0f0:#ff4ac8:#8a2ae8:#3a1a9a')

    def g(name, m, cw, f):
        a0 = m.alpha if m is not None else cw.arm
        X, Y = cw.cv.X / cw.cv.w, cw.cv.Y / cw.cv.h
        r = np.hypot(X, (Y - 0.35) * 0.9)
        wave_ = np.clip(1 - np.abs(((r - f / N) % 1.0) - 0.5) / 0.08, 0, 1)
        return glow_img(grad(np.clip(r, 0, 1), '#ffffff:#ff8ae8:#a86aff'), a0 * (wave_ * 0.7 + 0.12))
    return clean('hawk', shape='point', color=col, line='#4a1a7a', glow=g, frames=N, frame_time=70,
                 flap=dict(amp=17, speed=0.46))


# ================================================================ new: dark

@design('wings_voidwalker', 'Voidwalker', 'Dark', 'Black wings rimmed with violet void-light.',
        'Gliding beats; violet light chases around the edges')
def voidwalker():
    N = 14

    def mem(mw, f):
        return grad(np.clip(mw.d * 1.5, 0, 1), '#2a1a42:#140a24:#06030c')

    def g(mw, f):
        cv = mw.cv
        Jx, Jy = cv.p(*mw.Wr)
        ang = np.arctan2(cv.Y - Jy, cv.X - Jx)
        chase = 0.3 + 0.7 * (0.5 + 0.5 * np.cos(ang * 2 - 2 * math.pi * f / N)) ** 2
        inner = blur(mw.edge, 1.5) * mw.mem
        return glow_img(rgb('#b06aff'), np.clip(mw.edge * mw.mem * chase * 1.1 + inner * 0.35 * chase, 0, 1))
    return membraned(27, 28, -14, seed=13, mem=mem, bone_col=('#3a2a52', '#120a1e'), horn_col=('#d8b8ff', '#6a4aa8'),
                     spikes=2, glow=g, frames=N, frame_time=80, edge_col='#8a4aff', edge_amt=0.5, bow=0.26,
                     flap=dict(amp=15, speed=0.34))


@design('wings_bone_reaper', 'Bone Reaper', 'Dark', 'Bare bone wings veiled in drifting green ghost-light.',
        'Slow, eerie beats; ghost-light drifts through the bones')
def bone_reaper():
    N = 16

    def mem(mw, f):
        return grad(mw.d, '#5affb0:#1a8a5a')

    def g(mw, f):
        X, Y = mw.cv.X / mw.cv.w, mw.cv.Y / mw.cv.h
        drift = 0.5 + 0.5 * np.cos(2 * math.pi * (Y * 1.2 - X * 0.5 + f / N))
        return glow_img(rgb('#7affc0'), mw.mem * (0.08 + 0.3 * drift ** 2) + mw.edge * mw.mem * 0.25)
    return membraned(27, 28, -14, seed=14, mem=mem, bone_col=('#f4f0e2', '#b8b09a'), horn_col=('#ffffff', '#c8c0aa'),
                     spikes=3, mem_alpha=0.18, translucent=True, glow=g, frames=N, frame_time=100, edge_amt=0.0,
                     flap=dict(amp=14, speed=0.3, down=0.34))


@design('wings_hellforge', 'Hellforge', 'Dark', 'Forged iron blades with molten seams.',
        'Heavy, armoured beats; the seams glow hotter with every beat')
def hellforge():
    N = 12

    def col(name, m, cw, f):
        if name == 'arm':
            return grad(cw.arm_v, '#5a5452:#2e2a2a')
        return grad(m.t, '#5a5452:#2e2a2a:#181616')

    def g(name, m, cw, f):
        if m is None:
            return glow_img(rgb('#ff6a1a'), cw.arm_edge * cw.arm * 0.7 * pulse(f, N, 0.5, 1.0))
        p = pulse(f, N, 0.45, 1.0)
        core = smoothstep(0.3, 0.05, m.across) * smoothstep(0.15, 0.4, m.t) * (m.t < 0.92) * m.alpha
        return glow_img(grad(m.t, '#ff3a0a:#ff8a2a:#ffd060'), np.clip(core * 0.8 * p + smoothstep(0.86, 1, m.t) * m.alpha * 0.6 * p, 0, 1))
    return clean('hawk', shape='blade', n=(6, 4, 2), width=1.35, color=col, line='#000000', gloss=0.14, glow=g,
                 frames=N, frame_time=80, flap=dict(amp=14, speed=0.34, down=0.32))


@design('wings_tattered_shade', 'Tattered Shade', 'Dark', 'Ragged, torn bat wings in any colour you like.',
        'Uneven, flapping beats', tint=True, rarity='rare', color='#5a5a6a')
def tattered_shade():
    def mem(mw, f):
        return grad(np.clip(mw.d * 1.5, 0, 1), '#f0f0f0:#c0c0c0:#909090')
    return membraned(27, 28, -14, seed=15, mem=mem, bone_col=('#e0e0e0', '#8a8a8a'), horn_col=('#ffffff', '#b0b0b0'),
                     tatter=0.55, tint=True, edge_col='#303030', edge_amt=0.5, flap=dict(amp=18, speed=0.42, down=0.3))


def chain_paint(n=6, w=2.0, link=1.6):
    cv = part_canvas(w, n * link + 0.4)
    img = np.zeros((cv.h, cv.w, 4), np.float32)
    for i in range(n):
        cy = (i + 0.5) * link + 0.2
        cx = w / 2
        X, Y = cv.X / D, cv.Y / D
        if i % 2 == 0:
            d = np.hypot((X - cx) / 0.75, (Y - cy) / 0.95)
            ring = np.clip(1 - np.abs(d - 0.7) / 0.28, 0, 1)
        else:
            ring = np.clip(1 - np.abs(X - cx) / 0.3, 0, 1) * (np.abs(Y - cy) < 0.95)
        col = grad(np.clip((Y - cy) / 2 + 0.5, 0, 1), '#c8c4d8:#5a566a')
        img = over(img, rgba(col, smoothstep(0.1, 0.6, ring)))
    return img


@design('wings_cursed_chains', 'Cursed Chains', 'Dark', 'Dark wings bound with swinging iron chains.',
        'Heavy beats; the chains swing and sway with every move')
def cursed_chains():
    N = 12
    ch = chain_paint()

    def chain(name, attach, pivot, n_phase):
        return Part(name, attach, pivot, (0, 0, 0), (-1.0, 0, 2.0, 10.0), const(ch), z=0.55,
                    anims=[{'type': 'wave', 'rot': [10, 0, 12], 'speed': 0.5, 'speedMove': 1.1, 'phase': n_phase, 'idle': 0.6, 'move': 1.0}])

    def mem(mw, f):
        return grad(np.clip(mw.d * 1.5, 0, 1), '#3a3448:#1e1a28:#0c0a12')

    def g(mw, f):
        return glow_img(rgb('#9a6aff'), mw.edge * mw.mem * pulse(f, N, 0.25, 0.6))
    parts = [chain('chain_a', 'mid', (3.0, 1.0, 0), -0.4), chain('chain_b', 'tip', (4.5, 2.0, 0), -1.2)]
    return membraned(27, 28, -14, seed=16, mem=mem, bone_col=('#5a5468', '#1e1a26'), horn_col=('#c8c4d8', '#5a566a'),
                     spikes=2, glow=g, frames=N, frame_time=100, parts=parts, edge_col='#7a5ac8', edge_amt=0.35,
                     flap=dict(amp=14, speed=0.32, down=0.34))


# ================================================================ new: tech

@design('wings_thruster_rig', 'Thruster Rig', 'Tech', 'White armour panels with blue thrusters at the tips.',
        'Mechanical beats; the thrusters fire and pulse (in game, they trail sparks)')
def thruster_rig():
    N = 8

    def col(name, m, cw, f):
        if name == 'arm':
            return grad(cw.arm_v, '#f4f6fa:#c8ced8')
        stripe = smoothstep(0.62, 0.65, m.t) * (1 - smoothstep(0.7, 0.73, m.t))
        base = grad(m.t, '#f4f6fa:#c8ced8:#9aa2b0')
        return base * (1 - stripe[..., None]) + rgb('#ff8a2a') * stripe[..., None]

    def g(name, m, cw, f):
        if name != 'long':
            return None
        flame = smoothstep(0.84, 1.0, m.t) * smoothstep(0.7, 0.1, m.across)
        p = 0.6 + 0.4 * (0.5 + 0.5 * math.cos(2 * math.pi * f / N * 2))
        return glow_img(rgb('#5ac8ff'), m.alpha * flame * p * 1.2)
    return clean('swept', shape='blade', n=(4, 3, 1), width=1.75, color=col, line='#3a4252', gloss=0.18, glow=g,
                 frames=N, frame_time=60, flap=dict(amp=10, speed=0.3, down=0.45), cov=4)


@design('wings_hardlight', 'Hardlight', 'Tech', 'See-through hologram wings built from glowing hexagons.',
        'Smooth beats; a scan line sweeps across the hexagons', rarity='legendary')
def hardlight():
    N = 16
    fingers = [(26.4, -9.0), (25.0, 1.5), (19.0, 9.0), (11.0, 12.0)]

    def mem(mw, f):
        return grad(mw.d, '#5affff:#1a8aff')

    def g(mw, f):
        cv = mw.cv
        X, Y = cv.X / D, cv.Y / D
        s = 1.6
        q = (X * 2 / 3) / s
        r = (-X / 3 + math.sqrt(3) / 3 * Y) / s
        # distance to the nearest hexagon edge
        x3, z3 = q, r
        y3 = -x3 - z3
        rx, ry, rz = np.round(x3), np.round(y3), np.round(z3)
        dx, dy, dz = np.abs(rx - x3), np.abs(ry - y3), np.abs(rz - z3)
        dist = np.maximum(np.maximum(dx, dy), dz)
        grid = smoothstep(0.38, 0.5, dist)
        scan = np.clip(1 - np.abs((cv.Y / cv.h) - (f / N * 1.3 - 0.15)) / 0.06, 0, 1)
        return glow_img(rgb('#7affff'), np.clip(grid * 0.55 + scan * 0.6 + mw.edge * 0.8, 0, 1) * mw.mem)
    return membraned(27, 28, -14, fingers=fingers, bow=0.06, seed=17, mem=mem, bone_col=('#c8ffff', '#4ac8ff'),
                     claw=False, mem_alpha=0.22, translucent=True, glow=g,
                     bone_glow=lambda mw, f: glow_img(rgb('#8affff'), mw.bone * 0.6), frames=N, frame_time=70,
                     edge_amt=0.0, flap=dict(amp=12, speed=0.32, down=0.45))


@design('wings_circuit_glider', 'Circuit Glider', 'Tech', 'Circuit-board plates with pulses racing along the traces.',
        'Steady beats; data pulses race from root to tip')
def circuit_glider():
    N = 12

    def col(name, m, cw, f):
        if name == 'arm':
            return grad(cw.arm_v, '#1e4a3a:#0e2a22')
        return grad(m.t, '#1e4a3a:#0e2a22')

    def g(name, m, cw, f):
        if name != 'long':
            return None
        trace = smoothstep(0.16, 0.06, m.across) * (m.t > 0.08) * (m.t < 0.9)
        side = smoothstep(0.08, 0.0, np.abs(m.across - 0.55)) * (np.sin(m.t * 30) > 0.3) * (m.t > 0.15) * (m.t < 0.8)
        run = np.clip(1 - np.abs(((m.t - f / N) % 1.0) - 0.5) / 0.1, 0, 1)
        return glow_img(rgb('#4affa8'), m.alpha * np.clip((trace + side * 0.6) * (0.3 + 0.7 * run), 0, 1))
    return clean('swept', shape='blade', n=(6, 4, 2), width=1.3, color=col, line='#4ac88a', rim=0.4, gloss=0.08, glow=g,
                 frames=N, frame_time=70, flap=dict(amp=11, speed=0.34, down=0.45))


def blade_paint(L=15.0, W=2.8, steel=('#e8eef6', '#8a96a8'), edge='#38e8ff'):
    cv = part_canvas(L, W)
    X, Y = cv.X / D, cv.Y / D
    t = X / L
    half = (W / 2) * np.clip((1 - t) / 0.3, 0, 1) * np.clip(t * 8 + 0.4, 0, 1)
    off = Y - W / 2
    a = np.clip((half - np.abs(off)) * D + 0.5, 0, 1)
    col = grad(np.clip((off / (W / 2) + 1) / 2, 0, 1), list(steel))
    col = col * (1 + 0.18 * np.sign(off)[..., None])
    e = np.clip(1 - (half - np.abs(off)) * D / 1.5, 0, 1)
    col = col * (1 - e[..., None] * 0.4)
    img = rgba(np.clip(col, 0, 1), a)
    gl = glow_img(rgb(edge), a * smoothstep(0.5, 1.0, e) * 0.9)
    return img, gl


@design('wings_blade_array', 'Blade Array', 'Tech', 'Seven floating steel blades per side, fanned like wings.',
        'The blades fan open and closed in a ripple', rarity='legendary')
def blade_array():
    N = 12
    img, gl = blade_paint(19.0, 3.2)
    cvm = part_canvas(5, 5)
    X, Y = cvm.X / D, cvm.Y / D
    mount = rgba(grad(Y / 5, '#c8d0dc:#6a7484'), np.clip((2.2 - np.hypot(X - 2.5, Y - 2.5)) * D, 0, 1))

    def gpulse(f):
        g2 = gl.copy()
        g2[..., 3] *= pulse(f, N, 0.5, 1.0)
        return g2
    gp = cached(gpulse, True)
    parts = []
    for i in range(7):
        ang = -38 + i * 15
        L = 19 - abs(i - 2.5) * 1.1
        parts.append(Part(f'blade{i}', 'root', (1.5, 0.5, 0.3 + i * 0.05), (0, 4 + i * 2, ang), (0.5, -1.6, L, 3.2),
                          const(img), glow=gp, z=0,
                          anims=[{'type': 'flap', 'rot': [0, 6, 7], 'speed': 0.4, 'speedMove': 1.0, 'phase': -i * 0.45,
                                  'idle': 0.5, 'move': 1.0, 'down': 0.4}]))
    rig = Rig([Layer(0.0, const(mount))], (5, 5), -2.5, cuts=[], parts=parts, pose=dict(spread=18, lift=0),
              flap=dict(amp=8, lift=3, speed=0.4, speedMove=1.0))
    return WingModel([rig], frames=N, frame_time=80), None


@design('wings_glitch', 'Glitch', 'Tech', 'Chunky pixel wings that glitch in magenta and cyan.',
        'Snappy beats; the wings glitch and split into colour every few seconds')
def glitch():
    N = 16

    def col(name, m, cw, f):
        if name == 'arm':
            return grad(cw.arm_v, '#6a5aa8:#3a3070')
        return grad(m.t, '#5a4a9a:#2e2860:#1a1640')

    def g(name, m, cw, f):
        a0 = m.alpha if m is not None else cw.arm
        k = f % N
        burst = k in (3, 4, 11)
        cv = cw.cv
        mag = shift(a0, 4, 0) * (1 - a0) * (1.0 if burst else 0.0)
        cyn = shift(a0, -4, 0) * (1 - a0) * (1.0 if burst else 0.0)
        c = rgb('#ff2ad8')[None, None, :] * mag[..., None] + rgb('#2affff')[None, None, :] * cyn[..., None]
        a = np.clip(mag + cyn, 0, 1) * 0.9
        rim = smoothstep(0.3, 1, m.edge) * m.alpha if m is not None else cw.arm_edge
        c = c + rgb('#9a6aff') * rim[..., None] * 0.6
        a = np.clip(a + rim * 0.6, 0, 1)
        if burst:
            rng = np.random.default_rng(k)
            for _ in range(3):
                y0 = int(rng.integers(0, cv.h - 6))
                band = (cv.Y > y0) & (cv.Y < y0 + int(rng.integers(3, 8)))
                a = np.where(band, np.clip(a0 * 0.9, 0, 1), a)
                c = np.where(band[..., None], rgb('#2affff') if rng.random() < 0.5 else rgb('#ff2ad8'), c)
        return glow_img(np.clip(c, 0, 1), a)
    model, cw = clean('hawk', color=col, line='#2affff', rim=0.6, glow=g, frames=N, frame_time=90,
                      flap=dict(amp=15, speed=0.46, down=0.3))
    return pixelate(model), cw


def pixelate(model, block=D):
    """Makes every picture of a model chunky (one colour per model pixel)."""
    for rig in model.rigs:
        for L in rig.layers:
            L.paint = _px(L.paint, block)
            if L.glow:
                L.glow = _px(L.glow, block)
    return model


def _px(fn, b):
    def p(f):
        img = np.asarray(fn(f), np.float32)
        h, w = img.shape[:2]
        H2, W2 = (h + b - 1) // b, (w + b - 1) // b
        pad = np.zeros((H2 * b, W2 * b, 4), np.float32)
        pad[:h, :w] = img
        blk = pad.reshape(H2, b, W2, b, 4)
        a = blk[..., 3].mean((1, 3))
        c = (blk[..., :3] * blk[..., 3:4]).sum((1, 3)) / np.maximum(blk[..., 3].sum((1, 3)), 1e-6)[..., None]
        out = np.concatenate([c, (a > 0.45).astype(np.float32)[..., None] * np.clip(a * 1.6, 0, 1)[..., None]], -1)
        out = np.repeat(np.repeat(out, b, 0), b, 1)[:h, :w]
        return out
    return p


# ================================================================ new: nature

@design('wings_sunset_monarch', 'Sunset Monarch', 'Nature', 'Swallowtail wings painted in a pink-to-orange sunset.',
        'Lazy, fluttering beats', rarity='rare')
def sunset_monarch():
    shapes = [('fore', 0.5, -1.0, 15, 13, False), ('tail', 0.5, 0.0, 11, 10, False)]

    def fills(cv, k):
        X, Y = cv.X / cv.w, cv.Y / cv.h
        return grad(np.clip(np.hypot(X, Y - 0.5) * 1.3, 0, 1), '#ffe08a:#ff9a4a:#ff5a8a:#8a3ab8')

    def paint(cv, masks, f):
        return clean_insect(cv, masks, fills, border='#1a1020', border_w=1.5, n_dots=8, vein='#2a1430', vein_amt=0.55,
                            root=(0.6, -0.3), tips=[(14, -12), (15, -7), (12, -2), (8, 8), (6, 12)])
    return insect(16, 28, -14, shapes, paint, flap=dict(amp=26, speed=0.6, speedMove=1.4))


@design('wings_petal_drift', 'Petal Drift', 'Nature', 'Wings of soft layered petals in any colour you like.',
        'Gentle beats (in game, petals drift off behind you)', tint=True, rarity='rare', color='#ff9ac8')
def petal_drift():
    def col(name, m, cw, f):
        if name == 'arm':
            return grad(cw.arm_v, '#ffffff:#f0f0f0')
        return grad(m.t, '#e8e8e8:#ffffff:#f8f8f8')
    return clean('angel', shape='petal', width=1.6, color=col, line='#9a9a9a', rim=0.4, gloss=0.06, tint=True,
                 flap=dict(amp=14, speed=0.36))


@design('wings_autumn_leaf', 'Autumn Leaf', 'Nature', 'Wings of red, orange and gold autumn leaves.',
        'Breezy beats; the leaves rustle')
def autumn_leaf():
    pal = [rgb(c) for c in ('#d8401a', '#f07a1a', '#f4b42a', '#b8261a', '#e8901a', '#c8a020')]

    def col(name, m, cw, f):
        if name == 'arm':
            return grad(cw.arm_v, '#7a4a2a:#5a341c')
        k = (m.id.astype(int) * 7 + 3) % len(pal)
        base = np.stack(pal)[k]
        base = base * (0.85 + 0.25 * (1 - m.t))[..., None]
        rib = smoothstep(0.1, 0.0, m.across) * (m.t < 0.92)
        veins = smoothstep(0.08, 0.0, np.abs(((m.across * 2.2 - m.t * 3.5) % 1.0) - 0.5) - 0.42) * (m.across < 0.8) * (m.t > 0.15)
        return base * (1 - (rib * 0.35 + veins * 0.15)[..., None])
    return clean('hawk', shape='leaf', width=1.5, color=col, line='#5a2a0a', rim=0.4, gloss=0.05,
                 flap=dict(amp=15, speed=0.4, kind='wave'))


@design('wings_dragonfly', 'Dragonfly', 'Nature', 'Four slim, glassy wings in any colour you like.',
        'A very fast buzzing flutter', tint=True, rarity='rare', color='#5ad8ff')
def dragonfly():
    shapes = [('fore', 0.5, -0.8, 22, 12, True), ('hind', 0.5, 0.6, 20, 13, True)]

    def paint(cv, masks, f):
        def fills(cv, k):
            X = cv.X / cv.w
            return grad(X, '#ffffff:#e0e0e0')
        img = clean_insect(cv, masks, fills, border='#505050', border_w=0.9, n_dots=0, alpha=0.82, vein='#3a3a3a',
                           vein_amt=0.6, root=(0.6, 0.0), tips=[(21, -7.5), (20, -4.0), (18, 3.5), (19, 6.5)])
        return img
    return insect(23, 21, -10.5, shapes, paint, translucent=True, tint=True, pose=dict(spread=6, lift=2),
                  flap=dict(amp=10, lift=8, speed=3.0, speedMove=4.0, idle=0.6))


@design('wings_overgrowth', 'Overgrowth', 'Nature', 'A wing of fresh green leaves on a mossy branch, with tiny flowers.',
        'Soft, swaying beats')
def overgrowth():
    def col(name, m, cw, f):
        if name == 'arm':
            v = cw.arm_v
            bark = grad(v, '#8a5a34:#5a3a20')
            moss = smoothstep(0.35, 0.65, vnoise(cw.cv.w, cw.cv.h, 6, 4)) * (v > 0.35)
            c = bark * (1 - moss[..., None]) + rgb('#5aa83a') * moss[..., None]
            dots = (vnoise(cw.cv.w, cw.cv.h, 3, 9) > 0.86) * (v < 0.7)
            return np.where(dots[..., None], rgb('#ffe0f0'), c)
        k = np.clip(m.t * 0.7 + (m.rnd - 0.5) * 0.3, 0, 1)
        base = grad(k, '#8ae85a:#4ab83a:#2a7a2a')
        rib = smoothstep(0.1, 0.0, m.across) * (m.t < 0.92)
        return base * (1 - rib[..., None] * 0.3)
    return clean('round', shape='leaf', width=1.6, color=col, line='#1a4a1a', rim=0.4, gloss=0.06, arm=3.2,
                 flap=dict(amp=13, speed=0.34, kind='wave'))


# ================================================================ new: energy

@design('wings_stormcaller', 'Stormcaller', 'Energy', 'Storm-cloud wings crackling with lightning.',
        'Strong beats; lightning forks across the wing every few seconds', rarity='legendary')
def stormcaller():
    N = 16

    def mem(mw, f):
        return grad(np.clip(mw.d * 1.4, 0, 1), '#5a6a8a:#2e3a54:#141a2a')

    def bolt(cv, rng, start, n=7):
        x, y = start
        pts = [(x, y)]
        ang = rng.uniform(0.6, 1.4)
        for _ in range(n):
            ang += rng.uniform(-0.6, 0.6)
            step = rng.uniform(1.6, 2.6)
            x, y = x + math.cos(ang) * step, y + math.sin(ang) * step
            pts.append((x, y))
        return pts

    def g(mw, f):
        cv = mw.cv
        out = mw.edge * mw.mem * 0.25
        k = f % N
        if k in (2, 3, 9, 10, 11):
            rng = np.random.default_rng(k // 2 + 7)
            lines = []
            for _ in range(2):
                sx = rng.uniform(mw.E[0], mw.Wr[0] + 4)
                sy = rng.uniform(mw.Wr[1] + 2, mw.Wr[1] + 6)
                b = bolt(cv, rng, (sx, sy))
                lines.append(b)
                br = b[len(b) // 2:]
                lines.append(bolt(cv, rng, br[0], 3))
            core = lines_mask(cv, lines, 0.35)
            halo = blur(core, 2.0) * 2.0
            flash = 1.0 if k in (2, 9) else 0.6
            out = np.clip(out + (core + halo * 0.5) * flash, 0, 1) * mw.mem
        return glow_img(rgb('#cfe8ff'), out)
    return membraned(27, 28, -14, seed=18, mem=mem, bone_col=('#7a8aa8', '#2a3448'), horn_col=('#e8f4ff', '#8aa8d8'),
                     claw=False, bow=0.2, glow=g, frames=N, frame_time=70, edge_col='#a8c8ff', edge_amt=0.35,
                     flap=dict(amp=16, speed=0.38))


@design('wings_prism', 'Prism', 'Energy', 'Clear crystal feathers that split light into a moving rainbow.',
        'Crisp beats; a rainbow slides through the crystal', rarity='legendary')
def prism():
    N = 16

    def col(name, m, cw, f):
        if name == 'arm':
            return grad(cw.arm_v, '#ffffff:#eef4ff')
        return grad(m.t, '#ffffff:#e8f0ff:#d0dcf4')

    def g(name, m, cw, f):
        a0 = m.alpha if m is not None else cw.arm
        X, Y = cw.cv.X / cw.cv.w, cw.cv.Y / cw.cv.h
        hue = X * 0.9 + Y * 0.4 - f / N
        k = (0.55 + 0.45 * m.side) if m is not None else 0.6
        return glow_img(hsv2rgb(hue, 0.75, 1.0), a0 * (0.35 * k + (rim_glow(m, 0.3) if m is not None else 0)))
    return clean('swept', shape='point', color=col, line='#8a9ac8', crease=0.24, gloss=0.05, glow=g, frames=N,
                 frame_time=80, alpha=0.92)


def ribbon_parts(n_ribbons=3, seg=5, L=4.8, W=3.6, palette=('#ff6ad8', '#6a8aff', '#3affc8')):
    parts = []
    for r in range(n_ribbons):
        base_ang = 8 + r * 26
        cols = palette[r % len(palette)]
        prev = None
        chain_root = None
        for k in range(seg):
            w = W * (1 - 0.5 * k / seg)
            cv = part_canvas(L, W)
            X, Y = cv.X / D, cv.Y / D
            wk = w * (1 - 0.5 * (X / L) / seg)
            a = np.clip((wk / 2 - np.abs(Y - W / 2)) * D + 0.5, 0, 1)
            t = (k + X / L) / seg
            col = grad(np.clip(t * 1.4, 0, 1), '#ffffff:' + cols)
            e = np.clip(1 - (wk / 2 - np.abs(Y - W / 2)) * D / 1.4, 0, 1)
            col = col * (1 - e[..., None] * 0.25)
            img = rgba(np.clip(col, 0, 1), a)
            gl = glow_img(rgb(cols), a * (0.3 + 0.4 * t) * (0.6 + 0.4 * e))
            p = Part(f'rib{r}_{k}', 'root', (0.8, 0.5 + r * 1.2, 0.4 + r * 0.1) if k == 0 else (L, 0, 0),
                     (0, 8, base_ang) if k == 0 else (0, 4, 4), (0, -W / 2, L + 0.3, W), const(img), glow=const(gl),
                     anims=[{'type': 'wave', 'rot': [0, 9, 9], 'speed': 0.45, 'speedMove': 1.0, 'phase': -k * 0.9 - r * 0.6,
                             'idle': 0.7, 'move': 1.0}])
            if prev is None:
                chain_root = p
            else:
                prev.children.append(p)
            prev = p
        parts.append(chain_root)
    return parts


@design('wings_aether_ribbon', 'Aether Ribbon', 'Energy', 'Three flowing ribbons of pastel light on each side.',
        'The ribbons ripple and flow like silk in the wind', rarity='legendary')
def aether_ribbon():
    cvm = part_canvas(4, 4)
    X, Y = cvm.X / D, cvm.Y / D
    clasp = rgba(grad(Y / 4, '#ffffff:#c8d0ff'), np.clip((1.7 - np.hypot(X - 2, Y - 2)) * D, 0, 1))
    rig = Rig([Layer(0.0, const(clasp), const(glow_img(rgb('#ffffff'), clasp[..., 3] * 0.5)))], (4, 4), -2, cuts=[],
              parts=ribbon_parts(), pose=dict(spread=14, lift=0), flap=dict(amp=6, lift=3, speed=0.4, speedMove=0.9, kind='wave'))
    return WingModel([rig], frames=1), None


@design('wings_plasma_coil', 'Plasma Coil', 'Energy', 'Wings drawn in glowing plasma tubes of magenta and cyan.',
        'Smooth beats; energy pulses race along the tubes')
def plasma_coil():
    N = 14

    def mem(mw, f):
        return grad(mw.d, '#3a1a5a:#140a2a')

    def g(mw, f):
        cv = mw.cv
        tube = np.clip(mw.bone * 1.3 + smoothstep(0.3, 0.9, mw.edge) * mw.mem, 0, 1)
        run = 0.35 + 0.65 * (0.5 + 0.5 * np.cos(2 * math.pi * (mw.d * 3.5 - f / N))) ** 2
        c = grad(np.clip(mw.d * 1.6, 0, 1), '#ff4ae8:#9a6aff:#4ae8ff')
        return glow_img(c, np.clip(tube * run * 1.2 + blur(tube, 1.5) * 0.35 * run, 0, 1))
    return membraned(27, 28, -14, seed=19, mem=mem, bone_col=('#7a2a9a', '#3a1a5a'), horn_col=('#ff8af0', '#8a3ad8'),
                     claw=False, bow=0.16, mem_alpha=0.55, translucent=True, glow=g, frames=N, frame_time=70,
                     edge_amt=0.0, flap=dict(amp=13, speed=0.36))


@design('wings_wisp', 'Wisp', 'Energy', 'Ghostly, see-through feathers of pale teal light.',
        'Floaty, weightless beats; the light breathes softly', rarity='rare')
def wisp():
    N = 16

    def col(name, m, cw, f):
        if name == 'arm':
            return grad(cw.arm_v, '#e8fff8:#a8f0e0')
        return grad(m.t, '#e8fff8:#8ae8d0:#4ac8b8')

    def g(name, m, cw, f):
        a0 = m.alpha if m is not None else cw.arm
        b = 0.5 + 0.5 * np.cos(2 * math.pi * (U(cw) * 0.8 - f / N))
        return glow_img(rgb('#c8fff0'), a0 * (0.15 + 0.35 * b))
    return clean('swept', shape='point', color=col, line='#4ab8a8', rim=0.35, alpha=0.6, glow=g, frames=N,
                 frame_time=90, translucent=True, flap=dict(amp=14, speed=0.3, kind='wave'))


# ================================================================ new: fun

@design('wings_pixel', 'Pixel Wings', 'Fun', 'Chunky 8-bit wings in any colour you like.',
        'Snappy, retro beats', tint=True, rarity='rare', color='#4aa8ff')
def pixel_wings():
    def col(name, m, cw, f):
        if name == 'arm':
            return grad(cw.arm_v, '#ffffff:#e8e8e8')
        return grad(m.t, '#ffffff:#d8d8d8:#b0b0b0')
    model, cw = clean('hawk', n=(5, 4, 1), width=1.4, color=col, line='#202020', rim=0.8, gloss=0.0, crease=0.0,
                      tint=True, flap=dict(amp=15, speed=0.5, down=0.5))
    return pixelate(model), cw


@design('wings_candy_swirl', 'Candy Swirl', 'Fun', 'Sweet pink-and-white candy-striped wings.',
        'Bouncy beats; the stripes swirl slowly')
def candy_swirl():
    N = 12

    def mem(mw, f):
        X, Y = mw.cv.X / D, mw.cv.Y / D
        s = 0.5 + 0.5 * np.sin((X + Y) * 0.9 - 2 * math.pi * f / N)
        stripe = smoothstep(0.45, 0.55, s)
        return rgb('#ff6aa8') * stripe[..., None] + rgb('#fff4f8') * (1 - stripe)[..., None]
    return membraned(26, 26, -13, seed=20, mem=mem, bone_col=('#ffffff', '#ffd0e4'), horn_col=('#ffffff', '#ffb8d8'),
                     claw=False, bow=0.3, animated=True, frames=N, frame_time=110, edge_col='#c83a78', edge_amt=0.5,
                     flap=dict(amp=17, speed=0.5, kind='wave'))


@design('wings_paper_crane', 'Paper Crane', 'Fun', 'Folded origami wings in any colour of paper you like.',
        'Crisp, papery beats', tint=True, rarity='rare', color='#ff6a5a')
def paper_crane():
    def col(name, m, cw, f):
        if name == 'arm':
            return grad(cw.arm_v, '#ffffff:#f0f0f0')
        return grad(m.t, '#ffffff:#f2f2f2')
    return clean('swept', shape='blade', n=(3, 3, 1), width=2.6, color=col, line='#8a8a8a', rim=0.35, crease=0.32,
                 gloss=0.0, cov=3, tint=True, flap=dict(amp=12, speed=0.4, down=0.5))


@design('wings_bubble', 'Bubble', 'Fun', 'Soap-bubble wings with a swirling rainbow film.',
        'Wobbly, floaty beats; colours swirl across the film')
def bubble():
    N = 16
    shapes = [('lobe', 0.5, -0.5, 14, 15, False), ('lobe', 0.5, 0.8, 10, -11, False)]

    def paint(cv, masks, f):
        img = np.zeros((cv.h, cv.w, 4), np.float32)
        for k, m in enumerate(masks):
            X, Y = cv.X / cv.w, cv.Y / cv.h
            hue = X * 0.8 + Y * 0.6 + np.sin(X * 6 + Y * 4) * 0.15 + f / N
            col = hsv2rgb(hue, 0.5, 1.0)
            e = edge_of(m, 1.4)
            col = col * (1 - e[..., None] * 0.2) + rgb('#ffffff') * (e[..., None] * 0.6)
            img = over(img, rgba(col, m * (0.45 + 0.5 * e)))
        hi = np.clip(1 - np.hypot(cv.X - cv.p(4, -8)[0], cv.Y - cv.p(4, -8)[1]) / 6, 0, 1)
        img = over(img, rgba(bc('#ffffff', (cv.h, cv.w)), hi * masks[0] * 0.8))
        return img
    return insect(15, 30, -16, shapes, paint, animated=True, frames=N, frame_time=90, translucent=True,
                  flap=dict(amp=18, speed=0.6, speedMove=1.2))


@design('wings_chibi_bat', 'Chibi Bat', 'Fun', 'Tiny, round bat wings in any colour you like.',
        'Fast, flappy little beats', tint=True, rarity='rare', color='#8a5ad8')
def chibi_bat():
    fingers = [(13.4, -4.0), (11.5, 2.5), (6.5, 5.5)]

    def mem(mw, f):
        return grad(np.clip(mw.d * 1.5, 0, 1), '#ffffff:#e0e0e0:#c4c4c4')
    return membraned(14, 13, -7, fingers=fingers, elbow=(5, -3.5), wrist=(8.5, -5.5), root_bottom=(0.3, 4.5), bow=0.3,
                     seed=21, mem=mem, bone_col=('#f0f0f0', '#9a9a9a'), claw=True, tint=True, edge_col='#404040',
                     edge_amt=0.6, pose=dict(spread=10, lift=14), flap=dict(amp=22, speed=1.2, speedMove=2.2, kind='wave'))


# ================================================================ new: mythic showpieces

@design('wings_archangel', 'Archangel', 'Mythic', 'Three pairs of radiant white-and-gold wings.',
        'Majestic, staggered beats: each pair follows the one above it', rarity='mythic')
def archangel():
    N = 14
    gold = rgb('#ffd98a')

    def g(name, m, cw, f):
        if m is None:
            return glow_img(gold, cw.arm_edge * cw.arm * 0.5)
        s = sweep(cw.cv, f, N, 0.1)
        return glow_img(gold, rim_glow(m, 0.5) + m.alpha * (s * 0.45 + smoothstep(0.86, 1, m.t) * 0.3))
    cols = ('#ffffff:#efe6d0', '#ffffff:#f6efdc', '#fffdf6:#f3ead2')
    r1, _ = clean('angel', cols=cols, line='#b8913f', glow=g, frames=N, rig_only=True, scale=0.9,
                  flap=dict(amp=14, speed=0.34))
    r2, _ = clean('angel', cols=cols, line='#b8913f', glow=g, frames=N, rig_only=True, scale=0.68, prefix='b_',
                  pose=dict(spread=22, lift=-12), flap=dict(amp=14, speed=0.34, phase0=-0.9))
    r3, _ = clean('angel', cols=cols, line='#b8913f', glow=g, frames=N, rig_only=True, scale=0.5, prefix='c_',
                  pose=dict(spread=30, lift=-34), flap=dict(amp=14, speed=0.34, phase0=-1.8))
    r2.pivot, r3.pivot = (1.4, 6.0, 2.4), (1.3, 9.0, 2.5)
    return WingModel([r1, r2, r3], frames=N, frame_time=90), None


@design('wings_elder_dragon', 'Elder Dragon', 'Mythic', 'Huge crimson-and-bronze dragon wings, horned and armoured.',
        'Slow, thunderous beats', rarity='mythic')
def elder_dragon():
    N = 12

    def mem(mw, f):
        return grad(np.clip(mw.d * 1.4, 0, 1), '#c8402a:#8a1e14:#4a0a08')

    def g(mw, f):
        near = blur(mw.bone, 2.5) * mw.mem
        return glow_img(rgb('#ffb04a'), near * pulse(f, N, 0.1, 0.35) + mw.edge * mw.mem * 0.2)
    return membraned(32, 33, -17, seed=22, mem=mem, bone_col=('#d8a860', '#6a4220'), horn_col=('#fff0d0', '#a88a5a'),
                     spikes=6, glow=g, frames=N, frame_time=120, scale=0.92, edge_col='#ffb04a', edge_amt=0.35,
                     flap=dict(amp=18, speed=0.26, speedMove=0.7, down=0.3))


def gear_paint(r=3.2, teeth=10, col=('#ffe58a', '#b8862a')):
    cv = part_canvas(2 * r, 2 * r)
    X, Y = cv.X / D - r, cv.Y / D - r
    rr = np.hypot(X, Y)
    ang = np.arctan2(Y, X)
    tooth = smoothstep(-0.2, 0.2, np.cos(ang * teeth)) * 0.55
    outer = r - 0.6 + tooth
    a = np.clip((outer - rr) * D, 0, 1) * np.clip((rr - r * 0.32) * D, 0, 1)
    spokes = (np.abs(np.sin(ang * 3)) < 0.22) | (rr > r * 0.62)
    a = a * spokes
    c = grad(np.clip((Y / r + 1) / 2, 0, 1), list(col))
    e = np.clip(1 - (outer - rr) * D / 1.2, 0, 1)
    c = c * (1 - e[..., None] * 0.35)
    img = rgba(np.clip(c, 0, 1), a)
    return img


@design('wings_celestial_clockwork', 'Celestial Clockwork', 'Mythic', 'A brass frame of turning gears holding a starry sky.',
        'Measured beats; the gears turn and the stars twinkle', rarity='mythic')
def celestial_clockwork():
    N = 16
    big, small = gear_paint(3.4, 12), gear_paint(2.2, 8, ('#fff0b0', '#c8962a'))
    gl_big = glow_img(rgb('#ffe9a0'), big[..., 3] * 0.25)

    def gear(name, attach, pivot, img, r, speed):
        return Part(name, attach, pivot, (0, 0, 0), (-r, -r, 2 * r, 2 * r), const(img), glow=const(gl_big if img is big else glow_img(rgb('#ffe9a0'), img[..., 3] * 0.25)),
                    z=0.6, anims=[{'type': 'spin', 'rot': [0, 0, speed]}])

    def mem(mw, f):
        return grad(np.clip(mw.d * 1.5, 0, 1), '#1e2a6a:#121a48:#080c24')

    def g(mw, f):
        s = twinkle_stars(mw.cv, 90, 41, f, N, mw.mem)
        return glow_img(rgb('#fff6d8'), s + mw.edge * mw.mem * 0.3)
    parts = [gear('gear_a', 'root', (5.0, 3.0, 0), big, 3.4, 40), gear('gear_b', 'mid', (3.5, 2.0, 0), small, 2.2, -65),
             gear('gear_c', 'tip', (3.0, 4.0, 0), small, 2.2, 55)]
    return membraned(27, 28, -14, seed=23, mem=mem, bone_col=('#ffe58a', '#a8762a'), horn_col=('#fff6c8', '#c8962a'),
                     spikes=3, claw=False, bow=0.16, glow=g,
                     bone_glow=lambda mw, f: glow_img(rgb('#ffe9a0'), mw.bone * 0.25), frames=N, frame_time=100,
                     parts=parts, edge_col='#ffe58a', edge_amt=0.55, flap=dict(amp=12, speed=0.3, down=0.42))


@design('wings_chromatic_seraph', 'Chromatic Seraph', 'Mythic', 'Long plumes that cycle through every colour of the rainbow.',
        'Graceful beats; the rainbow flows from root to tip', rarity='mythic')
def chromatic_seraph():
    N = 20

    def col(name, m, cw, f):
        if name == 'arm':
            return hsv2rgb(U(cw) * 0.3 - f / N, 0.25, 1.0)
        return hsv2rgb(U(cw) * 0.5 + m.t * 0.35 - f / N, 0.55, 1.0)

    def g(name, m, cw, f):
        if m is None:
            return None
        return glow_img(hsv2rgb(U(cw) * 0.5 + m.t * 0.35 - f / N, 0.6, 1.0), m.alpha * smoothstep(0.75, 1, m.t) * 0.55 + rim_glow(m, 0.3))
    return clean('plume', color=col, line='#5a5a8a', rim=0.4, glow=g, animated=True, frames=N, frame_time=80,
                 flap=dict(amp=13, speed=0.34))


@design('wings_polar_guardian', 'Polar Guardian', 'Mythic', "Polar's own: ice-white feathers with a cold blue glow and drifting snowflakes.",
        'Calm, powerful beats; a blue glow breathes and snow sparkles', rarity='mythic')
def polar_guardian():
    N = 16

    def col(name, m, cw, f):
        if name == 'arm':
            return grad(cw.arm_v, '#ffffff:#dcefff')
        return grad(m.t, '#ffffff:#e4f2ff:#9ad0ff')

    def g(name, m, cw, f):
        a0 = m.alpha if m is not None else cw.arm
        p = pulse(f, N, 0.45, 1.0)
        tw = twinkle_stars(cw.cv, 40, 51 + len(name), f, N, a0)
        tip = smoothstep(0.84, 1, m.t) * m.alpha * 0.55 if m is not None else 0
        rim = rim_glow(m, 0.5) if m is not None else cw.arm_edge * cw.arm * 0.4
        return glow_img(rgb('#8ad4ff'), np.clip((rim + tip) * p + tw, 0, 1))
    return clean('angel', size=1.08, color=col, line='#3a7ac8', glow=g, frames=N, frame_time=90,
                 flap=dict(amp=15, speed=0.34))
