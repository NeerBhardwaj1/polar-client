'use strict';
// Microsoft -> Xbox Live -> XSTS -> Minecraft sign-in (the same chain the official launcher uses).
const { postForm, postJson, request } = require('./http');

/**
 * OAuth client. For a public release, register your own Azure app and request Minecraft API access
 * (https://aka.ms/mce-reviewappid), then set POLAR_MSA_CLIENT_ID / redirect accordingly.
 */
const CLIENT_ID = process.env.POLAR_MSA_CLIENT_ID || '00000000402b5328';
const REDIRECT_URI = process.env.POLAR_MSA_REDIRECT || 'https://login.live.com/oauth20_desktop.srf';
const SCOPE = 'XboxLive.signin offline_access';

const XSTS_ERRORS = {
  2148916233: 'This Microsoft account has no Xbox profile yet. Sign in once at minecraft.net, then try again.',
  2148916235: 'Xbox Live is not available in your country.',
  2148916236: 'This account needs adult verification on xbox.com.',
  2148916237: 'This account needs adult verification on xbox.com.',
  2148916238: 'This is a child account — an adult must add it to a Microsoft Family to play.',
};

function authorizeUrl(prompt = 'select_account') {
  const q = new URLSearchParams({ client_id: CLIENT_ID, response_type: 'code', redirect_uri: REDIRECT_URI, scope: SCOPE, prompt });
  return `https://login.live.com/oauth20_authorize.srf?${q}`;
}

/** Pulls ?code= (or an error) out of the redirect URL; returns null for unrelated URLs. */
function parseRedirect(url) {
  if (!url || !url.startsWith(REDIRECT_URI)) return null;
  const params = new URL(url).searchParams;
  if (params.get('error')) return { error: params.get('error_description') || params.get('error') };
  const code = params.get('code');
  return code ? { code } : null;
}

async function exchangeCode(code) {
  return postForm('https://login.live.com/oauth20_token.srf', {
    client_id: CLIENT_ID, code, grant_type: 'authorization_code', redirect_uri: REDIRECT_URI,
  });
}

async function refreshMicrosoft(refreshToken) {
  return postForm('https://login.live.com/oauth20_token.srf', {
    client_id: CLIENT_ID, refresh_token: refreshToken, grant_type: 'refresh_token',
  });
}

async function xsts(userToken, relyingParty) {
  try {
    return await postJson('https://xsts.auth.xboxlive.com/xsts/authorize', {
      Properties: { SandboxId: 'RETAIL', UserTokens: [userToken] },
      RelyingParty: relyingParty,
      TokenType: 'JWT',
    });
  } catch (e) {
    let code;
    try {
      code = JSON.parse(e.body).XErr;
    } catch {
      // not JSON
    }
    throw new Error(XSTS_ERRORS[code] || `Xbox sign-in failed (${code || e.message}).`);
  }
}

/** Microsoft access token -> Minecraft access token + profile. Throws if the account doesn't own Java Edition. */
async function minecraftLogin(msAccessToken) {
  const xbl = await postJson('https://user.auth.xboxlive.com/user/authenticate', {
    Properties: { AuthMethod: 'RPS', SiteName: 'user.auth.xboxlive.com', RpsTicket: `d=${msAccessToken}` },
    RelyingParty: 'http://auth.xboxlive.com',
    TokenType: 'JWT',
  });
  const mcXsts = await xsts(xbl.Token, 'rp://api.minecraftservices.com/');
  const uhs = mcXsts.DisplayClaims.xui[0].uhs;
  let xuid = '0';
  try {
    const xboxXsts = await xsts(xbl.Token, 'http://xboxlive.com');
    xuid = (xboxXsts.DisplayClaims.xui[0] && xboxXsts.DisplayClaims.xui[0].xid) || '0';
  } catch {
    // xuid is optional
  }
  const mc = await postJson('https://api.minecraftservices.com/authentication/login_with_xbox', {
    identityToken: `XBL3.0 x=${uhs};${mcXsts.Token}`,
  });
  const profile = await getProfile(mc.access_token);
  return {
    uuid: profile.id,
    name: profile.name,
    xuid,
    mcToken: mc.access_token,
    mcExpiresAt: Date.now() + (mc.expires_in || 86400) * 1000,
  };
}

async function getProfile(mcToken) {
  try {
    const res = await request('https://api.minecraftservices.com/minecraft/profile', {
      headers: { Authorization: `Bearer ${mcToken}`, Accept: 'application/json' },
    });
    return res.json();
  } catch (e) {
    if (e.status === 404) {
      const err = new Error('This Microsoft account doesn\'t own Minecraft: Java Edition (or hasn\'t picked a username yet at minecraft.net).');
      err.notOwned = true;
      throw err;
    }
    throw e;
  }
}

/** Full sign-in after the browser step. */
async function loginWithCode(code) {
  const ms = await exchangeCode(code);
  const mc = await minecraftLogin(ms.access_token);
  return { ...mc, msRefreshToken: ms.refresh_token };
}

/** Refreshes a saved account; returns fresh tokens (and possibly a rotated refresh token). */
async function refreshAccount(msRefreshToken) {
  const ms = await refreshMicrosoft(msRefreshToken);
  const mc = await minecraftLogin(ms.access_token);
  return { ...mc, msRefreshToken: ms.refresh_token || msRefreshToken };
}

module.exports = { CLIENT_ID, REDIRECT_URI, authorizeUrl, parseRedirect, loginWithCode, refreshAccount, getProfile };
