'use strict';
// The launcher's mod browser: searches Modrinth for mods that run on the selected Minecraft version and loader, and
// installs them (with whatever they require) into that version's own mods folder.
//
// Mods added here are recorded in mods/.polar-browser.json so the launcher can list and remove them. The performance
// pack and Polar's own jar are tracked separately (mods.js), and mods you drop into the folder yourself are left alone.
const fs = require('node:fs');
const fsp = require('node:fs/promises');
const path = require('node:path');
const { getJson, download } = require('./http');
const { findModrinthFile, OPTIONAL_MODS } = require('./mods');

const API = 'https://api.modrinth.com/v2';
const STATE_FILE = '.polar-browser.json';
const MANAGED_FILE = '.polar-managed.json';
const SORTS = ['relevance', 'downloads', 'follows', 'newest', 'updated'];
/** Modrinth's mod categories offered as filters, with the label shown in the launcher. */
const CATEGORIES = [
  ['optimization', 'Optimization'], ['utility', 'Utility'], ['decoration', 'Decoration'], ['equipment', 'Equipment'],
  ['game-mechanics', 'Game mechanics'], ['adventure', 'Adventure'], ['magic', 'Magic'], ['mobs', 'Mobs'],
  ['social', 'Social'], ['storage', 'Storage'], ['technology', 'Technology'], ['worldgen', 'World gen'],
  ['transportation', 'Transportation'], ['food', 'Food'], ['library', 'Library'],
];
/** Resource pack and shader categories offered as filters. */
const PACK_CATEGORIES = {
  resourcepack: [
    ['vanilla-like', 'Vanilla-like'], ['simplistic', 'Simplistic'], ['realistic', 'Realistic'], ['themed', 'Themed'], ['combat', 'PvP'],
    ['gui', 'GUI'], ['fonts', 'Fonts'], ['tweaks', 'Tweaks'], ['utility', 'Utility'], ['decoration', 'Decoration'], ['audio', 'Sounds'],
    ['16x', '16x'], ['32x', '32x'], ['64x', '64x'], ['128x', '128x'],
  ],
  shader: [
    ['vanilla-like', 'Vanilla-like'], ['semi-realistic', 'Semi-realistic'], ['realistic', 'Realistic'], ['fantasy', 'Fantasy'], ['cartoon', 'Cartoon'],
    ['potato', 'Potato PC'], ['low', 'Low-end'], ['medium', 'Mid-range'], ['high', 'High-end'], ['shadows', 'Shadows'], ['reflections', 'Reflections'],
    ['path-tracing', 'Path tracing'],
  ],
};
/** Where each kind of Modrinth project goes in a game folder, and which loaders' builds work there. */
const KINDS = {
  resourcepack: { folder: 'resourcepacks', loaders: ['minecraft'], label: 'resource pack' },
  shader: { folder: 'shaderpacks', loaders: ['iris', 'optifine'], label: 'shader pack' },
};
const LOADER_TAGS = new Set(['fabric', 'legacy-fabric', 'forge', 'neoforge', 'quilt', 'liteloader', 'rift', 'modloader', 'minecraft', 'iris', 'optifine', 'canvas', 'vanilla']);
/** Fabric API / Legacy Fabric API: the launcher always installs these itself. */
const BASE_PROJECTS = new Set(['P7dR8mSH', 'fabric-api', 'legacy-fabric-api', '9CJED7xi']);

/**
 * Searches mods that have a build for this game version and loader and work on the client.
 * Returns { total, offset, hits: [{ id, slug, title, description, author, icon, downloads, follows, categories, updated }] }.
 */
async function search({ query = '', gameVersion, loader = 'fabric', kind = 'mod', sort = 'relevance', category = '', offset = 0, limit = 20 }) {
  let facets;
  let cats = CATEGORIES;
  if (KINDS[kind]) {
    facets = [[`project_type:${kind}`], [`versions:${gameVersion}`]];
    if (kind === 'shader') facets.push(['categories:iris', 'categories:optifine']); // packs Iris can load
    cats = PACK_CATEGORIES[kind];
  } else {
    facets = [['project_type:mod'], [`categories:${loader}`], [`versions:${gameVersion}`], ['client_side:required', 'client_side:optional']];
  }
  if (category && cats.some(([id]) => id === category)) facets.push([`categories:${category}`]);
  const q = new URLSearchParams({
    query: String(query || '').slice(0, 100),
    facets: JSON.stringify(facets),
    index: SORTS.includes(sort) ? sort : 'relevance',
    offset: String(Math.max(0, offset | 0)),
    limit: String(Math.min(50, Math.max(1, limit | 0))),
  });
  const r = await getJson(`${API}/search?${q}`);
  const hits = (r && Array.isArray(r.hits) ? r.hits : []).map((h) => ({
    id: h.project_id,
    slug: h.slug,
    title: h.title,
    description: h.description || '',
    author: h.author || '',
    icon: typeof h.icon_url === 'string' && h.icon_url.startsWith('https://') ? h.icon_url : null,
    downloads: h.downloads || 0,
    follows: h.follows || 0,
    categories: (h.display_categories || h.categories || []).filter((c) => !LOADER_TAGS.has(c)),
    updated: h.date_modified || null,
  }));
  return { total: (r && r.total_hits) || 0, offset: (r && r.offset) || 0, hits };
}

async function readJson(file, fallback) {
  try {
    return JSON.parse(await fsp.readFile(file, 'utf8'));
  } catch {
    return fallback;
  }
}

async function readState(modsDir) {
  const s = await readJson(path.join(modsDir, STATE_FILE), []);
  return Array.isArray(s) ? s.filter((e) => e && e.projectId && e.filename) : [];
}

async function writeState(modsDir, state) {
  await fsp.writeFile(path.join(modsDir, STATE_FILE), JSON.stringify(state, null, 2));
}

/** The optional-mod id (performance pack / extras on the Mods page) for a Modrinth project, if it is one. */
function optionalFor(slugOrId) {
  const m = OPTIONAL_MODS.find((o) => o.id === slugOrId || o.projectId === slugOrId);
  return m ? m.id : null;
}

/**
 * Installs a mod and the mods it requires. Mods the launcher already manages (Fabric API, the performance pack and the
 * extras on the Mods page) aren't downloaded twice: they are returned in `enable` so the caller can tick them instead.
 * Returns { installed: [titles], enable: [optional mod ids], entry }.
 */
async function install({ gameDir, gameVersion, loader = 'fabric', project, log = () => {} }) {
  const modsDir = path.join(gameDir, 'mods');
  await fsp.mkdir(modsDir, { recursive: true });
  const state = await readState(modsDir);
  const installed = [];
  const enable = [];

  const own = optionalFor(project.slug) || optionalFor(project.id);
  if (own) return { installed, enable: [own], entry: null };
  if (BASE_PROJECTS.has(project.id) || BASE_PROJECTS.has(project.slug)) return { installed, enable, entry: null };

  const file = await findModrinthFile(project.id || project.slug, gameVersion, loader);
  if (!file) throw new Error(`${project.title || 'This mod'} has no build for Minecraft ${gameVersion} on ${loader === 'legacy-fabric' ? 'Legacy Fabric' : 'Fabric'}.`);

  // required dependencies (one level deep is what Modrinth mods need in practice; their own deps are followed once more)
  const deps = [];
  const seen = new Set([file.projectId]);
  const queue = (file.deps || []).map((id) => ({ id, depth: 1 }));
  while (queue.length) {
    const { id, depth } = queue.shift();
    if (seen.has(id) || BASE_PROJECTS.has(id)) continue;
    seen.add(id);
    let info = null;
    try {
      info = await getJson(`${API}/project/${encodeURIComponent(id)}`);
    } catch {
      // no details: still try to install it by id
    }
    const slug = info && info.slug;
    const opt = optionalFor(slug) || optionalFor(id);
    if (opt) {
      enable.push(opt);
      continue;
    }
    if (state.some((e) => e.projectId === id)) continue; // already added from the browser
    const depFile = await findModrinthFile(id, gameVersion, loader);
    const title = (info && info.title) || slug || id;
    if (!depFile) {
      log(`${title} (needed by ${project.title}) has no build for Minecraft ${gameVersion}; the mod may not start without it.`);
      continue;
    }
    await download(depFile.url, path.join(modsDir, depFile.filename), { sha1: depFile.sha1, size: depFile.size });
    deps.push({ projectId: id, title, filename: depFile.filename, version: depFile.version });
    installed.push(title);
    if (depth < 2) for (const d of depFile.deps || []) queue.push({ id: d, depth: depth + 1 });
  }

  // replace an older copy of the same mod
  const old = state.find((e) => e.projectId === file.projectId);
  if (old && old.filename !== file.filename) await fsp.rm(path.join(modsDir, old.filename), { force: true });
  await download(file.url, path.join(modsDir, file.filename), { sha1: file.sha1, size: file.size });
  installed.unshift(project.title || project.slug);

  const entry = {
    projectId: file.projectId || project.id,
    slug: project.slug || null,
    title: project.title || project.slug || 'Mod',
    icon: project.icon || null,
    author: project.author || '',
    version: file.version,
    filename: file.filename,
    deps: deps.concat(old ? (old.deps || []).filter((d) => !deps.some((n) => n.projectId === d.projectId)) : []),
    installedAt: Date.now(),
  };
  const next = state.filter((e) => e.projectId !== entry.projectId);
  next.push(entry);
  await writeState(modsDir, next);
  return { installed, enable: [...new Set(enable)], entry };
}

/** Removes a mod added from the browser, and the dependencies it brought that nothing else added here still needs. */
async function remove({ gameDir, projectId }) {
  const modsDir = path.join(gameDir, 'mods');
  const state = await readState(modsDir);
  const entry = state.find((e) => e.projectId === projectId);
  if (!entry) return { removed: false };
  const rest = state.filter((e) => e !== entry);
  await fsp.rm(path.join(modsDir, entry.filename), { force: true });
  for (const dep of entry.deps || []) {
    const stillNeeded = rest.some((e) => e.projectId === dep.projectId || (e.deps || []).some((d) => d.filename === dep.filename));
    if (!stillNeeded) await fsp.rm(path.join(modsDir, dep.filename), { force: true });
  }
  await writeState(modsDir, rest);
  return { removed: true, title: entry.title };
}

/**
 * What's in a version's mods folder: mods added from the browser (`added`) and jars you put there yourself (`own`).
 * The performance pack and Polar Client's jar aren't listed (they're on the Mods page).
 */
async function list({ gameDir }) {
  const modsDir = path.join(gameDir, 'mods');
  if (!fs.existsSync(modsDir)) return { added: [], own: [] };
  const state = await readState(modsDir);
  const managed = await readJson(path.join(modsDir, MANAGED_FILE), []);
  const known = new Set([...state.map((e) => e.filename), ...state.flatMap((e) => (e.deps || []).map((d) => d.filename)),
    ...(Array.isArray(managed) ? managed.map((m) => m.filename) : [])]);
  const added = state.filter((e) => fs.existsSync(path.join(modsDir, e.filename)));
  const own = (await fsp.readdir(modsDir)).filter((f) => /\.jar$/i.test(f) && !known.has(f)).sort();
  return { added, own };
}

/** Mods added from the browser that have a newer build for this version: [{ projectId, title, current, latest }]. */
async function checkUpdates({ gameDir, gameVersion, loader = 'fabric' }) {
  const modsDir = path.join(gameDir, 'mods');
  const state = await readState(modsDir);
  const out = [];
  for (const e of state) {
    try {
      const file = await findModrinthFile(e.projectId, gameVersion, loader);
      if (file && file.filename !== e.filename) out.push({ projectId: e.projectId, slug: e.slug, title: e.title, icon: e.icon, author: e.author, current: e.version, latest: file.version });
    } catch {
      // offline or gone from Modrinth: no update to offer
    }
  }
  return out;
}

/**
 * Mod profiles: a saved set of browser mods (and performance-pack picks) for one version. Applying one removes the
 * browser mods it doesn't list and installs the ones it does. Returns { installed, removed, failed, enable }.
 */
async function applyProfile({ gameDir, gameVersion, loader = 'fabric', profile, log = () => {} }) {
  const modsDir = path.join(gameDir, 'mods');
  const state = await readState(modsDir);
  const want = new Set((profile.mods || []).map((m) => m.projectId));
  const removed = [];
  for (const e of state) {
    if (!want.has(e.projectId)) {
      await remove({ gameDir, projectId: e.projectId });
      removed.push(e.title);
    }
  }
  const have = new Set((await readState(modsDir)).map((e) => e.projectId));
  const installed = [];
  const failed = [];
  const enable = [];
  for (const m of profile.mods || []) {
    if (have.has(m.projectId)) continue;
    try {
      const r = await install({ gameDir, gameVersion, loader, project: { id: m.projectId, slug: m.slug, title: m.title, icon: m.icon, author: m.author }, log });
      installed.push(m.title);
      enable.push(...r.enable);
    } catch (e) {
      failed.push(`${m.title}: ${e.message}`);
    }
  }
  return { installed, removed, failed, enable };
}

// ---------------------------------------------------------------- resource packs and shader packs

/** Newest build of a pack for this game version (release preferred). Null when there is none. */
async function findPackFile(idOrSlug, gameVersion, kind) {
  const q = new URLSearchParams({ loaders: JSON.stringify(KINDS[kind].loaders), game_versions: JSON.stringify([gameVersion]) });
  const versions = await getJson(`${API}/project/${encodeURIComponent(idOrSlug)}/version?${q}`);
  if (!Array.isArray(versions) || versions.length === 0) return null;
  const pick = versions.find((v) => v.version_type === 'release') || versions[0];
  const file = (pick.files || []).find((f) => f.primary) || (pick.files || [])[0];
  if (!file) return null;
  const filename = path.basename(String(file.filename || ''));
  // never trust a file name to stay inside the folder
  if (!/\.zip$/i.test(filename) || filename !== file.filename) throw new Error(`Modrinth sent an unexpected file name for ${idOrSlug}.`);
  return { version: pick.version_number, url: file.url, filename, sha1: file.hashes && file.hashes.sha1, size: file.size, projectId: pick.project_id };
}

/** Downloads a resource pack or shader pack into the version's resourcepacks / shaderpacks folder. Returns { entry, replaced }. */
async function installPack({ gameDir, gameVersion, kind, project }) {
  if (!KINDS[kind]) throw new Error('Unknown kind of pack.');
  const dir = path.join(gameDir, KINDS[kind].folder);
  await fsp.mkdir(dir, { recursive: true });
  const state = await readState(dir);
  const file = await findPackFile(project.id || project.slug, gameVersion, kind);
  if (!file) throw new Error(`${project.title || 'This pack'} has no build for Minecraft ${gameVersion}.`);
  const old = state.find((e) => e.projectId === (file.projectId || project.id));
  if (old && old.filename !== file.filename) await fsp.rm(path.join(dir, old.filename), { force: true });
  await download(file.url, path.join(dir, file.filename), { sha1: file.sha1, size: file.size });
  const entry = {
    projectId: file.projectId || project.id, slug: project.slug || null, title: project.title || project.slug || 'Pack', icon: project.icon || null,
    author: project.author || '', version: file.version, filename: file.filename, installedAt: Date.now(),
  };
  await writeState(dir, state.filter((e) => e.projectId !== entry.projectId).concat([entry]));
  return { entry, replaced: old ? old.filename : null };
}

async function removePack({ gameDir, kind, projectId }) {
  if (!KINDS[kind]) throw new Error('Unknown kind of pack.');
  const dir = path.join(gameDir, KINDS[kind].folder);
  const state = await readState(dir);
  const entry = state.find((e) => e.projectId === projectId);
  if (!entry) return { removed: false };
  await fsp.rm(path.join(dir, entry.filename), { force: true });
  await writeState(dir, state.filter((e) => e !== entry));
  return { removed: true, title: entry.title, filename: entry.filename };
}

/** Packs added from the browser, and pack files you put in the folder yourself, for each kind. */
async function listPacks({ gameDir }) {
  const out = {};
  for (const kind of Object.keys(KINDS)) {
    const dir = path.join(gameDir, KINDS[kind].folder);
    if (!fs.existsSync(dir)) {
      out[kind] = { added: [], own: [] };
      continue;
    }
    const state = await readState(dir);
    const known = new Set(state.map((e) => e.filename));
    const added = state.filter((e) => fs.existsSync(path.join(dir, e.filename)));
    const own = (await fsp.readdir(dir)).filter((f) => /\.zip$/i.test(f) && !known.has(f)).sort();
    out[kind] = { added, own };
  }
  return out;
}

module.exports = { search, install, remove, list, checkUpdates, applyProfile, readState, installPack, removePack, listPacks, findPackFile,
  CATEGORIES, PACK_CATEGORIES, KINDS, SORTS, STATE_FILE };
