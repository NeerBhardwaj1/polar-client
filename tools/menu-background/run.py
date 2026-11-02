import base64
import sys
import time
from playwright.sync_api import sync_playwright

# usage: run.py <out_dir> <mode> <w> <h> [tiles]
out, mode, w, h = sys.argv[1], sys.argv[2], int(sys.argv[3]), int(sys.argv[4])
tiles = int(sys.argv[5]) if len(sys.argv) > 5 else 24
here = __file__.rsplit('/', 1)[0]
with sync_playwright() as p:
    b = p.chromium.launch(args=['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'])
    pg = b.new_page()
    logs = []
    pg.on('console', lambda m: logs.append(m.text))
    pg.on('pageerror', lambda e: logs.append('PAGEERROR ' + str(e)))
    pg.set_default_timeout(0)
    pg.goto(f'file://{here}/index.html')
    print('build ms', pg.evaluate('window.buildMs'), 'info', pg.evaluate('worldInfo()'))
    t = time.time()
    try:
        r = pg.evaluate(f"renderMode('{mode}', {w}, {h}, {tiles})")
    except Exception as e:
        print('ERROR', e, logs[:5])
        raise
    data = base64.b64decode(r['url'].split(',', 1)[1])
    open(f'{out}/{mode}_{w}x{h}.png', 'wb').write(data)
    print(mode, w, h, 'render ms', r['ms'], 'wall', round(time.time() - t, 1), logs[:5])
    b.close()
