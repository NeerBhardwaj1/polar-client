'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { Store, offlineUuid } = require('../src/core/store');
const { buildLaunchCommand } = require('../src/core/launch');
const { mergeProfiles, resolveLibraries } = require('../src/core/versions');

const vanilla = require('./fixtures/vanilla-26.3.json');
const fabric = require('./fixtures/fabric-profile.json');

function tempStore() {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'polar-accounts-'));
  return new Store(dir);
}

test('offline UUID matches what Minecraft servers use', () => {
  // UUID.nameUUIDFromBytes("OfflinePlayer:Notch") = b50ad385-829d-3141-a216-7e7d7539ba7f
  assert.equal(offlineUuid('Notch'), 'b50ad385829d3141a2167e7d7539ba7f');
});

test('offline profiles are unlocked and can be added directly', async () => {
  const store = tempStore();
  assert.equal(store.listAccounts().offlineUnlocked, true);

  await store.addOfflineAccount('Steve_2');
  await store.addOfflineAccount('Builder');
  const list = store.listAccounts();
  assert.deepEqual(list.accounts.map((a) => [a.name, a.type]), [['Steve_2', 'offline'], ['Builder', 'offline']]);
  assert.equal(list.selected, offlineUuid('Builder'), 'a new offline profile is selected');

  await assert.rejects(store.addOfflineAccount('builder'), /already/);
  await assert.rejects(store.addOfflineAccount('ab'), /3-16/);
  await assert.rejects(store.addOfflineAccount('has space'), /3-16/);
  await assert.rejects(store.addOfflineAccount('way_too_long_name_123'), /3-16/);

  const acc = store.getAccount(offlineUuid('Steve_2'));
  assert.equal(acc.type, 'offline');
  assert.equal(acc.mcToken, '0');
});

test('offline launch passes the name, offline UUID and a dummy token', () => {
  const merged = mergeProfiles(vanilla, fabric);
  const env = { os: 'windows', arch: 'x86_64', osVersion: '10.0.26200' };
  const account = { name: 'Steve_2', uuid: offlineUuid('Steve_2'), mcToken: '0', xuid: '0', type: 'offline' };
  const { args } = buildLaunchCommand({
    profile: merged, env, java: 'javaw.exe', libraries: resolveLibraries(merged, env),
    dirs: { game: 'g', assets: 'a', libraries: 'l', natives: 'n', clientJar: 'c.jar' }, account, settings: { memoryGb: 4 },
  });
  const val = (flag) => args[args.indexOf(flag) + 1];
  assert.equal(val('--username'), 'Steve_2');
  assert.equal(val('--uuid'), offlineUuid('Steve_2'));
  assert.equal(val('--accessToken'), '0');
});

test('account bridge: secret required, lists accounts, hands out a session', async () => {
  const { startBridge } = require('../src/core/bridge');
  const accounts = { selected: 'a1', accounts: [{ uuid: 'a1', name: 'Owner', type: 'msa', secret: 'x' }] };
  const bridge = await startBridge({
    listAccounts: () => accounts,
    session: async (uuid) => ({ uuid, name: 'Owner', type: 'msa', accessToken: 'tok', xuid: '1' }),
  });
  try {
    const base = `http://127.0.0.1:${bridge.port}`;
    assert.equal(bridge.env.POLAR_BRIDGE, `${bridge.port}:${bridge.token}`);
    assert.equal((await fetch(`${base}/accounts`)).status, 403, 'no secret, no answer');
    assert.equal((await fetch(`${base}/accounts`, { headers: { 'x-polar-token': 'wrong' } })).status, 403);
    const list = await (await fetch(`${base}/accounts`, { headers: { 'x-polar-token': bridge.token } })).json();
    assert.deepEqual(list, { selected: 'a1', accounts: [{ uuid: 'a1', name: 'Owner', type: 'msa' }] }, 'only public fields go out');
    const bad = await fetch(`${base}/session`, { method: 'POST', headers: { 'x-polar-token': bridge.token }, body: JSON.stringify({ uuid: '../x' }) });
    assert.equal(bad.status, 400);
    const ok = await fetch(`${base}/session`, { method: 'POST', headers: { 'x-polar-token': bridge.token }, body: JSON.stringify({ uuid: 'b50ad385829d3141a2167e7d7539ba7f' }) });
    assert.equal((await ok.json()).accessToken, 'tok');
  } finally {
    await bridge.close();
  }
});
