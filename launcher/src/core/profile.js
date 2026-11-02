'use strict';
// Skins and capes of a Microsoft account, through Minecraft's own profile API (the same calls minecraft.net uses).
const fsp = require('node:fs/promises');
const { request } = require('./http');

const API = 'https://api.minecraftservices.com/minecraft/profile';

const auth = (token) => ({ Authorization: `Bearer ${token}`, Accept: 'application/json' });

/** Downloads a texture and returns it as a data: URL so the launcher page can draw it without remote access. */
async function textureDataUrl(url) {
  if (!url || !/^https:\/\/textures\.minecraft\.net\//.test(url.replace(/^http:/, 'https:'))) return null;
  const res = await request(url.replace(/^http:/, 'https:'), { timeout: 15000 });
  const buf = Buffer.from(await res.arrayBuffer());
  return `data:image/png;base64,${buf.toString('base64')}`;
}

function shape(p) {
  const skins = Array.isArray(p.skins) ? p.skins : [];
  const capes = Array.isArray(p.capes) ? p.capes : [];
  const active = skins.find((s) => s.state === 'ACTIVE') || skins[0] || null;
  return {
    id: p.id,
    name: p.name,
    skin: active ? { url: active.url, variant: String(active.variant || 'CLASSIC').toUpperCase() } : null,
    capes: capes.map((c) => ({ id: c.id, alias: c.alias || 'Cape', url: c.url, active: c.state === 'ACTIVE' })),
  };
}

async function getProfile(token) {
  const res = await request(API, { headers: auth(token), timeout: 15000 });
  const p = shape(await res.json());
  if (p.skin) p.skin.data = await textureDataUrl(p.skin.url).catch(() => null);
  for (const c of p.capes) c.data = await textureDataUrl(c.url).catch(() => null);
  return p;
}

/** Uploads a 64x64 (or legacy 64x32) PNG skin. variant: 'classic' | 'slim'. */
async function uploadSkin(token, filePath, variant) {
  const png = await fsp.readFile(filePath);
  if (png.length > 24 * 1024 || png.readUInt32BE(0) !== 0x89504e47) throw new Error('Choose a Minecraft skin PNG (64×64).');
  const w = png.readUInt32BE(16), h = png.readUInt32BE(20);
  if (w !== 64 || (h !== 64 && h !== 32)) throw new Error(`Skins must be 64×64 pixels (this one is ${w}×${h}).`);
  const boundary = `----polar${Date.now().toString(16)}`;
  const body = Buffer.concat([
    Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name="variant"\r\n\r\n${variant === 'slim' ? 'slim' : 'classic'}\r\n`),
    Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name="file"; filename="skin.png"\r\nContent-Type: image/png\r\n\r\n`),
    png,
    Buffer.from(`\r\n--${boundary}--\r\n`),
  ]);
  await request(`${API}/skins`, { method: 'POST', body, headers: { ...auth(token), 'Content-Type': `multipart/form-data; boundary=${boundary}` }, timeout: 30000 });
  return getProfile(token);
}

async function resetSkin(token) {
  await request(`${API}/skins/active`, { method: 'DELETE', headers: auth(token), timeout: 15000 });
  return getProfile(token);
}

/** capeId null hides the cape. */
async function setCape(token, capeId) {
  if (capeId) {
    await request(`${API}/capes/active`, { method: 'PUT', body: JSON.stringify({ capeId }), headers: { ...auth(token), 'Content-Type': 'application/json' }, timeout: 15000 });
  } else {
    await request(`${API}/capes/active`, { method: 'DELETE', headers: auth(token), timeout: 15000 });
  }
  return getProfile(token);
}

/** Is Minecraft's login/profile service reachable? */
async function servicesOnline() {
  try {
    await fetch('https://api.minecraftservices.com/', { method: 'HEAD', signal: AbortSignal.timeout(6000) });
    return true;
  } catch {
    return false;
  }
}

module.exports = { getProfile, uploadSkin, resetSkin, setCape, servicesOnline, shape };
