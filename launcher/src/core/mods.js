'use strict';
// Installs Fabric API, the Polar Client mod and optional performance mods into the instance's mods folder.
// Only files the launcher installed itself are ever removed; your own mods are left alone.
const fs = require('node:fs');
const fsp = require('node:fs/promises');
const path = require('node:path');
const { getJson, download } = require('./http');

const MODRINTH = 'https://api.modrinth.com/v2';

/**
 * Optional mods shown in the launcher, all from Modrinth for Fabric. "performance" is the performance pack; "extras" are
 * other people's mods Polar can add for you. Each is installed only on versions Modrinth has a build of it for, and
 * anything a mod requires (Iris needs Sodium, for example) is installed with it.
 */
const OPTIONAL_MODS = [
  { id: 'sodium', name: 'Sodium', description: 'Modern rendering engine — the biggest FPS boost.', default: true, group: 'performance' },
  { id: 'lithium', name: 'Lithium', description: 'Faster game logic, physics and AI.', default: true, group: 'performance' },
  { id: 'ferrite-core', name: 'FerriteCore', description: 'Cuts memory usage a lot.', default: true, group: 'performance' },
  { id: 'entityculling', name: 'EntityCulling', description: 'Skips drawing entities you can\'t see.', default: true, group: 'performance' },
  { id: 'immediatelyfast', name: 'ImmediatelyFast', description: 'Speeds up HUD, text and entity rendering.', default: true, group: 'performance' },
  { id: 'badoptimizations', name: 'BadOptimizations', description: 'Stops the game redoing the same lighting, sky and fog work every frame (1.21 and newer).', default: true, group: 'performance' },
  { id: 'scalablelux', name: 'ScalableLux', description: 'Faster lighting: fewer hitches while chunks load and blocks change (1.21 and newer).', default: false, group: 'performance' },
  { id: 'modernfix', name: 'ModernFix', description: 'Starts faster and uses less memory (1.16.5).', default: true, group: 'performance' },
  { id: 'lazydfu', name: 'LazyDFU', description: 'Cuts game start time by skipping work it doesn\'t need yet (1.16.5).', default: true, group: 'performance' },
  { id: 'moreculling', name: 'More Culling', description: 'Skips even more hidden blocks and faces.', default: false, group: 'performance' },
  { id: 'dynamic-fps', name: 'Dynamic FPS', description: 'Uses less power while the game is in the background.', default: false, group: 'performance' },
  { id: 'c2me-fabric', name: 'C2ME', description: 'Faster chunk loading on all your CPU cores.', default: false, group: 'performance' },
  { id: 'sodium-extra', name: 'Sodium Extra', description: 'Extra graphics options for Sodium.', default: false, group: 'performance' },
  { id: 'particle-core', name: 'Particle Core', description: 'Draws particles faster and skips the ones you can\'t see (1.21 and newer).', default: false, group: 'performance' },
  { id: 'iris', name: 'Iris Shaders', description: 'Shader packs (installs Sodium too).', default: false, group: 'extras' },
  { id: 'xaeros-minimap', name: 'Xaero\'s Minimap', description: 'A minimap with waypoints. Some servers limit it.', default: false, group: 'extras' },
  { id: 'xaeros-world-map', name: 'Xaero\'s World Map', description: 'A full-screen map of everywhere you\'ve been.', default: false, group: 'extras' },
  { id: 'appleskin', name: 'AppleSkin', description: 'Shows food, saturation and hunger in the HUD.', default: false, group: 'extras' },
  { id: 'jade', name: 'Jade', description: 'Shows what block or mob you\'re looking at.', default: false, group: 'extras' },
  { id: 'emi', name: 'EMI', description: 'Recipe and item viewer in your inventory.', default: false, group: 'extras' },
  { id: 'replaymod', name: 'Replay Mod', description: 'Record your games and replay them from any angle.', default: false, group: 'extras' },
  { id: 'simple-voice-chat', name: 'Simple Voice Chat', description: 'Proximity voice chat (the server needs it too).', default: false, group: 'extras' },
  { id: 'modmenu', name: 'Mod Menu', description: 'Lists installed mods in the game.', default: false, group: 'extras' },
];

const REQUIRED_MODS = [{ id: 'fabric-api', name: 'Fabric API' }];

/** 1.8.9 runs on Legacy Fabric: its API is installed when Modrinth has a build, and there is no performance pack. */
const LEGACY_MODS = [{ id: 'legacy-fabric-api', name: 'Legacy Fabric API' }];

/** Fabric API and the optional mods by Modrinth project id, so required dependencies get readable names in the log. */
const KNOWN_PROJECTS = { P7dR8mSH: 'Fabric API', AANobbMI: 'Sodium', YL57xq9U: 'Iris Shaders' };

function dependencyName(projectId, forFile) {
  return KNOWN_PROJECTS[projectId] || `a library needed by ${forFile}`;
}

/** Newest Modrinth file for a project that supports this game version on the loader (release preferred). */
async function findModrinthFile(slug, gameVersion, loader = 'fabric') {
  const q = new URLSearchParams({ loaders: JSON.stringify([loader]), game_versions: JSON.stringify([gameVersion]) });
  const versions = await getJson(`${MODRINTH}/project/${encodeURIComponent(slug)}/version?${q}`);
  if (!Array.isArray(versions) || versions.length === 0) return null;
  const pick = versions.find((v) => v.version_type === 'release') || versions[0];
  const file = pick.files.find((f) => f.primary) || pick.files[0];
  const deps = (pick.dependencies || []).filter((d) => d && d.dependency_type === 'required' && d.project_id).map((d) => d.project_id);
  // never trust a file name to stay inside the mods folder
  const filename = path.basename(String(file.filename || ''));
  if (!/\.jar$/i.test(filename) || filename !== file.filename) throw new Error(`Modrinth sent an unexpected file name for ${slug}.`);
  return { version: pick.version_number, url: file.url, filename, sha1: file.hashes && file.hashes.sha1, size: file.size, projectId: pick.project_id, deps };
}

/**
 * Brings the mods folder in line with the selection.
 * loader: 'fabric' or 'legacy-fabric'.
 * bundledJar: path to the Polar Client mod jar shipped with the launcher (may be missing in dev);
 * null for versions the mod isn't built for.
 * log(msg): progress/warnings. Returns { installed: [names], skipped: [names] }.
 */
async function syncMods({ gameDir, gameVersion, loader = 'fabric', enabledOptional, bundledJar, polar = true, log = () => {} }) {
  const modsDir = path.join(gameDir, 'mods');
  await fsp.mkdir(modsDir, { recursive: true });
  const stateFile = path.join(modsDir, '.polar-managed.json');
  let managed = [];
  try {
    managed = JSON.parse(await fsp.readFile(stateFile, 'utf8'));
  } catch {
    // first run
  }

  const wanted = loader === 'legacy-fabric'
    ? [...LEGACY_MODS]
    : [...REQUIRED_MODS, ...OPTIONAL_MODS.filter((m) => (enabledOptional || []).includes(m.id))];
  const keep = new Set();
  const installed = [];
  const skipped = [];

  // explicit picks first, then whatever they require (by Modrinth project id), each project once
  const queue = wanted.map((m) => ({ ...m, explicit: true }));
  const done = new Set();
  for (let i = 0; i < queue.length && i < 64; i++) {
    const mod = queue[i];
    if (done.has(mod.id)) continue;
    done.add(mod.id);
    const required = REQUIRED_MODS.some((r) => r.id === mod.id);
    let file;
    try {
      file = await findModrinthFile(mod.id, gameVersion, loader);
    } catch (e) {
      // offline: keep whatever we installed last time
      const previous = managed.find((m) => m.id === mod.id);
      if (previous && fs.existsSync(path.join(modsDir, previous.filename))) {
        keep.add(previous.filename);
        installed.push(mod.name);
        continue;
      }
      if (required) throw new Error(`Couldn't download ${mod.name}: ${e.message}`);
      skipped.push(mod.name);
      continue;
    }
    if (!file) {
      if (required) throw new Error(`${mod.name} isn't available for Minecraft ${gameVersion} yet.`);
      log(`${mod.name} has no build for Minecraft ${gameVersion}, skipping it.`);
      skipped.push(mod.name);
      continue;
    }
    if (file.projectId) {
      if (done.has(`project:${file.projectId}`)) continue; // e.g. Sodium picked and also required by Iris
      done.add(`project:${file.projectId}`);
    }
    await download(file.url, path.join(modsDir, file.filename), { sha1: file.sha1, size: file.size });
    keep.add(file.filename);
    installed.push(`${mod.name} ${file.version}`);
    // older versions stay in `managed` until the cleanup below deletes them
    if (!managed.some((m) => m.filename === file.filename)) managed.push({ id: mod.id, filename: file.filename });
    for (const dep of file.deps || []) {
      if (!done.has(`project:${dep}`)) queue.push({ id: dep, name: dependencyName(dep, file.filename) });
    }
  }

  // Polar Client itself (only on the versions it is built for)
  const polarName = 'polarclient.jar';
  if (!polar) {
    // nothing to add; a stray copy we put here earlier is removed below
  } else if (bundledJar && fs.existsSync(bundledJar)) {
    await fsp.copyFile(bundledJar, path.join(modsDir, polarName));
    keep.add(polarName);
    installed.push('Polar Client');
    if (!managed.some((m) => m.filename === polarName)) managed.push({ id: 'polarclient', filename: polarName });
  } else {
    log('Polar Client mod jar is not bundled (run "npm run bundle-mod" after building the mod). Starting without it.');
    skipped.push('Polar Client');
  }

  // remove older versions / deselected mods that we installed
  for (const m of managed) {
    if (!keep.has(m.filename)) await fsp.rm(path.join(modsDir, m.filename), { force: true });
  }
  managed = managed.filter((m) => keep.has(m.filename));
  await fsp.writeFile(stateFile, JSON.stringify(managed, null, 2));
  return { installed, skipped };
}

module.exports = { OPTIONAL_MODS, REQUIRED_MODS, LEGACY_MODS, findModrinthFile, syncMods };
