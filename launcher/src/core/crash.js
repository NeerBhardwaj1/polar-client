'use strict';
// Reads the end of the game log and the crash report after Minecraft closes with an error, and explains the most
// likely cause in plain words, with what to do about it.

const MODS_ACTION = 'mods';

/** Turns "fabric-api" / "Fabric API" style names into the name to show. */
function clean(name) {
  return String(name || '').replace(/^'|'$/g, '').trim();
}

/** "'Fabric API' (fabric-api)" or a bare "fabric-api"; names may contain apostrophes (Fabric doesn't escape them). */
const DEP = String.raw`(?:mod )?(?:'(?<dep>.+?)' \((?<depId>[\w.-]+)\)|'?(?<depBare>[\w .-]+?)'?)`;
const MOD = String.raw`Mod '(?<mod>.+?)' \((?<modId>[\w.-]+)\)[^\n]*?`;
const depName = (g) => clean(g.dep || g.depBare);
const depId = (g) => g.depId || g.depBare || '';

const RULES = [
  {
    // Fabric Loader: a mod needs another mod that isn't installed
    re: new RegExp(`${MOD} requires (?<range>[^\n]*?) of ${DEP}, which is missing`),
    make: ({ groups: g }) => ({
      title: `${g.mod} needs ${depName(g)}`,
      detail: `${g.mod} won't start without ${depName(g)} (${g.range}), and it isn't installed for this version.`,
      fix: `Install ${depName(g)} from the Browse page, or remove ${g.mod}.`,
      mods: [g.modId], action: MODS_ACTION,
    }),
  },
  {
    // Fabric Loader: the right mod is there but in the wrong version
    re: new RegExp(`${MOD} requires (?<range>[^\n]*?) of ${DEP}, but only the wrong version is present: (?<present>[^\\s!]+)`),
    make: ({ groups: g }) => ({
      title: `${g.mod} needs a different ${depName(g)}`,
      detail: `${g.mod} needs ${g.range} of ${depName(g)}, but ${g.present} is installed.`,
      fix: /^minecraft$/i.test(depName(g)) || depId(g) === 'minecraft'
        ? `This build of ${g.mod} is for another Minecraft version. Remove it, then install the build for this version from Browse.`
        : `Update ${depName(g)} (Browse page) or use an older ${g.mod}.`,
      mods: [g.modId], action: MODS_ACTION,
    }),
  },
  {
    re: new RegExp(`${MOD} is incompatible with (?<range>[^\n]*?) of ${DEP}, but`),
    make: ({ groups: g }) => ({
      title: `${g.mod} and ${depName(g)} don't work together`,
      detail: `${g.mod} says it is incompatible with ${depName(g)}.`,
      fix: 'Remove one of them (Browse > Installed, or the Mods page).',
      mods: [g.modId].concat(g.depId ? [g.depId] : []), action: MODS_ACTION,
    }),
  },
  {
    re: /(?:Found duplicate mods|Duplicate mod(?:s)?[^\n]*?:?)\s*([^\n]*)/,
    make: (m) => ({
      title: 'The same mod is installed twice',
      detail: `Two copies of a mod are in the mods folder${m[1] ? `: ${m[1].trim()}` : ''}.`,
      fix: 'Open the mods folder and delete the older copy.',
      mods: [], action: MODS_ACTION,
    }),
  },
  {
    re: /Mixin apply (?:for mod ([\w.-]+) )?failed|MixinApplyError|InvalidInjectionException[^\n]*?from mod ([\w.-]+)/,
    make: (m) => ({
      title: m[1] || m[2] ? `${m[1] || m[2]} failed to load` : 'A mod failed to load',
      detail: `${m[1] || m[2] ? `The mod "${m[1] || m[2]}"` : 'One of the mods'} couldn't hook into this Minecraft version, usually because the build is for another version.`,
      fix: 'Update or remove that mod.',
      mods: [m[1] || m[2]].filter(Boolean), action: MODS_ACTION,
    }),
  },
  {
    re: /java\.lang\.OutOfMemoryError|There is insufficient memory for the Java Runtime/,
    make: () => ({
      title: 'Minecraft ran out of memory',
      detail: 'The game used all the memory it was given.',
      fix: 'Give it more in Settings > Memory (or remove heavy mods and shader packs).',
      mods: [], action: 'memory',
    }),
  },
  {
    re: /UnsupportedClassVersionError|has been compiled by a more recent version of the Java Runtime/,
    make: () => ({
      title: 'Wrong Java version',
      detail: 'This version of Minecraft or one of its mods needs a newer Java than the one used.',
      fix: 'Clear "Java path" in Settings so the launcher picks the right Java automatically.',
      mods: [], action: 'java',
    }),
  },
  {
    re: /Could not create the Java Virtual Machine|Unrecognized (?:VM )?option|Invalid maximum heap size/,
    make: () => ({
      title: 'Java refused the launch options',
      detail: 'Java could not start with the extra JVM arguments or memory setting.',
      fix: 'Clear "JVM arguments" in Settings, and keep Memory below what your PC has free.',
      mods: [], action: 'java',
    }),
  },
  {
    re: /GLFW error 65542|WGL: The driver does not appear to support OpenGL|Pixel format not accelerated|OpenGL 3\.2 or (?:above|later) is required|Failed to create the GLFW window|couldn't set pixel format/i,
    make: () => ({
      title: 'Graphics driver problem',
      detail: 'Minecraft could not open its window because the graphics driver is missing OpenGL support.',
      fix: 'Update your graphics driver from NVIDIA, AMD or Intel (not just Windows Update), then try again.',
      mods: [], action: 'drivers',
    }),
  },
];

/** "Suspected Mods: Sodium (sodium), Iris (iris)" in newer crash reports. */
function suspects(text) {
  const m = text.match(/Suspected Mods?: ([^\n]+)/);
  if (!m || /^(None|Unknown)/i.test(m[1].trim())) return [];
  return m[1].split(',').map((s) => s.trim()).filter(Boolean);
}

/**
 * Explains a crash. `log` = the last lines the game printed, `report` = the crash report text (may be empty).
 * Returns { title, detail, fix, mods, action, suspects } or a generic explanation if nothing specific matched.
 */
function analyze(log, report = '') {
  const text = `${report || ''}\n${log || ''}`;
  for (const rule of RULES) {
    const m = text.match(rule.re);
    if (m) return { ...rule.make(m), suspects: suspects(text) };
  }
  const sus = suspects(text);
  if (sus.length) {
    return {
      title: `${sus[0].replace(/\s*\(.*\)$/, '')} probably crashed the game`,
      detail: `The crash report names ${sus.join(', ')} as the likely cause.`,
      fix: 'Update or remove that mod, then try again.',
      mods: [], action: MODS_ACTION, suspects: sus,
    };
  }
  const desc = (report.match(/Description: ([^\n]+)/) || [])[1];
  const ex = (text.match(/^(?:Caused by: )?([\w.$]+(?:Exception|Error))(?::[^\n]*)?$/m) || [])[0];
  return {
    title: 'Minecraft closed unexpectedly',
    detail: desc ? `Minecraft said: "${desc.trim()}".` : ex ? `The last error was: ${ex.trim().slice(0, 160)}` : 'No clear cause was found in the log.',
    fix: 'If you added mods recently, remove them and try again. The crash report has the full details.',
    mods: [], action: null, suspects: [],
  };
}

module.exports = { analyze, suspects };
