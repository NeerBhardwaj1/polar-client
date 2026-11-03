"""Auras: particles around the player. The game and the launcher simulate them from these settings and draw the
sprites from here (a strip of frames; one is picked at random per particle and tinted with one of `colors`).

Particle settings (ranges are [min, max]): rate (per second), life (s), size (blocks), radius (spawn distance from
the player's centre), height (spawn height above the feet), vx/vy/vz (blocks per second), spin (degrees per second),
swirl (radians per second around the player), gravity (blocks/s^2, positive pulls down), shrink (true: shrink as
they age), glow (true: additive and full-bright), colors (picked at random).
"""
import math

import numpy as np
from PIL import Image, ImageDraw

from lib import save_png, rgba, gradient, blur
from paint import sprite_strip, snowflake_sprite, soft_dot, star_sprite, heart_sprite, note_sprite, petal_sprite, coords


def _poly_sprite(size, polys, width=0):
    im = Image.new('L', (size * 4, size * 4), 0)
    d = ImageDraw.Draw(im)
    for p in polys:
        pts = [(x * size * 4, y * size * 4) for x, y in p]
        if width:
            d.line(pts, fill=255, width=int(width * size * 4), joint='curve')
        else:
            d.polygon(pts, fill=255)
    a = np.asarray(im.resize((size, size), Image.LANCZOS), np.float32) / 255
    return a


def flame_sprite(size, variant=0):
    x, y = coords(size, size)
    lean = (variant - 1.5) * 0.04
    w = 0.34 * np.clip((y - 0.08) / 0.92, 0, 1) ** 0.6 * np.clip((1 - y) / 0.35, 0, 1) ** 0.5
    d = np.abs(x - 0.5 - lean * (1 - y)) - w
    a = np.clip(-d * size * 0.9, 0, 1)
    core = np.clip(1 - np.hypot((x - 0.5) / 0.16, (y - 0.72) / 0.22), 0, 1)
    col = np.ones((size, size, 3), np.float32) * (0.75 + 0.25 * core[..., None])
    return rgba(col, np.clip(a + core * 0.3, 0, 1))


def bubble_sprite(size):
    x, y = coords(size, size)
    r = np.hypot(x - 0.5, y - 0.5) * 2
    ringm = np.clip(1 - np.abs(r - 0.82) * 7, 0, 1)
    fill = (r < 0.85) * 0.18
    hi = np.clip(1 - np.hypot((x - 0.35) / 0.12, (y - 0.32) / 0.08), 0, 1)
    return rgba(np.ones((size, size, 3), np.float32), np.clip(ringm + fill + hi, 0, 1))


def spark_sprite(size, variant=0):
    rng = np.random.default_rng(variant + 3)
    pts = [(0.5, 0.5)]
    polys = []
    for arm in range(3):
        a = rng.random() * 2 * math.pi
        px, py = 0.5, 0.5
        line = [(px, py)]
        for k in range(3):
            a += rng.normal(0, 0.7)
            px, py = px + math.cos(a) * 0.15, py + math.sin(a) * 0.15
            line.append((min(0.97, max(0.03, px)), min(0.97, max(0.03, py))))
        polys.append(line)
    a = _poly_sprite(size, polys, width=0.07)
    core = np.clip(1 - np.hypot(*(np.array(coords(size, size)) - 0.5)) * 7, 0, 1)
    return rgba(np.ones((size, size, 3), np.float32), np.clip(a + blur(a, size / 16) * 0.6 + core, 0, 1))


def leaf_sprite(size, variant=0):
    a = math.radians(variant * 70 + 20)
    x, y = coords(size, size)
    dx, dy = x - 0.5, y - 0.5
    u = dx * math.cos(a) + dy * math.sin(a)
    v = -dx * math.sin(a) + dy * math.cos(a)
    half = 0.2 * np.clip(1 - (u / 0.4) ** 2, 0, 1) * (1 + 0.15 * np.sign(u))
    shape = np.clip((half - np.abs(v)) * size * 0.8, 0, 1) * (np.abs(u) < 0.4)
    stem = (np.abs(v) < 0.025) * (u < -0.36) * (u > -0.48)
    shape = np.clip(shape + stem, 0, 1)
    vein = (np.abs(v) < 0.025) * (np.abs(u) < 0.36)
    col = np.ones((size, size, 3), np.float32) * (1 - vein[..., None] * 0.35)
    return rgba(col, shape.astype(np.float32))


def ring_sprite(size):
    x, y = coords(size, size)
    r = np.hypot(x - 0.5, y - 0.5) * 2
    return rgba(np.ones((size, size, 3), np.float32), np.clip(1 - np.abs(r - 0.7) * 6, 0, 1) + np.clip(1 - r * 4, 0, 1) * 0.6)


def build(out, item):
    strip = item['sprites']()
    save_png(strip, f"{out}/textures/{item['id']}.png")
    p = dict(item['particles'])
    p['texture'] = f"textures/{item['id']}.png"
    p['frames'] = strip.shape[1] // strip.shape[0]
    return {'particles': p}


def _a(id, name, collection, rarity, sprites, description, **particles):
    return dict(id=id, name=name, collection=collection, rarity=rarity, sprites=sprites, description=description, particles=particles)


ITEMS = [
    _a('aura_snowfall', 'Snowfall', 'polar', 'rare', lambda: sprite_strip(4, 16, lambda i, s: snowflake_sprite(s, i)),
       'Gentle snow falls around you.',
       rate=7, life=[2.2, 3.4], size=[0.06, 0.11], radius=[0.35, 0.8], height=[1.6, 2.3], vx=[-0.04, 0.04], vy=[-0.55, -0.35],
       vz=[-0.04, 0.04], spin=[-60, 60], colors=['#ffffff', '#dff1ff', '#bfe3ff'], glow=False),
    _a('aura_aurora', 'Aurora Sparkle', 'polar', 'epic', lambda: sprite_strip(3, 16, lambda i, s: star_sprite(s, 4 + (i % 2) * 2) if i < 2 else soft_dot(s)),
       'Northern-light sparkles spiralling up around you.',
       rate=15, life=[1.6, 2.4], size=[0.08, 0.15], radius=[0.45, 0.7], height=[0.0, 0.6], vy=[0.45, 0.8], swirl=[1.2, 2.0],
       spin=[-90, 90], shrink=True, colors=['#5cf2d6', '#3dffb0', '#7a7cff', '#c08bff'], glow=True),
    _a('aura_flames', 'Flame Ring', 'inferno', 'legendary', lambda: sprite_strip(4, 16, lambda i, s: flame_sprite(s, i)),
       'A ring of fire licking up from your feet.',
       rate=26, life=[0.5, 0.9], size=[0.09, 0.16], radius=[0.38, 0.55], height=[0.0, 0.12], vx=[-0.03, 0.03], vy=[0.7, 1.1],
       vz=[-0.03, 0.03], shrink=True, colors=['#ffb13b', '#ff7a1a', '#ff4b1f', '#ffd23f'], glow=True),
    _a('aura_embers', 'Embers', 'inferno', 'rare', lambda: sprite_strip(2, 8, lambda i, s: soft_dot(s, 1.5 + i)),
       'Hot embers drifting up into the air.',
       rate=12, life=[1.4, 2.4], size=[0.05, 0.09], radius=[0.2, 0.75], height=[0.0, 1.0], vx=[-0.12, 0.12], vy=[0.35, 0.7],
       vz=[-0.12, 0.12], shrink=True, colors=['#ff9c4a', '#ffd27a', '#ff6a10'], glow=True),
    _a('aura_hearts', 'Lovestruck', 'cute', 'rare', lambda: sprite_strip(1, 16, lambda i, s: heart_sprite(s)),
       'Little hearts floating up around you.',
       rate=3, life=[1.8, 2.6], size=[0.08, 0.13], radius=[0.4, 0.75], height=[0.6, 1.6], vx=[-0.05, 0.05], vy=[0.3, 0.5],
       vz=[-0.05, 0.05], colors=['#ff4f8b', '#ff7ab8', '#e11d48', '#ff9fbf'], glow=False),
    _a('aura_stars', 'Starfall', 'celestial', 'epic', lambda: sprite_strip(2, 16, lambda i, s: star_sprite(s, 4 if i == 0 else 5, 0.2)),
       'Golden stars orbiting and twinkling around you.',
       rate=9, life=[1.6, 2.4], size=[0.08, 0.15], radius=[0.6, 0.8], height=[0.4, 1.9], vy=[-0.05, 0.05], swirl=[1.0, 1.6],
       spin=[-120, 120], shrink=True, colors=['#ffe08a', '#fff4c2', '#ffd23f', '#ffffff'], glow=True),
    _a('aura_notes', 'Melody', 'neon', 'rare', lambda: sprite_strip(2, 16, lambda i, s: note_sprite(s, i)),
       'Music notes in every colour bobbing up around you.',
       rate=3, life=[1.8, 2.6], size=[0.08, 0.12], radius=[0.45, 0.8], height=[0.8, 1.8], vx=[-0.06, 0.06], vy=[0.25, 0.45],
       vz=[-0.06, 0.06], colors=['#ff4f8b', '#ffd23f', '#3dffb0', '#38bdf8', '#c084fc'], glow=False),
    _a('aura_petals', 'Petal Breeze', 'nature', 'rare', lambda: sprite_strip(3, 16, lambda i, s: petal_sprite(s, i)),
       'Cherry blossom petals drifting down around you.',
       rate=5, life=[2.4, 3.4], size=[0.06, 0.1], radius=[0.4, 0.85], height=[1.8, 2.4], vx=[-0.12, 0.12], vy=[-0.4, -0.25],
       vz=[-0.12, 0.12], spin=[-120, 120], swirl=[0.3, 0.6], colors=['#ffb7d0', '#ff9ec4', '#ffd6e5'], glow=False),
    _a('aura_leaves', 'Autumn Leaves', 'nature', 'common', lambda: sprite_strip(3, 16, lambda i, s: leaf_sprite(s, i)),
       'Orange and red leaves tumbling down around you.',
       rate=4, life=[2.4, 3.4], size=[0.07, 0.11], radius=[0.4, 0.85], height=[1.8, 2.4], vx=[-0.15, 0.15], vy=[-0.45, -0.3],
       vz=[-0.15, 0.15], spin=[-150, 150], colors=['#f97316', '#ea580c', '#facc15', '#b91c1c'], glow=False),
    _a('aura_bubbles', 'Bubbles', 'nature', 'common', lambda: sprite_strip(1, 16, lambda i, s: bubble_sprite(s)),
       'Shiny bubbles wobbling up around you.',
       rate=4, life=[1.8, 2.8], size=[0.05, 0.12], radius=[0.35, 0.8], height=[0.0, 1.2], vx=[-0.08, 0.08], vy=[0.3, 0.55],
       vz=[-0.08, 0.08], colors=['#bfefff', '#e0f7ff', '#a5d8ff'], glow=False),
    _a('aura_void', 'Void Rift', 'cosmic', 'legendary', lambda: sprite_strip(3, 16, lambda i, s: soft_dot(s, 1.2) if i == 0 else (ring_sprite(s) if i == 1 else star_sprite(s, 4))),
       'Dark matter swirling around you, flecked with violet light.',
       rate=18, life=[1.2, 2.0], size=[0.08, 0.16], radius=[0.5, 0.75], height=[0.0, 1.9], vy=[0.05, 0.25], swirl=[-2.6, -1.6],
       spin=[-200, 200], shrink=True, colors=['#5b21b6', '#2e0854', '#7c3aed', '#c084fc', '#a855f7'], glow=False),
    _a('aura_sparks', 'Static Charge', 'neon', 'epic', lambda: sprite_strip(4, 16, lambda i, s: spark_sprite(s, i)),
       'Crackling electric sparks jumping around you.',
       rate=14, life=[0.18, 0.4], size=[0.08, 0.15], radius=[0.3, 0.6], height=[0.1, 1.9], vx=[-0.3, 0.3], vy=[-0.3, 0.3],
       vz=[-0.3, 0.3], spin=[-400, 400], colors=['#7dd3fc', '#e0f2fe', '#a5b4fc', '#ffffff'], glow=True),
    _a('aura_fireflies', 'Fireflies', 'nature', 'rare', lambda: sprite_strip(1, 8, lambda i, s: soft_dot(s, 1.8)),
       'Fireflies blinking lazily around you.',
       rate=4, life=[3.0, 4.5], size=[0.06, 0.1], radius=[0.5, 1.0], height=[0.3, 1.8], vx=[-0.12, 0.12], vy=[-0.08, 0.12],
       vz=[-0.12, 0.12], swirl=[-0.4, 0.4], colors=['#d9ff7a', '#f6ffb0', '#b6f542'], glow=True),
]
