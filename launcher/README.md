# Polar Client — Launcher

Electron launcher: Microsoft sign-in, Mojang's Java runtimes, Minecraft 26.3, 1.21.11, 1.21.10, 1.16.5 (Fabric) and 1.8.9 (Legacy Fabric), the Polar Client mod built for each, and a performance pack from Modrinth.

```bash
npm install
npm test             # launcher logic tests (real 26.3 version data in test/fixtures)
npm run bundle-mod   # copy ../mod/<version>/build/libs/polarclient-*.jar into resources/mod/polarclient-<version>.jar
npm start            # run
npm run dist         # installer for this OS in dist/
```

- `src/main.js` — window, Microsoft sign-in window, IPC
- `src/preload.js` — the only API the UI can call
- `src/core/` — `auth` (Microsoft/Xbox/Minecraft), `versions` (Mojang + Fabric profiles), `rules`, `java` (Mojang runtime), `assets`, `mods` (Modrinth + bundled jar), `launch` (command line), `game` (orchestration), `store` (settings + encrypted accounts), `http` (downloads with SHA-1 checks)
- `src/renderer/` — UI (plain HTML/CSS/JS)

No third-party runtime dependencies — only Electron itself.
