'use strict';
// syncMods against a fake Modrinth: installs, updates, removes deselected mods, never touches user mods.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const crypto = require('node:crypto');
const { syncMods } = require('../src/core/mods');

function fakeModrinth(versions) {
  const files = {};
  global.fetch = async (url) => {
    const u = String(url);
    if (u.startsWith('https://api.modrinth.com/v2/project/')) {
      const slug = decodeURIComponent(u.split('/project/')[1].split('/')[0]);
      const v = versions[slug];
      const fileUrl = `https://cdn.test/${slug}-${v}.jar`;
      const body = v ? [{ version_number: v, version_type: 'release', files: [{ primary: true, url: fileUrl, filename: `${slug}-${v}.jar`, size: Buffer.byteLength(`jar:${fileUrl}`), hashes: {} }] }] : [];
      return new Response(JSON.stringify(body), { status: 200 });
    }
    if (u.startsWith('https://cdn.test/')) {
      const data = Buffer.from(`jar:${u}`);
      files[u] = data;
      return new Response(data, { status: 200 });
    }
    return new Response('nope', { status: 404 });
  };
  return files;
}

test('installs, updates and cleans up only launcher-managed mods', async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'polar-mods-'));
  const modsDir = path.join(dir, 'mods');
  fs.mkdirSync(modsDir);
  fs.writeFileSync(path.join(modsDir, 'my-own-mod.jar'), 'mine');
  const bundled = path.join(dir, 'polarclient.jar');
  fs.writeFileSync(bundled, 'polar');

  fakeModrinth({ 'fabric-api': '0.165.0', sodium: '0.8.0', lithium: '0.17.0' });
  let res = await syncMods({ gameDir: dir, gameVersion: '26.3', enabledOptional: ['sodium', 'lithium', 'modmenu'], bundledJar: bundled });
  let files = fs.readdirSync(modsDir).sort();
  assert.deepEqual(files, ['.polar-managed.json', 'fabric-api-0.165.0.jar', 'lithium-0.17.0.jar', 'my-own-mod.jar', 'polarclient.jar', 'sodium-0.8.0.jar']);
  assert.ok(res.skipped.includes('Mod Menu'), 'mods without a 26.3 build are skipped, not fatal');

  // update sodium, drop lithium
  fakeModrinth({ 'fabric-api': '0.165.0', sodium: '0.8.1' });
  res = await syncMods({ gameDir: dir, gameVersion: '26.3', enabledOptional: ['sodium'], bundledJar: bundled });
  files = fs.readdirSync(modsDir).sort();
  assert.deepEqual(files, ['.polar-managed.json', 'fabric-api-0.165.0.jar', 'my-own-mod.jar', 'polarclient.jar', 'sodium-0.8.1.jar']);
  assert.equal(fs.readFileSync(path.join(modsDir, 'my-own-mod.jar'), 'utf8'), 'mine');
  fs.rmSync(dir, { recursive: true, force: true });
});

test('required dependencies come along once, by Modrinth project id', async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'polar-mods-'));
  const ids = { 'fabric-api': 'P7dR8mSH', sodium: 'AANobbMI', iris: 'YL57xq9U' };
  const deps = { iris: ['AANobbMI', 'P7dR8mSH'] };
  global.fetch = async (url) => {
    const u = String(url);
    if (u.startsWith('https://api.modrinth.com/v2/project/')) {
      const key = decodeURIComponent(u.split('/project/')[1].split('/')[0]);
      const slug = Object.keys(ids).find((k) => k === key || ids[k] === key);
      if (!slug) return new Response('[]', { status: 200 });
      const fileUrl = `https://cdn.test/${slug}.jar`;
      return new Response(JSON.stringify([{ project_id: ids[slug], version_number: '1', version_type: 'release',
        dependencies: (deps[slug] || []).map((d) => ({ project_id: d, dependency_type: 'required' })),
        files: [{ primary: true, url: fileUrl, filename: `${slug}.jar`, size: Buffer.byteLength(`jar:${fileUrl}`), hashes: {} }] }]), { status: 200 });
    }
    if (u.startsWith('https://cdn.test/')) return new Response(Buffer.from(`jar:${u}`), { status: 200 });
    return new Response('nope', { status: 404 });
  };
  // Iris alone brings Sodium; picking both doesn't install Sodium twice
  let res = await syncMods({ gameDir: dir, gameVersion: '26.3', enabledOptional: ['iris'], bundledJar: null, polar: false });
  assert.deepEqual(fs.readdirSync(path.join(dir, 'mods')).filter((f) => f.endsWith('.jar')).sort(), ['fabric-api.jar', 'iris.jar', 'sodium.jar']);
  res = await syncMods({ gameDir: dir, gameVersion: '26.3', enabledOptional: ['sodium', 'iris'], bundledJar: null, polar: false });
  assert.equal(res.installed.filter((n) => n.startsWith('Sodium')).length, 1);
  // switching Iris off removes the Sodium it pulled in only if Sodium isn't picked itself
  await syncMods({ gameDir: dir, gameVersion: '26.3', enabledOptional: [], bundledJar: null, polar: false });
  assert.deepEqual(fs.readdirSync(path.join(dir, 'mods')).filter((f) => f.endsWith('.jar')).sort(), ['fabric-api.jar']);
  fs.rmSync(dir, { recursive: true, force: true });
});

test('missing Fabric API build is a clear error', async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'polar-mods-'));
  fakeModrinth({});
  await assert.rejects(() => syncMods({ gameDir: dir, gameVersion: '26.3', enabledOptional: [], bundledJar: null }), /Fabric API isn't available/);
  fs.rmSync(dir, { recursive: true, force: true });
});

test('sha1 helper sanity', () => {
  assert.equal(crypto.createHash('sha1').update('').digest('hex'), 'da39a3ee5e6b4b0d3255bfef95601890afd80709');
});
