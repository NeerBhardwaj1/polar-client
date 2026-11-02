'use strict';
// Crash helper, screenshot gallery, launcher self-update, Browse mod updates and mod profiles.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const crash = require('../src/core/crash');
const screenshots = require('../src/core/screenshots');
const updater = require('../src/core/updater');
const modrinth = require('../src/core/modrinth');
const catalog = require('../src/core/catalog');

test('crash helper: missing dependency names the mod and what it needs', () => {
  const log = `[main/ERROR]: Incompatible mods found!
net.fabricmc.loader.impl.FormattedException: Some of your mods are incompatible with the game or each other!
A potential solution has been determined:
	 - Install fabric-api, version 0.140.0 or later.
Unmet dependency listing:
	 - Mod 'Xaero's Minimap' (xaerominimap) 25.2.0 requires version 0.140.0 or later of mod 'Fabric API' (fabric-api), which is missing!`;
  const a = crash.analyze(log, '');
  assert.equal(a.title, "Xaero's Minimap needs Fabric API");
  assert.match(a.detail, /version 0\.140\.0 or later/);
  assert.deepEqual(a.mods, ['xaerominimap']);
  assert.equal(a.action, 'mods');
});

test('crash helper: wrong Minecraft build, memory, Java and suspected mods', () => {
  let a = crash.analyze("Mod 'Sodium' (sodium) 0.6.0 requires version 1.21.4 of 'Minecraft' (minecraft), but only the wrong version is present: 1.21.11!");
  assert.equal(a.title, 'Sodium needs a different Minecraft');
  assert.match(a.fix, /another Minecraft version/);

  a = crash.analyze('Exception in thread "Render thread" java.lang.OutOfMemoryError: Java heap space');
  assert.equal(a.action, 'memory');

  a = crash.analyze('java.lang.UnsupportedClassVersionError: net/minecraft/client/main/Main has been compiled by a more recent version of the Java Runtime');
  assert.equal(a.action, 'java');

  a = crash.analyze('', '---- Minecraft Crash Report ----\nDescription: Rendering overlay\nSuspected Mods: Iris (iris), Sodium (sodium)\n');
  assert.equal(a.title, 'Iris probably crashed the game');
  assert.deepEqual(a.suspects, ['Iris (iris)', 'Sodium (sodium)']);

  a = crash.analyze('nothing useful here', '---- Minecraft Crash Report ----\nDescription: Ticking entity\n');
  assert.equal(a.title, 'Minecraft closed unexpectedly');
  assert.match(a.detail, /Ticking entity/);
});

test('screenshots: lists every version newest first and refuses paths outside the folders', async () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'polar-shots-'));
  const a = screenshots.dirFor(root, '26.3');
  const b = screenshots.dirFor(root, '1.8.9');
  fs.mkdirSync(a, { recursive: true });
  fs.mkdirSync(b, { recursive: true });
  fs.writeFileSync(path.join(a, 'old.png'), 'x');
  fs.utimesSync(path.join(a, 'old.png'), new Date(1000), new Date(1000));
  fs.writeFileSync(path.join(b, 'new.png'), 'xy');
  fs.writeFileSync(path.join(b, 'notes.txt'), 'not a screenshot');

  const list = await screenshots.list(root);
  assert.deepEqual(list.map((s) => s.id), ['1.8.9/new.png', '26.3/old.png']);
  assert.equal(screenshots.resolve(root, '1.8.9/new.png'), path.join(b, 'new.png'));
  for (const bad of ['1.8.9/../../../secret.png', '1.8.9/notes.txt', 'nope/new.png', '26.3/missing.png', 'new.png', 42]) {
    assert.equal(screenshots.resolve(root, bad), null, String(bad));
  }
  assert.ok(catalog.VERSIONS.length >= 2);
  fs.rmSync(root, { recursive: true, force: true });
});

test('updater: compares versions and only offers a newer release with an installer', async () => {
  assert.equal(updater.compareVersions('1.2.10', '1.2.9'), 1);
  assert.equal(updater.compareVersions('v1.3.0', '1.3.0'), 0);
  assert.equal(updater.compareVersions('1.0.0', '1.0.1'), -1);

  let release = { tag_name: 'v1.4.0', body: 'New stuff', html_url: 'https://github.com/x/y/releases/v1.4.0',
    assets: [{ name: 'Polar.Client.Setup.1.4.0.exe', browser_download_url: 'https://github.com/x/y/releases/download/v1.4.0/setup.exe', size: 10 }] };
  global.fetch = async () => new Response(JSON.stringify(release), { status: 200 });
  const u = await updater.check({ repo: 'x/y', currentVersion: '1.3.2', platform: 'win32' });
  assert.equal(u.version, '1.4.0');
  assert.equal(u.name, 'Polar.Client.Setup.1.4.0.exe');
  assert.equal(await updater.check({ repo: 'x/y', currentVersion: '1.4.0', platform: 'win32' }), null, 'same version');
  assert.equal(await updater.check({ repo: 'x/y', currentVersion: '1.3.2', platform: 'darwin' }), null, 'no dmg in the release');
  assert.equal(await updater.check({ repo: 'not a repo', currentVersion: '1.0.0' }), null);
  release = { ...release, prerelease: true };
  assert.equal(await updater.check({ repo: 'x/y', currentVersion: '1.3.2', platform: 'win32' }), null, 'pre-releases are skipped');
});

/** A fake Modrinth with projects { id: { slug, title, version } } (version null = no build). */
function fakeModrinth(projects) {
  global.fetch = async (url) => {
    const u = String(url);
    const m = u.match(/^https:\/\/api\.modrinth\.com\/v2\/project\/([^/?]+)(\/version)?/);
    if (m) {
      const key = decodeURIComponent(m[1]);
      const id = Object.keys(projects).find((k) => k === key || projects[k].slug === key);
      const p = id && projects[id];
      if (!m[2]) return p ? new Response(JSON.stringify({ id, slug: p.slug, title: p.title }), { status: 200 }) : new Response('{}', { status: 404 });
      if (!p || !p.version) return new Response('[]', { status: 200 });
      const fileUrl = `https://cdn.test/${p.slug}-${p.version}.jar`;
      return new Response(JSON.stringify([{ project_id: id, version_number: p.version, version_type: 'release', dependencies: [],
        files: [{ primary: true, url: fileUrl, filename: `${p.slug}-${p.version}.jar`, size: Buffer.byteLength(`jar:${fileUrl}`), hashes: {} }] }]), { status: 200 });
    }
    if (u.startsWith('https://cdn.test/')) return new Response(Buffer.from(`jar:${u}`), { status: 200 });
    return new Response('nope', { status: 404 });
  };
}

test('Browse mods: newer builds are found and Update replaces the old file', async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'polar-upd-'));
  const projects = { AAA: { slug: 'zoomify', title: 'Zoomify', version: '1.0' }, BBB: { slug: 'skinshuffle', title: 'SkinShuffle', version: '2.0' } };
  fakeModrinth(projects);
  for (const id of ['AAA', 'BBB']) await modrinth.install({ gameDir: dir, gameVersion: '26.3', project: { id, slug: projects[id].slug, title: projects[id].title } });
  assert.deepEqual(await modrinth.checkUpdates({ gameDir: dir, gameVersion: '26.3' }), []);

  projects.AAA.version = '1.1';
  const ups = await modrinth.checkUpdates({ gameDir: dir, gameVersion: '26.3' });
  assert.deepEqual(ups.map((x) => [x.title, x.current, x.latest]), [['Zoomify', '1.0', '1.1']]);

  await modrinth.install({ gameDir: dir, gameVersion: '26.3', project: { id: 'AAA', slug: 'zoomify', title: 'Zoomify' } });
  const jars = fs.readdirSync(path.join(dir, 'mods')).filter((f) => f.endsWith('.jar')).sort();
  assert.deepEqual(jars, ['skinshuffle-2.0.jar', 'zoomify-1.1.jar']);
  assert.deepEqual(await modrinth.checkUpdates({ gameDir: dir, gameVersion: '26.3' }), []);
  fs.rmSync(dir, { recursive: true, force: true });
});

test('mod profiles: switching installs what the profile lists and removes the rest', async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'polar-prof-'));
  const projects = { AAA: { slug: 'zoomify', title: 'Zoomify', version: '1.0' }, BBB: { slug: 'skinshuffle', title: 'SkinShuffle', version: '2.0' },
    CCC: { slug: 'gone', title: 'Gone Mod', version: null } };
  fakeModrinth(projects);
  await modrinth.install({ gameDir: dir, gameVersion: '26.3', project: { id: 'AAA', slug: 'zoomify', title: 'Zoomify' } });
  fs.writeFileSync(path.join(dir, 'mods', 'my-own.jar'), 'mine');

  const profile = { name: 'PvP', mods: [{ projectId: 'BBB', slug: 'skinshuffle', title: 'SkinShuffle' }, { projectId: 'CCC', slug: 'gone', title: 'Gone Mod' }] };
  const res = await modrinth.applyProfile({ gameDir: dir, gameVersion: '26.3', profile });
  assert.deepEqual(res.removed, ['Zoomify']);
  assert.deepEqual(res.installed, ['SkinShuffle']);
  assert.equal(res.failed.length, 1);
  assert.match(res.failed[0], /^Gone Mod: .*no build/);
  const jars = fs.readdirSync(path.join(dir, 'mods')).filter((f) => f.endsWith('.jar')).sort();
  assert.deepEqual(jars, ['my-own.jar', 'skinshuffle-2.0.jar'], 'your own jars are left alone');
  fs.rmSync(dir, { recursive: true, force: true });
});
