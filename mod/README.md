# Polar Client — the mod

One mod, five Minecraft versions. Everything you see — the main menu, the server list, the Right Shift menu, the HUD
editor, all the mods, settings and account switching — is written once in `common/`. Each version folder is a small
Gradle project that builds `common/` together with that version's glue code into its own jar.

| Folder | Minecraft | Loader | Java | Names |
|---|---|---|---|---|
| `26.3/` | 26.3 | Fabric Loader 0.20.1, Fabric API 0.165.0 | 25 | unobfuscated |
| `1.21.11/` | 1.21.11 | Fabric Loader 0.20.1, Fabric API 0.141.6 | 21 | Mojang |
| `1.21.10/` | 1.21.10 | Fabric Loader 0.20.1, Fabric API 0.138.4 | 21 | Mojang |
| `1.16.5/` | 1.16.5 | Fabric Loader 0.20.1, Fabric API 0.42.0 | 8 | Mojang |
| `1.8.9/` | 1.8.9 | Legacy Fabric (Loader 0.18.3), no API needed | 8 | Legacy Yarn |

```bash
cd 1.16.5
./gradlew build        # build/libs/polarclient-mc1.16.5-1.0.0.jar
./gradlew runClient    # play it in a dev client
```

All five build with JDK 25 (the older ones compile for Java 8 or 21). `../build-exe.bat` builds them all and packs them
into the launcher, which installs the right jar for the version you play.

## `common/` (plain Java 8, no Minecraft classes)

- `Polar` — start-up, config, the per-tick logic
- `platform/Platform` — everything the shared code asks of the game (player, world, keys, options, screens, servers,
  account), `ServerBackend` — the saved server list, pinger and LAN worlds
- `gfx/Gfx` — everything the shared code draws with (rectangles, textures, text in Polar's fonts, items, scissor,
  rotation)
- `gui/` — the screens as `View`s: `TitleView` (main menu), `MultiplayerView`, `PolarMenuView` (Right Shift),
  `SettingsView`, `HudEditorView`, `WaypointsView`, `AccountCard`, plus `ui/` (theme, rounded shapes, icons, text field)
- `hooks/` — what the chat and inventory mods do, called from each version's chat and container-screen hooks
  (`ChatHooks`: time stamps, filter, copy, stacking, background; `InventoryHooks`: search box, shulker preview)
- `world/` — waypoints and where they're saved; `MinimapImage` — the minimap's picture, read a few chunks per tick
  and shaded like a vanilla map
- `module/`, `modules/`, `modules/hud/`, `setting/`, `config/` — the mods and their settings
- `hud/HudRenderer` — draws the HUD mods; `account/` — talks to the launcher to switch accounts and to tell it where
  you are for the Discord status
- `src/main/resources` — textures, fonts (TTF), language

## Each version folder (`src/main/java/dev/polarclient/impl`)

- `PolarMod` — entrypoint: `Polar.init(new McPlatform())`, key binds, HUD and event hooks
- `McPlatform` — the `Platform` for that version; `McGfx` — the `Gfx` on that version's renderer;
  `ViewScreen` — shows a `View` as that version's `Screen`; `McServerBackend` — `servers.dat`, pinging, LAN
- `mixin/` — zoom, freelook, fullbright, no hurt cam, window title, swapping in Polar's title and server screens,
  account switching, chat (time stamps, filter, copy, stacking, background), fire, water fog, time and weather,
  particles, container screens (search, shulker preview), the camera for waypoints, and (1.16.5 / 1.8.9) the HUD,
  crosshair, tab list and tooltip hooks Fabric API covers on newer versions; `EntityRenderDispatcherMixin` (and, by
  version, the hitbox renderer mixins) for Hide Far Entities and the Hitboxes mod, which only restyle the game's own
  F3+B hitboxes
- `MapReader` — the minimap's chunk reads (map colour, height and water depth of each column) and its texture
- `Render3d` — the last frame's camera, to place waypoints on screen; `Tints` — Hit Colour and Glint Colour
  (rewrites the hurt-overlay and glint textures; on 1.8.9 the colours the renderers read); `ChatText` — builds a
  chat line with the chat mods' additions
- `src/main/resources` — `fabric.mod.json`, the mixin list, and the font definitions for that version
  (1.8.9 can't load TTF fonts, so `TtfFont` draws Polar's text itself, measured exactly like the newer versions)

To add a mod: extend `Module` (or `TextHudModule` for a text HUD element) in `common/`, add settings with `add(...)`,
and register it in `ModuleManager`. If it needs something new from the game, add a method to `Platform` and implement
it in each version's `McPlatform`.
