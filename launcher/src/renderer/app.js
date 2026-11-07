'use strict';
/* global polar */
const $ = (id) => document.getElementById(id);

// Links in the footer. Leave a url empty to hide that button.
const LINKS = [
  { icon: 'i-link', label: 'Website', url: '' },
  { icon: 'i-chat', label: 'Discord', url: '' },
  { icon: 'i-code', label: 'GitHub', url: 'https://github.com/PolarClientMC/polar-client' },
];

const BUILT_IN = {
  HUD: ['Watermark', 'FPS', 'CPS', 'Ping', 'Keystrokes', 'Armor Status', 'Potion Effects', 'Coordinates', 'Direction HUD', 'Biome Display', 'Chunk Coordinates', 'Compass', 'Clock', 'Server Address', 'Speed', 'Session Time', 'Memory', 'Entity Counter', 'Held Item Display', 'Health HUD', 'Saturation HUD', 'Durability Warning', 'Pack Display', 'Scoreboard', 'Boss Bar', 'Tab List', 'HUD Tweaks'],
  Combat: ['Reach Display', 'Combo Counter', 'Item Counter', 'Totem Counter', 'Low Health Warning', 'Hit Indicator', 'Health Indicator', 'Hit Sound', 'Attack Cooldown', 'Cooldowns', 'Kill Counter', 'Death Counter', 'Auto GG'],
  Visual: ['Zoom', 'Freelook', 'Fullbright', 'Custom Crosshair', 'No Hurt Cam', 'Smooth Camera', 'Entity Shadows', 'Low Fire', 'Clear Water', 'Motion Blur', 'Time & Weather', 'Particles', 'Hit Colour', 'Glint Colour', 'Hitboxes'],
  World: ['Minimap', 'Waypoints', 'Coordinates Hotkey', 'Death Waypoints', 'Journey Tracker', 'World Border Display', 'Distance Calculator'],
  Inventory: ['Inventory Search', 'Shulker Preview', 'Exact Durability'],
  Social: ['Chat Timestamps', 'Chat Filter', 'Copy Chat', 'Compact Chat', 'Chat Background', 'Mention Alerts', 'Discord Status'],
  Performance: ['FPS Limiter', 'Frame Time', 'CPU Monitor', 'Stutter Monitor', 'Hide Far Entities', 'XP Bottle Optimizer'],
  Accessibility: ['Toggle Sprint', 'Screen Shake Control', 'Sensitivity Profiles'],
  Customization: ['Custom Font', 'Polar Cosmetics'],
};
const CAT_ICON = { HUD: 'i-layers', Combat: 'i-bolt', Visual: 'i-sun', World: 'i-globe', Inventory: 'i-cube', Social: 'i-chat', Performance: 'i-bolt', Accessibility: 'i-user', Customization: 'i-edit' };

const FEATURES = [
  { title: 'Right Shift Menu', text: 'All your features in one place.', icon: 'i-menu', img: 'assets/features/menu.webp',
    long: 'Press Right Shift in game to open the Polar menu: search every mod, switch it on or off with a click, and open its settings.',
    points: ['Search, categories and favourites-style cards', 'Per-mod settings: colours, sizes, keybinds', 'Pick an accent colour in Polar Settings'] },
  { title: 'Drag-and-drop HUD', text: 'Move and resize HUD elements.', icon: 'i-cube', img: 'assets/features/hud.webp',
    long: 'Open the HUD editor from the Polar menu and drag FPS, CPS, keystrokes, armor and more wherever you like. They snap to edges and to each other.',
    points: ['Scroll over an element to resize it', 'Right-click an element for its settings', 'Reset puts everything back'] },
  { title: 'Zoom & Freelook', text: 'Zoom and look around freely.', icon: 'i-search', img: 'assets/features/zoom.webp',
    long: 'Hold C to zoom (scroll while holding for more). Hold Left Alt to look around without turning your player.',
    points: ['Smooth zoom with adjustable speed', 'Freelook can be switched off for servers that ban it', 'Rebind both in Options → Controls'] },
  { title: 'Performance Pack', text: 'Sodium, Lithium and more pre-installed.', icon: 'i-bolt', img: 'assets/features/performance.webp',
    long: 'The launcher installs and updates a performance pack from Modrinth so the game runs as smoothly as it can.',
    points: ['Sodium, Lithium, FerriteCore, EntityCulling, ImmediatelyFast', 'Turn each one on or off on the Mods page', 'Always the builds made for the version you play'], goto: 'mods' },
];

const state = {
  info: null,
  settings: null,
  accounts: { selected: null, accounts: [] },
  servers: [],
  recent: [],
  pings: new Map(), // address -> { at, result | pending }
  running: false,
  launching: false,
  profile: null,
  skinSide: 'front',
  versions: { selected: '26.3', versions: [] }, // the version dropdown
  runningVersion: null,
};

// ---------------------------------------------------------------- helpers
function h(tag, props = {}, ...children) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(props)) {
    if (v == null || v === false) continue;
    if (k === 'class') el.className = v;
    else if (k === 'text') el.textContent = v;
    else if (k.startsWith('on')) el.addEventListener(k.slice(2), v);
    else el.setAttribute(k, v === true ? '' : v);
  }
  for (const c of children.flat()) if (c != null) el.append(c);
  return el;
}
function icon(id, cls) {
  const ns = 'http://www.w3.org/2000/svg';
  const svg = document.createElementNS(ns, 'svg');
  if (cls) svg.setAttribute('class', cls);
  const use = document.createElementNS(ns, 'use');
  use.setAttribute('href', `#${id}`);
  svg.append(use);
  return svg;
}
function toast(message, type = 'info', ms = 5200) {
  const el = h('div', { class: `toast ${type}`, text: message });
  $('toasts').append(el);
  setTimeout(() => el.remove(), ms);
}
const store = {
  get(key, fallback) {
    try {
      const v = localStorage.getItem(key);
      return v == null ? fallback : JSON.parse(v);
    } catch {
      return fallback;
    }
  },
  set(key, value) {
    try {
      localStorage.setItem(key, JSON.stringify(value));
    } catch {
      // storage unavailable: keep it in memory only
    }
  },
};
let saveTimer;
function saveSettings(patch) {
  Object.assign(state.settings, patch);
  clearTimeout(saveTimer);
  saveTimer = setTimeout(() => polar.settings.set(state.settings).catch((e) => toast(e.message, 'error')), 250);
}
const avatarUrl = (a) => (a && a.type !== 'offline' && a.uuid ? `https://mc-heads.net/avatar/${a.uuid}/64` : 'assets/mark-256.png');
// heads come from mc-heads.net; fall back to the logo when offline
document.addEventListener('error', (e) => {
  const img = e.target;
  if (img.tagName === 'IMG' && !img.dataset.fallback) {
    img.dataset.fallback = '1';
    img.src = 'assets/mark-256.png';
  }
}, true);
function timeAgo(t) {
  const s = Math.round((Date.now() - t) / 1000);
  if (s < 60) return 'just now';
  if (s < 3600) return `${Math.floor(s / 60)} min ago`;
  if (s < 86400) return `${Math.floor(s / 3600)} h ago`;
  if (s < 86400 * 60) return `${Math.floor(s / 86400)} d ago`;
  if (s < 86400 * 365) return `${Math.floor(s / (86400 * 30))} mo ago`;
  return `${Math.floor(s / (86400 * 365))} y ago`;
}

// ---------------------------------------------------------------- notifications (bell)
let notes = store.get('polar.notes', []);
function notify(title, text = '', level = 'info') {
  notes = [{ title, text, level, at: Date.now(), unread: true }, ...notes].slice(0, 30);
  store.set('polar.notes', notes);
  renderNotes();
}
function renderNotes() {
  const unread = notes.filter((n) => n.unread).length;
  $('bellBadge').hidden = unread === 0;
  $('bellBadge').textContent = unread > 9 ? '9+' : String(unread);
  const list = $('noteList');
  list.replaceChildren();
  if (!notes.length) list.append(h('div', { class: 'note-empty', text: 'Nothing new.' }));
  for (const n of notes) {
    const dot = h('span', { class: `dot ${n.level === 'error' ? 'bad' : n.level === 'ok' ? 'ok' : ''}` });
    list.append(h('div', { class: `note${n.unread ? ' unread' : ''}` }, dot,
      h('div', { class: 'grow' }, h('b', { text: n.title }), n.text ? h('span', { text: n.text }) : null, h('small', { text: timeAgo(n.at) }))));
  }
}
$('clearNotes').addEventListener('click', () => {
  notes = [];
  store.set('polar.notes', notes);
  renderNotes();
});

// ---------------------------------------------------------------- dropdowns & navigation
function closeMenus(except) {
  for (const [menu, btn] of [['accountMenu', 'accountChip'], ['bellMenu', 'bellBtn'], ['versionMenu', 'versionBtn']]) {
    if (menu === except) continue;
    $(menu).hidden = true;
    $(btn).classList.remove('open');
  }
}
function toggleMenu(menu, btn) {
  const open = $(menu).hidden;
  closeMenus(menu);
  $(menu).hidden = !open;
  $(btn).classList.toggle('open', open);
  return open;
}
document.addEventListener('click', (e) => {
  if (!e.target.closest('.top-actions, #versionBtn, #versionMenu')) closeMenus();
});
$('bellBtn').addEventListener('click', () => {
  if (toggleMenu('bellMenu', 'bellBtn')) {
    renderNotes();
    notes = notes.map((n) => ({ ...n, unread: false }));
    store.set('polar.notes', notes);
    setTimeout(renderNotes, 1500);
  }
});
$('gearBtn').addEventListener('click', () => setPage('settings'));

function setPage(name) {
  closeMenus();
  document.querySelectorAll('.nav-item').forEach((b) => b.classList.toggle('active', b.dataset.page === name));
  document.querySelectorAll('.page').forEach((p) => p.classList.toggle('active', p.id === `page-${name}`));
  $('page-' + name).scrollTop = 0;
  document.querySelector('.pages').scrollTop = 0;
  if (name === 'servers') { renderServers(); pingAll(); }
  if (name === 'cosmetics') openCosmetics();
  else stopWardrobe();
  if (name === 'browse') openBrowse();
  if (name === 'shots') loadShots();
  if (name === 'friends') openFriends();
}
document.querySelectorAll('.nav-item').forEach((b) => b.addEventListener('click', () => setPage(b.dataset.page)));
document.addEventListener('click', (e) => {
  const go = e.target.closest('[data-goto]');
  if (go) setPage(go.dataset.goto);
});
document.querySelectorAll('[data-win]').forEach((b) => b.addEventListener('click', () => polar.window[b.dataset.win]()));

// modals: any [data-close] inside closes it, as does clicking the backdrop or Escape
function openModal(id) {
  closeMenus();
  $(id).hidden = false;
  const first = $(id).querySelector('input:not([disabled])');
  if (first) setTimeout(() => first.focus(), 30);
}
document.querySelectorAll('.modal-backdrop').forEach((m) => {
  m.addEventListener('click', (e) => {
    if (e.target === m || e.target.closest('[data-close]')) m.hidden = true;
  });
});
document.addEventListener('keydown', (e) => {
  if (e.key === 'Escape') {
    document.querySelectorAll('.modal-backdrop').forEach((m) => { m.hidden = true; });
    closeMenus();
  }
});

// ---------------------------------------------------------------- hero: parallax + snow
const hero = $('hero');
hero.addEventListener('mousemove', (e) => {
  const r = hero.getBoundingClientRect();
  const x = (e.clientX - r.left) / r.width - 0.5, y = (e.clientY - r.top) / r.height - 0.5;
  hero.querySelector('.sky').style.transform = `translate(${-x * 6}px, ${-y * 4}px)`;
  hero.querySelectorAll('.aurora').forEach((a) => { a.style.transform = `translate(${-x * 9}px, ${-y * 5}px)`; });
  hero.querySelector('.scene').style.transform = `translate(${-x * 16}px, ${-y * 8}px)`;
});
hero.addEventListener('mouseleave', () => hero.querySelectorAll('.hero-layer').forEach((l) => { l.style.transform = ''; }));

const snow = (() => {
  const canvas = $('snow');
  const ctx = canvas.getContext('2d');
  let flakes = [];
  let on = store.get('polar.snow', true);
  let last = performance.now();
  const reset = (f, anywhere) => {
    f.z = Math.random();
    f.x = Math.random() * canvas.width;
    f.y = anywhere ? Math.random() * canvas.height : -8;
    f.p = Math.random() * 6.28;
  };
  const resize = () => {
    const r = canvas.getBoundingClientRect();
    canvas.width = Math.max(1, Math.round(r.width));
    canvas.height = Math.max(1, Math.round(r.height));
    flakes = Array.from({ length: Math.round(canvas.width / 9) }, () => { const f = {}; reset(f, true); return f; });
  };
  const frame = (now) => {
    const dt = Math.min(0.05, (now - last) / 1000);
    last = now;
    const visible = on && !document.hidden && $('page-home').classList.contains('active');
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    if (visible) {
      for (const f of flakes) {
        f.y += (14 + f.z * f.z * 60) * dt;
        f.x += (Math.sin(now / 1000 * (0.6 + f.z) + f.p) * (6 + f.z * 14) - 5) * dt;
        if (f.y > canvas.height + 8 || f.x < -8 || f.x > canvas.width + 8) reset(f, false);
        const r = f.z > 0.9 ? 2.6 + (f.z - 0.9) * 20 : 0.6 + f.z * 1.6;
        ctx.globalAlpha = f.z > 0.9 ? 0.35 : 0.45 + f.z * 0.45;
        ctx.beginPath();
        ctx.arc(f.x, f.y, r, 0, Math.PI * 2);
        ctx.fillStyle = '#fff';
        ctx.fill();
      }
      ctx.globalAlpha = 1;
    }
    requestAnimationFrame(frame);
  };
  window.addEventListener('resize', resize);
  resize();
  requestAnimationFrame(frame);
  return { set(v) { on = v; store.set('polar.snow', v); }, get on() { return on; }, resize };
})();

// ---------------------------------------------------------------- play / status
function setPlayState({ label, main, sub, percent, disabled, running, dot }) {
  if (label != null) $('playLabel').textContent = label;
  if (main != null) $('statusMain').textContent = main;
  if (sub != null) $('statusSub').textContent = sub;
  if (percent != null) {
    $('playFill').style.width = `${percent}%`;
    $('statusFill').style.width = `${percent}%`;
  }
  if (disabled != null) $('playBtn').disabled = disabled;
  if (running != null) $('playBtn').classList.toggle('running', running);
  if (dot != null) $('statusDot').className = `dot ${dot}`;
}
function idleSub() {
  const q = state.settings && state.settings.quickJoin;
  const v = currentVersion();
  return q ? `Minecraft ${v.id} · joins ${q} on start` : `Minecraft ${v.id} · ${v.loaderName}${v.polar ? ' · Polar' : ''}`;
}
const runningSub = () => {
  const v = state.versions.versions.find((x) => x.id === state.runningVersion);
  if (!v) return 'Right Shift → Polar Menu';
  return v.polar ? 'Right Shift → Polar Menu' : `Minecraft ${v.id} · ${v.loaderName}`;
};
function resetPlay() {
  state.launching = false;
  state.running = false;
  $('killGame').disabled = true;
  setPlayState({ label: 'PLAY MINECRAFT', main: 'Ready to play', sub: idleSub(), percent: 0, disabled: false, running: false, dot: '' });
}

async function launch(server, name) {
  if (state.launching || state.running) {
    if (state.running) toast('Minecraft is already running.');
    return;
  }
  if (!state.accounts.selected) {
    openModal('accountsModal');
    toast('Add an account to play.');
    return;
  }
  state.launching = true;
  const v = currentVersion();
  setPlayState({ label: 'LAUNCHING…', main: `Getting Minecraft ${v.id} ready…`, sub: server ? `Then joining ${server}` : v.installed ? 'Checking files…' : 'This takes a minute the first time.', percent: 1, disabled: true, dot: 'busy' });
  try {
    await polar.game.launch(server ? { server, name } : {});
  } catch (e) {
    toast(e.message, 'error', 9000);
    notify('Launch failed', e.message, 'error');
    resetPlay();
  }
}
$('playBtn').addEventListener('click', () => launch());
$('statusCard').addEventListener('click', () => setPage('console'));

polar.on('game:progress', (p) => setPlayState({ main: p.label, sub: p.detail || ' ', percent: p.percent }));
polar.on('game:started', (info = {}) => {
  state.launching = false;
  state.running = true;
  state.runningVersion = info.version || null;
  $('killGame').disabled = false;
  setPlayState({ label: 'RUNNING', main: info.version ? `Minecraft ${info.version} Running` : 'Minecraft Running', sub: runningSub(), percent: 100, disabled: true, running: true, dot: 'ok' });
  loadVersions(); // the version now counts as installed
  setTimeout(() => { if (state.running) { $('playFill').style.width = '0%'; $('statusFill').style.width = '0%'; } }, 700);
  notify('Minecraft started', state.accounts.accounts.find((a) => a.uuid === state.accounts.selected)?.name || '', 'ok');
});
polar.on('game:exit', ({ code, error }) => {
  state.runningVersion = null;
  resetPlay();
  if (error) {
    toast(`Couldn't start Java: ${error}`, 'error', 9000);
    notify("Couldn't start Java", error, 'error');
  } else if (code && code !== 0) {
    toast(`The game closed with error code ${code}.`, 'error', 6000);
  } else {
    notify('Minecraft closed');
  }
  loadServers();
});

// ---------------------------------------------------------------- version dropdown
const LOADER_NAMES = { fabric: 'Fabric', 'legacy-fabric': 'Legacy Fabric' };
function currentVersion() {
  const list = state.versions.versions;
  return list.find((v) => v.id === state.versions.selected) || list[0]
    || { id: state.versions.selected || '26.3', loader: 'fabric', loaderName: 'Fabric', polar: true, installed: false, summary: '' };
}
async function loadVersions() {
  try {
    state.versions = await polar.versions.list();
  } catch (e) {
    toast(e.message, 'error');
  }
  renderVersion();
}
function versionIcon(v) {
  return h('span', { class: 'ver-ico' }, v.polar ? h('img', { src: 'assets/mark-256.png', alt: '' }) : h('span', { class: 'chip-ico grass' }));
}
function renderVersionMenu() {
  const menu = $('versionMenu');
  menu.replaceChildren(h('div', { class: 'dropdown-label', text: 'MINECRAFT VERSION' }));
  for (const v of state.versions.versions) {
    const current = v.id === state.versions.selected;
    menu.append(h('button', { class: `menu-row version-row${current ? ' current' : ''}`, role: 'option', 'aria-selected': current ? 'true' : 'false', onclick: () => selectVersion(v.id) },
      versionIcon(v),
      h('span', { class: 'grow' }, h('b', {}, `Minecraft ${v.id}`, v.polar ? h('span', { class: 'ver-tag', text: 'POLAR' }) : null), h('small', { text: v.summary })),
      current ? icon('i-check', 'check') : v.installed ? h('span', { class: 'ver-state ready', text: 'Installed' }) : null));
  }
  menu.append(h('div', { class: 'menu-sep' }),
    h('div', { class: 'menu-foot', text: 'Polar Client runs on every version. Each one has its own game folder and downloads on first Play.' }));
}
function renderVersion() {
  const v = currentVersion();
  $('versionLabel').textContent = v.id;
  $('versionChip').textContent = `Minecraft ${v.id}`;
  $('loaderChip').textContent = v.loaderName || LOADER_NAMES[v.loader] || 'Fabric';
  const builtIn = Object.values(BUILT_IN).reduce((n, l) => n + l.length, 0);
  $('modCountChip').textContent = v.polar ? `${builtIn} built-in mods` : v.loader === 'legacy-fabric' ? 'Legacy Fabric API' : 'Performance pack';
  $('footGame').textContent = `MINECRAFT ${v.id}`;
  $('footLoader').textContent = (v.loaderName || 'Fabric').toUpperCase();
  $('serversSub').textContent = `The server list of Minecraft ${v.id}. Play joins the server as soon as the game opens.`;
  if (!state.launching && !state.running) $('statusSub').textContent = idleSub();
  renderModsNotice();
  renderVersionMenu();
}
function renderModsNotice() {
  const v = currentVersion();
  const legacy = v.loader === 'legacy-fabric';
  $('modsNotice').hidden = v.polar && !legacy;
  $('builtinList').classList.toggle('dim', !v.polar);
  for (const list of ['perfList', 'extraList']) {
    $(list).classList.toggle('dim', legacy);
    $(list).querySelectorAll('input[data-mod]').forEach((cb) => { cb.disabled = legacy; });
  }
  if (v.polar) $('modsSub').replaceChildren("Polar Client's own features are always installed. Press ", h('kbd', { text: 'Right Shift' }), ' in game to turn them on or off.');
  else $('modsSub').textContent = `Mods for Minecraft ${v.id}. The Game folder button opens this version's own folder.`;
  $('modsNoticeText').textContent = legacy
    ? `Minecraft ${v.id} runs on Legacy Fabric with Legacy Fabric API. Polar Client's mods are all here; the performance pack and the other Modrinth mods are made for newer versions, so they aren't installed on ${v.id}. You can add Legacy Fabric mods to this version's mods folder.`
    : `Polar Client isn't built for Minecraft ${v.id}. The launcher installs Fabric API and the performance mods below that have a ${v.id} build, and you can add your own Fabric mods to this version's mods folder.`;
}
function placeVersionMenu() {
  const menu = $('versionMenu');
  const r = $('versionBtn').getBoundingClientRect();
  const hgt = menu.offsetHeight;
  const below = window.innerHeight - r.bottom - 12;
  menu.style.top = `${below >= hgt ? r.bottom + 8 : Math.max(8, r.top - 8 - hgt)}px`;
  menu.style.left = `${Math.max(8, Math.min(r.left, window.innerWidth - menu.offsetWidth - 12))}px`;
}
$('versionBtn').addEventListener('click', () => {
  renderVersionMenu();
  if (toggleMenu('versionMenu', 'versionBtn')) placeVersionMenu();
});
window.addEventListener('resize', () => { if (!$('versionMenu').hidden) placeVersionMenu(); });
async function selectVersion(id) {
  closeMenus();
  if (id === state.versions.selected) return;
  if (state.launching) {
    toast('Wait until the game has started, then pick another version.');
    return;
  }
  try {
    state.versions = await polar.versions.select(id);
    state.settings.gameVersion = id;
    renderVersion();
    if ($('page-browse').classList.contains('active')) openBrowse();
    else checkModUpdates();
    const v = currentVersion();
    toast(v.polar ? `Minecraft ${v.id} with Polar Client.` : `Minecraft ${v.id} with ${v.loaderName}.`);
    if (state.running) toast('The new version is used the next time you press Play.');
  } catch (e) {
    toast(e.message, 'error', 7000);
  }
}

// ---------------------------------------------------------------- quick join
function renderQuickJoinButton() {
  const q = state.settings.quickJoin;
  $('quickJoinLabel').textContent = q ? `Join ${q.length > 18 ? `${q.slice(0, 17)}…` : q}` : 'Quick Join';
  $('quickJoinBtn').classList.toggle('set', Boolean(q));
  if (!state.launching && !state.running) $('statusSub').textContent = idleSub();
}
$('quickJoinBtn').addEventListener('click', () => {
  $('quickAddress').value = state.settings.quickJoin || '';
  $('quickAlways').checked = Boolean(state.settings.quickJoin);
  const picks = $('quickPicks');
  picks.replaceChildren(...state.servers.slice(0, 8).map((s) => h('button', { type: 'button', text: s.name, title: s.ip, onclick: () => { $('quickAddress').value = s.ip; } })));
  openModal('quickModal');
});
$('quickForm').addEventListener('submit', (e) => {
  e.preventDefault();
  const address = $('quickAddress').value.trim();
  saveSettings({ quickJoin: $('quickAlways').checked ? address : '' });
  renderQuickJoinButton();
  $('quickModal').hidden = true;
  const known = state.servers.find((s) => s.ip === address);
  if (address) launch(address, known ? known.name : address);
  else if ($('quickAlways').checked === false) toast('Quick Join turned off.');
});

// ---------------------------------------------------------------- servers (home + servers page)
function letterIcon(name) {
  const el = h('div', { class: 'server-icon letter', text: (name || '?').trim().charAt(0).toUpperCase() || '?' });
  let hash = 0;
  for (const c of name || '') hash = (hash * 31 + c.charCodeAt(0)) | 0;
  const hue = Math.abs(hash) % 360;
  el.style.background = `linear-gradient(135deg, hsl(${hue} 70% 55%), hsl(${(hue + 40) % 360} 70% 38%))`;
  return el;
}
function serverIcon(server) {
  const p = state.pings.get(server.ip);
  const src = (p && p.result && p.result.favicon) || server.icon;
  return src ? h('img', { class: 'server-icon', src, alt: '' }) : letterIcon(server.name);
}
function pingBadge(ip) {
  const p = state.pings.get(ip);
  if (!p || p.pending) return h('span', { class: 'ping wait', text: '···' });
  if (!p.result.online) return h('span', { class: 'ping off', text: 'Offline', title: p.result.error || '' });
  const ms = p.result.ping;
  return h('span', { class: `ping ${ms < 80 ? 'good' : ms < 200 ? 'mid' : 'bad'}`, text: `${ms} ms` });
}
async function pingServer(ip, force = false) {
  const cur = state.pings.get(ip);
  if (cur && (cur.pending || (!force && Date.now() - cur.at < 60000))) return;
  state.pings.set(ip, { pending: true, at: Date.now(), result: cur && cur.result });
  let result;
  try {
    result = await polar.servers.ping(ip);
  } catch (e) {
    result = { online: false, error: e.message };
  }
  state.pings.set(ip, { pending: false, at: Date.now(), result });
  renderRecent();
  if ($('page-servers').classList.contains('active')) renderServers();
}
function pingAll(force = false) {
  for (const s of state.servers) pingServer(s.ip, force);
}

function recentServers() {
  const byIp = new Map(state.servers.map((s) => [s.ip, s]));
  const out = [];
  for (const r of state.recent) {
    if (out.length >= 3) break;
    if (!out.some((o) => o.ip === r.ip)) out.push(byIp.get(r.ip) || { name: r.name || r.ip, ip: r.ip, icon: null });
  }
  for (const s of state.servers) {
    if (out.length >= 3) break;
    if (!out.some((o) => o.ip === s.ip)) out.push(s);
  }
  return out;
}
function renderRecent() {
  const grid = $('recentGrid');
  const list = recentServers();
  grid.replaceChildren(...list.map((s) => h('div', { class: 'server-card' },
    serverIcon(s),
    h('div', { class: 'server-meta' }, h('b', { text: s.name, title: s.name }), h('span', { text: s.ip })),
    pingBadge(s.ip),
    h('button', { class: 'mini-play', title: `Play on ${s.name}`, onclick: () => launch(s.ip, s.name) }, icon('i-play')))));
  grid.append(h('button', { class: 'add-card', onclick: () => openServerModal() }, h('span', { class: 'plus' }, icon('i-plus')), h('span', { text: 'Add Server' })));
  for (const s of list) pingServer(s.ip);
}

function motdLine(ip) {
  const p = state.pings.get(ip);
  const line = h('span', { class: 'motd' });
  if (!p || p.pending) line.textContent = 'Pinging…';
  else if (!p.result.online) line.textContent = p.result.error || "Can't reach this server";
  else {
    for (const seg of p.result.motd || []) {
      const text = seg.text.replace(/\n/g, ' ');
      if (!text) continue;
      const span = h('span', { class: `${seg.bold ? 'b' : ''} ${seg.italic ? 'i' : ''}`.trim(), text });
      if (seg.color) span.style.color = seg.color;
      line.append(span);
    }
    if (!line.textContent.trim()) line.textContent = 'A Minecraft Server';
  }
  return line;
}
function renderServers() {
  const list = $('serverList');
  if (!state.servers.length) {
    list.replaceChildren(h('div', { class: 'empty-state' }, h('b', { text: 'No servers yet' }),
      h('span', { text: 'Add a server here or in the game. Both use the same list.' }),
      h('div', {}, h('button', { class: 'btn primary', onclick: () => openServerModal() }, icon('i-plus'), 'Add Server'))));
    return;
  }
  list.replaceChildren(...state.servers.map((s) => {
    const p = state.pings.get(s.ip);
    const players = p && p.result && p.result.online ? `${p.result.players.online.toLocaleString()}/${p.result.players.max.toLocaleString()}` : '';
    const playersEl = h('span', { class: 'players', text: players });
    if (p && p.result && p.result.players && p.result.players.sample.length) playersEl.title = p.result.players.sample.join('\n');
    return h('div', { class: 'server-row' },
      serverIcon(s),
      h('div', { class: 'server-meta' }, h('b', { text: s.name }), h('span', { text: s.ip }), motdLine(s.ip)),
      playersEl,
      pingBadge(s.ip),
      h('button', { class: 'icon-action', title: 'Edit', onclick: () => openServerModal(s) }, icon('i-edit')),
      h('button', { class: 'icon-action danger', title: 'Remove', onclick: () => removeServer(s) }, icon('i-trash')),
      h('button', { class: 'mini-play', title: `Play on ${s.name}`, onclick: () => launch(s.ip, s.name) }, icon('i-play')));
  }));
}

async function loadServers() {
  try {
    const { servers, recent } = await polar.servers.list();
    state.servers = servers;
    state.recent = recent;
  } catch (e) {
    toast(e.message, 'error');
  }
  renderRecent();
  if ($('page-servers').classList.contains('active')) renderServers();
}
polar.on('servers:changed', loadServers);
$('refreshServers').addEventListener('click', async () => {
  await loadServers();
  pingAll(true);
});
$('addServerBtn').addEventListener('click', () => openServerModal());

let editingIp = null;
function openServerModal(server) {
  editingIp = server ? server.ip : null;
  $('serverModalTitle').textContent = server ? 'Edit Server' : 'Add Server';
  $('serverName').value = server ? server.name : '';
  $('serverIp').value = server ? server.ip : '';
  openModal('serverModal');
}
$('serverForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  const server = { name: $('serverName').value, ip: $('serverIp').value };
  try {
    state.servers = editingIp ? await polar.servers.edit(editingIp, server) : await polar.servers.add(server);
    $('serverModal').hidden = true;
    notify(editingIp ? 'Server updated' : 'Server added', server.ip.trim(), 'ok');
    renderRecent();
    renderServers();
    pingServer(server.ip.trim(), true);
  } catch (err) {
    toast(err.message, 'error', 7000);
  }
});
async function removeServer(s) {
  try {
    state.servers = await polar.servers.remove(s.ip);
    renderRecent();
    renderServers();
    toast(`Removed ${s.name}.`);
  } catch (e) {
    toast(e.message, 'error', 7000);
  }
}

// ---------------------------------------------------------------- features
function renderFeatures() {
  $('featureGrid').replaceChildren(...FEATURES.map((f) => h('button', { class: 'feature', onclick: () => openFeature(f) },
    h('img', { src: f.img, alt: '' }),
    h('div', { class: 'feature-body' },
      h('span', { class: 'feature-ico' }, icon(f.icon)),
      h('div', { class: 'grow' }, h('b', { text: f.title }), h('span', { text: f.text })),
      icon('i-chevron', 'chev')))));
  const count = Object.values(BUILT_IN).reduce((n, l) => n + l.length, 0);
  $('modCountChip').textContent = `${count} built-in mods`;
}
function openFeature(f) {
  $('featureShot').src = f.img;
  $('featureTitle').textContent = f.title;
  $('featureText').textContent = f.long;
  $('featurePoints').replaceChildren(...f.points.map((p) => h('li', { text: p })));
  openModal('featureModal');
}

// ---------------------------------------------------------------- mods page
async function renderMods() {
  renderModsNotice();
  $('builtinList').replaceChildren(...Object.entries(BUILT_IN).map(([cat, names]) => h('div', { class: 'mod-cat' },
    h('h3', {}, icon(CAT_ICON[cat]), cat, h('small', { text: `· ${names.length}` })),
    h('div', { class: 'tags' }, names.map((n) => h('span', { class: 'tag', text: n }))))));
  const catalog = await polar.mods.catalog();
  state.catalog = catalog;
  const row = (m) => {
    const cb = h('input', { type: 'checkbox', 'data-mod': m.id });
    cb.checked = state.settings.optionalMods.includes(m.id);
    cb.disabled = currentVersion().loader === 'legacy-fabric';
    cb.addEventListener('change', () => {
      const ids = [...document.querySelectorAll('#perfList input[data-mod]:checked, #extraList input[data-mod]:checked')].map((c) => c.dataset.mod);
      saveSettings({ optionalMods: ids });
    });
    return h('label', { class: 'mod-row toggle' }, cb, h('span'), h('div', { class: 'mod-text' }, h('div', { class: 'mod-name', text: m.name }), h('div', { class: 'mod-desc', text: m.description })));
  };
  $('perfList').replaceChildren(...catalog.filter((m) => m.group !== 'extras').map(row));
  $('extraList').replaceChildren(...catalog.filter((m) => m.group === 'extras').map(row));
}
$('openModsFolder').addEventListener('click', () => polar.game.openFolder('game').catch((e) => toast(e.message, 'error')));

// ---------------------------------------------------------------- browse (Modrinth)
const browse = { query: '', sort: 'relevance', category: '', offset: 0, total: 0, hits: [], loading: false, error: null,
  tab: 'discover', kind: 'mod', state: { added: [], own: [], optional: [], packs: {}, enabledPacks: [], shader: null }, busy: new Set(), seq: 0,
  loadedFor: null, categories: [],
  updates: [], updatesFor: null, updatesAt: 0, packs: { profiles: [], active: null } };

function fmtCount(n) {
  if (n >= 1e6) return `${(n / 1e6).toFixed(n >= 1e7 ? 0 : 1)}M`;
  if (n >= 1e3) return `${(n / 1e3).toFixed(n >= 1e4 ? 0 : 1)}k`;
  return String(n);
}
function modIcon(src, cls = 'mr-icon') {
  if (!src) return h('div', { class: cls }, icon('i-cube'));
  const img = h('img', { class: cls, src, alt: '', loading: 'lazy', 'data-fallback': '1' });
  img.addEventListener('error', () => img.replaceWith(h('div', { class: cls }, icon('i-cube'))));
  return img;
}
function catalogEntry(hit) {
  return (state.catalog || []).find((m) => m.id === hit.slug) || null;
}
async function loadBrowserState() {
  try {
    browse.state = await polar.modrinth.installed();
  } catch {
    browse.state = { added: [], own: [], optional: [], packs: {}, enabledPacks: [], shader: null };
  }
  $('browseCount').textContent = browse.state.added.length ? String(browse.state.added.length) : '';
}
function renderBrowseCats() {
  const cats = [['', 'All'], ...browse.categories];
  $('browseCats').replaceChildren(...cats.map(([id, label]) => {
    const b = h('button', { class: browse.category === id ? 'active' : '', text: label });
    b.addEventListener('click', () => {
      if (browse.category === id) return;
      browse.category = id;
      renderBrowseCats();
      runSearch();
    });
    return b;
  }));
}
const KIND_INFO = {
  mod: { one: 'mod', many: 'mods', page: 'mod', placeholder: 'Search Modrinth mods' },
  resourcepack: { one: 'resource pack', many: 'resource packs', page: 'resourcepack', placeholder: 'Search resource packs' },
  shader: { one: 'shader pack', many: 'shader packs', page: 'shader', placeholder: 'Search shader packs' },
};
const shadersBlocked = () => browse.kind === 'shader' && currentVersion().loader === 'legacy-fabric';
function setBrowseKind(kind) {
  if (browse.kind === kind) return;
  browse.kind = kind;
  browse.category = '';
  browse.categories = [];
  browse.hits = [];
  browse.total = 0;
  browse.loadedFor = null;
  document.querySelectorAll('#browseKinds button').forEach((b) => b.classList.toggle('active', b.dataset.kind === kind));
  $('browseQuery').placeholder = KIND_INFO[kind].placeholder;
  renderBrowseCats();
  runSearch();
}
async function runSearch(append = false) {
  if (shadersBlocked()) {
    browse.hits = [];
    browse.total = 0;
    browse.error = null;
    $('browseCats').replaceChildren();
    $('browseStatus').textContent = '';
    $('browseMore').hidden = true;
    $('browseResults').replaceChildren(h('div', { class: 'empty-state' }, h('b', { text: 'No shaders on 1.8.9' }),
      "Shader packs need Iris, which doesn't run on Minecraft 1.8.9. Pick another version to browse shaders."));
    return;
  }
  const seq = ++browse.seq;
  browse.loading = true;
  browse.error = null;
  if (!append) {
    browse.offset = 0;
    browse.hits = [];
    $('browseResults').replaceChildren(...Array.from({ length: 6 }, () => h('div', { class: 'mr-skeleton' })));
  }
  $('browseMore').disabled = true;
  renderBrowseStatus();
  try {
    const res = await polar.modrinth.search({ kind: browse.kind, query: browse.query, sort: browse.sort, category: browse.category, offset: append ? browse.hits.length : 0, limit: 20 });
    if (seq !== browse.seq) return; // a newer search started meanwhile
    if (!browse.categories.length || res.kind !== browse.kind) {
      browse.categories = res.categories || [];
      renderBrowseCats();
    }
    browse.total = res.total;
    browse.hits = append ? browse.hits.concat(res.hits) : res.hits;
    browse.loadedFor = `${res.gameVersion}:${res.kind}`;
  } catch (e) {
    if (seq !== browse.seq) return;
    browse.error = e.message;
  } finally {
    if (seq === browse.seq) {
      browse.loading = false;
      renderResults();
      renderBrowseStatus();
    }
  }
}
function renderBrowseStatus() {
  const el = $('browseStatus');
  el.classList.toggle('error', !!browse.error);
  if (browse.error) el.textContent = `Couldn't reach Modrinth: ${browse.error}`;
  else if (browse.loading && !browse.hits.length) el.textContent = 'Searching Modrinth…';
  else el.textContent = browse.total ? `${browse.total.toLocaleString()} ${KIND_INFO[browse.kind].many} for Minecraft ${currentVersion().id}` : '';
}
function packEntry(kind, projectId) {
  const p = (browse.state.packs || {})[kind];
  return p ? p.added.find((e) => e.projectId === projectId) : null;
}
function packButton(hit) {
  const kind = browse.kind;
  if (browse.busy.has(hit.id)) return h('button', { class: 'btn small ghost', disabled: true }, 'Installing…');
  const added = packEntry(kind, hit.id);
  if (added) {
    const label = kind === 'shader' ? (browse.state.shader === added.filename ? 'In use' : 'Installed') : 'Installed';
    const b = h('button', { class: 'btn small done', title: 'Click to remove' }, icon('i-check'), label);
    b.addEventListener('mouseenter', () => { b.replaceChildren(icon('i-trash'), 'Remove'); b.className = 'btn small danger'; });
    b.addEventListener('mouseleave', () => { b.replaceChildren(icon('i-check'), label); b.className = 'btn small done'; });
    b.addEventListener('click', () => removePack(kind, hit.id, hit.title));
    return b;
  }
  const b = h('button', { class: 'btn small primary' }, icon('i-download'), 'Install');
  b.addEventListener('click', () => installPack(hit));
  return b;
}
async function installPack(hit) {
  const kind = browse.kind;
  if (browse.busy.has(hit.id)) return;
  browse.busy.add(hit.id);
  refreshCard(hit.id);
  try {
    const res = await polar.modrinth.installPack({ kind, id: hit.id, slug: hit.slug, title: hit.title, icon: hit.icon, author: hit.author });
    browse.state = res.state;
    state.settings.optionalMods = res.state.optional;
    const v = currentVersion().id;
    let msg;
    if (kind === 'shader') {
      msg = res.applied ? `Installed ${hit.title} and set it as your shader for Minecraft ${v}.` : `Installed ${hit.title}. Pick it in game: Options > Video Settings > Shader Packs.`;
      if (res.enabled.length) msg += ' Iris Shaders is turned on and installs when you press Play.';
    } else {
      msg = res.applied ? `Installed ${hit.title} and switched it on for Minecraft ${v}.`
        : state.running ? `Installed ${hit.title}. Switch it on in game: Options > Resource Packs.`
          : `Installed ${hit.title}. It's switched on automatically after you've started Minecraft ${v} once, or pick it in game.`;
    }
    toast(msg, 'info', 7000);
    notify(kind === 'shader' ? 'Shader installed' : 'Resource pack installed', hit.title, 'ok');
    renderMods();
  } catch (e) {
    toast(e.message, 'error', 8000);
  } finally {
    browse.busy.delete(hit.id);
    refreshCard(hit.id);
    if (browse.tab === 'installed') renderInstalled();
  }
}
async function removePack(kind, projectId, title) {
  try {
    const res = await polar.modrinth.removePack(kind, projectId);
    browse.state = res.state;
    toast(`Removed ${res.title || title}.`);
    refreshCard(projectId);
    if (browse.tab === 'installed') renderInstalled();
  } catch (e) {
    toast(e.message, 'error');
  }
}
function installButton(hit) {
  if (browse.kind !== 'mod') return packButton(hit);
  const added = browse.state.added.find((e) => e.projectId === hit.id);
  const managed = catalogEntry(hit);
  if (hit.slug === 'fabric-api' || hit.slug === 'legacy-fabric-api') return h('button', { class: 'btn small done', disabled: true }, icon('i-check'), 'Included');
  if (managed) {
    const on = (state.settings.optionalMods || []).includes(managed.id);
    const b = h('button', { class: `btn small ${on ? 'done' : 'ghost'}` }, icon(on ? 'i-check' : 'i-layers'), on ? 'On Mods page' : 'Turn on');
    b.addEventListener('click', () => (on ? setPage('mods') : installMod(hit)));
    return b;
  }
  if (browse.busy.has(hit.id)) return h('button', { class: 'btn small ghost', disabled: true }, 'Installing…');
  const upd = browse.updates.find((u) => u.projectId === hit.id);
  if (added && upd) {
    const b = h('button', { class: 'btn small update', title: `${upd.current} → ${upd.latest}` }, icon('i-download'), 'Update');
    b.addEventListener('click', () => installMod(hit));
    return b;
  }
  if (added) {
    const b = h('button', { class: 'btn small done', title: 'Click to remove' }, icon('i-check'), 'Installed');
    b.addEventListener('mouseenter', () => { b.replaceChildren(icon('i-trash'), 'Remove'); b.className = 'btn small danger'; });
    b.addEventListener('mouseleave', () => { b.replaceChildren(icon('i-check'), 'Installed'); b.className = 'btn small done'; });
    b.addEventListener('click', () => removeMod(hit.id, hit.title));
    return b;
  }
  const b = h('button', { class: 'btn small primary' }, icon('i-download'), 'Install');
  b.addEventListener('click', () => installMod(hit));
  return b;
}
function modCard(hit) {
  const link = h('a', { class: 'mr-link', href: '#' }, 'Modrinth', icon('i-external'));
  const page = KIND_INFO[browse.kind].page;
  link.addEventListener('click', (e) => { e.preventDefault(); polar.openLink(`https://modrinth.com/${page}/${encodeURIComponent(hit.slug || hit.id)}`); });
  return h('div', { class: 'mr-card', 'data-id': hit.id },
    modIcon(hit.icon),
    h('div', { class: 'mr-body' },
      h('div', { class: 'mr-title' }, h('b', { text: hit.title }), hit.author ? h('small', { text: `by ${hit.author}` }) : null),
      h('div', { class: 'mr-desc', text: hit.description }),
      h('div', { class: 'mr-meta' },
        h('span', { title: `${hit.downloads.toLocaleString()} downloads` }, icon('i-download'), fmtCount(hit.downloads)),
        h('span', { title: `${hit.follows.toLocaleString()} followers` }, icon('i-heart'), fmtCount(hit.follows)),
        hit.updated ? h('span', { text: `Updated ${timeAgo(new Date(hit.updated).getTime())}` }) : null,
        ...hit.categories.slice(0, 2).map((c) => h('span', { class: 'tag', text: c.replace(/-/g, ' ') })))),
    h('div', { class: 'mr-actions' }, installButton(hit), link));
}
function renderResults() {
  const list = $('browseResults');
  if (browse.error && !browse.hits.length) {
    list.replaceChildren(h('div', { class: 'empty-state' }, h('b', { text: "Modrinth didn't answer" }), 'Check your internet connection and try again.'));
  } else if (!browse.hits.length && !browse.loading) {
    list.replaceChildren(h('div', { class: 'empty-state' }, h('b', { text: `No ${KIND_INFO[browse.kind].many} found` }),
      `Nothing on Modrinth matches that for Minecraft ${currentVersion().id}. Try other words or another category.`));
  } else {
    list.replaceChildren(...browse.hits.map(modCard));
  }
  const more = browse.hits.length < browse.total;
  $('browseMore').hidden = !more;
  $('browseMore').disabled = browse.loading;
}
function refreshCard(id) {
  const hit = browse.hits.find((x) => x.id === id);
  const old = document.querySelector(`.mr-card[data-id="${CSS.escape(id)}"]`);
  if (hit && old) old.replaceWith(modCard(hit));
}
async function installMod(hit) {
  if (browse.busy.has(hit.id)) return;
  browse.busy.add(hit.id);
  refreshCard(hit.id);
  try {
    const res = await polar.modrinth.install({ id: hit.id, slug: hit.slug, title: hit.title, icon: hit.icon, author: hit.author });
    browse.state = res.state;
    browse.updates = browse.updates.filter((u) => u.projectId !== hit.id);
    renderUpdateBadge();
    $('browseCount').textContent = browse.state.added.length ? String(browse.state.added.length) : '';
    state.settings.optionalMods = res.state.optional;
    const parts = [];
    if (res.installed.length) parts.push(`Installed ${res.installed[0]}${res.installed.length > 1 ? ` and ${res.installed.length - 1} mod${res.installed.length > 2 ? 's' : ''} it needs` : ''}`);
    if (res.enabled.length) parts.push(`turned on ${res.enabled.join(', ')} on the Mods page (installed when you press Play)`);
    toast(`${parts.join('; ')} for Minecraft ${currentVersion().id}.${state.running ? ' Restart the game to load it.' : ''}`, 'info', 7000);
    notify('Mod installed', hit.title, 'ok');
    renderMods();
  } catch (e) {
    toast(e.message, 'error', 8000);
  } finally {
    browse.busy.delete(hit.id);
    refreshCard(hit.id);
    if (browse.tab === 'installed') renderInstalled();
  }
}
async function removeMod(projectId, title) {
  try {
    const res = await polar.modrinth.remove(projectId);
    browse.state = res.state;
    $('browseCount').textContent = browse.state.added.length ? String(browse.state.added.length) : '';
    toast(`Removed ${res.title || title}.${state.running ? ' Restart the game to unload it.' : ''}`);
    refreshCard(projectId);
    if (browse.tab === 'installed') renderInstalled();
  } catch (e) {
    toast(e.message, 'error');
  }
}
function renderUpdateBadge() {
  const n = browse.updates.length;
  $('browseBadge').hidden = !n;
  $('browseBadge').textContent = String(n);
}
/** Checks Modrinth for newer builds of the mods added from Browse (at most every 30 minutes per version). */
async function checkModUpdates(force = false) {
  const v = currentVersion().id;
  if (!force && browse.updatesFor === v && Date.now() - browse.updatesAt < 30 * 60 * 1000) return;
  try {
    const res = await polar.modrinth.updates();
    browse.updates = res.updates;
    browse.updatesFor = res.version;
    browse.updatesAt = Date.now();
    renderUpdateBadge();
    if (res.updates.length) notify('Mod updates', `${res.updates.length} mod${res.updates.length > 1 ? 's have' : ' has'} a newer version for Minecraft ${res.version}`, 'info');
    if (browse.tab === 'installed' && $('page-browse').classList.contains('active')) renderInstalled();
  } catch {
    // offline: try again later
  }
}
async function updateAll() {
  for (const u of [...browse.updates]) await installMod({ id: u.projectId, slug: u.slug, title: u.title, icon: u.icon, author: u.author });
}
async function loadPacks() {
  try {
    browse.packs = await polar.packs.list();
  } catch {
    browse.packs = { profiles: [], active: null };
  }
}
function profileBar() {
  const { profiles, active } = browse.packs;
  const name = h('input', { type: 'text', placeholder: 'Profile name, e.g. PvP', maxlength: '32', spellcheck: 'false' });
  const save = h('button', { class: 'btn small primary' }, icon('i-plus'), 'Save current');
  const doSave = async () => {
    try {
      await polar.packs.save(name.value);
      toast(`Saved mod profile ${name.value.trim()}.`);
      await loadPacks();
      renderInstalled();
    } catch (e) {
      toast(e.message, 'error');
    }
  };
  save.addEventListener('click', doSave);
  name.addEventListener('keydown', (e) => { if (e.key === 'Enter') doSave(); });
  const chips = profiles.map((p) => {
    const apply = h('button', { text: p.name, title: `${p.mods.length} Browse mods · ${p.optionalMods.length} from the Mods page` });
    apply.addEventListener('click', async () => {
      if (p.name === active) return;
      apply.textContent = 'Switching…';
      try {
        const res = await polar.packs.apply(p.name);
        browse.state = res.state;
        state.settings.optionalMods = res.state.optional;
        await loadPacks();
        renderMods();
        renderInstalled();
        const bits = [];
        if (res.installed.length) bits.push(`installed ${res.installed.length}`);
        if (res.removed.length) bits.push(`removed ${res.removed.length}`);
        toast(`Switched to ${p.name}${bits.length ? ` (${bits.join(', ')})` : ''}.`);
        if (res.failed.length) toast(`Couldn't install: ${res.failed.join('; ')}`, 'error', 9000);
      } catch (e) {
        toast(e.message, 'error');
        renderInstalled();
      }
    });
    const del = h('button', { class: 'x', title: 'Delete profile', text: '×' });
    del.addEventListener('click', async () => {
      await polar.packs.remove(p.name).catch((e) => toast(e.message, 'error'));
      await loadPacks();
      renderInstalled();
    });
    return h('span', { class: `profile-chip${p.name === active ? ' active' : ''}` }, apply, del);
  });
  return h('div', { class: 'profile-bar' }, h('span', { class: 'label', text: 'Mod profiles' }),
    ...(chips.length ? chips : [h('span', { class: 'hint', text: 'Save the mods you have now as a profile, then switch with one click.' })]),
    h('span', { class: 'grow' }), name, save);
}
function renderInstalled() {
  const { added, own } = browse.state;
  const box = $('browseInstalled');
  const parts = [profileBar()];
  const head = h('h2', { class: 'section-title' }, 'Added from Modrinth ', h('span', { class: 'muted', text: `· Minecraft ${currentVersion().id}` }));
  parts.push(head);
  if (browse.updates.length) {
    const all = h('button', { class: 'btn small update' }, icon('i-download'), `Update all (${browse.updates.length})`);
    all.addEventListener('click', updateAll);
    parts.push(h('div', { class: 'row-actions' }, all));
  }
  if (!added.length) {
    parts.push(h('div', { class: 'empty-state' }, h('b', { text: 'Nothing added yet' }), 'Find mods on the Discover tab and press Install.'));
  } else {
    parts.push(h('div', { class: 'installed-list' }, ...added.map((e) => {
      const rm = h('button', { class: 'btn small danger' }, icon('i-trash'), 'Remove');
      rm.addEventListener('click', () => removeMod(e.projectId, e.title));
      const upd = browse.updates.find((u) => u.projectId === e.projectId);
      let updBtn = null;
      if (upd) {
        updBtn = h('button', { class: 'btn small update' }, icon('i-download'), `Update to ${upd.latest}`);
        updBtn.addEventListener('click', () => installMod({ id: e.projectId, slug: e.slug, title: e.title, icon: e.icon, author: e.author }));
      }
      const deps = (e.deps || []).map((d) => d.title);
      return h('div', { class: 'installed-row' }, modIcon(e.icon),
        h('div', {}, h('b', { text: e.title }), h('small', { text: `Version ${e.version}${deps.length ? `  ·  also installed: ${deps.join(', ')}` : ''}` })),
        h('div', { class: 'row-actions' }, updBtn, rm));
    })));
  }
  if (own.length) {
    parts.push(h('h2', { class: 'section-title' }, 'Your own mods ', h('span', { class: 'muted', text: '· files you put in the mods folder' })));
    parts.push(h('div', { class: 'installed-list' }, ...own.map((f) => h('div', { class: 'installed-row' }, h('div', { class: 'mr-icon' }, icon('i-cube')),
      h('div', {}, h('b', { text: f }), h('small', { text: 'Added by you. Open the mods folder to remove it.' })), h('span')))));
  }
  parts.push(...packSections());
  const folderButton = (label, which) => {
    const b = h('button', { class: 'btn ghost' }, icon('i-folder'), label);
    b.addEventListener('click', () => polar.game.openFolder(which).catch((err) => toast(err.message, 'error')));
    return b;
  };
  parts.push(h('div', { class: 'row-actions end' }, folderButton('Mods folder', 'mods'), folderButton('Resource packs', 'resourcepacks'),
    currentVersion().loader === 'legacy-fabric' ? null : folderButton('Shader packs', 'shaderpacks')));
  box.replaceChildren(...parts);
}
/** Resource packs (switch on/off) and shader packs (use one) on the Installed tab. */
function packSections() {
  const st = browse.state;
  const packsOf = (kind) => (st.packs && st.packs[kind]) || { added: [], own: [] };
  const out = [];
  const lock = state.running ? 'Close Minecraft to switch packs here (or switch them in game).' : null;

  const rp = packsOf('resourcepack');
  out.push(h('h2', { class: 'section-title' }, 'Resource packs ', h('span', { class: 'muted', text: '· on ones load at the next start, top of the list wins' })));
  const rpRows = [...rp.added.map((e) => ({ file: e.filename, title: e.title, icon: e.icon, sub: `Version ${e.version}`, projectId: e.projectId })),
    ...rp.own.map((f) => ({ file: f, title: f.replace(/\.zip$/i, ''), icon: null, sub: 'Added by you', projectId: null }))];
  if (!rpRows.length) out.push(h('div', { class: 'empty-state' }, h('b', { text: 'No resource packs yet' }), 'Find them on Discover > Resource packs.'));
  else {
    out.push(h('div', { class: 'installed-list' }, ...rpRows.map((r) => {
      const on = (st.enabledPacks || []).includes(r.file);
      const sw = h('button', { class: `switch-btn${on ? ' on' : ''}`, title: lock || (on ? 'Switch off' : 'Switch on'), disabled: !!lock }, h('span'));
      sw.addEventListener('click', async () => {
        try {
          browse.state = await polar.modrinth.toggleResource(r.file, !on);
          renderInstalled();
        } catch (e) {
          toast(e.message, 'error', 7000);
        }
      });
      let rm = null;
      if (r.projectId) {
        rm = h('button', { class: 'btn small danger' }, icon('i-trash'), 'Remove');
        rm.addEventListener('click', () => removePack('resourcepack', r.projectId, r.title));
      }
      return h('div', { class: 'installed-row' }, r.icon ? modIcon(r.icon) : h('div', { class: 'mr-icon' }, icon('i-image')),
        h('div', {}, h('b', { text: r.title }), h('small', { text: `${r.sub}  ·  ${on ? 'On' : 'Off'}` })), h('div', { class: 'row-actions' }, rm, sw));
    })));
  }

  if (!st.shadersSupported) return out;
  const sp = packsOf('shader');
  const off = h('button', { class: 'btn small ghost', disabled: !st.shader || !!lock, title: lock || '' }, 'Turn shaders off');
  off.addEventListener('click', async () => {
    try {
      browse.state = await polar.modrinth.useShader(null);
      renderInstalled();
    } catch (e) {
      toast(e.message, 'error', 7000);
    }
  });
  out.push(h('div', { class: 'section-head' }, h('h2', { class: 'section-title' }, 'Shader packs ', h('span', { class: 'muted', text: '· one at a time, with Iris' })), off));
  const spRows = [...sp.added.map((e) => ({ file: e.filename, title: e.title, icon: e.icon, sub: `Version ${e.version}`, projectId: e.projectId })),
    ...sp.own.map((f) => ({ file: f, title: f.replace(/\.zip$/i, ''), icon: null, sub: 'Added by you', projectId: null }))];
  if (!spRows.length) out.push(h('div', { class: 'empty-state' }, h('b', { text: 'No shader packs yet' }), 'Find them on Discover > Shaders.'));
  else {
    out.push(h('div', { class: 'installed-list' }, ...spRows.map((r) => {
      const inUse = st.shader === r.file;
      const use = h('button', { class: `btn small ${inUse ? 'done' : 'primary'}`, disabled: inUse || !!lock, title: lock || '' }, icon(inUse ? 'i-check' : 'i-sun'), inUse ? 'In use' : 'Use');
      use.addEventListener('click', async () => {
        try {
          browse.state = await polar.modrinth.useShader(r.file);
          state.settings.optionalMods = browse.state.optional;
          renderInstalled();
          toast(`${r.title} will load with Iris next time you play.`);
        } catch (e) {
          toast(e.message, 'error', 7000);
        }
      });
      let rm = null;
      if (r.projectId) {
        rm = h('button', { class: 'btn small danger' }, icon('i-trash'), 'Remove');
        rm.addEventListener('click', () => removePack('shader', r.projectId, r.title));
      }
      return h('div', { class: 'installed-row' }, r.icon ? modIcon(r.icon) : h('div', { class: 'mr-icon' }, icon('i-sun')),
        h('div', {}, h('b', { text: r.title }), h('small', { text: r.sub })), h('div', { class: 'row-actions' }, rm, use));
    })));
  }
  return out;
}
function setBrowseTab(tab) {
  browse.tab = tab;
  document.querySelectorAll('#browseTabs button').forEach((b) => b.classList.toggle('active', b.dataset.tab === tab));
  $('browseDiscover').hidden = tab !== 'discover';
  $('browseInstalled').hidden = tab !== 'installed';
  if (tab === 'installed') Promise.all([loadBrowserState(), loadPacks()]).then(renderInstalled);
}
async function openBrowse() {
  const v = currentVersion();
  $('browseSub').textContent = `Mods, resource packs and shaders from Modrinth for Minecraft ${v.id} (${v.loaderName || 'Fabric'}). They go into this version's own folder.`;
  if (!state.catalog) state.catalog = await polar.mods.catalog().catch(() => []);
  await loadBrowserState();
  if (browse.loadedFor !== `${v.id}:${browse.kind}` || browse.error) runSearch();
  else renderResults();
  if (browse.tab === 'installed') {
    await loadPacks();
    renderInstalled();
  }
  checkModUpdates();
}
document.querySelectorAll('#browseTabs button').forEach((b) => b.addEventListener('click', () => setBrowseTab(b.dataset.tab)));
document.querySelectorAll('#browseKinds button').forEach((b) => b.addEventListener('click', () => setBrowseKind(b.dataset.kind)));
let browseTimer = null;
$('browseQuery').addEventListener('input', () => {
  clearTimeout(browseTimer);
  browseTimer = setTimeout(() => {
    browse.query = $('browseQuery').value.trim();
    runSearch();
  }, 350);
});
$('browseQuery').addEventListener('keydown', (e) => {
  if (e.key === 'Enter') {
    clearTimeout(browseTimer);
    browse.query = $('browseQuery').value.trim();
    runSearch();
  }
});
$('browseSort').addEventListener('change', () => {
  browse.sort = $('browseSort').value;
  runSearch();
});
$('browseMore').addEventListener('click', () => runSearch(true));
$('modrinthLink').addEventListener('click', (e) => { e.preventDefault(); polar.openLink(`https://modrinth.com/${KIND_INFO[browse.kind].many.replace(' ', '').replace('shaderpacks', 'shaders')}`); });

// ---------------------------------------------------------------- skin library
let skinLib = [];
async function loadSkinLibrary() {
  try {
    skinLib = await polar.skins.list();
  } catch (e) {
    skinLib = [];
    toast(e.message, 'error');
  }
  renderSkinLibrary();
}
function canUseSkins() {
  const current = state.accounts.accounts.find((a) => a.uuid === state.accounts.selected);
  return current && current.type !== 'offline';
}
function renderSkinLibrary() {
  $('skinLibCount').textContent = skinLib.length ? `· ${skinLib.length}` : '';
  $('saveCurrentSkin').disabled = !canUseSkins();
  const grid = $('skinLibGrid');
  if (!skinLib.length) {
    grid.replaceChildren(h('div', { class: 'empty-state' }, h('b', { text: 'No saved skins yet' }),
      'Save the skin you wear now, or add skin files, then switch between them with one click.'));
    return;
  }
  grid.replaceChildren(...skinLib.map((sk) => {
    const cv = h('canvas', { width: 72, height: 108 });
    drawSkin(cv, sk.data, sk.variant === 'slim', 'front').catch(() => {});
    const title = h('div', { class: 'skin-title' }, h('b', { text: sk.name, title: sk.name }));
    const edit = h('button', { class: 'mini', title: 'Rename' }, icon('i-edit'));
    edit.addEventListener('click', () => {
      const input = h('input', { type: 'text', value: sk.name, maxlength: '32', spellcheck: 'false' });
      const done = async (save) => {
        if (save && input.value.trim() && input.value.trim() !== sk.name) {
          try {
            skinLib = await polar.skins.update(sk.id, { name: input.value });
          } catch (e) {
            toast(e.message, 'error');
          }
        }
        renderSkinLibrary();
      };
      input.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') done(true);
        if (e.key === 'Escape') done(false);
      });
      input.addEventListener('blur', () => done(true));
      title.replaceWith(input);
      input.focus();
      input.select();
    });
    title.append(edit);
    const variant = h('button', { class: 'variant', title: 'Arm style (click to change)', text: sk.variant === 'slim' ? 'Slim arms' : 'Classic arms' });
    variant.addEventListener('click', async () => {
      try {
        skinLib = await polar.skins.update(sk.id, { variant: sk.variant === 'slim' ? 'classic' : 'slim' });
        renderSkinLibrary();
      } catch (e) {
        toast(e.message, 'error');
      }
    });
    const use = h('button', { class: 'btn small primary', disabled: !canUseSkins(), title: canUseSkins() ? '' : 'Needs a Microsoft account' }, icon('i-check'), 'Use');
    use.addEventListener('click', async () => {
      use.disabled = true;
      use.lastChild.textContent = 'Uploading…';
      try {
        state.profile = await polar.skins.apply(sk.id);
        renderProfile();
        toast(`Now wearing ${sk.name}. It shows in game the next time you join a world.`);
        notify('Skin changed', sk.name, 'ok');
      } catch (e) {
        toast(e.message, 'error', 8000);
      } finally {
        renderSkinLibrary();
      }
    });
    const del = h('button', { class: 'icon-btn del', title: 'Delete from the library' }, icon('i-trash'));
    del.addEventListener('click', async () => {
      if (!del.dataset.confirm) {
        del.dataset.confirm = '1';
        del.classList.add('danger');
        del.title = 'Click again to delete';
        setTimeout(() => { delete del.dataset.confirm; del.classList.remove('danger'); del.title = 'Delete from the library'; }, 2500);
        return;
      }
      try {
        skinLib = await polar.skins.remove(sk.id);
        renderSkinLibrary();
      } catch (e) {
        toast(e.message, 'error');
      }
    });
    return h('div', { class: 'skin-item' }, del, cv, title, variant, h('div', { class: 'row-actions' }, use));
  }));
}
$('saveCurrentSkin').addEventListener('click', async () => {
  try {
    skinLib = await polar.skins.saveCurrent();
    renderSkinLibrary();
    toast('Saved your current skin to the library.');
  } catch (e) {
    toast(e.message, 'error', 8000);
  }
});
$('addSkinFiles').addEventListener('click', async () => {
  const variant = document.querySelector('#skinModel button.active').dataset.model;
  try {
    const res = await polar.skins.addFiles(variant);
    skinLib = res.list;
    renderSkinLibrary();
    if (res.added) toast(`Added ${res.added} skin${res.added > 1 ? 's' : ''} to the library.`);
    if (res.errors.length) toast(res.errors.join('\n'), 'error', 9000);
  } catch (e) {
    toast(e.message, 'error', 8000);
  }
});

// ---------------------------------------------------------------- news
let newsItems = [];
const seenNews = () => new Set(store.get('polar.newsSeen', []));
function newsDate(d) {
  return new Date(`${d}T12:00:00`).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' });
}
function newsImage(src) {
  if (!src) return null;
  const img = h('img', { src, alt: '', loading: 'lazy' });
  img.addEventListener('error', () => img.remove());
  return img;
}
function renderNews() {
  $('newsPanel').hidden = !newsItems.length;
  const seen = seenNews();
  $('newsGrid').replaceChildren(...newsItems.slice(0, 3).map((n) => {
    const card = h('button', { class: 'news-card' }, newsImage(n.image),
      h('div', { class: 'news-meta' }, seen.has(n.id) ? null : h('span', { class: 'news-new', title: 'New' }), n.tag ? h('span', { class: 'news-tag', text: n.tag }) : null,
        h('span', { text: newsDate(n.date) })),
      h('b', { text: n.title }), h('p', { text: n.body }));
    card.addEventListener('click', () => openNews(n.id));
    return card;
  }));
  $('allNews').hidden = newsItems.length <= 3;
}
function openNews(id) {
  $('newsList').replaceChildren(...newsItems.map((n) => {
    let link = null;
    if (n.link) {
      link = h('button', { class: 'btn small ghost' }, icon('i-external'), n.linkLabel);
      link.addEventListener('click', () => polar.openLink(n.link));
    }
    return h('div', { class: 'news-item', 'data-id': n.id }, newsImage(n.image),
      h('div', { class: 'news-meta' }, n.tag ? h('span', { class: 'news-tag', text: n.tag }) : null, h('span', { text: newsDate(n.date) })),
      h('b', { text: n.title }), h('p', { text: n.body }), link);
  }));
  openModal('newsModal');
  if (id) {
    const el = $('newsList').querySelector(`[data-id="${CSS.escape(id)}"]`);
    if (el) el.scrollIntoView({ block: 'start' });
  }
  markNewsSeen();
}
function markNewsSeen() {
  store.set('polar.newsSeen', [...new Set([...seenNews(), ...newsItems.map((n) => n.id)])].slice(-100));
  renderNews();
}
async function loadNews() {
  try {
    const res = await polar.news();
    newsItems = res.items || [];
  } catch {
    newsItems = [];
  }
  const seen = seenNews();
  const fresh = newsItems.filter((n) => !seen.has(n.id));
  // the first start just shows the news; after that, new posts also go to the bell
  if (store.get('polar.newsSeen', null) === null) store.set('polar.newsSeen', []);
  else for (const n of fresh.slice(0, 3).reverse()) notify('News', n.title, 'info');
  renderNews();
}
$('allNews').addEventListener('click', () => openNews());

// ---------------------------------------------------------------- screenshots
const shots = { list: [], filter: 'all', open: -1 };
function visibleShots() {
  return shots.filter === 'all' ? shots.list : shots.list.filter((x) => x.version === shots.filter);
}
async function loadShots() {
  try {
    shots.list = await polar.shots.list();
  } catch (e) {
    shots.list = [];
    toast(e.message, 'error');
  }
  renderShots();
}
function renderShots() {
  const versions = [...new Set(shots.list.map((x) => x.version))];
  if (shots.filter !== 'all' && !versions.includes(shots.filter)) shots.filter = 'all';
  $('shotFilter').replaceChildren(...['all', ...versions].map((v) => {
    const b = h('button', { class: shots.filter === v ? 'active' : '', text: v === 'all' ? `All (${shots.list.length})` : v });
    b.addEventListener('click', () => { shots.filter = v; renderShots(); });
    return b;
  }));
  $('shotFilter').hidden = versions.length < 2;
  const list = visibleShots();
  if (!list.length) {
    $('shotGrid').replaceChildren(h('div', { class: 'empty-state' }, h('b', { text: 'No screenshots yet' }), 'Press F2 in game to take one. They show up here.'));
    return;
  }
  const io = new IntersectionObserver((entries) => {
    for (const en of entries) {
      if (!en.isIntersecting) continue;
      io.unobserve(en.target);
      const img = en.target.querySelector('img');
      polar.shots.thumb(en.target.dataset.id).then((url) => { img.src = url; }).catch(() => {});
    }
  }, { rootMargin: '300px' });
  $('shotGrid').replaceChildren(...list.map((x, i) => {
    const card = h('div', { class: 'shot', 'data-id': x.id, title: x.name },
      h('img', { alt: '', 'data-fallback': '1' }),
      h('span', { text: `${x.version} · ${new Date(x.mtime).toLocaleDateString()}` }));
    card.addEventListener('click', () => openShot(i));
    io.observe(card);
    return card;
  }));
}
async function openShot(i) {
  const list = visibleShots();
  if (!list.length) return;
  shots.open = (i + list.length) % list.length;
  const x = list[shots.open];
  $('shotInfo').textContent = `${x.name} · Minecraft ${x.version} · ${(x.size / 1048576).toFixed(1)} MB`;
  openModal('shotModal');
  $('shotImg').removeAttribute('src');
  try {
    $('shotImg').src = await polar.shots.full(x.id);
  } catch (e) {
    toast(e.message, 'error');
  }
}
const currentShot = () => visibleShots()[shots.open];
$('shotPrev').addEventListener('click', () => openShot(shots.open - 1));
$('shotNext').addEventListener('click', () => openShot(shots.open + 1));
$('shotCopy').addEventListener('click', () => polar.shots.copy(currentShot().id).then(() => toast('Screenshot copied to the clipboard.')).catch((e) => toast(e.message, 'error')));
$('shotReveal').addEventListener('click', () => polar.shots.reveal(currentShot().id).catch((e) => toast(e.message, 'error')));
$('shotDelete').addEventListener('click', async () => {
  const btn = $('shotDelete');
  if (!btn.dataset.confirm) {
    btn.dataset.confirm = '1';
    btn.lastChild.textContent = 'Delete?';
    setTimeout(() => { delete btn.dataset.confirm; btn.lastChild.textContent = 'Delete'; }, 2500);
    return;
  }
  delete btn.dataset.confirm;
  btn.lastChild.textContent = 'Delete';
  try {
    shots.list = await polar.shots.remove(currentShot().id);
    toast('Moved to the Recycle Bin.');
    renderShots();
    if (visibleShots().length) openShot(Math.min(shots.open, visibleShots().length - 1));
    else $('shotModal').hidden = true;
  } catch (e) {
    toast(e.message, 'error');
  }
});
document.addEventListener('keydown', (e) => {
  if ($('shotModal').hidden) return;
  if (e.key === 'ArrowLeft') openShot(shots.open - 1);
  if (e.key === 'ArrowRight') openShot(shots.open + 1);
});

// ---------------------------------------------------------------- crash helper
let lastCrash = null;
polar.on('game:crash', (c) => {
  lastCrash = c;
  const a = c.analysis || {};
  $('crashTitle').textContent = a.title || 'Minecraft closed unexpectedly';
  $('crashDetail').textContent = a.detail || '';
  $('crashFix').textContent = a.fix || '';
  $('crashMeta').textContent = `Minecraft ${c.version} · exit code ${c.code}${a.suspects && a.suspects.length ? ` · suspected: ${a.suspects.join(', ')}` : ''}`;
  $('crashReport').hidden = !c.report;
  const action = $('crashAction');
  const actions = { mods: ['Open Browse', () => { setPage('browse'); setBrowseTab('installed'); }], memory: ['Open Settings', () => setPage('settings')],
    java: ['Open Settings', () => setPage('settings')] };
  const pick = actions[a.action];
  action.hidden = !pick;
  if (pick) {
    action.textContent = pick[0];
    action.onclick = () => { $('crashModal').hidden = true; pick[1](); };
  }
  openModal('crashModal');
  notify('Minecraft crashed', a.title || '', 'error');
});
$('crashCopy').addEventListener('click', () => {
  if (!lastCrash) return;
  navigator.clipboard.writeText(lastCrash.log || '').then(() => toast('Log copied.')).catch(() => toast("Couldn't copy the log.", 'error'));
});
$('crashReport').addEventListener('click', () => polar.crash.openReport().catch((e) => toast(e.message, 'error')));

// ---------------------------------------------------------------- launcher updates
async function checkLauncherUpdate() {
  try {
    const u = await polar.update.check();
    if (!u) return;
    $('updateText').textContent = `Polar Client ${u.version} is available (you have ${state.info.version}).`;
    $('updateBanner').hidden = false;
    notify('Launcher update', `Version ${u.version} is ready to install`, 'info');
  } catch {
    // offline or no releases yet
  }
}
$('updateLater').addEventListener('click', () => { $('updateBanner').hidden = true; });
$('updateBtn').addEventListener('click', async () => {
  const btn = $('updateBtn');
  btn.disabled = true;
  btn.textContent = 'Downloading…';
  try {
    await polar.update.install();
    $('updateText').textContent = 'Starting the installer… Polar Client will close.';
  } catch (e) {
    toast(e.message, 'error', 8000);
    btn.disabled = false;
    btn.textContent = 'Update now';
  }
});

// ---------------------------------------------------------------- cosmetics
function loadImage(src) {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => resolve(img);
    img.onerror = reject;
    img.src = src;
  });
}
/** Draws a flat front or back view of a Minecraft skin (64x64, or legacy 64x32). */
async function drawSkin(canvas, dataUrl, slim, side) {
  const ctx = canvas.getContext('2d');
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  ctx.imageSmoothingEnabled = false;
  if (!dataUrl) return;
  const img = await loadImage(dataUrl);
  const legacy = img.height === 32;
  const u = Math.floor(Math.min(canvas.width / 16, canvas.height / 32));
  const ox = Math.floor((canvas.width - 16 * u) / 2), oy = Math.floor((canvas.height - 32 * u) / 2);
  const part = (sx, sy, w, hgt, dx, dy, flip = false) => {
    ctx.save();
    if (flip) {
      ctx.translate(ox + (dx + w) * u, oy + dy * u);
      ctx.scale(-1, 1);
      ctx.drawImage(img, sx, sy, w, hgt, 0, 0, w * u, hgt * u);
    } else {
      ctx.drawImage(img, sx, sy, w, hgt, ox + dx * u, oy + dy * u, w * u, hgt * u);
    }
    ctx.restore();
  };
  const aw = slim ? 3 : 4;
  if (side === 'front') {
    part(8, 8, 8, 8, 4, 0); part(40, 8, 8, 8, 4, 0);
    part(20, 20, 8, 12, 4, 8); if (!legacy) part(20, 36, 8, 12, 4, 8);
    part(44, 20, aw, 12, 4 - aw, 8); if (!legacy) part(44, 36, aw, 12, 4 - aw, 8);
    if (legacy) part(44, 20, 4, 12, 12, 8, true); else { part(36, 52, aw, 12, 12, 8); part(52, 52, aw, 12, 12, 8); }
    part(4, 20, 4, 12, 4, 20); if (!legacy) part(4, 36, 4, 12, 4, 20);
    if (legacy) part(4, 20, 4, 12, 8, 20, true); else { part(20, 52, 4, 12, 8, 20); part(4, 52, 4, 12, 8, 20); }
  } else {
    part(24, 8, 8, 8, 4, 0); part(56, 8, 8, 8, 4, 0);
    part(32, 20, 8, 12, 4, 8); if (!legacy) part(32, 36, 8, 12, 4, 8);
    // the player's right arm is on the right when seen from behind
    part(44 + 4 + aw, 20, aw, 12, 12, 8); if (!legacy) part(44 + 4 + aw, 36, aw, 12, 12, 8);
    if (legacy) part(52, 20, 4, 12, 4 - 4, 8, true); else { part(36 + 4 + aw, 52, aw, 12, 4 - aw, 8); part(52 + 4 + aw, 52, aw, 12, 4 - aw, 8); }
    part(12, 20, 4, 12, 8, 20); if (!legacy) part(12, 36, 4, 12, 8, 20);
    if (legacy) part(12, 20, 4, 12, 4, 20, true); else { part(28, 52, 4, 12, 4, 20); part(12, 52, 4, 12, 4, 20); }
  }
}
async function drawCape(canvas, dataUrl) {
  const ctx = canvas.getContext('2d');
  ctx.imageSmoothingEnabled = false;
  const img = await loadImage(dataUrl);
  const s = img.width / 64; // HD capes scale with the texture
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  ctx.drawImage(img, 1 * s, 1 * s, 10 * s, 16 * s, 0, 0, canvas.width, canvas.height);
}
function renderProfile() {
  const p = state.profile;
  $('cosmetics').hidden = !p;
  if (!p) return;
  $('skinName').textContent = p.name;
  const slim = p.skin && p.skin.variant === 'SLIM';
  drawSkin($('skinCanvas'), p.skin && p.skin.data, slim, state.skinSide).catch(() => {});
  const grid = $('capeGrid');
  const none = h('button', { class: `cape${p.capes.some((c) => c.active) ? '' : ' active'}`, onclick: () => setCape(null) }, h('div', { class: 'none' }), h('span', { text: 'No cape' }));
  grid.replaceChildren(none, ...p.capes.map((c) => {
    const cv = h('canvas', { width: 50, height: 80 });
    if (c.data) drawCape(cv, c.data).catch(() => {});
    return h('button', { class: `cape${c.active ? ' active' : ''}`, title: c.alias, onclick: () => setCape(c.id) }, cv, h('span', { text: c.alias }));
  }));
  $('capeCount').textContent = p.capes.length ? `· ${p.capes.length} owned` : '· none owned yet';
}
async function loadProfile(force) {
  const current = state.accounts.accounts.find((a) => a.uuid === state.accounts.selected);
  const empty = $('cosmeticsEmpty');
  if (!current || current.type === 'offline') {
    state.profile = null;
    renderProfile();
    empty.hidden = false;
    empty.replaceChildren(h('b', { text: current ? 'Offline profiles use the default skin' : 'No account yet' }),
      h('span', { text: 'Skins and capes belong to a Microsoft account. Switch to one to change them here.' }),
      h('div', {}, h('button', { class: 'btn primary', onclick: () => openModal('accountsModal') }, icon('i-user'), 'Accounts')));
    return;
  }
  empty.hidden = true;
  if (state.profile && state.profile.id === current.uuid && !force) return renderProfile();
  $('cosmetics').hidden = true;
  empty.hidden = false;
  empty.replaceChildren(h('b', { text: 'Loading your skin…' }));
  try {
    state.profile = await polar.profile.get();
    empty.hidden = true;
    renderProfile();
  } catch (e) {
    empty.replaceChildren(h('b', { text: "Couldn't load your profile" }), h('span', { text: e.message }));
  }
}
async function setCape(id) {
  try {
    state.profile = await polar.profile.setCape(id);
    renderProfile();
    toast(id ? 'Cape equipped.' : 'Cape hidden.');
  } catch (e) {
    toast(e.message, 'error', 8000);
  }
}
document.querySelectorAll('#skinSide button').forEach((b) => b.addEventListener('click', () => {
  state.skinSide = b.dataset.side;
  document.querySelectorAll('#skinSide button').forEach((x) => x.classList.toggle('active', x === b));
  renderProfile();
}));
document.querySelectorAll('#skinModel button').forEach((b) => b.addEventListener('click', () => {
  document.querySelectorAll('#skinModel button').forEach((x) => x.classList.toggle('active', x === b));
}));
$('uploadSkin').addEventListener('click', async () => {
  const variant = document.querySelector('#skinModel button.active').dataset.model;
  try {
    const p = await polar.profile.uploadSkin(variant);
    if (!p) return;
    state.profile = p;
    renderProfile();
    toast('Skin uploaded. It shows in game the next time you join a world.');
    notify('Skin updated', '', 'ok');
  } catch (e) {
    toast(e.message, 'error', 8000);
  }
});
$('resetSkin').addEventListener('click', async () => {
  try {
    state.profile = await polar.profile.resetSkin();
    renderProfile();
    toast('Skin reset to the default.');
  } catch (e) {
    toast(e.message, 'error', 8000);
  }
});
$('reloadProfile').addEventListener('click', () => loadProfile(true));

// ---------------------------------------------------------------- settings
function bindSettings() {
  const s = state.settings;
  const mem = $('memSlider');
  const updateMem = () => {
    $('memValue').textContent = `${mem.value} GB`;
    mem.style.setProperty('--fill', `${((mem.value - mem.min) / (mem.max - mem.min)) * 100}%`);
  };
  mem.value = s.memoryGb;
  updateMem();
  mem.addEventListener('input', () => { updateMem(); saveSettings({ memoryGb: Number(mem.value) }); });
  const check = (id, key) => {
    $(id).checked = Boolean(s[key]);
    $(id).addEventListener('change', (e) => saveSettings({ [key]: e.target.checked }));
  };
  check('fullscreen', 'fullscreen');
  check('customRes', 'customResolution');
  check('closeOnLaunch', 'closeOnLaunch');
  check('discordStatus', 'discordStatus');
  $('polarSync').checked = s.polarSync !== false;
  $('polarSync').addEventListener('change', (e) => saveSettings({ polarSync: e.target.checked }));
  if (state.info && !state.info.discord) {
    $('discordStatus').disabled = true;
    $('discordStatus').closest('label').classList.add('disabled');
    $('discordHint').textContent = 'Not available in this build: it needs a Discord application id (see the README).';
  }
  const text = (id, key, transform = (v) => v) => {
    $(id).value = s[key] ?? '';
    $(id).addEventListener('input', (e) => saveSettings({ [key]: transform(e.target.value) }));
  };
  text('resW', 'width', Number);
  text('resH', 'height', Number);
  text('javaPath', 'javaPath', (v) => v.trim());
  text('jvmArgs', 'jvmArgs');
  $('snowToggle').checked = snow.on;
  $('snowToggle').addEventListener('change', (e) => snow.set(e.target.checked));
  $('pickJava').addEventListener('click', async () => {
    const p = await polar.game.pickJava();
    if (p) {
      $('javaPath').value = p;
      saveSettings({ javaPath: p });
    }
  });
  $('openGameFolder').addEventListener('click', () => polar.game.openFolder('game').catch((e) => toast(e.message, 'error')));
  $('openLogs').addEventListener('click', () => polar.game.openFolder('logs').catch((e) => toast(e.message, 'error')));
  $('manageAccounts').addEventListener('click', () => openModal('accountsModal'));
}

// ---------------------------------------------------------------- console
const consoleEl = $('console');
let logLines = 0;
polar.on('game:log', (line) => {
  const atBottom = consoleEl.scrollHeight - consoleEl.scrollTop - consoleEl.clientHeight < 40;
  const span = document.createElement('span');
  if (line.startsWith('[launcher]')) span.className = 'launcher';
  else if (/\b(ERROR|FATAL)\b|Exception/.test(line)) span.className = 'error';
  else if (/\bWARN\b/.test(line)) span.className = 'warn';
  span.textContent = `${line}\n`;
  consoleEl.appendChild(span);
  if (++logLines > 4000) {
    consoleEl.removeChild(consoleEl.firstChild);
    logLines--;
  }
  if (atBottom) consoleEl.scrollTop = consoleEl.scrollHeight;
});
$('clearLog').addEventListener('click', () => { consoleEl.textContent = ''; logLines = 0; });
$('copyLog').addEventListener('click', async () => {
  await navigator.clipboard.writeText(consoleEl.textContent);
  toast('Console copied.');
});
$('killGame').addEventListener('click', () => polar.game.kill());

// ---------------------------------------------------------------- accounts
function renderAccounts() {
  const { selected, accounts, offlineUnlocked } = state.accounts;
  const current = accounts.find((a) => a.uuid === selected);
  $('accountName').textContent = current ? current.name : 'Sign in';
  $('accountSub').textContent = current ? (current.type === 'offline' ? 'Offline profile' : 'Minecraft Account') : 'Add an account';
  $('accountAvatar').src = avatarUrl(current);
  $('accountAvatar').dataset.fallback = '';

  // dropdown: quick switch
  const menu = $('accountMenu');
  menu.replaceChildren(h('div', { class: 'dropdown-label', text: 'ACCOUNTS' }));
  if (!accounts.length) menu.append(h('div', { class: 'note-empty', text: 'No accounts yet.' }));
  for (const a of accounts) {
    const isCurrent = a.uuid === selected;
    menu.append(h('button', { class: `menu-row${isCurrent ? ' current' : ''}`, onclick: () => selectAccount(a) },
      h('img', { src: avatarUrl(a), alt: '' }),
      h('span', { class: 'grow' }, h('b', { text: a.name }), h('small', { text: a.type === 'offline' ? 'Offline profile' : 'Microsoft account' })),
      isCurrent ? icon('i-check') : null));
  }
  menu.append(h('div', { class: 'menu-sep' }),
    h('button', { class: 'menu-row', onclick: () => openModal('accountsModal') }, icon('i-user'), h('span', { class: 'grow', text: 'Manage accounts' })),
    h('button', { class: 'menu-row', onclick: () => setPage('cosmetics') }, icon('i-shirt'), h('span', { class: 'grow', text: 'Skin & capes' })));

  // modal list
  $('accountList').replaceChildren(...(accounts.length ? accounts.map((a) => h('div', { class: `account-row${a.uuid === selected ? ' selected' : ''}`, onclick: (e) => { if (!e.target.closest('button')) selectAccount(a); } },
    h('img', { src: avatarUrl(a), alt: '' }),
    h('span', { class: 'name', text: a.name }),
    h('span', { class: `badge-type${a.type === 'offline' ? ' offline' : ''}`, text: a.type === 'offline' ? 'Offline' : 'Microsoft' }),
    a.type === 'offline' ? h('button', { class: 'key', title: `Copy ${a.name}'s Polar key, to use this profile on Polar from another PC`, onclick: () => copyPolarKey(a) }, icon('i-lock')) : null,
    h('button', { class: 'remove', 'data-remove': a.uuid, text: 'Remove', onclick: () => removeAccount(a) }))) : [h('div', { class: 'empty', text: 'No accounts yet.' })]));
  $('offlineName').disabled = false;
  $('addOffline').disabled = false;
  $('offlineHint').textContent = 'Plays under any name with the default skin: LAN worlds, singleplayer and offline-mode servers. Polar cosmetics and friends work too.';
}
async function selectAccount(a) {
  closeMenus();
  if (a.uuid === state.accounts.selected) return;
  state.accounts = await polar.accounts.select(a.uuid);
  state.profile = null;
  renderAccounts();
  toast(`Switched to ${a.name}.${state.running ? ' Use the account card in game to switch there too.' : ''}`);
  wr.loaded = false;
  if ($('page-cosmetics').classList.contains('active')) openCosmetics();
  if ($('page-friends').classList.contains('active')) openFriends();
}
async function removeAccount(a) {
  state.accounts = await polar.accounts.remove(a.uuid);
  renderAccounts();
}
$('accountChip').addEventListener('click', () => toggleMenu('accountMenu', 'accountChip'));
$('addAccount').addEventListener('click', async () => {
  const btn = $('addAccount');
  btn.disabled = true;
  try {
    state.accounts = await polar.accounts.login();
    renderAccounts();
    const acc = state.accounts.accounts.find((a) => a.uuid === state.accounts.selected);
    toast(`Signed in as ${acc ? acc.name : 'your account'}.`);
    notify('Signed in', acc ? acc.name : '', 'ok');
    $('accountsModal').hidden = true;
  } catch (e) {
    if (!/cancelled/i.test(e.message)) toast(e.message, 'error', 9000);
  } finally {
    btn.disabled = false;
  }
});
$('offlineForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  const name = $('offlineName').value.trim();
  if (!name) return;
  $('addOffline').disabled = true;
  try {
    state.accounts = await polar.accounts.addOffline(name);
    $('offlineName').value = '';
    renderAccounts();
    toast(`Added offline profile ${name}.`);
  } catch (err) {
    toast(err.message, 'error', 7000);
  } finally {
    $('addOffline').disabled = false;
  }
});
// the game switched account through its own account menu
polar.on('accounts:changed', (accounts) => {
  state.accounts = accounts;
  renderAccounts();
  const acc = accounts.accounts.find((a) => a.uuid === accounts.selected);
  if (acc) notify('Account switched in game', acc.name, 'ok');
});

// ---------------------------------------------------------------- Polar: shared sign-in gate
const RARITY = { common: 'Common', rare: 'Rare', epic: 'Epic', legendary: 'Legendary' };
const SLOT_NAMES = { cape: 'cape', wings: 'wings', head: 'hat', back: 'back item', aura: 'aura' };
const TINTS = ['#ffffff', '#1f1f24', '#ff4f5e', '#ff9a2e', '#ffd23f', '#3fbf74', '#2fd3c1', '#4fa3ff', '#2f4fe6', '#9b5cff', '#ff6fb5', '#8a5a35'];

/** Signs in to the Polar server when needed. Returns the state, or shows why it can't in the gate element. */
async function polarReady(gateId, bodyId) {
  const gate = $(gateId);
  const body = $(bodyId);
  const show = (title, text, button) => {
    body.hidden = true;
    gate.hidden = false;
    gate.replaceChildren(h('b', { text: title }), h('div', { text }), button || '');
    return null;
  };
  let st;
  try {
    st = await polar.online.state();
  } catch (e) {
    return show("Couldn't load Polar", e.message);
  }
  if (!st.configured) return show("The Polar server isn't set up yet", 'Cosmetics, the Polar badge and friends turn on once the Polar server is running (deploy-server.bat).');
  if (!st.account) return show('Add an account first', 'Polar cosmetics and friends work with a Microsoft account or an offline profile.', h('button', { class: 'btn primary', onclick: () => openModal('accountsModal') }, 'Add an account'));
  const reopen = () => (gateId === 'wardrobeGate' ? openWardrobe(true) : openFriends());
  if (!st.signedIn) {
    show('Signing in to Polar…', `Using ${st.account.name}.`);
    try {
      st = await polar.online.signIn();
    } catch (e) {
      return show("Couldn't sign in to Polar", e.message, h('button', { class: 'btn primary', onclick: reopen }, 'Try again'));
    }
    if (st.taken) return show(`${st.account.name} is taken on Polar`, st.taken, keyForm(reopen));
  }
  gate.hidden = true;
  body.hidden = false;
  return st;
}

/** Pasting the Polar key of an offline profile from another PC (when this PC can't use the name on Polar). */
function keyForm(done) {
  const input = h('input', { type: 'text', placeholder: 'Paste the Polar key from your other PC', spellcheck: 'false', autocomplete: 'off' });
  const btn = h('button', { class: 'btn primary', type: 'submit' }, 'Use key');
  return h('form', { class: 'offline-form key-form', onsubmit: async (e) => {
    e.preventDefault();
    if (!input.value.trim()) return input.focus();
    btn.disabled = true;
    try {
      await polar.online.useOfflineKey(input.value);
      toast('Signed in to Polar with your key.', 'ok');
      done();
    } catch (err) {
      toast(err.message, 'error', 8000);
    } finally {
      btn.disabled = false;
    }
  } }, input, btn);
}

/** Copies an offline profile's Polar key, to use the same profile on Polar from another PC. */
async function copyPolarKey(a) {
  try {
    await navigator.clipboard.writeText(await polar.online.offlineKey(a.uuid));
    toast(`Polar key for ${a.name} copied. On your other PC, add the offline profile ${a.name} and paste the key on the Cosmetics page. Keep it private: anyone with it can be ${a.name} on Polar.`, 'ok', 12000);
  } catch (e) {
    toast(e.message, 'error');
  }
}

// ---------------------------------------------------------------- Polar wardrobe (Cosmetics page, first tab)
const wr = { tab: 'wardrobe', loaded: false, catalog: null, me: null, slot: 'cape', collection: 'all', draft: null, saved: null, viewer: null, skinFor: null };
const COS_BASE = window.POLAR_COS_BASE || 'polar-cos://cosmetics/'; // the launcher's cosmetics cache (tests point it elsewhere)
const cosmeticUrl = (rel) => `${COS_BASE}${rel}`;
const sameLoadout = (a, b) => JSON.stringify(a) === JSON.stringify(b);
const itemById = (id) => (wr.catalog ? wr.catalog.items.find((i) => i.id === id) : null);

function openCosmetics() {
  document.querySelectorAll('#cosTabs button').forEach((b) => b.classList.toggle('active', b.dataset.tab === wr.tab));
  $('wardrobeTab').hidden = wr.tab !== 'wardrobe';
  $('skinTab').hidden = wr.tab !== 'skin';
  $('reloadProfile').hidden = wr.tab !== 'skin';
  $('cosSubtitle').textContent = wr.tab === 'wardrobe' ? 'Polar cosmetics that every Polar player can see.' : 'Your skin and capes, saved to your Minecraft account so everyone sees them.';
  if (wr.tab === 'wardrobe') {
    openWardrobe();
  } else {
    stopWardrobe();
    loadProfile();
    loadSkinLibrary();
  }
}
document.querySelectorAll('#cosTabs button').forEach((b) => b.addEventListener('click', () => {
  wr.tab = b.dataset.tab;
  openCosmetics();
}));

function stopWardrobe() {
  if (wr.viewer) wr.viewer.stop();
}

async function openWardrobe(force) {
  const st = await polarReady('wardrobeGate', 'wardrobe');
  if (!st) return stopWardrobe();
  if (!wr.loaded || force) {
    try {
      const [catalog, me] = await Promise.all([polar.online.catalog(), polar.online.me()]);
      wr.catalog = catalog;
      wr.me = me;
      wr.saved = { slots: me.loadout.slots || {}, hideBadge: Boolean(me.loadout.hideBadge) };
      wr.draft = structuredClone(wr.saved);
      wr.loaded = true;
    } catch (e) {
      $('wardrobe').hidden = true;
      $('wardrobeGate').hidden = false;
      $('wardrobeGate').replaceChildren(h('b', { text: "Couldn't load the wardrobe" }), h('div', { text: e.message }),
        h('button', { class: 'btn primary', onclick: () => openWardrobe(true) }, 'Try again'));
      return;
    }
  }
  if (!wr.viewer) {
    wr.viewer = window.PolarCosmetics3D.create($('wrCanvas'), { baseUrl: COS_BASE, yaw: 200, pitch: 8 });
  }
  await wardrobeSkin();
  renderWardrobe();
  wr.viewer.start();
}

/** The player's own skin on the model (falls back to a plain one). */
async function wardrobeSkin() {
  const current = state.accounts.selected;
  if (wr.skinFor === current) return;
  wr.skinFor = current;
  let skin = 'assets/mannequin.png', slim = false;
  try {
    if (!state.profile || state.profile.id !== current) state.profile = await polar.profile.get();
    if (state.profile && state.profile.skin && state.profile.skin.data) {
      skin = state.profile.skin.data;
      slim = state.profile.skin.variant === 'SLIM';
    }
  } catch {
    // no skin: the plain model
  }
  await wr.viewer.setSkin(skin, slim);
}

function viewerLoadout() {
  const out = {};
  for (const [slot, v] of Object.entries(wr.draft.slots)) {
    const it = itemById(v.id);
    if (it) out[slot] = { ...it, color: v.color || it.defaultColor };
  }
  return out;
}

function frameFor(slot) {
  if (slot === 'head') return { yaw: 25, pitch: 12, zoom: 1.9, targetY: 1.72 };
  if (slot === 'aura') return { yaw: 200, pitch: 10, zoom: 0.85, targetY: 1.0 };
  if (slot === 'wings') return { yaw: 205, pitch: 8, zoom: 0.95, targetY: 1.15 };
  return { yaw: 200, pitch: 8, zoom: 1.05, targetY: 1.0 };
}

function renderWardrobe() {
  const owned = new Set(wr.me.owned || []);
  const items = wr.catalog.items.filter((i) => i.type === wr.slot);
  const collections = (wr.catalog.collections || []).filter((c) => items.some((i) => i.collection === c.id));
  if (wr.collection !== 'all' && !collections.some((c) => c.id === wr.collection)) wr.collection = 'all';
  document.querySelectorAll('#wrSlots button').forEach((b) => b.classList.toggle('active', b.dataset.slot === wr.slot));
  $('wrCollections').replaceChildren(
    h('button', { class: `chip${wr.collection === 'all' ? ' on' : ''}`, onclick: () => { wr.collection = 'all'; renderWardrobe(); } }, 'All'),
    ...collections.map((c) => h('button', { class: `chip${wr.collection === c.id ? ' on' : ''}`, onclick: () => { wr.collection = c.id; renderWardrobe(); } }, c.name)));
  const shown = items.filter((i) => wr.collection === 'all' || i.collection === wr.collection);
  const worn = wr.draft.slots[wr.slot];
  const none = h('button', { class: `wr-item none${worn ? '' : ' on'}`, onclick: () => equip(null) },
    h('div', { class: 'wr-thumb' }, icon('i-close')), h('div', { class: 'wr-name', text: `No ${SLOT_NAMES[wr.slot]}` }));
  $('wrGrid').replaceChildren(none, ...shown.map((it) => {
    const locked = it.special && !owned.has(it.id);
    return h('button', { class: `wr-item r-${it.rarity}${worn && worn.id === it.id ? ' on' : ''}${locked ? ' locked' : ''}`, title: it.description || it.name, onclick: () => equip(it) },
      h('div', { class: 'wr-thumb' }, h('img', { src: cosmeticUrl(`thumbs/${it.id}.png`), alt: '', loading: 'lazy' }), locked ? h('span', { class: 'wr-lock' }, icon('i-lock')) : null),
      h('div', { class: 'wr-name', text: it.name }),
      h('div', { class: 'wr-rarity' }, it.special ? h('span', { class: 'wr-special', text: 'Special' }) : null, RARITY[it.rarity] || ''));
  }));
  // colour picker for tintable items
  const wornItem = worn && itemById(worn.id);
  $('wrTint').hidden = !(wornItem && wornItem.tint);
  if (wornItem && wornItem.tint) {
    const cur = (worn.color || wornItem.defaultColor || '#ffffff').toLowerCase();
    const picker = h('input', { type: 'color', value: cur, title: 'Any colour' });
    picker.addEventListener('input', () => setTint(picker.value));
    $('wrTint').replaceChildren(h('span', { class: 'muted', text: `${wornItem.name} colour` }),
      ...TINTS.map((c) => h('button', { class: `swatch${c === cur ? ' on' : ''}`, style: `background:${c}`, title: c, onclick: () => setTint(c) })), picker);
  }
  $('wrBadge').checked = !wr.draft.hideBadge;
  const dirty = !sameLoadout(wr.draft, wr.saved);
  $('wrSave').disabled = !dirty;
  $('wrUndo').disabled = !dirty;
  wr.viewer.set(frameFor(wr.slot));
  wr.viewer.setLoadout(viewerLoadout());
}

function equip(it) {
  if (!it || (wr.draft.slots[wr.slot] && wr.draft.slots[wr.slot].id === it.id)) {
    delete wr.draft.slots[wr.slot];
  } else {
    if (it.special && !(wr.me.owned || []).includes(it.id)) {
      toast(`${it.name} is a special cosmetic. You get it from the Polar team or with a code. Here's a preview.`, 'info', 6000);
      wr.viewer.setLoadout({ ...viewerLoadout(), [wr.slot]: it });
      return;
    }
    wr.draft.slots[wr.slot] = it.tint ? { id: it.id, color: it.defaultColor } : { id: it.id };
  }
  renderWardrobe();
}

function setTint(color) {
  const worn = wr.draft.slots[wr.slot];
  if (!worn) return;
  worn.color = color.toLowerCase();
  clearTimeout(wr.tintTimer);
  wr.viewer.setLoadout(viewerLoadout());
  wr.tintTimer = setTimeout(renderWardrobe, 120);
}

document.querySelectorAll('#wrSlots button').forEach((b) => b.addEventListener('click', () => {
  wr.slot = b.dataset.slot;
  renderWardrobe();
}));
document.querySelectorAll('#wrPose button').forEach((b) => b.addEventListener('click', () => {
  document.querySelectorAll('#wrPose button').forEach((x) => x.classList.toggle('active', x === b));
  if (wr.viewer) wr.viewer.set({ walking: Number(b.dataset.walk) });
}));
$('wrBadge').addEventListener('change', (e) => {
  wr.draft.hideBadge = !e.target.checked;
  renderWardrobe();
});
$('wrUndo').addEventListener('click', () => {
  wr.draft = structuredClone(wr.saved);
  renderWardrobe();
});
$('wrSave').addEventListener('click', async () => {
  $('wrSave').disabled = true;
  try {
    const r = await polar.online.saveLoadout(wr.draft);
    wr.saved = { slots: r.loadout.slots, hideBadge: r.loadout.hideBadge };
    wr.draft = structuredClone(wr.saved);
    toast('Outfit saved. Polar players see it next time they spot you.', 'ok');
  } catch (e) {
    toast(e.message, 'error', 7000);
  }
  renderWardrobe();
});
$('wrRedeem').addEventListener('submit', async (e) => {
  e.preventDefault();
  const code = $('wrCode').value.trim();
  if (!code) return;
  try {
    const r = await polar.online.redeem(code);
    $('wrCode').value = '';
    wr.me = await polar.online.me();
    const it = itemById(r.item);
    toast(`You got ${r.name}! It's in your wardrobe now.`, 'ok', 7000);
    notify('Cosmetic unlocked', r.name, 'ok');
    if (it) {
      wr.slot = it.type;
      wr.draft.slots[it.type] = it.tint ? { id: it.id, color: it.defaultColor } : { id: it.id };
    }
    renderWardrobe();
  } catch (err) {
    toast(err.message, 'error', 7000);
  }
});

// ---------------------------------------------------------------- friends
const fr = { data: null, timer: null };

function whereText(f) {
  if (!f.online) return f.lastSeen ? `Offline · last seen ${timeAgo(f.lastSeen)}` : 'Offline';
  const w = f.where || {};
  if (w.state === 'server' && w.server) return `Playing on ${w.server}`;
  if (w.state === 'singleplayer') return 'In singleplayer';
  if (w.state === 'menu') return 'In the game menus';
  if (w.state === 'launcher') return 'In the launcher';
  return 'Online';
}

async function openFriends() {
  clearInterval(fr.timer);
  const st = await polarReady('friendsGate', 'friendsBody');
  if (!st) return;
  await refreshFriends();
  fr.timer = setInterval(() => {
    if ($('page-friends').classList.contains('active')) refreshFriends();
    else clearInterval(fr.timer);
  }, 30000);
}

async function refreshFriends(data) {
  try {
    fr.data = data || (await polar.online.friends());
  } catch (e) {
    toast(e.message, 'error');
    return;
  }
  const playing = await polar.online.playing().catch(() => null);
  renderFriends(playing);
}

const friendName = (f) => h('b', {}, f.name, f.offline ? h('span', { class: 'tag-offline', title: 'Plays with an offline profile, not a Minecraft account', text: 'Offline profile' }) : null);

function renderFriends(playing) {
  const d = fr.data;
  $('friendsBadge').hidden = !d.incoming.length;
  $('friendsBadge').textContent = d.incoming.length;
  $('friendReqPanel').hidden = !d.incoming.length && !d.outgoing.length;
  const act = (fn, okText) => async () => {
    try {
      await refreshFriends(await fn());
      if (okText) toast(okText, 'ok');
    } catch (e) {
      toast(e.message, 'error', 7000);
    }
  };
  $('friendReqs').replaceChildren(
    ...d.incoming.map((f) => h('div', { class: 'friend-row' },
      h('img', { src: `https://mc-heads.net/avatar/${f.uuid}/64`, alt: '' }),
      h('div', { class: 'grow' }, friendName(f), h('small', { text: 'wants to be your friend' })),
      h('button', { class: 'btn primary small', onclick: act(() => polar.online.acceptFriend(f.uuid), `You and ${f.name} are now friends.`) }, 'Accept'),
      h('button', { class: 'btn ghost small', onclick: act(() => polar.online.removeFriend(f.uuid)) }, 'Decline'))),
    ...d.outgoing.map((f) => h('div', { class: 'friend-row' },
      h('img', { src: `https://mc-heads.net/avatar/${f.uuid}/64`, alt: '' }),
      h('div', { class: 'grow' }, friendName(f), h('small', { text: 'request sent' })),
      h('button', { class: 'btn ghost small', onclick: act(() => polar.online.removeFriend(f.uuid)) }, 'Cancel'))));
  const online = d.friends.filter((f) => f.online).length;
  $('friendCount').textContent = d.friends.length ? `${online} of ${d.friends.length} online` : '';
  $('friendList').replaceChildren(...(d.friends.length ? d.friends.map((f) => {
    const server = f.online && f.where && f.where.state === 'server' ? f.where.server : null;
    return h('div', { class: `friend-row${f.online ? ' online' : ''}` },
      h('div', { class: 'friend-head' }, h('img', { src: `https://mc-heads.net/avatar/${f.uuid}/64`, alt: '' }), h('span', { class: 'presence' })),
      h('div', { class: 'grow' }, friendName(f), h('small', { text: whereText(f) })),
      server && !state.running ? h('button', { class: 'btn primary small', onclick: () => launch(server, server) }, icon('i-play'), 'Join') : null,
      f.online && playing ? h('button', { class: 'btn ghost small', onclick: act(async () => { await polar.online.invite(f.uuid); return fr.data; }, `Invited ${f.name} to ${playing}.`) }, 'Invite') : null,
      h('button', { class: 'btn ghost small danger-text', onclick: () => {
        if (confirm(`Remove ${f.name} from your friends?`)) act(() => polar.online.removeFriend(f.uuid), `Removed ${f.name}.`)();
      } }, 'Remove'));
  }) : [h('div', { class: 'empty-state small' }, h('b', { text: 'No friends yet' }), h('div', { text: 'Add friends by their Minecraft name. They need to have signed in to Polar once.' }))]));
}

$('friendAdd').addEventListener('submit', async (e) => {
  e.preventDefault();
  const name = $('friendName').value.trim();
  if (!name) return;
  try {
    const r = await polar.online.addFriend(name);
    $('friendName').value = '';
    toast(r.message || 'Friend request sent.', 'ok');
    await refreshFriends(r);
  } catch (err) {
    toast(err.message, 'error', 7000);
  }
});

/** A toast with a button (for invites). */
function actionToast(message, label, onClick, ms = 15000) {
  const el = h('div', { class: 'toast info action' }, h('span', { text: message }),
    h('button', { class: 'btn primary small', onclick: () => { el.remove(); onClick(); } }, label));
  $('toasts').append(el);
  setTimeout(() => el.remove(), ms);
}

polar.on('polar:event', (msg) => {
  if (msg.type === 'invite' && msg.from) {
    notify(`${msg.from.name} invited you`, `to ${msg.server}`, 'info');
    if (!state.running) actionToast(`${msg.from.name} invited you to ${msg.server}`, 'Join', () => launch(msg.server, msg.server));
  } else if (msg.type === 'friends' && msg.from) {
    if (msg.event === 'request') {
      notify('Friend request', `${msg.from.name} wants to be your friend`, 'info');
      toast(`${msg.from.name} sent you a friend request.`);
    } else if (msg.event === 'accepted') {
      toast(`${msg.from.name} accepted your friend request.`, 'ok');
    }
    if ($('page-friends').classList.contains('active')) refreshFriends();
    else polar.online.friends().then((d) => { fr.data = d; $('friendsBadge').hidden = !d.incoming.length; $('friendsBadge').textContent = d.incoming.length; }).catch(() => {});
  }
});

// ---------------------------------------------------------------- footer
function renderLinks() {
  $('footLinks').replaceChildren(...LINKS.filter((l) => l.url).map((l) => h('button', { title: l.label, onclick: () => polar.openLink(l.url) }, icon(l.icon))));
}
async function checkServices() {
  try {
    const ok = await polar.services.status();
    $('servicesDot').className = `dot ${ok ? 'ok' : 'bad'}`;
    $('servicesLabel').textContent = ok ? 'MINECRAFT SERVICES ONLINE' : 'MINECRAFT SERVICES OFFLINE';
  } catch {
    $('servicesDot').className = 'dot bad';
    $('servicesLabel').textContent = 'MINECRAFT SERVICES OFFLINE';
  }
}

// ---------------------------------------------------------------- start
(async function init() {
  renderNotes();
  renderFeatures();
  renderLinks();
  try {
    const [info, settings, accounts] = await Promise.all([polar.info(), polar.settings.get(), polar.accounts.list()]);
    state.info = info;
    state.settings = settings;
    state.accounts = accounts;
    state.versions.selected = info.gameVersion;
    $('brandVersion').textContent = `v${info.version}`;
    $('footVersion').textContent = info.version;
    $('aboutLine').textContent = `Polar Client Launcher ${info.version}${info.bundledMod ? '' : ' · mod jar not bundled (dev build)'}`;
    bindSettings();
    renderAccounts();
    await loadVersions();
    renderQuickJoinButton();
    resetPlay();
    await renderMods();
    await loadServers();
    const status = await polar.game.status();
    if (status.running) {
      state.running = true;
      $('killGame').disabled = false;
      setPlayState({ label: 'RUNNING', main: 'Minecraft Running', sub: runningSub(), disabled: true, running: true, dot: 'ok' });
    }
  } catch (e) {
    toast(e.message, 'error', 9000);
  }
  checkServices();
  setInterval(checkServices, 5 * 60 * 1000);
  // friend requests waiting: a badge on Friends
  polar.online.state().then((st) => (st.signedIn ? polar.online.friends() : null)).then((d) => {
    if (!d) return;
    $('friendsBadge').hidden = !d.incoming.length;
    $('friendsBadge').textContent = d.incoming.length;
  }).catch(() => {});
  loadNews();
  setTimeout(checkLauncherUpdate, 4000);
  setTimeout(() => checkModUpdates(), 6000);
})();
