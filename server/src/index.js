// The Polar Client server: a Cloudflare Worker. Everything under /v1/ goes to one Durable Object (the Hub), which keeps
// the data in its built-in SQLite database and holds the live connections of players who are online. Other paths are
// static files from ./public (the cosmetics' textures and models, the admin page), which Cloudflare serves for free.
import { Hub } from './hub.js';

export { Hub };

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.pathname.startsWith('/v1/')) {
      const hub = env.HUB.get(env.HUB.idFromName('polar'));
      return hub.fetch(request);
    }
    return env.ASSETS.fetch(request);
  },
};
