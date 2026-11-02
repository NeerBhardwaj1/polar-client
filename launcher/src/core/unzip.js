'use strict';
// A small zip reader for unpacking native libraries (.dll/.so/.dylib) from the "natives" jars of older versions.
// Handles stored and deflated entries, which is all jar files use.
const fsp = require('node:fs/promises');
const path = require('node:path');
const zlib = require('node:zlib');

const EOCD = 0x06054b50;
const CENTRAL = 0x02014b50;
const LOCAL = 0x04034b50;

/** Lists the entries of a zip held in memory: [{ name, method, compressedSize, size, localOffset }]. */
function listEntries(buf) {
  let eocd = -1;
  for (let i = buf.length - 22; i >= Math.max(0, buf.length - 22 - 0xffff); i--) {
    if (buf.readUInt32LE(i) === EOCD) {
      eocd = i;
      break;
    }
  }
  if (eocd < 0) throw new Error('Not a zip file');
  const count = buf.readUInt16LE(eocd + 10);
  let p = buf.readUInt32LE(eocd + 16);
  const entries = [];
  for (let n = 0; n < count; n++) {
    if (buf.readUInt32LE(p) !== CENTRAL) throw new Error('Broken zip directory');
    const nameLen = buf.readUInt16LE(p + 28);
    const extraLen = buf.readUInt16LE(p + 30);
    const commentLen = buf.readUInt16LE(p + 32);
    entries.push({
      method: buf.readUInt16LE(p + 10),
      compressedSize: buf.readUInt32LE(p + 20),
      size: buf.readUInt32LE(p + 24),
      localOffset: buf.readUInt32LE(p + 42),
      name: buf.toString('utf8', p + 46, p + 46 + nameLen),
    });
    p += 46 + nameLen + extraLen + commentLen;
  }
  return entries;
}

function readEntry(buf, e) {
  if (buf.readUInt32LE(e.localOffset) !== LOCAL) throw new Error(`Broken zip entry ${e.name}`);
  const start = e.localOffset + 30 + buf.readUInt16LE(e.localOffset + 26) + buf.readUInt16LE(e.localOffset + 28);
  const data = buf.subarray(start, start + e.compressedSize);
  if (e.method === 0) return data;
  if (e.method === 8) return zlib.inflateRawSync(data);
  throw new Error(`Unsupported compression in ${e.name}`);
}

/**
 * Extracts every file of a zip into destDir, skipping names that start with one of `exclude`
 * (version JSONs exclude "META-INF/"). Returns the extracted relative paths.
 */
async function extractZip(file, destDir, { exclude = [] } = {}) {
  const buf = await fsp.readFile(file);
  const root = path.resolve(destDir);
  const out = [];
  for (const e of listEntries(buf)) {
    if (e.name.endsWith('/') || exclude.some((x) => e.name.startsWith(x))) continue;
    const target = path.resolve(root, e.name);
    if (target !== root && !target.startsWith(root + path.sep)) continue; // never write outside destDir
    await fsp.mkdir(path.dirname(target), { recursive: true });
    await fsp.writeFile(target, readEntry(buf, e));
    out.push(e.name);
  }
  return out;
}

module.exports = { listEntries, extractZip };
