'use strict';
// Lists the screenshots of every version's game folder (F2 in game), newest first.
const fs = require('node:fs');
const fsp = require('node:fs/promises');
const path = require('node:path');
const catalog = require('./catalog');

function dirFor(rootDir, versionId) {
  return path.join(catalog.gameDirFor(rootDir, versionId), 'screenshots');
}

async function list(rootDir) {
  const out = [];
  for (const v of catalog.VERSIONS) {
    const dir = dirFor(rootDir, v.id);
    let names = [];
    try {
      names = await fsp.readdir(dir);
    } catch {
      continue;
    }
    for (const name of names) {
      if (!/\.(png|jpe?g)$/i.test(name)) continue;
      try {
        const st = await fsp.stat(path.join(dir, name));
        if (st.isFile()) out.push({ id: `${v.id}/${name}`, version: v.id, name, size: st.size, mtime: st.mtimeMs });
      } catch {
        // deleted meanwhile
      }
    }
  }
  return out.sort((a, b) => b.mtime - a.mtime);
}

/** Absolute path for an id from {@link list}; null if it isn't a screenshot in a game folder (no ../ tricks). */
function resolve(rootDir, id) {
  if (typeof id !== 'string') return null;
  const slash = id.indexOf('/');
  if (slash < 0) return null;
  const version = id.slice(0, slash), name = id.slice(slash + 1);
  if (!catalog.VERSIONS.some((v) => v.id === version)) return null;
  if (path.basename(name) !== name || !/\.(png|jpe?g)$/i.test(name)) return null;
  const file = path.join(dirFor(rootDir, version), name);
  return fs.existsSync(file) ? file : null;
}

module.exports = { list, resolve, dirFor };
