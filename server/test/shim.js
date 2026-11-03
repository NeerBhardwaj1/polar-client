// A stand-in for the parts of the Cloudflare Workers runtime the server uses, so the tests run on plain Node:
// SQLite storage (node:sqlite), hibernatable WebSockets and 101 responses.
import { DatabaseSync } from 'node:sqlite';

export class FakeSocket {
  constructor() {
    this.readyState = 1;
    this.attachment = null;
    this.tags = [];
    this.sent = [];
  }

  serializeAttachment(a) {
    this.attachment = structuredClone(a);
  }

  deserializeAttachment() {
    return structuredClone(this.attachment);
  }

  send(m) {
    this.sent.push(m);
  }

  close(code, reason) {
    this.readyState = 3;
    this.closed = { code, reason };
  }
}

globalThis.WebSocketPair = function WebSocketPair() {
  return { 0: new FakeSocket(), 1: new FakeSocket() };
};
globalThis.WebSocketRequestResponsePair = class {
  constructor(request, response) {
    this.request = request;
    this.response = response;
  }
};

// Node's Response refuses status 101; the Workers runtime uses it for WebSocket upgrades.
const NodeResponse = globalThis.Response;
globalThis.Response = class Response extends NodeResponse {
  constructor(body, init = {}) {
    if (init.status === 101) {
      super(null, { status: 200 });
      this.upgraded = 101;
      this.webSocket = init.webSocket;
    } else {
      super(body, init);
    }
  }

  get status() {
    return this.upgraded || super.status;
  }
};

export function makeCtx() {
  const db = new DatabaseSync(':memory:');
  const sockets = [];
  const sql = {
    exec(query, ...bindings) {
      const rows = db.prepare(query).all(...bindings).map((r) => ({ ...r }));
      const rowsWritten = db.prepare('SELECT changes() AS c').get().c;
      return {
        toArray: () => rows,
        one: () => {
          if (rows.length !== 1) throw new Error(`Expected exactly one row, got ${rows.length}`);
          return rows[0];
        },
        rowsWritten,
      };
    },
  };
  return {
    db,
    sockets,
    storage: {
      sql,
      transactionSync(fn) {
        db.exec('BEGIN');
        try {
          const r = fn();
          db.exec('COMMIT');
          return r;
        } catch (e) {
          db.exec('ROLLBACK');
          throw e;
        }
      },
    },
    acceptWebSocket(ws, tags = []) {
      ws.tags = tags;
      sockets.push(ws);
    },
    getWebSockets(tag) {
      return sockets.filter((ws) => ws.readyState === 1 && (!tag || ws.tags.includes(tag)));
    },
    setWebSocketAutoResponse(pair) {
      this.autoResponse = pair;
    },
  };
}
