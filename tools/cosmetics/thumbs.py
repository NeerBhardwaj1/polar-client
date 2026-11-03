"""Renders the wardrobe thumbnails (server/public/cosmetics/thumbs/<id>.png) with the same 3D viewer the launcher uses.
Run after build.py whenever cosmetics change. Needs Playwright with Chromium:

    pip install playwright && python -m playwright install chromium
    python tools/cosmetics/thumbs.py            # every cosmetic
    python tools/cosmetics/thumbs.py cape_aurora # just these
"""
import base64
import functools
import http.server
import io
import json
import os
import sys
import threading

from PIL import Image
from playwright.sync_api import sync_playwright

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
OUT = os.path.join(ROOT, 'server', 'public', 'cosmetics')
SKIN = os.path.join(ROOT, 'launcher', 'src', 'renderer', 'assets', 'mannequin.png')
# camera per kind: yaw:pitch:zoom, and the height it looks at
CAMERAS = {'cape': ('200:6:1.55', 0.92), 'wings': ('205:10:0.86', 1.32), 'head': ('28:14:2.3', 1.74), 'back': ('205:8:1.4', 1.0), 'aura': ('200:8:1.0', 1.0)}
SIZE = (320, 400)
PER_PAGE = 12  # browsers allow about 16 WebGL canvases per page


def serve():
    handler = functools.partial(http.server.SimpleHTTPRequestHandler, directory=ROOT)
    handler.log_message = lambda *a: None
    srv = http.server.ThreadingHTTPServer(('127.0.0.1', 0), handler)
    threading.Thread(target=srv.serve_forever, daemon=True).start()
    return srv


def main(only):
    catalog = json.load(open(os.path.join(OUT, 'catalog.json')))
    items = [i for i in catalog['items'] if not only or i['id'] in only]
    skin = 'data:image/png;base64,' + base64.b64encode(open(SKIN, 'rb').read()).decode()
    os.makedirs(os.path.join(OUT, 'thumbs'), exist_ok=True)
    srv = serve()
    port = srv.server_address[1]
    with sync_playwright() as p:
        browser = p.chromium.launch(args=['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'])
        for kind, (views, ty) in CAMERAS.items():
            group = [i for i in items if i['type'] == kind]
            for start in range(0, len(group), PER_PAGE):
                chunk = group[start:start + PER_PAGE]
                page = browser.new_page(viewport={'width': 1400, 'height': 900})
                page.add_init_script(f'window.SKIN = {json.dumps(skin)};')
                sets = ';'.join(i['id'] for i in chunk)
                page.goto(f'http://127.0.0.1:{port}/tools/cosmetics/preview/index.html?sets={sets}&views={views}&size={SIZE[0]}&ty={ty}&boost={2.5 if kind == 'aura' else 1}')
                page.wait_for_function('window.ready === true', timeout=60000)
                page.wait_for_timeout(300)
                page.evaluate('window.stepAll(1/30, 75)')
                urls = page.evaluate('[...document.querySelectorAll("canvas")].map(c => c.toDataURL("image/png"))')
                page.close()
                for item, url in zip(chunk, urls):
                    im = Image.open(io.BytesIO(base64.b64decode(url.split(',')[1]))).convert('RGBA')
                    im = im.resize((SIZE[0] // 2, SIZE[1] // 2), Image.LANCZOS)
                    im.save(os.path.join(OUT, 'thumbs', f"{item['id']}.png"), optimize=True)
                    print(f"  {item['id']}")
        browser.close()
    srv.shutdown()
    print(f'{len(items)} thumbnails -> {os.path.relpath(os.path.join(OUT, "thumbs"), ROOT)}')


if __name__ == '__main__':
    main(set(sys.argv[1:]))
