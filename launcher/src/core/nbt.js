'use strict';
// Minimal NBT (Minecraft's binary tag format) reader/writer, enough to edit servers.dat without losing any data.
// Values are kept as { type, value } so unknown tags survive a round trip untouched.

const T = { END: 0, BYTE: 1, SHORT: 2, INT: 3, LONG: 4, FLOAT: 5, DOUBLE: 6, BYTE_ARRAY: 7, STRING: 8, LIST: 9, COMPOUND: 10, INT_ARRAY: 11, LONG_ARRAY: 12 };

/** Java's "modified UTF-8" (only differs from UTF-8 for NUL and supplementary characters). */
function decodeMutf8(buf) {
  let out = '';
  for (let i = 0; i < buf.length;) {
    const a = buf[i++];
    if (a < 0x80) out += String.fromCharCode(a);
    else if ((a & 0xe0) === 0xc0) out += String.fromCharCode(((a & 0x1f) << 6) | (buf[i++] & 0x3f));
    else {
      const b = buf[i++], c = buf[i++];
      out += String.fromCharCode(((a & 0x0f) << 12) | ((b & 0x3f) << 6) | (c & 0x3f));
    }
  }
  return out;
}

function encodeMutf8(str) {
  const bytes = [];
  for (let i = 0; i < str.length; i++) {
    const c = str.charCodeAt(i);
    if (c !== 0 && c < 0x80) bytes.push(c);
    else if (c < 0x800) bytes.push(0xc0 | (c >> 6), 0x80 | (c & 0x3f));
    else bytes.push(0xe0 | (c >> 12), 0x80 | ((c >> 6) & 0x3f), 0x80 | (c & 0x3f));
  }
  return Buffer.from(bytes);
}

function read(buffer) {
  let p = 0;
  const need = (n) => {
    if (p + n > buffer.length) throw new Error('NBT data is truncated');
  };
  const u8 = () => { need(1); return buffer.readUInt8(p++); };
  const i8 = () => { need(1); return buffer.readInt8(p++); };
  const i16 = () => { need(2); const v = buffer.readInt16BE(p); p += 2; return v; };
  const u16 = () => { need(2); const v = buffer.readUInt16BE(p); p += 2; return v; };
  const i32 = () => { need(4); const v = buffer.readInt32BE(p); p += 4; return v; };
  const i64 = () => { need(8); const v = buffer.readBigInt64BE(p); p += 8; return v; };
  const str = () => { const n = u16(); need(n); const s = decodeMutf8(buffer.subarray(p, p + n)); p += n; return s; };

  function payload(type) {
    switch (type) {
      case T.BYTE: return i8();
      case T.SHORT: return i16();
      case T.INT: return i32();
      case T.LONG: return i64();
      case T.FLOAT: { need(4); const v = buffer.readFloatBE(p); p += 4; return v; }
      case T.DOUBLE: { need(8); const v = buffer.readDoubleBE(p); p += 8; return v; }
      case T.BYTE_ARRAY: { const n = i32(); need(n); const v = Buffer.from(buffer.subarray(p, p + n)); p += n; return v; }
      case T.STRING: return str();
      case T.LIST: {
        const itemType = u8();
        const n = i32();
        const items = [];
        for (let i = 0; i < n; i++) items.push(payload(itemType));
        return { itemType, items };
      }
      case T.COMPOUND: {
        const entries = {};
        for (;;) {
          const t = u8();
          if (t === T.END) return entries;
          const name = str();
          entries[name] = { type: t, value: payload(t) };
        }
      }
      case T.INT_ARRAY: { const n = i32(); const v = []; for (let i = 0; i < n; i++) v.push(i32()); return v; }
      case T.LONG_ARRAY: { const n = i32(); const v = []; for (let i = 0; i < n; i++) v.push(i64()); return v; }
      default: throw new Error(`Unknown NBT tag type ${type}`);
    }
  }

  const rootType = u8();
  if (rootType !== T.COMPOUND) throw new Error('NBT root must be a compound');
  const name = str();
  return { name, value: payload(T.COMPOUND) };
}

function write(root) {
  const chunks = [];
  const push = (b) => chunks.push(b);
  const u8 = (v) => { const b = Buffer.alloc(1); b.writeUInt8(v); push(b); };
  const i8 = (v) => { const b = Buffer.alloc(1); b.writeInt8(v); push(b); };
  const i16 = (v) => { const b = Buffer.alloc(2); b.writeInt16BE(v); push(b); };
  const i32 = (v) => { const b = Buffer.alloc(4); b.writeInt32BE(v); push(b); };
  const i64 = (v) => { const b = Buffer.alloc(8); b.writeBigInt64BE(BigInt(v)); push(b); };
  const str = (s) => { const e = encodeMutf8(s); if (e.length > 65535) throw new Error('NBT string too long'); const n = Buffer.alloc(2); n.writeUInt16BE(e.length); push(n); push(e); };

  function payload(type, v) {
    switch (type) {
      case T.BYTE: return i8(v);
      case T.SHORT: return i16(v);
      case T.INT: return i32(v);
      case T.LONG: return i64(v);
      case T.FLOAT: { const b = Buffer.alloc(4); b.writeFloatBE(v); return push(b); }
      case T.DOUBLE: { const b = Buffer.alloc(8); b.writeDoubleBE(v); return push(b); }
      case T.BYTE_ARRAY: i32(v.length); return push(Buffer.from(v));
      case T.STRING: return str(v);
      case T.LIST: u8(v.items.length ? v.itemType : (v.itemType || T.END)); i32(v.items.length); return v.items.forEach((it) => payload(v.itemType, it));
      case T.COMPOUND:
        for (const [name, tag] of Object.entries(v)) {
          u8(tag.type);
          str(name);
          payload(tag.type, tag.value);
        }
        return u8(T.END);
      case T.INT_ARRAY: i32(v.length); return v.forEach(i32);
      case T.LONG_ARRAY: i32(v.length); return v.forEach(i64);
      default: throw new Error(`Unknown NBT tag type ${type}`);
    }
  }

  u8(T.COMPOUND);
  str(root.name || '');
  payload(T.COMPOUND, root.value);
  return Buffer.concat(chunks);
}

module.exports = { T, read, write };
