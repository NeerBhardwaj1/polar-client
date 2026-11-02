'use strict';
// The skin library: skins you've saved in the launcher (PNG files plus a small index), to switch between with one click.
// Kept in <launcher folder>/skins, shared by every account on this computer.
const fs = require('node:fs');
const fsp = require('node:fs/promises');
const path = require('node:path');
const crypto = require('node:crypto');

const MAX_SKINS = 60;

/** Throws unless the bytes are a Minecraft skin: a PNG of 64x64 (or the old 64x32), at most 24 KB. */
function checkSkin(png) {
  if (!Buffer.isBuffer(png) || png.length < 24 || png.readUInt32BE(0) !== 0x89504e47) throw new Error("That file isn't a PNG image.");
  if (png.length > 24 * 1024) throw new Error('That PNG is too big for a skin (24 KB at most).');
  const w = png.readUInt32BE(16), h = png.readUInt32BE(20);
  if (w !== 64 || (h !== 64 && h !== 32)) throw new Error(`Skins are 64×64 pixels (this image is ${w}×${h}).`);
}

class SkinLibrary {
  constructor(dir) {
    this.dir = dir;
    this.index = path.join(dir, 'library.json');
  }

  async entries() {
    try {
      const list = JSON.parse(await fsp.readFile(this.index, 'utf8'));
      return Array.isArray(list) ? list.filter((e) => e && /^[a-f0-9]{16}$/.test(e.id) && fs.existsSync(this.file(e.id))) : [];
    } catch {
      return [];
    }
  }

  file(id) {
    if (!/^[a-f0-9]{16}$/.test(String(id))) throw new Error('Unknown skin.');
    return path.join(this.dir, `${id}.png`);
  }

  async save(list) {
    await fsp.mkdir(this.dir, { recursive: true });
    await fsp.writeFile(this.index, JSON.stringify(list, null, 2));
  }

  /** Every skin with its image as a data: URL for the page, newest first. */
  async list() {
    const out = [];
    for (const e of await this.entries()) {
      const png = await fsp.readFile(this.file(e.id));
      out.push({ ...e, data: `data:image/png;base64,${png.toString('base64')}` });
    }
    return out.sort((a, b) => b.addedAt - a.addedAt);
  }

  /** Adds a skin. The same image twice isn't stored twice: the existing one is renamed instead. */
  async add(png, { name, variant }) {
    checkSkin(png);
    const list = await this.entries();
    const id = crypto.createHash('sha1').update(png).digest('hex').slice(0, 16);
    const clean = String(name || '').trim().replace(/\s+/g, ' ').slice(0, 32) || `Skin ${list.length + 1}`;
    const v = variant === 'slim' ? 'slim' : 'classic';
    const existing = list.find((e) => e.id === id);
    if (existing) {
      existing.name = clean;
      existing.variant = v;
    } else {
      if (list.length >= MAX_SKINS) throw new Error(`The library holds ${MAX_SKINS} skins. Delete one first.`);
      await fsp.mkdir(this.dir, { recursive: true });
      await fsp.writeFile(this.file(id), png);
      list.push({ id, name: clean, variant: v, addedAt: Date.now() });
    }
    await this.save(list);
    return id;
  }

  async update(id, patch) {
    const list = await this.entries();
    const e = list.find((x) => x.id === id);
    if (!e) throw new Error('That skin is gone from the library.');
    if (patch.name !== undefined) e.name = String(patch.name).trim().replace(/\s+/g, ' ').slice(0, 32) || e.name;
    if (patch.variant !== undefined) e.variant = patch.variant === 'slim' ? 'slim' : 'classic';
    await this.save(list);
  }

  async remove(id) {
    const list = await this.entries();
    await fsp.rm(this.file(id), { force: true });
    await this.save(list.filter((e) => e.id !== id));
  }
}

/** Turns a data: URL (from the profile API) back into PNG bytes. */
function pngFromDataUrl(url) {
  const m = /^data:image\/png;base64,([A-Za-z0-9+/=]+)$/.exec(String(url || ''));
  if (!m) throw new Error("Couldn't read that skin.");
  return Buffer.from(m[1], 'base64');
}

module.exports = { SkinLibrary, checkSkin, pngFromDataUrl, MAX_SKINS };
