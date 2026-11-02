# Menu background generator

The animated main-menu background is built from pre-rendered layers plus live effects drawn in game by
`client-mod/.../gui/MenuBackground.java`. These scripts make the layers.

- `index.html` builds the snowy voxel world (mountains, pine forest, villages, frozen lake, ice blocks) and
  ray-traces it with WebGL2 in three modes: `scene` (landscape with a transparent sky), `sky` (sky, clouds,
  faint stars, moon halo) and `aurora` (a horizontally tileable aurora strip).
- `run.py` renders one mode in headless Chromium (Playwright).
- `post.py` grades and downsizes the renders into `textures/gui/menu/*.png` (sky, scene, aurora, glow, moon)
  and writes `BackgroundData.java` with the positions of the window lights, stars, ice glints, moon and the
  lake edge that the live effects use.

```bash
pip install playwright numpy scipy pillow && python -m playwright install chromium
mkdir -p out
python run.py out scene 2560 1440 48     # slow without a GPU (software WebGL)
python run.py out sky 2560 1440 8
python run.py out aurora 2048 512 8
python post.py out ../../client-mod
```

Change the world in `buildWorld()` and the camera in `CAM` (both in `index.html`). The moon position is set in
two places: `screenDir(...)` in `renderMode` and `MOON` in `post.py`.
