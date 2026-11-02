'use strict';
// The Minecraft versions the launcher offers, and where each one keeps its game folder.
const fs = require('node:fs');
const fsp = require('node:fs/promises');
const path = require('node:path');

/**
 * loader: 'fabric' (meta.fabricmc.net) or 'legacy-fabric' (meta.legacyfabric.net, for versions Fabric doesn't support).
 * polar: the Polar Client mod is built for this version (each version has its own jar, see modJarName).
 */
const VERSIONS = [
  { id: '26.3', loader: 'fabric', polar: true, summary: 'Polar Client · Fabric · performance pack' },
  { id: '1.21.11', loader: 'fabric', polar: true, summary: 'Polar Client · Fabric · performance pack' },
  { id: '1.21.10', loader: 'fabric', polar: true, summary: 'Polar Client · Fabric · performance pack' },
  { id: '1.16.5', loader: 'fabric', polar: true, summary: 'Polar Client · Fabric · performance pack' },
  { id: '1.8.9', loader: 'legacy-fabric', polar: true, summary: 'Polar Client · Legacy Fabric' },
];

const DEFAULT_VERSION = '26.3';
const LOADER_NAMES = { fabric: 'Fabric', 'legacy-fabric': 'Legacy Fabric' };

/** The catalog entry for an id; unknown or missing ids fall back to the default version. */
function versionInfo(id) {
  const v = VERSIONS.find((x) => x.id === id) || VERSIONS.find((x) => x.id === DEFAULT_VERSION);
  return { ...v, loaderName: LOADER_NAMES[v.loader] };
}

/** File name of the Polar Client mod jar built for a version (in the launcher's resources/mod folder). */
function modJarName(id) {
  return `polarclient-${versionInfo(id).id}.jar`;
}

/** 26.3 keeps the original "instance" folder so existing worlds, settings and servers stay where they are. */
function gameDirFor(rootDir, id) {
  const v = versionInfo(id);
  return v.id === DEFAULT_VERSION ? path.join(rootDir, 'instance') : path.join(rootDir, 'instances', v.id);
}

/** True once the version's game jar has been downloaded. */
function isInstalled(rootDir, id) {
  return fs.existsSync(path.join(rootDir, 'versions', id, `${id}.jar`));
}

function listVersions(rootDir) {
  return VERSIONS.map((v) => ({ ...versionInfo(v.id), installed: isInstalled(rootDir, v.id) }));
}

/**
 * Creates a version's game folder. A new folder starts with a copy of the 26.3 server list
 * (servers.dat is the same format in every version), so your servers show up everywhere.
 */
async function prepareGameDir(rootDir, id) {
  const dir = gameDirFor(rootDir, id);
  await fsp.mkdir(dir, { recursive: true });
  const servers = path.join(dir, 'servers.dat');
  const main = path.join(gameDirFor(rootDir, DEFAULT_VERSION), 'servers.dat');
  if (dir !== path.dirname(main) && !fs.existsSync(servers) && fs.existsSync(main)) {
    await fsp.copyFile(main, servers).catch(() => {});
  }
  return dir;
}

module.exports = { VERSIONS, DEFAULT_VERSION, LOADER_NAMES, versionInfo, modJarName, gameDirFor, isInstalled, listVersions, prepareGameDir };
