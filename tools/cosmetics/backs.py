"""Back items: weapons, packs and gadgets worn on the back. Anchored to the body (pixels; the body is the box
(-4, 0, -2)..(4, 12, 2), y down; the back is the +z side, so a box's 'back' face is the one people behind you see)."""
import math

import numpy as np

from lib import Model, Bone, Cube, card, card_x, rgb, rgba, gradient, bevel, fbm, solid, blur, vnoise
from paint import ice, silver, leather, gold, wood, metal, fabric, coords, mask_polys, mask_ellipse
from heads import M, faces, fur, felt, gem


def _ice(face, w, h, f):
    return ice(w, h, 5, alpha=0.92)


def _ice_glow(face, w, h, f):
    x, y = coords(w, h)
    a = np.clip(0.6 - np.abs(x - 0.5) * 1.6, 0, 1) * 0.8
    return rgba(np.broadcast_to(rgb('#9ff6ff'), (h, w, 3)).copy(), a)


def straps(colour_a='#3a2a20', colour_b='#21160f', buckle=True):
    """Two straps over the shoulders and down the chest, so packs look worn."""
    lth = M(leather, 2, colour_a, colour_b)
    cubes = []
    for x in (-2.3, 2.3):
        cubes.append(Cube((x - 0.6, -0.45, -2.25), (1.2, 0.5, 4.5), lth))
        cubes.append(Cube((x - 0.6, 0.0, -2.35), (1.2, 7.0, 0.3), lth))
        if buckle:
            cubes.append(Cube((x - 0.75, 4.2, -2.5), (1.5, 1.0, 0.3), M(silver, 3)))
    return cubes


# ---------------------------------------------------------------- blades

def frost_sword():
    blade = Cube((-1, -1.5, -0.5), (2, 15, 1), _ice, glow=_ice_glow)
    tip = Cube((-0.5, 13.5, -0.5), (1, 1.5, 1), _ice, glow=_ice_glow)
    guard = Cube((-3.5, -3, -1), (7, 1.5, 2), M(silver, 2))
    gems = Cube((-0.6, -3.1, -1.1), (1.2, 1.7, 2.2), gem('#38bdf8'))
    grip = Cube((-0.75, -7.5, -0.75), (1.5, 4.5, 1.5), M(leather, 4, '#3d5a8a', '#1d2c4d'))
    pommel = Cube((-1.25, -9.5, -1.25), (2.5, 2, 2.5), M(silver, 3))
    sword = Bone('sword', pivot=(0, 5, 3.3), rot=(0, 0, -36), cubes=[blade, tip, guard, gems, grip, pommel])
    return Model('body', [sword], density=4, translucent=True)


def katana():
    lacquer = M(metal, '#2a2a2e', '#141417', '#060608', 3, 0.02, 0.25, 0.3)

    def cord(face, w, h, f):
        x, y = coords(w, h)
        base = rgb('#121214') * np.ones((h, w, 1), np.float32)
        wrap = ((np.floor(y * h / 2.5) % 3) == 0)
        col = np.where(wrap[..., None], rgb('#c81e3a'), base)
        return bevel(rgba(col), 0.15, 0.25, 1)

    def tsuka(face, w, h, f):
        x, y = coords(w, h)
        a = (x * w + y * h) / 3
        b = (x * w - y * h) / 3
        diamond = ((np.abs(a - np.round(a)) < 0.18) | (np.abs(b - np.round(b)) < 0.18))
        col = np.where(diamond[..., None], rgb('#1b1b1f'), rgb('#efe9dc'))
        return bevel(rgba(col), 0.1, 0.25, 1)
    cubes = [Cube((-0.65, -1.0, -0.5), (1.3, 17.5, 1.0), lacquer),
             Cube((-0.7, 0.5, -0.55), (1.4, 3.5, 1.1), cord),
             Cube((-0.3, 16.5, -0.3), (0.6, 1.2, 0.6), M(gold, 2)),
             Cube((-1.5, -1.6, -1.5), (3.0, 0.6, 3.0), M(gold, 4)),
             Cube((-0.6, -6.8, -0.6), (1.2, 5.2, 1.2), tsuka),
             Cube((-0.7, -7.4, -0.7), (1.4, 0.7, 1.4), M(gold, 5))]
    return Model('body', [Bone('katana', (0, 5.5, 3.2), (0, 0, 38), cubes)], density=6)


# ---------------------------------------------------------------- packs

def backpack():
    canvas = M(fabric, '#f4f4f4', '#c9c9c9', 2, 0.05)
    flap = M(fabric, '#e0e0e0', '#b4b4b4', 3, 0.05)

    def pocket(face, w, h, f):
        img = fabric(w, h, '#ececec', '#c4c4c4', 4, 0.05)
        if face == 'back':
            x, y = coords(w, h)
            zip_ = (np.abs(y - 0.22) < 0.05) * (x > 0.1) * (x < 0.9)
            img[..., :3] = np.where(zip_[..., None], rgb('#4a4a4a'), img[..., :3])
        return img
    cubes = [Cube((-3.5, 1.0, 2.0), (7, 9, 3.2), canvas, tint=True),
             Cube((-3.65, 0.6, 1.9), (7.3, 3.4, 3.5), flap, tint=True),
             Cube((-2.6, 5.6, 5.2), (5.2, 3.6, 1.0), pocket, tint=True),
             Cube((-0.8, 3.3, 5.3), (1.6, 1.2, 0.3), M(silver, 2)),
             Cube((-4.3, 3.5, 2.6), (0.8, 5.0, 2.0), flap, tint=True),
             Cube((3.5, 3.5, 2.6), (0.8, 5.0, 2.0), flap, tint=True)]
    return Model('body', [Bone('pack', (0, 0, 0), (0, 0, 0), cubes + straps())], density=4)


def quiver():
    tube = M(leather, 5, '#8a5a35', '#4f2f19')
    band = M(gold, 2)
    shaft = M(wood, 2, '#d9b382', '#8c6239')

    def fletch(face, w, h, f):
        x, y = coords(w, h)
        m = mask_polys(w, h, [[(0, h * 0.15), (w, 0), (w, h), (0, h * 0.85)]])
        col = np.where((y < 0.5)[..., None], rgb('#e8344a'), rgb('#ffffff'))
        return rgba(col * (0.85 + 0.15 * x[..., None]), m)
    cubes = [Cube((-1.6, 0, -1.6), (3.2, 11, 3.2), tube),
             Cube((-1.75, 0.6, -1.75), (3.5, 0.8, 3.5), band),
             Cube((-1.75, 9.4, -1.75), (3.5, 0.8, 3.5), band)]
    for i, (x, z) in enumerate(((-0.8, -0.6), (0.7, -0.7), (0.0, 0.5), (-0.9, 0.8), (0.9, 0.7))):
        top = -3.8 - (i % 3) * 0.7
        cubes.append(Cube((x - 0.2, top, z - 0.2), (0.4, -top + 1, 0.4), shaft))
        cubes.append(Cube((x, top - 0.2, z - 0.9), (0, 2.4, 1.8), card_x(fletch)))
        cubes.append(Cube((x - 0.9, top - 0.2, z), (1.8, 2.4, 0), card(fletch)))
    return Model('body', [Bone('quiver', (1.2, 3.8, 3.8), (0, 0, -24), cubes)], density=6)


def guitar():
    spruce = M(wood, 3, '#e8c18a', '#b07a3f')
    dark = M(wood, 4, '#6b3a1e', '#3a1e0e')

    def top_face(face, w, h, f):
        img = wood(w, h, 3, '#edc890', '#b98245')
        if face in ('back', 'front'):
            x, y = coords(w, h)
            hole = mask_ellipse(w, h, w * 0.5, h * 0.05, w * 0.2, w * 0.2)
            ringm = mask_ellipse(w, h, w * 0.5, h * 0.05, w * 0.27, w * 0.27) - mask_ellipse(w, h, w * 0.5, h * 0.05, w * 0.23, w * 0.23)
            img[..., :3] = img[..., :3] * (1 - hole[..., None]) + rgb('#1a0d05') * hole[..., None]
            img[..., :3] = img[..., :3] * (1 - ringm[..., None]) + rgb('#3a1e0e') * ringm[..., None]
            bridge = (np.abs(y - 0.68) < 0.05) * (np.abs(x - 0.5) < 0.22)
            img[..., :3] = np.where(bridge[..., None], rgb('#2a140a'), img[..., :3])
            strings = (np.abs(((x - 0.5) * w) % 1.0 - 0.5) < 0.18) * (np.abs(x - 0.5) < 0.1) * (y < 0.68)
            img[..., :3] = np.where(strings[..., None], rgb('#e8e8e8'), img[..., :3])
        return img

    def neck_face(face, w, h, f):
        img = wood(w, h, 5, '#5a3018', '#2f170a')
        if face in ('back', 'front'):
            x, y = coords(w, h)
            frets = ((np.floor(y * h) % 4) == 0)
            img[..., :3] = np.where(frets[..., None], rgb('#c9c9c9'), img[..., :3])
            strings = (np.abs(x - 0.5) < 0.32) * ((np.floor(x * w) % 2) == 0)
            img[..., :3] = img[..., :3] * (1 - strings[..., None] * 0.4) + rgb('#f0f0f0') * strings[..., None] * 0.4
        return img
    cubes = [Cube((-4.0, 0, -0.9), (8, 5.5, 1.8), faces(spruce, back=top_face, front=top_face)),
             Cube((-3.2, -4.0, -0.9), (6.4, 4.2, 1.8), faces(spruce, back=top_face, front=top_face)),
             Cube((-0.7, -13, -0.5), (1.4, 9.2, 1.0), neck_face),
             Cube((-1.0, -16, -0.45), (2.0, 3.1, 0.9), dark)]
    for i in range(3):
        for side in (-1, 1):
            cubes.append(Cube((side * 1.0 - (0.5 if side < 0 else 0), -15.5 + i * 0.9, -0.2), (0.5, 0.4, 0.4), M(silver, i)))
    return Model('body', [Bone('guitar', (0.5, 6.5, 3.4), (0, 0, 32), cubes)], density=4)


def rocket_pack():
    N = 8

    def tank(face, w, h, f):
        img = metal(w, h, '#ffffff', '#dfe5ec', '#9aa7b6', 4, 0.03)
        x, y = coords(w, h)
        stripe = (np.abs(y - 0.3) < 0.08) | (np.abs(y - 0.72) < 0.05)
        img[..., :3] = np.where(stripe[..., None], rgb('#e11d48'), img[..., :3])
        return img
    nozzle = M(metal, '#7b8794', '#3e4752', '#1c2229', 2)

    def flame(face, w, h, f):
        x, y = coords(w, h)
        rng = np.random.default_rng(f)
        length = 0.75 + 0.25 * math.sin(2 * math.pi * f / N * 3) * 0.6 + rng.random() * 0.1
        wob = 0.05 * np.sin(y * 14 + f * 2.1)
        width = 0.42 * np.clip(1 - y / length, 0, 1) ** 0.7
        m = np.clip((width - np.abs(x - 0.5 - wob * y)) * w, 0, 1) * (y < length)
        col = gradient(np.clip(y / length, 0, 1), [(0, '#ffffff'), (0.2, '#8fe9ff'), (0.45, '#ffd23f'), (0.75, '#ff6a1a'), (1, '#c2410c')])
        return rgba(col, m)

    def flame_glow(face, w, h, f):
        img = flame(face, w, h, f)
        img[..., 3] = np.clip(img[..., 3] * 0.95, 0, 1)
        return img

    def light(face, w, h, f):
        on = 1.0 if (f // 2) % 2 == 0 else 0.35
        x, y = coords(w, h)
        dot = (np.hypot(x - 0.5, (y - 0.15) * h / w) < 0.25) * on
        return rgba(np.broadcast_to(rgb('#3dff8f'), (h, w, 3)).copy(), dot.astype(np.float32) * (1.0 if face == 'back' else 0.0))
    cubes = [Cube((-1.0, 1.6, 2.0), (2.0, 6.5, 1.4), M(metal, '#4b5563', '#374151', '#1f2937', 6), glow=light)]
    for x in (-1.8, 1.8):
        cubes += [Cube((x - 1.3, 0.8, 2.6), (2.6, 8.2, 2.6), tank),
                  Cube((x - 1.4, 0.3, 2.5), (2.8, 0.9, 2.8), nozzle),
                  Cube((x - 0.9, 9.0, 3.0), (1.8, 1.3, 1.8), nozzle),
                  Cube((x - 0.9, 10.3, 3.9), (1.8, 5.0, 0), card(flame), glow=card(flame_glow)),
                  Cube((x, 10.3, 3.0), (0, 5.0, 1.8), card_x(flame), glow=card_x(flame_glow))]
    return Model('body', [Bone('pack', (0, 0, 0), (0, 0, 0), cubes + straps('#2b2f36', '#15181d'))], density=4, frames=N, frame_time=60, translucent=True)


# ---------------------------------------------------------------- cute and polar

def polar_pal():
    white = fur('#ffffff', '#e3eaf2', 3, 0.14)

    def face_tex(face, w, h, f):
        img = fur('#ffffff', '#e8eef5', 4, 0.1)(face, w, h, f)
        if face == 'back':
            x, y = coords(w, h)
            for ex in (0.28, 0.72):
                eye = mask_ellipse(w, h, w * ex, h * 0.42, w * 0.08, h * 0.09)
                img[..., :3] = img[..., :3] * (1 - eye[..., None]) + rgb('#141414') * eye[..., None]
                shine = mask_ellipse(w, h, w * (ex - 0.025), h * 0.39, w * 0.025, h * 0.03)
                img[..., :3] = img[..., :3] * (1 - shine[..., None]) + shine[..., None]
            for bx in (0.18, 0.82):
                blush = mask_ellipse(w, h, w * bx, h * 0.62, w * 0.08, h * 0.05) * 0.5
                img[..., :3] = img[..., :3] * (1 - blush[..., None]) + rgb('#ff9fb8') * blush[..., None]
        return img

    def muzzle(face, w, h, f):
        img = fur('#f3f6fa', '#d9e1ea', 5, 0.08)(face, w, h, f)
        if face == 'back':
            nose = mask_ellipse(w, h, w * 0.5, h * 0.3, w * 0.22, h * 0.2)
            img[..., :3] = img[..., :3] * (1 - nose[..., None]) + rgb('#1d1d1f') * nose[..., None]
        return img

    def ear(face, w, h, f):
        img = fur('#ffffff', '#e3eaf2', 6, 0.1)(face, w, h, f)
        if face == 'back':
            m = mask_ellipse(w, h, w * 0.5, h * 0.6, w * 0.3, h * 0.35)
            img[..., :3] = img[..., :3] * (1 - m[..., None]) + rgb('#c9d3df') * m[..., None]
        return img
    scarf = M(knitted_scarf)
    cubes = [Cube((-2.6, 3.0, 2.1), (5.2, 5.2, 3.2), white),
             Cube((-2.2, -0.8, 2.2), (4.4, 3.8, 3.6), faces(white, back=face_tex)),
             Cube((-1.0, 1.0, 5.75), (2.0, 1.5, 0.8), faces(fur('#f3f6fa', '#d9e1ea', 5, 0.08), back=muzzle)),
             Cube((-2.45, 2.8, 2.0), (4.9, 0.9, 3.6), scarf),
             Cube((1.4, 2.8, 5.2), (1.0, 2.4, 0.4), scarf)]
    for x in (-1.6, 1.6):
        cubes.append(Cube((x - 0.6, -1.6, 3.6), (1.2, 1.0, 0.8), faces(white, back=ear)))
        cubes.append(Cube(((5.0 if x > 0 else -5.0) - 0.75, -1.3, -1.8), (1.5, 1.3, 4.9), white))
        cubes.append(Cube((x - 0.9, 7.6, 2.9), (1.8, 2.4, 1.8), white))
    return Model('body', [Bone('plush', (0, 0, 0), (0, 0, 0), cubes)], density=6)


def knitted_scarf(w, h):
    x, y = coords(w, h)
    ix = np.arange(w)[None, :].repeat(h, 0)
    band = ((np.floor(ix / 3)) % 2).astype(np.float32)
    col = gradient(band, [(0, '#2f6fed'), (1, '#e6f1ff')])
    return bevel(rgba(col * (0.9 + 0.1 * vnoise(w, h, 2, 3))[..., None]), 0.08, 0.2, 1)


def snowboard():
    from capes import snowflake_mask

    def deck(face, w, h, f):
        x, y = coords(w, h)
        col = gradient(y, [(0, '#0b1f4d'), (0.5, '#1d4ed8'), (1, '#38bdf8')])
        stripe = np.abs((y - 0.75) - (x - 0.5) * 0.4) < 0.05
        col = np.where(stripe[..., None], rgb('#ffffff'), col)
        if face in ('back', 'front'):
            fl = snowflake_mask(w, h, w * 0.5, h * 0.35, w * 0.38, max(1.0, w / 18))
            col = col * (1 - fl[..., None]) + fl[..., None]
            r = w * 0.5
            yy = y * h
            ends = np.where(yy < r, np.hypot(x * w - w * 0.5, yy - r) <= r, np.where(yy > h - r, np.hypot(x * w - w * 0.5, yy - (h - r)) <= r, True))
            return bevel(rgba(col, ends.astype(np.float32)), 0.15, 0.2, 1)
        return rgba(np.broadcast_to(rgb('#f2f2f2'), (h, w, 3)).copy())
    binding = M(metal, '#3a3f47', '#22262c', '#101215', 2)
    cubes = [Cube((-2.6, -10, -0.3), (5.2, 20, 0.6), deck)]
    for y in (-4.5, 2.5):
        cubes.append(Cube((-2.0, y, 0.3), (4.0, 1.4, 0.9), binding))
    return Model('body', [Bone('board', (0.5, 6.0, 3.6), (0, 0, 18), cubes)], density=4)


def build(out, item):
    item['model']().build(out, item['id'])
    return {'model': f"models/{item['id']}.json"}


ITEMS = [
    dict(id='back_frost_sword', name='Frostbrand', collection='polar', rarity='epic', model=frost_sword,
         description='A blade of living ice, strapped across your back.'),
    dict(id='back_katana', name='Katana', collection='classic', rarity='rare', model=katana,
         description='A sheathed blade with a red cord wrap.'),
    dict(id='back_backpack', name='Adventure Pack', collection='classic', rarity='common', model=backpack, tint=True, defaultColor='#e2a33b',
         description='A roomy backpack in any colour you like.'),
    dict(id='back_quiver', name='Quiver', collection='nature', rarity='rare', model=quiver,
         description="A leather quiver full of arrows."),
    dict(id='back_guitar', name='Acoustic Guitar', collection='classic', rarity='rare', model=guitar,
         description='For campfire songs between matches.'),
    dict(id='back_rocket_pack', name='Rocket Pack', collection='neon', rarity='legendary', model=rocket_pack,
         description='Twin tanks with roaring flames.'),
    dict(id='back_polar_pal', name='Polar Pal', collection='polar', rarity='epic', model=polar_pal,
         description='A cuddly polar bear riding on your back.'),
    dict(id='back_snowboard', name='Snowboard', collection='polar', rarity='rare', model=snowboard,
         description='A snowboard with the Polar snowflake on it.'),
]
