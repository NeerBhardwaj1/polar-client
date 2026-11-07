"""Builders shared by the wing designs (wingdesigns.py): feathered, membrane, insect, shard and part-based wings."""
import math

import numpy as np

from lib import rgb, rgba, gradient, smoothstep, blur, fbm, vnoise
from wingkit import WingModel, Rig, Layer, Part, DENSITY as D
from wingpaint import (Canvas, FeatherWing, MembraneWing, CleanWing, clean_shade, clean_arm, membrane_layers, shade, grad, glow_img, polys_mask, lines_mask,
                       chaikin, insect_shape, shard_poly, facet_maps, edge_of, over, _along)


def cached(fn, animated):
    memo = {}

    def get(f):
        k = f if animated else 0
        if k not in memo:
            memo[k] = fn(k)
        return memo[k]
    return get


# ---------------------------------------------------------------- feathered

def feathered(style='angel', W=26, H=29, top=-15, shape='round', n=(9, 8, 4), seed=1, width=1.0, reach=1.0, droop=0.0,
              cols=('#ffffff:#dddddd', '#ffffff:#eeeeee', '#ffffff:#f6f6f6'), line='#556070', band=None, color=None,
              glow=None, frames=1, frame_time=100, animated=False, gloss=0.0, alpha=1.0, edge_dark=0.38, barbs=0.03,
              shaft=None, shaft_amt=0.35, flap=None, pose=None, scale=0.85, tint=False, translucent=False, parts=(),
              extra_rigs=(), cov=1.0, rig_only=False, prefix=''):
    fw = FeatherWing(W, H, top, style, shape, n, scale_cov=cov, seed=seed, width=width, droop=droop, reach=reach)
    names = ('long', 'mid', 'small')
    maps = (fw.long, fw.mid, fw.small)
    zs = (0.0, 0.35, 0.7)
    layers = []
    band_col = band or (cols[1].split(':')[0] if isinstance(cols[1], str) else '#ffffff')
    for i, (name, m) in enumerate(zip(names, maps)):
        def make(i=i, name=name, m=m):
            def paint(f):
                c = color(name, m, fw, f) if color else grad(m.t, cols[i])
                img = shade(m, c, line, edge_dark=edge_dark, barbs=barbs if name != 'small' else 0, gloss=gloss, alpha=alpha,
                            shaft_col=shaft, shaft_amt=shaft_amt)
                if i == 0:
                    bc = color('band', fw.small, fw, f) if (color and band is None) else rgb(band_col)
                    bc = np.asarray(bc, np.float32)
                    if bc.ndim == 1:
                        bc = np.broadcast_to(bc, img.shape[:2] + (3,)).copy()
                    img = over(rgba(bc * 0.92, fw.band * alpha), img)
                return img
            g = None
            if glow is not None and glow(name, m, fw, 0) is not None:
                g = cached(lambda f: glow(name, m, fw, f), frames > 1)
            return Layer(zs[i], cached(paint, animated and frames > 1), g)
        layers.append(make())
    rig = Rig(layers, (W, H), top, cuts=fw.cuts, flap=flap, pose=pose, scale=scale, tint=tint, parts=parts, prefix=prefix)
    if rig_only:
        return rig, fw
    return WingModel([rig] + list(extra_rigs), frames=frames, frame_time=frame_time, translucent=translucent), fw


# ---------------------------------------------------------------- membrane

def membraned(W=27, H=29, top=-15, fingers=None, bow=0.22, seed=1, spikes=0, tatter=0.0, claw=True, elbow=None, wrist=None,
              root_bottom=None, mem=None, bone_col=('#3a3236', '#141012'), horn_col=('#e8dcc4', '#8a7a62'), veins=None,
              vein_amt=0.45, mem_alpha=0.97, glow=None, bone_glow=None, frames=1, frame_time=100, animated=False, flap=None,
              pose=None, scale=0.85, tint=False, translucent=False, parts=(), bones=True, post=None, folds=0.0,
              edge_col=None, edge_amt=0.5):
    mw = MembraneWing(W, H, top, fingers=fingers, bow=bow, seed=seed, elbow=elbow, wrist=wrist, root_bottom=root_bottom,
                      spikes=spikes, tatter=tatter, claw=claw)

    def layers_for(f):
        col = mem(mw, f) if mem else grad(np.clip(mw.d * 1.5, 0, 1), '#5c0a12:#1a0309')
        m, b = membrane_layers(mw, col, bone_col=bone_col, mem_alpha=mem_alpha, veins=veins, vein_amt=vein_amt,
                               horn_col=horn_col, seed=seed, folds_amt=folds, edge_col=edge_col, edge_amt=edge_amt)
        if post:
            m, b = post(mw, m, b, f)
        return m, b
    get = cached(layers_for, animated and frames > 1)
    g = cached(lambda f: glow(mw, f), frames > 1) if glow else None
    bg = cached(lambda f: bone_glow(mw, f), frames > 1) if bone_glow else None
    layers = [Layer(0.0, lambda f: get(f)[0], g)]
    if bones:
        layers += [Layer(0.3, lambda f: get(f)[1], bg), Layer(-0.3, lambda f: get(f)[1], bg)]
    rig = Rig(layers, (W, H), top, cuts=mw.cuts, flap=flap, pose=pose, scale=scale, tint=tint, parts=parts)
    return WingModel([rig], frames=frames, frame_time=frame_time, translucent=translucent), mw


# ---------------------------------------------------------------- insects (rigid wings: one segment)

def insect(W, H, top, shapes, paint, glow=None, frames=1, frame_time=100, animated=False, flap=None, pose=None,
           scale=0.85, tint=False, translucent=False, z=0.0):
    """shapes: [(kind, x0, y0, w, h, slim)] -> masks list passed to paint(cv, masks, f) -> rgba."""
    cv = Canvas(W, H, top)
    masks = []
    for kind, x0, y0, w, h, slim in shapes:
        masks.append(polys_mask(cv, [insect_shape(cv, kind, x0, y0, w, h, slim)]))
    p = cached(lambda f: paint(cv, masks, f), animated and frames > 1)
    g = cached(lambda f: glow(cv, masks, f), frames > 1) if glow else None
    fl = dict(amp=26, lift=4, speed=0.8, speedMove=1.8, idle=0.5, move=1.0, kind='wave')
    if flap:
        fl.update(flap)
    po = dict(spread=16, lift=4)
    if pose:
        po.update(pose)
    rig = Rig([Layer(z, p, g)], (W, H), top, cuts=[], flap=fl, pose=po, scale=scale, tint=tint)
    return WingModel([rig], frames=frames, frame_time=frame_time, translucent=translucent), cv


# ---------------------------------------------------------------- shards

def shard_layout(W, H, top, n=13, seed=1, spread=(100, 12), length=(0.5, 0.75), width=1.8, lead=None, jitter=0.12,
                 small=0):
    """Crystal shards along a leading edge, pointing down and out. Returns [(poly, l1, l2)] back to front."""
    rng = np.random.default_rng(seed)
    if lead is None:
        lead = chaikin([(0.3, 0.0), (W * 0.35, -6.0), (W * 0.62, top + 3.0), (W * 0.84, top + 4.5)], 3, closed=False)
    out = []
    for i in range(n):
        k = (i + 0.5) / n
        bx, by = _along(lead, k)
        ang = spread[0] + (spread[1] - spread[0]) * k ** 1.1 + rng.uniform(-6, 6)
        ln = H * (length[0] + (length[1] - length[0]) * math.sin(math.pi * min(1, k * 1.1))) * (1 + rng.uniform(-jitter, jitter))
        wd = width * (1.15 - 0.4 * k) * (1 + rng.uniform(-0.15, 0.15))
        poly = shard_poly((bx, by + 0.6), ang, ln, wd, mid=0.28 + rng.uniform(-0.05, 0.05))
        poly = [(min(max(x, 0.2), W - 0.3), min(max(y, top + 0.3), top + H - 0.3)) for x, y in poly]
        out.append((poly, 0.95 + rng.uniform(-0.05, 0.05), 0.55 + rng.uniform(-0.08, 0.08), k))
    out.sort(key=lambda s: -s[3])
    smalls = []
    for i in range(small):
        k = (i + 0.5) / small
        bx, by = _along(lead, k * 0.95)
        ang = spread[0] - 5 + (spread[1] - spread[0] + 10) * k ** 1.2 + rng.uniform(-8, 8)
        ln = H * 0.24 * (1 - 0.35 * k) * (1 + rng.uniform(-0.2, 0.2))
        poly = shard_poly((bx, by - 0.4), ang, ln, width * 0.8 * (1.1 - 0.4 * k))
        smalls.append((poly, 0.98, 0.62, k))
    smalls.sort(key=lambda s: -s[3])
    return [(p, a, b) for p, a, b, _ in out], [(p, a, b) for p, a, b, _ in smalls], lead


def sharded(W, H, top, big, small, colour, glow=None, glow_small=None, cuts=None, frames=1, frame_time=100, animated=False,
            flap=None, pose=None, scale=0.85, tint=False, translucent=False, band=None, lead=None):
    """colour(cv, (alpha, light, ids, edge), which, f) -> rgba."""
    cv = Canvas(W, H, top)
    mb = facet_maps(cv, big)
    ms = facet_maps(cv, small) if small else None
    bandm = None
    if band and lead is not None:
        pts = lead + [(p[0], p[1] + 2.0) for p in reversed(lead)]
        bandm = polys_mask(cv, [[cv.p(*q) for q in pts]])

    def pb(f):
        img = colour(cv, mb, 'big', f)
        if bandm is not None:
            img = over(rgba(np.broadcast_to(rgb(band), img.shape[:2] + (3,)).copy(), bandm), img)
        return img
    layers = [Layer(0.0, cached(pb, animated and frames > 1), cached(lambda f: glow(cv, mb, 'big', f), frames > 1) if glow else None)]
    if ms is not None:
        layers.append(Layer(0.4, cached(lambda f: colour(cv, ms, 'small', f), animated and frames > 1),
                            cached(lambda f: (glow_small or glow)(cv, ms, 'small', f), frames > 1) if (glow_small or glow) else None))
    rig = Rig(layers, (W, H), top, cuts=cuts or [W * 0.35, W * 0.62], flap=flap, pose=pose, scale=scale, tint=tint)
    return WingModel([rig], frames=frames, frame_time=frame_time, translucent=translucent), cv


# ---------------------------------------------------------------- small pictures for parts

def part_canvas(w, h):
    """A canvas for a part's card: w x h model px, origin at its top-left."""
    return Canvas(w, h, 0)


def const(img):
    return lambda f: img


# ---------------------------------------------------------------- clean stylised feathered wings

def clean(style='angel', size=1.0, n=(6, 5, 2), shape='round', width=1.3, reach=1.0, cov=5, scallops=4, seed=1, droop=0.0,
          cols=('#ffffff:#dfe4ee', '#ffffff:#eef1f7', '#ffffff:#f3f5fa'), line='#5a6680', color=None, glow=None, frames=1,
          frame_time=100, animated=False, rim=0.55, gloss=0.1, alpha=1.0, flap=None, pose=None, scale=0.85, tint=False,
          translucent=False, parts=(), rig_only=False, prefix='', arm=2.6, extra_rigs=(), crease=0.05):
    """A clean feathered wing (CleanWing). color(name, maps or None, cw, f) -> rgb for 'long', 'mid', 'arm';
    glow(name, maps or None, cw, f) -> rgba or None."""
    cw = CleanWing(style, size, n, shape, width, reach, cov, scallops, seed, droop, arm)

    def colour(name, m, f):
        if color:
            return color(name, m, cw, f)
        i = {'long': 0, 'mid': 1, 'arm': 2}[name]
        return grad(m.t if m is not None else cw.arm_v, cols[i])

    def paint_long(f):
        img = clean_shade(cw.long, colour('long', cw.long, f), line, rim, gloss, alpha=alpha, crease=crease)
        under = colour('arm', None, f)
        return over(rgba(np.asarray(under, np.float32) * 0.9, cw.band * alpha), img)

    def paint_mid(f):
        return clean_shade(cw.mid, colour('mid', cw.mid, f), line, rim * 0.7, gloss, alpha=alpha, crease=crease)

    def paint_arm(f):
        return clean_arm(cw, colour('arm', None, f), line, rim * 0.7, alpha=alpha)

    def gl(name, m):
        if glow is None or glow(name, m, cw, 0) is None:
            return None
        return cached(lambda f: glow(name, m, cw, f), frames > 1)
    an = animated and frames > 1
    layers = [Layer(0.0, cached(paint_long, an), gl('long', cw.long)),
              Layer(0.35, cached(paint_mid, an), gl('mid', cw.mid)),
              Layer(0.7, cached(paint_arm, an), gl('arm', None))]
    rig = Rig(layers, (cw.W, cw.H), cw.top, cuts=cw.cuts, flap=flap, pose=pose, scale=scale, tint=tint, parts=parts, prefix=prefix)
    if rig_only:
        return rig, cw
    return WingModel([rig] + list(extra_rigs), frames=frames, frame_time=frame_time, translucent=translucent), cw
