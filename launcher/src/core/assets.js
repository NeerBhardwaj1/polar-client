'use strict';
// Game assets (sounds, languages, ...) from Mojang's asset index.
const fsp = require('node:fs/promises');
const path = require('node:path');
const { download, downloadAll, isValid } = require('./http');

const RESOURCES_URL = 'https://resources.download.minecraft.net/';

/** Lists the object downloads for an asset index (deduplicated by hash). */
function assetTasks(index, assetsDir) {
  const seen = new Set();
  const tasks = [];
  for (const { hash, size } of Object.values(index.objects)) {
    if (seen.has(hash)) continue;
    seen.add(hash);
    const sub = `${hash.slice(0, 2)}/${hash}`;
    tasks.push({ url: RESOURCES_URL + sub, dest: path.join(assetsDir, 'objects', hash.slice(0, 2), hash), sha1: hash, size });
  }
  return tasks;
}

async function ensureAssets(rootDir, assetIndex, { onProgress } = {}) {
  const assetsDir = path.join(rootDir, 'assets');
  const indexFile = path.join(assetsDir, 'indexes', `${assetIndex.id}.json`);
  try {
    await download(assetIndex.url, indexFile, { sha1: assetIndex.sha1 });
  } catch (e) {
    if (!(await isValid(indexFile))) throw e; // offline is fine if we already have it
  }
  const index = JSON.parse(await fsp.readFile(indexFile, 'utf8'));
  await downloadAll(assetTasks(index, assetsDir), { concurrency: 16, onProgress });
  return { assetsDir, indexId: assetIndex.id };
}

module.exports = { ensureAssets, assetTasks };
