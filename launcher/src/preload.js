'use strict';
// The only bridge between the UI and the launcher. The page gets these functions and nothing else.
const { contextBridge, ipcRenderer } = require('electron');

const EVENTS = ['game:progress', 'game:log', 'game:exit', 'game:started', 'game:crash', 'win:state', 'accounts:changed', 'servers:changed', 'polar:event'];

async function call(channel, ...args) {
  const r = await ipcRenderer.invoke(channel, ...args);
  if (!r.ok) throw new Error(r.error);
  return r.data;
}

contextBridge.exposeInMainWorld('polar', {
  info: () => call('app:info'),
  window: {
    minimize: () => ipcRenderer.send('win:minimize'),
    maximize: () => ipcRenderer.send('win:maximize'),
    close: () => ipcRenderer.send('win:close'),
  },
  accounts: {
    list: () => call('accounts:list'),
    login: () => call('accounts:login'),
    addOffline: (name) => call('accounts:addOffline', name),
    select: (uuid) => call('accounts:select', uuid),
    remove: (uuid) => call('accounts:remove', uuid),
  },
  settings: {
    get: () => call('settings:get'),
    set: (patch) => call('settings:set', patch),
  },
  mods: { catalog: () => call('mods:catalog') },
  modrinth: {
    search: (params) => call('modrinth:search', params),
    installed: () => call('modrinth:installed'),
    install: (project) => call('modrinth:install', project),
    remove: (projectId) => call('modrinth:remove', projectId),
    updates: () => call('modrinth:updates'),
    installPack: (project) => call('modrinth:installPack', project),
    removePack: (kind, projectId) => call('modrinth:removePack', { kind, projectId }),
    toggleResource: (filename, on) => call('packs:toggleResource', { filename, on }),
    useShader: (filename) => call('packs:useShader', filename),
  },
  packs: {
    list: () => call('packs:list'),
    save: (name) => call('packs:save', name),
    apply: (name) => call('packs:apply', name),
    remove: (name) => call('packs:delete', name),
  },
  skins: {
    list: () => call('skins:list'),
    saveCurrent: () => call('skins:saveCurrent'),
    addFiles: (variant) => call('skins:addFiles', variant),
    apply: (id) => call('skins:apply', id),
    update: (id, patch) => call('skins:update', { id, ...patch }),
    remove: (id) => call('skins:remove', id),
  },
  online: {
    state: () => call('polar:state'),
    signIn: () => call('polar:signIn'),
    offlineKey: (uuid) => call('polar:offlineKey', uuid),
    useOfflineKey: (key) => call('polar:useOfflineKey', key),
    me: () => call('polar:me'),
    catalog: () => call('polar:catalog'),
    saveLoadout: (loadout) => call('polar:saveLoadout', loadout),
    redeem: (code) => call('polar:redeem', code),
    friends: () => call('polar:friends'),
    addFriend: (name) => call('polar:addFriend', name),
    acceptFriend: (uuid) => call('polar:acceptFriend', uuid),
    removeFriend: (uuid) => call('polar:removeFriend', uuid),
    invite: (uuid) => call('polar:invite', uuid),
    playing: () => call('polar:playing'),
  },
  news: () => call('news:get'),
  shots: {
    list: () => call('shots:list'),
    thumb: (id) => call('shots:thumb', id),
    full: (id) => call('shots:full', id),
    copy: (id) => call('shots:copy', id),
    reveal: (id) => call('shots:reveal', id),
    remove: (id) => call('shots:delete', id),
  },
  crash: { openReport: () => call('crash:openReport') },
  update: {
    check: () => call('update:check'),
    install: () => call('update:install'),
  },
  versions: {
    list: () => call('versions:list'),
    select: (id) => call('versions:select', id),
  },
  servers: {
    list: () => call('servers:list'),
    add: (server) => call('servers:add', server),
    edit: (oldIp, server) => call('servers:edit', oldIp, server),
    remove: (ip) => call('servers:remove', ip),
    ping: (address) => call('servers:ping', address),
  },
  profile: {
    get: () => call('profile:get'),
    uploadSkin: (variant) => call('skin:upload', variant),
    resetSkin: () => call('skin:reset'),
    setCape: (id) => call('cape:set', id),
  },
  services: { status: () => call('services:status') },
  game: {
    launch: (opts) => call('game:launch', opts || {}),
    kill: () => call('game:kill'),
    status: () => call('game:status'),
    openFolder: (which) => call('folder:open', which),
    pickJava: () => call('java:pick'),
  },
  openLink: (url) => call('link:open', url),
  on: (channel, cb) => {
    if (!EVENTS.includes(channel)) return () => {};
    const handler = (_e, data) => cb(data);
    ipcRenderer.on(channel, handler);
    return () => ipcRenderer.removeListener(channel, handler);
  },
});
