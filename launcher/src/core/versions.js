'use strict';
// Resolves the vanilla + Fabric version JSONs and turns them into concrete files to download.
const path = require('node:path');
const { getJson, getJsonCached } = require('./http');
const { isAllowed } = require('./rules');

const MANIFEST_URL = 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json';
const FABRIC_META = 'https://meta.fabricmc.net/v2';
const LIBRARIES_URL = 'https://libraries.minecraft.net/';

/** Fabric for modern versions, Legacy Fabric (same loader, its own mappings) for 1.8.9 and older. */
const LOADERS = {
  fabric: { name: 'Fabric', meta: FABRIC_META, cachePrefix: 'fabric' },
  'legacy-fabric': { name: 'Legacy Fabric', meta: 'https://meta.legacyfabric.net/v2', cachePrefix: 'legacyfabric' },
};
const loaderOf = (loader) => LOADERS[loader] || LOADERS.fabric;

/** "group:artifact:version[:classifier][@ext]" -> "group/path/artifact/version/artifact-version[-classifier].ext" */
function mavenPath(name) {
  let [coords, ext = 'jar'] = name.split('@');
  const parts = coords.split(':');
  if (parts.length < 3) throw new Error(`Bad library name: ${name}`);
  const [group, artifact, version, classifier] = parts;
  const file = `${artifact}-${version}${classifier ? `-${classifier}` : ''}.${ext}`;
  return [...group.split('.'), artifact, version, file].join('/');
}

/** Key used to de-duplicate libraries: group:artifact[:classifier] (version ignored). */
function libraryKey(name) {
  const [group, artifact, , classifier] = name.split('@')[0].split(':');
  return [group, artifact, classifier].filter(Boolean).join(':');
}

async function fetchVanilla(versionId, cacheDir) {
  const manifest = await getJsonCached(MANIFEST_URL, path.join(cacheDir, 'version_manifest_v2.json'));
  const entry = manifest.versions.find((v) => v.id === versionId);
  if (!entry) throw new Error(`Minecraft ${versionId} was not found in Mojang's version list.`);
  return getJsonCached(entry.url, path.join(cacheDir, versionId, `${versionId}.json`));
}

async function latestStableLoader(gameVersion, cacheDir, loader = 'fabric') {
  const l = loaderOf(loader);
  const list = await getJsonCached(`${l.meta}/versions/loader/${encodeURIComponent(gameVersion)}`,
    path.join(cacheDir, `${l.cachePrefix}-loaders-${gameVersion}.json`));
  if (!Array.isArray(list) || list.length === 0) throw new Error(`${l.name} doesn't support Minecraft ${gameVersion} yet.`);
  const stable = list.find((e) => e.loader && e.loader.stable) || list[0];
  return stable.loader.version;
}

async function fetchFabricProfile(gameVersion, loaderVersion, cacheDir, loader = 'fabric') {
  const l = loaderOf(loader);
  const url = `${l.meta}/versions/loader/${encodeURIComponent(gameVersion)}/${encodeURIComponent(loaderVersion)}/profile/json`;
  return getJsonCached(url, path.join(cacheDir, `${l.cachePrefix}-loader-${loaderVersion}-${gameVersion}.json`));
}

/**
 * Merges a child profile (Fabric) onto its parent (vanilla) like the official launcher's "inheritsFrom":
 * child libraries come first and win over the parent's copies of the same artifact.
 * Versions before 1.13 have a single "minecraftArguments" string instead of "arguments"; it is kept as-is.
 */
function mergeProfiles(parent, child) {
  const seen = new Set();
  const libraries = [];
  for (const lib of [...(child.libraries || []), ...(parent.libraries || [])]) {
    const key = libraryKey(lib.name);
    if (seen.has(key)) continue;
    seen.add(key);
    libraries.push(lib);
  }
  const args = (side) => [...((parent.arguments || {})[side] || []), ...((child.arguments || {})[side] || [])];
  const merged = {
    ...parent,
    id: child.id || parent.id,
    mainClass: child.mainClass || parent.mainClass,
    libraries,
    arguments: {
      game: args('game'),
      jvm: args('jvm'),
      'default-user-jvm': ((parent.arguments || {})['default-user-jvm']) || [],
    },
  };
  const legacyArgs = child.minecraftArguments || parent.minecraftArguments;
  if (legacyArgs) merged.minecraftArguments = legacyArgs;
  return merged;
}

/** "group:artifact:version[@ext]" + classifier -> "group:artifact:version:classifier[@ext]" */
function withClassifier(name, classifier) {
  const [coords, ext] = name.split('@');
  return `${coords.split(':').slice(0, 3).join(':')}:${classifier}${ext ? `@${ext}` : ''}`;
}

/**
 * Library files this machine needs: [{ name, path, url, sha1, size, native?, extract? }].
 * Handles Mojang-style "downloads.artifact", plain Maven libraries (Fabric) and pre-1.19 "natives" classifiers,
 * both Mojang-style (downloads.classifiers) and Maven-style (Legacy Fabric's LWJGL 2). A library with "natives"
 * and no main artifact (lwjgl-platform) only contributes its natives jar.
 */
function resolveLibraries(profile, env) {
  const out = [];
  const arch = env.arch === 'x86' ? '32' : '64';
  for (const lib of profile.libraries || []) {
    if (!isAllowed(lib.rules, env)) continue;
    const art = lib.downloads && lib.downloads.artifact;
    const base = (lib.url || LIBRARIES_URL).replace(/\/?$/, '/');
    if (art && art.path) {
      out.push({ name: lib.name, path: art.path, url: art.url, sha1: art.sha1, size: art.size });
    } else if (!lib.downloads && !lib.natives) {
      const p = mavenPath(lib.name);
      out.push({ name: lib.name, path: p, url: base + p, sha1: lib.sha1, size: lib.size });
    }
    // natives jars, unpacked into the natives folder before launch (modern versions list natives as normal libraries)
    const key = lib.natives && lib.natives[env.os];
    if (!key) continue;
    const classifier = key.replace('${arch}', arch);
    const extract = lib.extract || null;
    if (lib.downloads && lib.downloads.classifiers) {
      const native = lib.downloads.classifiers[classifier];
      if (native) out.push({ name: `${lib.name}:natives`, path: native.path, url: native.url, sha1: native.sha1, size: native.size, native: true, extract });
    } else if (!lib.downloads) {
      const p = mavenPath(withClassifier(lib.name, classifier));
      out.push({ name: `${lib.name}:natives`, path: p, url: base + p, native: true, extract });
    }
  }
  return out;
}

module.exports = { MANIFEST_URL, LOADERS, mavenPath, libraryKey, withClassifier, fetchVanilla, latestStableLoader, fetchFabricProfile, mergeProfiles, resolveLibraries, getJson };
