'use strict';
// Which resource packs are switched on (options.txt) and which shader pack Iris uses (config/iris.properties), so a
// pack installed from the launcher is ready the next time the game starts. Only touched while the game is closed:
// Minecraft writes options.txt back when it exits.
const fs = require('node:fs');
const fsp = require('node:fs/promises');
const path = require('node:path');

/** 1.8.9 lists packs by file name; newer versions say "file/<name>" next to "vanilla". */
const packId = (filename, legacy) => (legacy ? filename : `file/${filename}`);

async function readLines(file) {
  try {
    return (await fsp.readFile(file, 'utf8')).split(/\r?\n/);
  } catch {
    return null;
  }
}

function parseList(value) {
  try {
    const list = JSON.parse(value);
    return Array.isArray(list) ? list.map(String) : [];
  } catch {
    return [];
  }
}

/** File names of the resource packs switched on in options.txt (in priority order, lowest first). */
async function enabledResourcePacks(gameDir, legacy) {
  const lines = await readLines(path.join(gameDir, 'options.txt'));
  const line = lines && lines.find((l) => l.startsWith('resourcePacks:'));
  if (!line) return [];
  return parseList(line.slice('resourcePacks:'.length))
    .filter((id) => (legacy ? true : id.startsWith('file/')))
    .map((id) => (legacy ? id : id.slice(5)));
}

/**
 * Switches a resource pack on (on top of the others) or off in options.txt. Returns false when the version has no
 * options.txt yet (the game hasn't run): the pack is still in the folder and can be switched on in game.
 */
async function setResourcePack(gameDir, filename, on, legacy) {
  const file = path.join(gameDir, 'options.txt');
  const lines = await readLines(file);
  if (!lines) return false;
  const id = packId(filename, legacy);
  let i = lines.findIndex((l) => l.startsWith('resourcePacks:'));
  let list = i >= 0 ? parseList(lines[i].slice('resourcePacks:'.length)) : legacy ? [] : ['vanilla'];
  list = list.filter((x) => x !== id);
  if (on) list.push(id);
  const line = `resourcePacks:${JSON.stringify(list)}`;
  if (i >= 0) lines[i] = line;
  else {
    i = lines.length && lines[lines.length - 1] === '' ? lines.length - 1 : lines.length;
    lines.splice(i, 0, line);
  }
  // a pack the game flagged as incompatible is listed there too; drop it so the game asks again
  const inc = lines.findIndex((l) => l.startsWith('incompatibleResourcePacks:'));
  if (inc >= 0) lines[inc] = `incompatibleResourcePacks:${JSON.stringify(parseList(lines[inc].slice(26)).filter((x) => x !== id))}`;
  await fsp.writeFile(file, lines.join('\n'));
  return true;
}

function readProperties(text) {
  const out = new Map();
  for (const line of String(text || '').split(/\r?\n/)) {
    const m = line.match(/^\s*([^#!=:\s][^=:]*?)\s*[=:]\s*(.*)$/);
    if (m) out.set(m[1], m[2]);
  }
  return out;
}

/** The shader pack Iris will load, or null when shaders are off. */
async function currentShader(gameDir) {
  let text;
  try {
    text = await fsp.readFile(path.join(gameDir, 'config', 'iris.properties'), 'utf8');
  } catch {
    return null;
  }
  const props = readProperties(text);
  if (props.get('enableShaders') === 'false') return null;
  const name = props.get('shaderPack');
  return name ? name.replace(/\\(.)/g, '$1') : null;
}

/** Points Iris at a shader pack (or turns shaders off with null), keeping Iris's other settings. */
async function setShader(gameDir, filename) {
  const file = path.join(gameDir, 'config', 'iris.properties');
  await fsp.mkdir(path.dirname(file), { recursive: true });
  const lines = fs.existsSync(file) ? (await fsp.readFile(file, 'utf8')).split(/\r?\n/) : ['#Iris settings, written by Polar Client'];
  const put = (key, value) => {
    const i = lines.findIndex((l) => new RegExp(`^\\s*${key}\\s*[=:]`).test(l));
    const line = `${key}=${value}`;
    if (i >= 0) lines[i] = line;
    else lines.push(line);
  };
  if (filename) {
    // .properties escaping for the characters pack names actually contain
    put('shaderPack', filename.replace(/\\/g, '\\\\').replace(/([:=#!])/g, '\\$1'));
    put('enableShaders', 'true');
  } else {
    put('enableShaders', 'false');
  }
  await fsp.writeFile(file, lines.filter((l, i) => l !== '' || i < lines.length - 1).join('\n') + '\n');
}

module.exports = { enabledResourcePacks, setResourcePack, currentShader, setShader, readProperties };
