'use strict';
// Builds the Java command line from a merged version profile. Pure functions, so they're unit-tested.
const path = require('node:path');
const { isAllowed } = require('./rules');

/** Replaces ${placeholders}; unknown ones are left as-is (the game ignores them). */
function substitute(value, vars) {
  return value.replace(/\$\{([a-zA-Z0-9_]+)\}/g, (m, key) => (vars[key] != null ? String(vars[key]) : m));
}

/** Flattens an "arguments.game/jvm" list, applying rules and placeholders. */
function processArgs(list, { env, features = {}, vars }) {
  const out = [];
  for (const entry of list || []) {
    if (typeof entry === 'string') {
      out.push(substitute(entry, vars));
    } else if (entry && isAllowed(entry.rules, env, features)) {
      const values = Array.isArray(entry.value) ? entry.value : [entry.value];
      for (const v of values) out.push(substitute(v, vars));
    }
  }
  return out;
}

/**
 * JVM arguments the official launcher adds for versions from before 1.13, whose JSON has no "arguments.jvm".
 * Same format as modern JSONs so the usual rules apply.
 */
const LEGACY_JVM_ARGS = [
  { rules: [{ action: 'allow', os: { name: 'osx' } }], value: ['-XstartOnFirstThread'] },
  { rules: [{ action: 'allow', os: { name: 'windows' } }], value: '-XX:HeapDumpPath=MojangTricksIntelDriversForPerformance_javaw.exe_minecraft.exe.heapdump' },
  { rules: [{ action: 'allow', os: { arch: 'x86' } }], value: '-Xss1M' },
  '-Djava.library.path=${natives_directory}',
  '-Dminecraft.launcher.brand=${launcher_name}',
  '-Dminecraft.launcher.version=${launcher_version}',
  '-cp',
  '${classpath}',
];

/** Pre-1.13 versions: one "minecraftArguments" string and no "arguments.game". */
function isLegacyProfile(profile) {
  const game = (profile.arguments && profile.arguments.game) || [];
  return typeof profile.minecraftArguments === 'string' && game.length === 0;
}

/** 1.20+ can join a server on start with --quickPlayMultiplayer; older versions use --server/--port. */
function supportsQuickPlay(profile) {
  return JSON.stringify((profile.arguments && profile.arguments.game) || []).includes('quickPlayMultiplayer');
}

/** "host", "host:port" or "[v6]:port" -> { host, port } */
function splitAddress(address) {
  const a = String(address || '').trim();
  const v6 = a.match(/^\[([^\]]+)\](?::(\d+))?$/);
  if (v6) return { host: v6[1], port: Number(v6[2] || 25565) };
  const m = a.match(/^([^:]+)(?::(\d+))?$/);
  return m ? { host: m[1], port: Number(m[2] || 25565) } : null;
}

/**
 * Returns { java, args, cwd }.
 * opts: { profile, env, java, dirs: { game, assets, libraries, natives, clientJar, loggingConfig },
 *         libraries: [resolved library {path, native}], account, settings, launcherVersion, clientId,
 *         server?: { host, port } — where --server should point on versions without quick play (SRV already resolved) }
 */
function buildLaunchCommand(opts) {
  const { profile, env, dirs, account, settings } = opts;
  const sep = env.os === 'windows' ? ';' : ':';
  const classpath = [
    ...opts.libraries.filter((l) => !l.native).map((l) => path.join(dirs.libraries, l.path)),
    dirs.clientJar,
  ].join(sep);
  const legacy = isLegacyProfile(profile);
  const quickPlay = supportsQuickPlay(profile);
  const profileArgs = profile.arguments || {};

  const quickJoin = (settings.quickJoin || '').trim();
  const features = {
    is_demo_user: false,
    has_custom_resolution: Boolean(settings.customResolution),
    has_quick_plays_support: false,
    is_quick_play_singleplayer: false,
    is_quick_play_multiplayer: quickPlay && quickJoin.length > 0,
    is_quick_play_realms: false,
  };
  const vars = {
    auth_player_name: account.name,
    version_name: profile.id,
    game_directory: dirs.game,
    assets_root: dirs.assets,
    assets_index_name: profile.assetIndex ? profile.assetIndex.id : profile.assets,
    auth_uuid: account.uuid,
    auth_access_token: account.mcToken,
    clientid: opts.clientId || '',
    auth_xuid: account.xuid || '0',
    user_type: account.type === 'offline' ? 'legacy' : 'msa',
    version_type: profile.type || 'release',
    natives_directory: dirs.natives,
    launcher_name: 'polar-client',
    launcher_version: opts.launcherVersion || '1.0.0',
    classpath,
    classpath_separator: sep,
    library_directory: dirs.libraries,
    resolution_width: settings.width,
    resolution_height: settings.height,
    quickPlayMultiplayer: quickJoin,
    // only used by old versions
    user_properties: '{}',
    auth_session: account.mcToken,
    game_assets: dirs.assets,
  };

  const memory = Math.max(1, Math.round(settings.memoryGb || 4));
  // Mojang's suggested JVM defaults, minus their -Xms/-Xmx (we use the RAM slider instead).
  const userDefaults = processArgs(profileArgs['default-user-jvm'], { env, features, vars })
    .filter((a) => !/^-Xm[sx]/.test(a));
  const extra = (settings.jvmArgs || '').split(/\s+/).filter(Boolean);
  const jvm = [
    `-Xms${Math.min(memory, 2)}G`,
    `-Xmx${memory}G`,
    ...userDefaults,
    ...extra,
  ];
  if (profile.logging && profile.logging.client && dirs.loggingConfig) {
    jvm.push(substitute(profile.logging.client.argument, { path: dirs.loggingConfig }));
  }
  if (legacy) jvm.push(...processArgs(LEGACY_JVM_ARGS, { env, features, vars }));
  jvm.push(...processArgs(profileArgs.jvm, { env, features, vars }));

  const game = legacy
    ? profile.minecraftArguments.split(/\s+/).filter(Boolean).map((a) => substitute(a, vars))
    : processArgs(profileArgs.game, { env, features, vars });
  if (legacy && settings.customResolution) game.push('--width', String(settings.width), '--height', String(settings.height));
  if (quickJoin && !quickPlay) {
    const target = opts.server || splitAddress(quickJoin);
    if (target) game.push('--server', target.host, '--port', String(target.port));
  }
  if (settings.fullscreen) game.push('--fullscreen');

  return { java: opts.java, args: [...jvm, profile.mainClass, ...game], cwd: dirs.game };
}

/** Hides the access token when logging a command line. */
function redact(args) {
  const out = [...args];
  const i = out.indexOf('--accessToken');
  if (i >= 0 && i + 1 < out.length) out[i + 1] = '<hidden>';
  return out;
}

module.exports = { substitute, processArgs, buildLaunchCommand, redact, isLegacyProfile, supportsQuickPlay, splitAddress, LEGACY_JVM_ARGS };
