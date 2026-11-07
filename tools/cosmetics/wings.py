"""Wings: the Polar wing collection (60 original designs in a clean, stylised look).

The designs live in wingdesigns.py; wingkit.py cuts each wing's artwork into hinged, layered parts and writes the
model, and wingpaint.py / winghelpers.py hold the painting. A few wings also shed particles in game (embers, snow,
petals, sparks): those use the same particle rules as auras.
"""
from lib import save_png
from paint import sprite_strip, snowflake_sprite, soft_dot, petal_sprite, star_sprite
from auras import spark_sprite
import wingdesigns

# the wardrobe's collections, until the wing families get collections of their own
COLLECTION = {
    'Feathered': 'celestial', 'Fire': 'inferno', 'Ice & Water': 'polar', 'Cosmic': 'cosmic', 'Dark': 'inferno',
    'Tech': 'neon', 'Nature': 'nature', 'Energy': 'neon', 'Fun': 'cute', 'Mythic': 'royal',
}

# particles that drift off some wings (spawned around the upper body, drawn like aura particles)
PARTICLES = {
    'wings_ember_drift': (lambda: sprite_strip(2, 8, lambda i, s: soft_dot(s, 1.5 + i)), dict(
        rate=6, life=[1.2, 2.0], size=[0.04, 0.07], radius=[0.3, 0.75], height=[1.0, 1.7], vx=[-0.08, 0.08], vy=[0.25, 0.5],
        vz=[-0.08, 0.08], shrink=True, colors=['#ff9c4a', '#ffd27a', '#ff6a10'], glow=True)),
    'wings_blizzard': (lambda: sprite_strip(4, 16, lambda i, s: snowflake_sprite(s, i)), dict(
        rate=5, life=[2.0, 3.0], size=[0.05, 0.08], radius=[0.35, 0.85], height=[1.4, 2.0], vx=[-0.05, 0.05], vy=[-0.35, -0.2],
        vz=[-0.05, 0.05], spin=[-60, 60], colors=['#ffffff', '#e4f3ff', '#c6e6ff'], glow=False)),
    'wings_petal_drift': (lambda: sprite_strip(3, 16, lambda i, s: petal_sprite(s, i)), dict(
        rate=3, life=[2.2, 3.2], size=[0.05, 0.08], radius=[0.35, 0.8], height=[1.3, 1.9], vx=[-0.1, 0.1], vy=[-0.3, -0.18],
        vz=[-0.1, 0.1], spin=[-120, 120], swirl=[0.2, 0.5], colors=['#ffb7d0', '#ff9ec4', '#ffd6e5'], glow=False)),
    'wings_thruster_rig': (lambda: sprite_strip(4, 16, lambda i, s: spark_sprite(s, i)), dict(
        rate=9, life=[0.25, 0.5], size=[0.05, 0.09], radius=[0.3, 0.6], height=[0.9, 1.5], vx=[-0.15, 0.15], vy=[-0.7, -0.3],
        vz=[-0.15, 0.15], spin=[-300, 300], colors=['#7dd3fc', '#e0f2fe', '#5ac8ff'], glow=True)),
    'wings_polar_guardian': (lambda: sprite_strip(2, 16, lambda i, s: snowflake_sprite(s, i) if i == 0 else star_sprite(s, 4)), dict(
        rate=4, life=[2.0, 3.0], size=[0.05, 0.08], radius=[0.35, 0.85], height=[1.2, 2.0], vx=[-0.05, 0.05], vy=[-0.25, -0.1],
        vz=[-0.05, 0.05], spin=[-60, 60], shrink=True, colors=['#ffffff', '#cfeaff', '#8ad4ff'], glow=True)),
}


def build(out, item):
    model, _ = item['make']()
    model.build(out, item['id'])
    built = {'model': f"models/{item['id']}.json"}
    if item['id'] in PARTICLES:
        sprites, settings = PARTICLES[item['id']]
        strip = sprites()
        save_png(strip, f"{out}/textures/{item['id']}_particles.png")
        p = dict(settings)
        p['texture'] = f"textures/{item['id']}_particles.png"
        p['frames'] = strip.shape[1] // strip.shape[0]
        built['particles'] = p
    return built


ITEMS = []
for d in wingdesigns.DESIGNS:
    item = dict(id=d['id'], name=d['name'], collection=COLLECTION[d['family']], rarity=d['rarity'], make=d['build'],
                description=d['desc'])
    if d['tint']:
        item['tint'] = True
        item['defaultColor'] = d['color']
    ITEMS.append(item)
