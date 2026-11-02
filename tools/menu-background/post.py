"""Post-processes the rendered layers into game textures + BackgroundData.java (light/star/lake positions)."""
import json
import os
import sys

import numpy as np
from PIL import Image
from scipy import ndimage

SRC = sys.argv[1]       # folder with scene/sky/aurora renders
MOD = sys.argv[2]       # client-mod root
OUT_W, OUT_H = 1920, 1080
TEX = os.path.join(MOD, 'src/main/resources/assets/polarclient/textures/gui/menu')
JAVA = os.path.join(MOD, 'src/main/java/dev/polarclient/gui/BackgroundData.java')
os.makedirs(TEX, exist_ok=True)
rng = np.random.default_rng(7)


def load(name):
    return np.asarray(Image.open(os.path.join(SRC, name)).convert('RGBA'), dtype=np.float32) / 255.0


def downscale_premult(img, w, h):
    """Area downscale that respects alpha (no dark fringes at transparent edges)."""
    a = img[..., 3:4]
    pm = np.concatenate([img[..., :3] * a, a], axis=-1)
    chans = [np.asarray(Image.fromarray(pm[..., i]).resize((w, h), Image.BOX), dtype=np.float32) for i in range(4)]
    out = np.stack(chans, axis=-1)
    return out  # premultiplied


def save_rgba_premult(pm, path):
    a = np.clip(pm[..., 3:4], 0, 1)
    rgb = np.where(a > 1e-4, pm[..., :3] / np.maximum(a, 1e-4), 0)
    img = np.concatenate([np.clip(rgb, 0, 1), a], axis=-1)
    Image.fromarray((img * 255 + 0.5).astype(np.uint8), 'RGBA').save(path, optimize=True)


def mcmeta(path):
    json.dump({'texture': {'blur': True, 'clamp': True}}, open(path + '.mcmeta', 'w'))


scene_hi = load('scene_2560x1440.png')
sky_hi = load('sky_2560x1440.png')
aurora = load('aurora_2048x512.png')

scene = downscale_premult(scene_hi, OUT_W, OUT_H)        # premultiplied RGBA
sky = downscale_premult(sky_hi, OUT_W, OUT_H)[..., :3]  # opaque
alpha = scene[..., 3]

# colour grade: slightly deeper blues and more contrast
def grade(rgb):
    rgb = np.clip(rgb, 0, 1)
    rgb = rgb * np.array([0.93, 0.97, 1.06], np.float32)
    lum_ = rgb @ np.array([0.2126, 0.7152, 0.0722], np.float32)
    rgb = lum_[..., None] + (rgb - lum_[..., None]) * 1.12          # saturation
    return np.clip(0.5 + (rgb - 0.5) * 1.08 - 0.015, 0, 1)        # contrast


sky = grade(sky)
a_ = scene[..., 3:4]
scene[..., :3] = grade(np.where(a_ > 1e-4, scene[..., :3] / np.maximum(a_, 1e-4), 0)) * a_

MOON = (0.17, 0.15)
# composite for analysis + bloom source
comp = scene[..., :3] + sky * (1 - alpha[..., None])

# ---- bloom (lights, moon, bright snow edges)
lum = comp @ np.array([0.2126, 0.7152, 0.0722], dtype=np.float32)
bright = comp * np.clip((lum - 0.58) / 0.42, 0, 1)[..., None]
yy_, xx_ = np.mgrid[0:OUT_H, 0:OUT_W]
moon_d = np.hypot(xx_ - MOON[0] * OUT_W, yy_ - MOON[1] * OUT_H)
bright *= np.clip((moon_d - 70) / 60, 0, 1)[..., None]  # the moon gets a live glow in-game instead
b1 = np.stack([ndimage.gaussian_filter(bright[..., i], 5) for i in range(3)], -1)
b2 = np.stack([ndimage.gaussian_filter(bright[..., i], 20) for i in range(3)], -1)
bloom = 0.55 * b1 + 0.65 * b2
sky_out = np.clip(sky + bloom, 0, 1)
scene_out = scene.copy()
scene_out[..., :3] = np.clip(scene[..., :3] + bloom * alpha[..., None], 0, 1)

# Lake: lower its opacity so the live aurora reflection (drawn behind) shows through, and solve the colour
# so the still composite is unchanged: S_pm = C - sky * (1 - a_new).
LAKE_ALPHA = 0.70
lake_px = (alpha > 0.8) & (alpha < 0.95)
target = scene_out[..., :3] + sky_out * (1 - alpha[..., None])
scene_out[..., :3] = np.where(lake_px[..., None], np.clip(target - sky_out * (1 - LAKE_ALPHA), 0, None), scene_out[..., :3])
scene_out[..., 3] = np.where(lake_px, LAKE_ALPHA, scene_out[..., 3])
Image.fromarray((sky_out * 255 + 0.5).astype(np.uint8), 'RGB').save(os.path.join(TEX, 'sky.png'), optimize=True)
save_rgba_premult(scene_out, os.path.join(TEX, 'scene.png'))
# aurora: keep tileable width, store at 2048x512
Image.fromarray((aurora * 255 + 0.5).astype(np.uint8), 'RGBA').save(os.path.join(TEX, 'aurora.png'), optimize=True)

# soft round glow sprite for lights, moon, stars and the logo
g = 128
yy, xx = np.mgrid[0:g, 0:g]
r = np.hypot(xx - (g - 1) / 2, yy - (g - 1) / 2) / (g / 2)
glow_a = np.clip(np.exp(-(r ** 2) * 4.2) - 0.012, 0, 1) / (1 - 0.012)
glow = np.zeros((g, g, 4), np.float32)
glow[..., :3] = 1
glow[..., 3] = glow_a
Image.fromarray((glow * 255 + 0.5).astype(np.uint8), 'RGBA').save(os.path.join(TEX, 'glow.png'))
# blocky 16x16 moon face (original pixel art), drawn as a live sprite above the aurora
mr = np.random.default_rng(11)
face = np.zeros((16, 16, 3), np.float32) + np.array([0.975, 0.985, 1.0], np.float32)
face += (mr.random((16, 16, 1)) - 0.5) * 0.025
crater = np.array([0.87, 0.90, 0.96], np.float32)
for y0, x0, h_, w_ in [(3, 4, 2, 3), (4, 5, 2, 1), (9, 10, 3, 2), (10, 9, 1, 4), (11, 3, 1, 2), (2, 12, 1, 1), (7, 7, 1, 1), (13, 8, 1, 2)]:
    face[y0:y0 + h_, x0:x0 + w_] = crater
yy16, xx16 = np.mgrid[0:16, 0:16]
face *= (1.0 - 0.05 * ((xx16 + yy16) / 30.0))[..., None]  # faint shading toward the lower right
moon = np.ones((16, 16, 4), np.float32)
moon[..., :3] = np.clip(face, 0, 1)
moon_img = Image.fromarray((moon * 255 + 0.5).astype(np.uint8), 'RGBA').resize((128, 128), Image.NEAREST)
moon_img.save(os.path.join(TEX, 'moon.png'))
for n in ('sky.png', 'scene.png', 'aurora.png', 'glow.png', 'moon.png'):
    mcmeta(os.path.join(TEX, n))
# aurora must wrap horizontally
json.dump({'texture': {'blur': True, 'clamp': False}}, open(os.path.join(TEX, 'aurora.png.mcmeta'), 'w'))

# ---- warm lights (windows + lanterns) for flicker glows
warm = (comp[..., 0] > 0.78) & (comp[..., 0] - comp[..., 2] > 0.30) & (alpha > 0.5)
labels, n = ndimage.label(warm)
lights = []
for i, sl in enumerate(ndimage.find_objects(labels), start=1):
    if sl is None:
        continue
    ys, xs = np.nonzero(labels[sl] == i)
    size = len(ys)
    if size < 3:
        continue
    cy = (ys.mean() + sl[0].start) / OUT_H
    cx = (xs.mean() + sl[1].start) / OUT_W
    lights.append((cx, cy, size))
lights.sort(key=lambda l: -l[2])
lights = lights[:70]

# ---- lake: mask, top edge (mirror line) and sparkle points
lake = lake_px
rows = np.nonzero(lake.any(axis=1))[0]
lake_top = rows.min() / OUT_H if len(rows) else 0.6
ly, lx = np.nonzero(lake)
pick = rng.choice(len(ly), size=min(60, len(ly)), replace=False) if len(ly) else []
sparkles = [(lx[i] / OUT_W, ly[i] / OUT_H) for i in pick]

# ---- stars: only where open sky has a comfortable margin (layers shift with parallax)
open_sky = ndimage.binary_erosion(alpha < 0.02, iterations=28)
open_sky[int(OUT_H * 0.55):] = False
my_, mx_ = np.mgrid[0:OUT_H, 0:OUT_W]
open_sky &= np.hypot(mx_ - MOON[0] * OUT_W, my_ - MOON[1] * OUT_H) > 110  # keep the moon's face clear
sy, sx = np.nonzero(open_sky)
pick = rng.choice(len(sy), size=min(110, len(sy)), replace=False)
stars = [(sx[i] / OUT_W, sy[i] / OUT_H, float(rng.uniform(0.6, 1.8)), float(rng.uniform(0, 6.283))) for i in pick]


def arr(name, rows_, fmt):
    body = ',\n\t\t'.join('{' + ', '.join(fmt(v) for v in row) + '}' for row in rows_)
    return f'\tstatic final float[][] {name} = {{\n\t\t{body}\n\t}};\n'


f = lambda v: f'{v:.4f}f'
java = f'''package dev.polarclient.gui;

/** Positions baked from the rendered menu background (normalised 0..1 screen coordinates). Generated. */
final class BackgroundData {{
\tprivate BackgroundData() {{
\t}}

\t/** Texture aspect (1920x1080). */
\tstatic final float ASPECT = {OUT_W}f / {OUT_H}f;
\t/** Top edge of the frozen lake: the mirror line for the aurora reflection. */
\tstatic final float LAKE_TOP = {lake_top:.4f}f;
\tstatic final float MOON_X = {MOON[0]}f, MOON_Y = {MOON[1]}f;

\t/** Windows and lanterns: x, y, size in pixels at 1080p. */
{arr('LIGHTS', [(x, y, float(s)) for x, y, s in lights], f)}
\t/** Bright stars that twinkle: x, y, size, phase. */
{arr('STARS', stars, f)}
\t/** Glints on the ice: x, y. */
{arr('SPARKLES', sparkles, f)}}}
'''
open(JAVA, 'w').write(java)

# preview composite
prev = np.clip(scene_out[..., :3] + sky_out * (1 - scene_out[..., 3:4]), 0, 1)
Image.fromarray((prev * 255).astype(np.uint8)).save(os.path.join(SRC, 'composite.png'))
print('lights', len(lights), 'stars', len(stars), 'sparkles', len(sparkles), 'lake_top', round(lake_top, 3))
for n_ in os.listdir(TEX):
    print(n_, os.path.getsize(os.path.join(TEX, n_)) // 1024, 'KB')
