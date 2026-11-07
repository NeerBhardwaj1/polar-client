'use strict';
// A small WebGL viewer for a player wearing Polar cosmetics: skin, cape, model cosmetics (bones of boxes, the way
// Minecraft builds its models), their animations, glow layers, animated textures and aura particles.
// Used by the launcher's wardrobe (and the cosmetics preview tool). window.PolarCosmetics3D.create(canvas, options).
(function () {
  // ---------------------------------------------------------------- 4x4 matrices (column-major, like WebGL)
  const M = {
    id: () => [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1],
    mul(a, b) {
      const o = new Array(16);
      for (let c = 0; c < 4; c++) for (let r = 0; r < 4; r++) {
        o[c * 4 + r] = a[r] * b[c * 4] + a[4 + r] * b[c * 4 + 1] + a[8 + r] * b[c * 4 + 2] + a[12 + r] * b[c * 4 + 3];
      }
      return o;
    },
    t: (x, y, z) => [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, x, y, z, 1],
    s: (x, y, z) => [x, 0, 0, 0, 0, y, 0, 0, 0, 0, z, 0, 0, 0, 0, 1],
    rx(a) { const c = Math.cos(a), s = Math.sin(a); return [1, 0, 0, 0, 0, c, s, 0, 0, -s, c, 0, 0, 0, 0, 1]; },
    ry(a) { const c = Math.cos(a), s = Math.sin(a); return [c, 0, -s, 0, 0, 1, 0, 0, s, 0, c, 0, 0, 0, 0, 1]; },
    rz(a) { const c = Math.cos(a), s = Math.sin(a); return [c, s, 0, 0, -s, c, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1]; },
    persp(fov, aspect, near, far) {
      const f = 1 / Math.tan(fov / 2), nf = 1 / (near - far);
      return [f / aspect, 0, 0, 0, 0, f, 0, 0, 0, 0, (far + near) * nf, -1, 0, 0, 2 * far * near * nf, 0];
    },
    apply(m, x, y, z) {
      return [m[0] * x + m[4] * y + m[8] * z + m[12], m[1] * x + m[5] * y + m[9] * z + m[13], m[2] * x + m[6] * y + m[10] * z + m[14]];
    },
  };
  const RAD = Math.PI / 180;

  /** A part's transform like Minecraft's ModelPart.translateAndRotate: pivot (pixels), then Z, Y, X rotations. */
  function partMatrix(pivot, rot) {
    let m = M.t(pivot[0] / 16, pivot[1] / 16, pivot[2] / 16);
    if (rot[2]) m = M.mul(m, M.rz(rot[2]));
    if (rot[1]) m = M.mul(m, M.ry(rot[1]));
    if (rot[0]) m = M.mul(m, M.rx(rot[0]));
    return m;
  }

  /**
   * The six faces of a box, exactly as Minecraft's ModelPart.Cube builds them, so box-UV textures (skins, capes,
   * cosmetics) land the same way. rects: face -> [u0, v0, u1, v1] in texels.
   */
  function boxQuads(from, to, rects, inflate = 0) {
    let [x0, y0, z0] = from, [x1, y1, z1] = to;
    x0 -= inflate; y0 -= inflate; z0 -= inflate; x1 += inflate; y1 += inflate; z1 += inflate;
    const v = [[x0, y0, z0], [x1, y0, z0], [x1, y1, z0], [x0, y1, z0], [x0, y0, z1], [x1, y0, z1], [x1, y1, z1], [x0, y1, z1]];
    // vertex order and the uv corner each vertex takes: (u1,v0) (u0,v0) (u0,v1) (u1,v1)
    const faces = {
      top: [v[5], v[4], v[0], v[1]],
      bottom: [v[2], v[3], v[7], v[6]],
      right: [v[0], v[4], v[7], v[3]],
      front: [v[1], v[0], v[3], v[2]],
      left: [v[5], v[1], v[2], v[6]],
      back: [v[4], v[5], v[6], v[7]],
    };
    const out = [];
    for (const face of Object.keys(faces)) {
      const r = rects[face];
      if (!r) continue;
      let [u0, v0, u1, v1] = r;
      if (face === 'bottom') [v0, v1] = [v1, v0]; // Minecraft flips the bottom face's texture
      out.push({ p: faces[face], uv: [[u1, v0], [u0, v0], [u0, v1], [u1, v1]] });
    }
    return out;
  }

  /** Box-UV rects for a box of size (w, h, d) at texture offset (u, v), as Minecraft lays them out. */
  function boxUv(u, v, w, h, d) {
    return {
      top: [u + d, v, u + d + w, v + d],
      bottom: [u + d + w, v, u + d + w + w, v + d],
      right: [u, v + d, u + d, v + d + h],
      front: [u + d, v + d, u + d + w, v + d + h],
      left: [u + d + w, v + d, u + d + w + d, v + d + h],
      back: [u + d + w + d, v + d, u + d + w + d + w, v + d + h],
    };
  }

  function player(slim) {
    const aw = slim ? 3 : 4;
    const box = (x, y, z, w, h, d, u, v, inflate = 0) => ({ from: [x, y, z], to: [x + w, y + h, z + d], rects: boxUv(u, v, w, h, d), inflate });
    return {
      head: { pivot: [0, 0, 0], boxes: [box(-4, -8, -4, 8, 8, 8, 0, 0), box(-4, -8, -4, 8, 8, 8, 32, 0, 0.5)] },
      body: { pivot: [0, 0, 0], boxes: [box(-4, 0, -2, 8, 12, 4, 16, 16), box(-4, 0, -2, 8, 12, 4, 16, 32, 0.25)] },
      rightArm: { pivot: [-5, slim ? 2.5 : 2, 0], boxes: [box(slim ? -2 : -3, -2, -2, aw, 12, 4, 40, 16), box(slim ? -2 : -3, -2, -2, aw, 12, 4, 40, 32, 0.25)] },
      leftArm: { pivot: [5, slim ? 2.5 : 2, 0], boxes: [box(-1, -2, -2, aw, 12, 4, 32, 48), box(-1, -2, -2, aw, 12, 4, 48, 48, 0.25)] },
      rightLeg: { pivot: [-1.9, 12, 0], boxes: [box(-2, 0, -2, 4, 12, 4, 0, 16), box(-2, 0, -2, 4, 12, 4, 0, 32, 0.25)] },
      leftLeg: { pivot: [1.9, 12, 0], boxes: [box(-2, 0, -2, 4, 12, 4, 16, 48), box(-2, 0, -2, 4, 12, 4, 0, 48, 0.25)] },
    };
  }

  const VS = `
    attribute vec3 aPos; attribute vec2 aUv; attribute vec4 aCol;
    uniform mat4 uProj; varying vec2 vUv; varying vec4 vCol;
    void main() { vUv = aUv; vCol = aCol; gl_Position = uProj * vec4(aPos, 1.0); }`;
  const FS = `
    precision mediump float; varying vec2 vUv; varying vec4 vCol; uniform sampler2D uTex; uniform float uCut;
    void main() { vec4 c = texture2D(uTex, vUv) * vCol; if (c.a < uCut) discard; gl_FragColor = c; }`;

  function hexColor(h) {
    const m = /^#?([0-9a-f]{6})$/i.exec(h || '');
    if (!m) return [1, 1, 1];
    const n = parseInt(m[1], 16);
    return [(n >> 16 & 255) / 255, (n >> 8 & 255) / 255, (n & 255) / 255];
  }

  function create(canvas, opts = {}) {
    const gl = canvas.getContext('webgl', { alpha: true, antialias: true, premultipliedAlpha: false, preserveDrawingBuffer: true });
    if (!gl) throw new Error('WebGL is not available');
    const compile = (type, src) => {
      const s = gl.createShader(type);
      gl.shaderSource(s, src);
      gl.compileShader(s);
      if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(s));
      return s;
    };
    const prog = gl.createProgram();
    gl.attachShader(prog, compile(gl.VERTEX_SHADER, VS));
    gl.attachShader(prog, compile(gl.FRAGMENT_SHADER, FS));
    gl.linkProgram(prog);
    gl.useProgram(prog);
    const loc = { pos: gl.getAttribLocation(prog, 'aPos'), uv: gl.getAttribLocation(prog, 'aUv'), col: gl.getAttribLocation(prog, 'aCol'),
      proj: gl.getUniformLocation(prog, 'uProj'), tex: gl.getUniformLocation(prog, 'uTex'), cut: gl.getUniformLocation(prog, 'uCut') };
    const buf = gl.createBuffer();

    const textures = new Map();
    const blank = gl.createTexture();
    gl.bindTexture(gl.TEXTURE_2D, blank);
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, 1, 1, 0, gl.RGBA, gl.UNSIGNED_BYTE, new Uint8Array([0, 0, 0, 0]));

    function texture(url) {
      if (!url) return null;
      if (textures.has(url)) return textures.get(url);
      const entry = { tex: gl.createTexture(), w: 1, h: 1, ready: false };
      textures.set(url, entry);
      const img = new Image();
      img.crossOrigin = 'anonymous';
      let done;
      entry.loaded = new Promise((resolve) => { done = resolve; });
      img.onerror = () => done(false);
      img.onload = () => {
        gl.bindTexture(gl.TEXTURE_2D, entry.tex);
        gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, img);
        gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.NEAREST);
        gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.NEAREST);
        gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
        gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
        entry.w = img.naturalWidth;
        entry.h = img.naturalHeight;
        entry.ready = true;
        done(true);
      };
      img.src = url;
      return entry;
    }

    const base = opts.baseUrl || '';
    const state = {
      skin: opts.skin || null, slim: Boolean(opts.slim), yaw: opts.yaw ?? 200, pitch: opts.pitch ?? 8, zoom: opts.zoom ?? 1,
      autoRotate: opts.autoRotate ?? 0, targetY: opts.targetY ?? 1.02, walking: 0, boost: opts.boost ?? 1, loadout: {}, models: {}, particles: [], spawnAcc: 0, phases: {}, t0: performance.now(),
      running: false, last: 0, time: opts.time ?? null,
    };

    /**
     * loadout: { cape: item, wings: item, ... } with catalog items. Models and every texture they need are fetched
     * from baseUrl; the promise settles once they're all loaded (or failed), so the next frame shows everything.
     */
    async function setLoadout(loadout) {
      state.loadout = loadout || {};
      state.particles = [];
      const waits = [];
      if (state.skin) waits.push(texture(state.skin).loaded);
      await Promise.all(Object.values(state.loadout).map(async (item) => {
        if (!item) return;
        if (item.texture) waits.push(texture(base + item.texture).loaded);
        if (item.particles) waits.push(texture(base + item.particles.texture).loaded);
        if (item.model && !state.models[item.model]) {
          state.models[item.model] = 'loading';
          try {
            const res = await fetch(base + item.model);
            if (!res.ok) throw new Error(String(res.status));
            state.models[item.model] = await res.json();
          } catch {
            delete state.models[item.model];
          }
        }
        const model = state.models[item.model];
        if (model && model !== 'loading') {
          waits.push(texture(base + model.texture).loaded);
          if (model.glow) waits.push(texture(base + model.glow).loaded);
        }
      }));
      await Promise.all(waits);
    }

    // ---------------------------------------------------------------- drawing
    let batch = [];
    function quad(mat, q, tex, texW, texH, col, light, frame = 0, frames = 1) {
      const pts = q.p.map((p) => M.apply(mat, p[0] / 16, p[1] / 16, p[2] / 16));
      // face normal in world space (from the transformed corners), for Minecraft-like shading
      const ax = pts[1][0] - pts[0][0], ay = pts[1][1] - pts[0][1], az = pts[1][2] - pts[0][2];
      const bx = pts[2][0] - pts[0][0], by = pts[2][1] - pts[0][1], bz = pts[2][2] - pts[0][2];
      let nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
      const nl = Math.hypot(nx, ny, nz) || 1;
      nx /= nl; ny /= nl; nz /= nl;
      const shade = light ? 1 : Math.min(1, 0.55 + 0.25 * Math.abs(ny) * (ny > 0 ? 1.6 : 0.8) + 0.2 * Math.max(Math.abs(nx) * 0.8, Math.abs(nz)));
      const c = [col[0] * shade, col[1] * shade, col[2] * shade, col[3]];
      const uvs = q.uv.map(([u, v]) => [u / texW, (v + frame * texH) / (texH * frames)]);
      batch.push({ tex, pts, uvs, c, depth: (pts[0][2] + pts[2][2]) / 2 });
    }

    function drawBatch(list, view, mode) {
      if (!list.length) return;
      const byTex = new Map();
      for (const q of list) {
        if (!byTex.has(q.tex)) byTex.set(q.tex, []);
        byTex.get(q.tex).push(q);
      }
      for (const [tex, qs] of byTex) {
        const data = new Float32Array(qs.length * 6 * 9);
        let o = 0;
        for (const q of qs) {
          for (const i of [0, 1, 2, 0, 2, 3]) {
            const p = M.apply(view, q.pts[i][0], q.pts[i][1], q.pts[i][2]);
            data.set([p[0], p[1], p[2], q.uvs[i][0], q.uvs[i][1], q.c[0], q.c[1], q.c[2], q.c[3]], o);
            o += 9;
          }
        }
        gl.bindBuffer(gl.ARRAY_BUFFER, buf);
        gl.bufferData(gl.ARRAY_BUFFER, data, gl.STREAM_DRAW);
        gl.enableVertexAttribArray(loc.pos);
        gl.vertexAttribPointer(loc.pos, 3, gl.FLOAT, false, 36, 0);
        gl.enableVertexAttribArray(loc.uv);
        gl.vertexAttribPointer(loc.uv, 2, gl.FLOAT, false, 36, 12);
        gl.enableVertexAttribArray(loc.col);
        gl.vertexAttribPointer(loc.col, 4, gl.FLOAT, false, 36, 20);
        gl.bindTexture(gl.TEXTURE_2D, tex || blank);
        gl.uniform1f(loc.cut, mode === 'cutout' ? 0.1 : 0.004);
        gl.drawArrays(gl.TRIANGLES, 0, qs.length * 6);
      }
    }

    /** A real wing beat (p in cycles): a quick power stroke over the share `down`, then a slower recovery. */
    function beat(p, down) {
      p -= Math.floor(p);
      return p < down ? Math.cos(Math.PI * p / down) : -Math.cos(Math.PI * (p - down) / (1 - down));
    }

    function animValue(item, a, dt, moving, motion = NO_MOTION) {
      const key = `${item.id}:${a.bone}:${a.type}:${a.phase || 0}`;
      if (a.type === 'spin') return { rot: (a.rot || [0, 0, 0]).map((r) => r * (now() / 1000)) };
      const speed = ((a.speed || 1) + ((a.speedMove ?? a.speed ?? 1) - (a.speed || 1)) * moving) * motion.speed;
      state.phases[key] = ((state.phases[key] || 0) + dt * speed) % 1000;
      const amp = ((a.idle ?? 1) + ((a.move ?? 1) - (a.idle ?? 1)) * moving) * motion.amp;
      const down = Math.min(0.95, Math.max(0.05, a.down ?? 0.38));
      const s = (a.type === 'flap' ? beat(state.phases[key] + (a.phase || 0) / (2 * Math.PI), down)
        : Math.sin(2 * Math.PI * state.phases[key] + (a.phase || 0))) * amp;
      return { rot: (a.rot || [0, 0, 0]).map((r) => r * s), pos: (a.pos || [0, 0, 0]).map((p) => p * s) };
    }

    // Wings react to what the player does, eased like in game (dev.polarclient.cosmetics.Cosmetics#wingMotion).
    const NO_MOTION = { amp: 1, speed: 1, fold: 0, glide: 0 };
    const POSES = {
      idle: { sprint: 0, fold: 0, glide: 0, fly: 0 }, walk: { sprint: 0, fold: 0, glide: 0, fly: 0 },
      sprint: { sprint: 1, fold: 0, glide: 0, fly: 0 }, sneak: { sprint: 0, fold: 1, glide: 0, fly: 0 },
      glide: { sprint: 0, fold: 0, glide: 1, fly: 0 }, fly: { sprint: 0, fold: 0, glide: 0, fly: 1 },
    };
    function wingMotion(dt) {
      const target = POSES[state.pose] || POSES.idle;
      const w = state.wing || (state.wing = { ...target });
      const k = dt > 0 ? 1 - Math.exp(-dt / 0.18) : 0;
      for (const key of Object.keys(target)) w[key] += (target[key] - w[key]) * k;
      return {
        amp: (1 + 0.3 * w.sprint + 0.45 * w.fly) * (1 - 0.8 * w.fold) * (1 - 0.5 * w.glide),
        speed: (1 + 0.35 * w.sprint + 0.6 * w.fly) * (1 - 0.35 * w.fold) * (1 - 0.3 * w.glide),
        fold: w.fold, glide: w.glide * (1 - w.fly),
      };
    }

    function drawModel(item, model, anchorMat, dt, moving, motion = NO_MOTION) {
      const tex = texture(base + model.texture);
      const glowTex = model.glow ? texture(base + model.glow) : null;
      if (!tex || !tex.ready) return;
      const frames = model.frames || 1;
      const frame = frames > 1 ? Math.floor(now() / (model.frameTime || 100)) % frames : 0;
      const tint = hexColor(item.color || item.defaultColor);
      const anims = {};
      for (const a of model.anims || []) {
        const v = animValue(item, a, dt, moving, motion);
        const cur = anims[a.bone] || { rot: [0, 0, 0], pos: [0, 0, 0] };
        cur.rot = cur.rot.map((r, i) => r + (v.rot ? v.rot[i] : 0));
        cur.pos = cur.pos.map((p, i) => p + (v.pos ? v.pos[i] : 0));
        anims[a.bone] = cur;
      }
      const walk = (bone, parent, top) => {
        const an = anims[bone.name] || { rot: [0, 0, 0], pos: [0, 0, 0] };
        const pivot = bone.pivot.map((p, i) => p + an.pos[i]);
        const extra = [0, 0, 0];
        if (top && (motion.fold || motion.glide)) {
          // fold back and droop, or open up and lift, mirrored for the right wing
          const side = bone.pivot[0] >= 0 ? 1 : -1;
          extra[1] = side * (-38 * motion.fold + 14 * motion.glide);
          extra[2] = side * (10 * motion.fold - 7 * motion.glide);
        }
        const rot = bone.rot.map((r, i) => (r + an.rot[i] + extra[i]) * RAD);
        const m = M.mul(parent, partMatrix(pivot, rot));
        for (const cube of bone.cubes) {
          const col = cube.tint ? [...tint, 1] : [1, 1, 1, 1];
          for (const q of boxQuads(cube.from, cube.to, cube.faces, cube.inflate || 0)) {
            quad(m, q, tex.tex, model.size[0], model.size[1], col, false, frame, frames);
            batch[batch.length - 1].layer = model.translucent ? 'translucent' : 'cutout';
            if (glowTex && glowTex.ready) {
              quad(m, q, glowTex.tex, model.size[0], model.size[1], [1, 1, 1, 1], true, frame, frames);
              batch[batch.length - 1].layer = 'glow';
            }
          }
        }
        for (const ch of bone.children || []) walk(ch, m);
      };
      for (const b of model.bones) walk(b, anchorMat, true);
    }

    // ---------------------------------------------------------------- aura particles (world space, player at 0,0,0)
    function stepParticles(item, dt) {
      const P = item.particles;
      if (!P) return;
      const rand = (r) => (Array.isArray(r) ? r[0] + Math.random() * (r[1] - r[0]) : r || 0);
      state.spawnAcc += dt * (P.rate || 6) * state.boost; // boost: busier auras for thumbnails
      while (state.spawnAcc >= 1 && state.particles.length < 200 * state.boost) {
        state.spawnAcc -= 1;
        const ang = Math.random() * Math.PI * 2, rad = rand(P.radius || [0.3, 0.7]);
        const h = rand(P.height || [0, 1.9]);
        state.particles.push({
          x: Math.cos(ang) * rad, y: h, z: Math.sin(ang) * rad,
          vx: rand(P.vx), vy: rand(P.vy), vz: rand(P.vz), age: 0, life: rand(P.life || [1.5, 2.5]),
          size: rand(P.size || [0.08, 0.14]), spin: rand(P.spin || 0) * RAD, rot: Math.random() * 6.28,
          col: hexColor((P.colors || ['#ffffff'])[Math.floor(Math.random() * (P.colors || ['#ffffff']).length)]),
          frame: Math.floor(Math.random() * (P.frames || 1)), swirl: rand(P.swirl || 0), ang, rad,
        });
      }
      for (const p of state.particles) {
        p.age += dt;
        p.vy -= (P.gravity || 0) * dt;
        if (p.swirl) {
          p.ang += p.swirl * dt;
          p.x = Math.cos(p.ang) * p.rad;
          p.z = Math.sin(p.ang) * p.rad;
        } else {
          p.x += p.vx * dt;
          p.z += p.vz * dt;
        }
        p.y += p.vy * dt;
        p.rot += p.spin * dt;
      }
      state.particles = state.particles.filter((p) => p.age < p.life);
    }

    function drawParticles(item, camRight, camUp) {
      const P = item.particles;
      const tex = P && texture(base + P.texture);
      if (!tex || !tex.ready) return;
      const frames = P.frames || 1;
      for (const p of state.particles) {
        const t = p.age / p.life;
        const alpha = Math.min(1, t * 5) * Math.min(1, (1 - t) * 3);
        const s = p.size * (P.shrink ? 1 - t * 0.6 : 1) * (state.boost > 1 ? 1.6 : 1);
        const cr = Math.cos(p.rot), sr = Math.sin(p.rot);
        const corner = (dx, dy) => {
          const rx = dx * cr - dy * sr, ry = dx * sr + dy * cr;
          return [p.x + (camRight[0] * rx + camUp[0] * ry) * s, p.y + (camRight[1] * rx + camUp[1] * ry) * s, p.z + (camRight[2] * rx + camUp[2] * ry) * s];
        };
        const fw = tex.w / frames;
        const u0 = p.frame * fw, u1 = u0 + fw;
        batch.push({ tex: tex.tex, layer: P.glow ? 'glow' : 'translucent',
          pts: [corner(-1, 1), corner(1, 1), corner(1, -1), corner(-1, -1)],
          uvs: [[u0 / tex.w, 0], [u1 / tex.w, 0], [u1 / tex.w, 1], [u0 / tex.w, 1]],
          c: [p.col[0], p.col[1], p.col[2], alpha], depth: 0 });
      }
    }

    function now() {
      return state.time != null ? state.time : performance.now() - state.t0;
    }

    function render(dt = 1 / 60) {
      const w = canvas.width, h = canvas.height;
      gl.viewport(0, 0, w, h);
      gl.clearColor(0, 0, 0, 0);
      gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);
      gl.enable(gl.DEPTH_TEST);
      gl.depthFunc(gl.LEQUAL); // glow layers sit exactly on top of the faces they light up
      gl.disable(gl.CULL_FACE);
      batch = [];
      const t = now() / 1000;
      const moving = state.walking;
      const yaw = state.yaw * RAD;
      // the entity transform Minecraft uses for players
      let ent = M.mul(M.ry(Math.PI - 0), M.s(-1, -1, 1));
      ent = M.mul(ent, M.s(0.9375, 0.9375, 0.9375));
      ent = M.mul(ent, M.t(0, -1.501, 0));
      const swing = Math.sin(t * 7) * 0.7 * moving;
      const idleArm = Math.sin(t * 1.1) * 0.05;
      const parts = player(state.slim);
      const rots = {
        head: [0, 0, 0], body: [0, 0, 0],
        rightArm: [swing, 0, 0.05 + idleArm], leftArm: [-swing, 0, -0.05 - idleArm],
        rightLeg: [-swing * 1.1, 0, 0], leftLeg: [swing * 1.1, 0, 0],
      };
      const skin = texture(state.skin);
      const mats = {};
      for (const [name, part] of Object.entries(parts)) {
        mats[name] = M.mul(ent, partMatrix(part.pivot, rots[name]));
        if (skin && skin.ready) {
          for (const b of part.boxes) {
            for (const q of boxQuads(b.from, b.to, b.rects, b.inflate)) {
              quad(mats[name], q, skin.tex, 64, skin.h >= 64 ? 64 : 32, [1, 1, 1, 1]);
              batch[batch.length - 1].layer = 'cutout';
            }
          }
        }
      }
      // cape: CapeLayer's pose (0.125 blocks behind the shoulders, tilted out a little)
      const cape = state.loadout.cape;
      if (cape && cape.texture) {
        const ct = texture(base + cape.texture);
        if (ct && ct.ready) {
          const frames = cape.frames || 1;
          const frame = frames > 1 ? Math.floor(now() / (cape.frameTime || 100)) % frames : 0;
          const lift = (6 + moving * 22 + Math.sin(t * 2) * 1.5) * RAD;
          let m = M.mul(mats.body, M.t(0, 0, 0.125));
          m = M.mul(m, M.rx(lift));
          m = M.mul(m, M.ry(Math.PI));
          const rects = boxUv(0, 0, 10, 16, 1);
          for (const q of boxQuads([-5, 0, -1], [5, 16, 0], rects)) {
            quad(m, q, ct.tex, 64, 32, [1, 1, 1, 1], false, frame, frames);
            batch[batch.length - 1].layer = 'cutout';
          }
        }
      }
      const motion = wingMotion(dt);
      for (const [slot, item] of Object.entries(state.loadout)) {
        if (!item || slot === 'cape' || slot === 'aura') continue;
        const model = state.models[item.model];
        if (!model || model === 'loading') continue;
        const wings = slot === 'wings';
        const m = wings && (state.pose === 'fly') ? 1 : moving;
        drawModel(item, model, model.anchor === 'head' ? mats.head : mats.body, dt, m, wings ? motion : NO_MOTION);
      }
      // camera: orbit around the player
      const camDist = 5.2 / state.zoom;
      const cy = state.yaw * RAD, cp = state.pitch * RAD;
      const target = [0, state.targetY, 0];
      const eye = [target[0] + Math.sin(cy) * Math.cos(cp) * camDist, target[1] + Math.sin(cp) * camDist, target[2] + Math.cos(cy) * Math.cos(cp) * camDist];
      const view = lookAt(eye, target);
      const camRight = [Math.cos(cy), 0, -Math.sin(cy)];
      const camUp = [-Math.sin(cy) * Math.sin(cp), Math.cos(cp), -Math.cos(cy) * Math.sin(cp)];
      const aura = state.loadout.aura;
      if (aura && aura.particles) {
        stepParticles(aura, dt);
        drawParticles(aura, camRight, camUp);
      }
      gl.uniformMatrix4fv(loc.proj, false, M.persp(30 * RAD, w / h, 0.05, 50));
      gl.enable(gl.BLEND);
      gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA);
      gl.depthMask(true);
      drawBatch(batch.filter((q) => q.layer === 'cutout'), view, 'cutout');
      const trans = batch.filter((q) => q.layer === 'translucent').map((q) => ({ ...q, d: M.apply(view, ...q.pts[0])[2] })).sort((a, b) => a.d - b.d);
      gl.depthMask(false);
      drawBatch(trans, view, 'translucent');
      gl.blendFunc(gl.SRC_ALPHA, gl.ONE);
      drawBatch(batch.filter((q) => q.layer === 'glow'), view, 'glow');
      gl.depthMask(true);
      gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA);
    }

    function lookAt(eye, target) {
      let zx = eye[0] - target[0], zy = eye[1] - target[1], zz = eye[2] - target[2];
      const zl = Math.hypot(zx, zy, zz);
      zx /= zl; zy /= zl; zz /= zl;
      let xx = zz, xy = 0, xz = -zx;
      const xl = Math.hypot(xx, xy, xz) || 1;
      xx /= xl; xz /= xl;
      const yx = zy * xz - zz * xy, yy = zz * xx - zx * xz, yz = zx * xy - zy * xx;
      return [xx, yx, zx, 0, xy, yy, zy, 0, xz, yz, zz, 0,
        -(xx * eye[0] + xy * eye[1] + xz * eye[2]), -(yx * eye[0] + yy * eye[1] + yz * eye[2]), -(zx * eye[0] + zy * eye[1] + zz * eye[2]), 1];
    }

    function frame(ts) {
      if (!state.running) return;
      const dt = state.last ? Math.min(0.1, (ts - state.last) / 1000) : 1 / 60;
      state.last = ts;
      if (state.autoRotate && !dragging) state.yaw += state.autoRotate * dt;
      render(dt);
      requestAnimationFrame(frame);
    }

    // drag to turn, wheel to zoom
    let dragging = false, lastX = 0, lastY = 0;
    canvas.addEventListener('pointerdown', (e) => {
      dragging = true;
      lastX = e.clientX;
      lastY = e.clientY;
      canvas.setPointerCapture(e.pointerId);
    });
    canvas.addEventListener('pointermove', (e) => {
      if (!dragging) return;
      state.yaw -= (e.clientX - lastX) * 0.6;
      state.pitch = Math.max(-30, Math.min(60, state.pitch + (e.clientY - lastY) * 0.4));
      lastX = e.clientX;
      lastY = e.clientY;
    });
    canvas.addEventListener('pointerup', () => { dragging = false; });
    canvas.addEventListener('wheel', (e) => {
      e.preventDefault();
      state.zoom = Math.max(0.6, Math.min(2.2, state.zoom * (e.deltaY < 0 ? 1.08 : 0.93)));
    }, { passive: false });

    return {
      async setSkin(url, slim) {
        state.skin = url;
        state.slim = Boolean(slim);
        if (url) await texture(url).loaded;
      },
      setLoadout,
      set(opts2) { Object.assign(state, opts2); },
      get state() { return state; },
      render,
      start() { if (!state.running) { state.running = true; state.last = 0; requestAnimationFrame(frame); } },
      stop() { state.running = false; },
      /** For previews: steps the animation clock by dt seconds and draws. */
      step(dt) { state.time = (state.time || 0) + dt * 1000; render(dt); },
    };
  }

  window.PolarCosmetics3D = { create, boxQuads, boxUv };
})();
