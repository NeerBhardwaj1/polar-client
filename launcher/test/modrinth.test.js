'use strict';
// The Modrinth browser against a fake Modrinth: search filters, installs with dependencies, removal, the Installed list.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const modrinth = require('../src/core/modrinth');

// project id -> { slug, title, deps: [project ids], has: whether a build exists for the version }
const PROJECTS = {
  P7dR8mSH: { slug: 'fabric-api', title: 'Fabric API', deps: [] },
  AANobbMI: { slug: 'sodium', title: 'Sodium', deps: [] },
  map00001: { slug: 'xaeros-minimap', title: "Xaero's Minimap", deps: [] },
  jei00001: { slug: 'cool-mod', title: 'Cool Mod', deps: ['P7dR8mSH', 'lib00001', 'AANobbMI'] },
  lib00001: { slug: 'cloth-config', title: 'Cloth Config', deps: [] },
  old00001: { slug: 'old-mod', title: 'Old Mod', deps: [], none: true },
  bad00001: { slug: 'bad-mod', title: 'Bad Mod', deps: [], file: '../../evil.jar' },
};

function fakeModrinth(seen) {
  global.fetch = async (url) => {
    const u = new URL(String(url));
    seen && seen.push(u);
    if (u.hostname === 'api.modrinth.com' && u.pathname === '/v2/search') {
      return new Response(JSON.stringify({ total_hits: 2, offset: 0, limit: 20, hits: [
        { project_id: 'jei00001', slug: 'cool-mod', title: 'Cool Mod', description: 'Does cool things', author: 'neer', icon_url: 'https://cdn.modrinth.com/x.png', downloads: 1234567, follows: 4321, display_categories: ['fabric', 'utility'], date_modified: '2026-10-01T00:00:00Z' },
        { project_id: 'AANobbMI', slug: 'sodium', title: 'Sodium', description: 'Fast', author: 'jelly', icon_url: 'javascript:alert(1)', downloads: 1, follows: 1, categories: ['optimization'] },
      ] }), { status: 200 });
    }
    const m = u.pathname.match(/^\/v2\/project\/([^/]+)(\/version)?$/);
    if (u.hostname === 'api.modrinth.com' && m) {
      const key = decodeURIComponent(m[1]);
      const id = Object.keys(PROJECTS).find((k) => k === key || PROJECTS[k].slug === key);
      if (!id) return new Response('{}', { status: 404 });
      const p = PROJECTS[id];
      if (!m[2]) return new Response(JSON.stringify({ id, slug: p.slug, title: p.title }), { status: 200 });
      if (p.none) return new Response('[]', { status: 200 });
      const fileUrl = `https://cdn.modrinth.com/data/${id}/${p.slug}-1.0.jar`;
      return new Response(JSON.stringify([{ project_id: id, version_number: '1.0', version_type: 'release',
        dependencies: p.deps.map((d) => ({ project_id: d, dependency_type: 'required' })).concat([{ project_id: 'opt00001', dependency_type: 'optional' }]),
        files: [{ primary: true, url: fileUrl, filename: p.file || `${p.slug}-1.0.jar`, size: Buffer.byteLength(`jar:${fileUrl}`), hashes: {} }] }]), { status: 200 });
    }
    if (u.hostname === 'cdn.modrinth.com') return new Response(Buffer.from(`jar:${u.href}`), { status: 200 });
    return new Response('nope', { status: 404 });
  };
}

test('search asks Modrinth for client mods of this version and loader, and cleans up the results', async () => {
  const seen = [];
  fakeModrinth(seen);
  const res = await modrinth.search({ query: 'cool', gameVersion: '1.21.11', loader: 'fabric', sort: 'downloads', category: 'utility' });
  const facets = JSON.parse(seen[0].searchParams.get('facets'));
  assert.deepEqual(facets, [['project_type:mod'], ['categories:fabric'], ['versions:1.21.11'], ['client_side:required', 'client_side:optional'], ['categories:utility']]);
  assert.equal(seen[0].searchParams.get('index'), 'downloads');
  assert.equal(res.total, 2);
  assert.deepEqual(res.hits[0].categories, ['utility'], 'loader tags are dropped');
  assert.equal(res.hits[1].icon, null, 'only https icons are passed to the page');
  // unknown sorts and categories fall back safely
  await modrinth.search({ gameVersion: '1.8.9', loader: 'legacy-fabric', sort: 'nope', category: 'nope' });
  assert.equal(seen[1].searchParams.get('index'), 'relevance');
  assert.equal(JSON.parse(seen[1].searchParams.get('facets')).length, 4);
});

test('install brings required libraries, ticks launcher-managed mods instead of duplicating them, and removes cleanly', async () => {
  fakeModrinth();
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'polar-browse-'));
  const mods = path.join(dir, 'mods');
  fs.mkdirSync(mods);
  fs.writeFileSync(path.join(mods, 'my-own.jar'), 'mine');
  fs.writeFileSync(path.join(mods, '.polar-managed.json'), JSON.stringify([{ id: 'fabric-api', filename: 'fabric-api-0.1.jar' }]));
  fs.writeFileSync(path.join(mods, 'fabric-api-0.1.jar'), 'fapi');

  const res = await modrinth.install({ gameDir: dir, gameVersion: '1.21.11', project: { id: 'jei00001', slug: 'cool-mod', title: 'Cool Mod' } });
  assert.deepEqual(res.installed, ['Cool Mod', 'Cloth Config']);
  assert.deepEqual(res.enable, ['sodium'], 'Sodium is on the Mods page, so it is switched on there');
  const jars = fs.readdirSync(mods).filter((f) => f.endsWith('.jar')).sort();
  assert.deepEqual(jars, ['cloth-config-1.0.jar', 'cool-mod-1.0.jar', 'fabric-api-0.1.jar', 'my-own.jar'], 'no second Fabric API, no Sodium copy, optional deps skipped');

  // a launcher-managed mod picked in the browser is only switched on
  const s = await modrinth.install({ gameDir: dir, gameVersion: '1.21.11', project: { id: 'AANobbMI', slug: 'sodium', title: 'Sodium' } });
  assert.deepEqual(s, { installed: [], enable: ['sodium'], entry: null });

  let listed = await modrinth.list({ gameDir: dir });
  assert.deepEqual(listed.added.map((e) => e.title), ['Cool Mod']);
  assert.deepEqual(listed.own, ['my-own.jar'], 'managed jars and dependencies are not listed as yours');

  // no build for this version: a clear error, nothing written
  await assert.rejects(() => modrinth.install({ gameDir: dir, gameVersion: '1.21.11', project: { id: 'old00001', slug: 'old-mod', title: 'Old Mod' } }), /no build for Minecraft 1\.21\.11/);

  await assert.rejects(() => modrinth.install({ gameDir: dir, gameVersion: '1.21.11', project: { id: 'bad00001', slug: 'bad-mod', title: 'Bad Mod' } }), /unexpected file name/);
  assert.ok(!fs.existsSync(path.join(dir, '..', 'evil.jar')));

  const rm = await modrinth.remove({ gameDir: dir, projectId: 'jei00001' });
  assert.equal(rm.removed, true);
  assert.deepEqual(fs.readdirSync(mods).filter((f) => f.endsWith('.jar')).sort(), ['fabric-api-0.1.jar', 'my-own.jar'], 'the library it brought goes too');
  listed = await modrinth.list({ gameDir: dir });
  assert.deepEqual(listed.added, []);
  fs.rmSync(dir, { recursive: true, force: true });
});
