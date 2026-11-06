#!/usr/bin/env node

// Development-only JWT tool for SyncPay's secured (non-dev) profile.
//
// The backend services validate JWTs via JWKS (SYNCPAY_JWKS_URI) with issuer
// and audience checks. This script provides the missing issuer for local work:
//
//   1. Generate a persistent dev RSA keypair under .local-keys/ (git-ignored).
//   2. `serve`  — run a tiny JWKS endpoint on http://localhost:8799
//   3. `mint`   — print a signed JWT for curl/Postman testing
//
// Usage:
//   node scripts/mint-dev-token.js serve [--port 8799]
//   node scripts/mint-dev-token.js mint --role ADMIN [--sub ops] [--scope "syncpay:ops syncpay:metrics:read"] [--ttl 3600]
//
// Then point the services at the JWKS endpoint, e.g. in .env:
//   SYNCPAY_JWKS_URI=http://localhost:8799/.well-known/jwks.json
//   SYNCPAY_JWT_ISSUER=syncpay-dev
//   SYNCPAY_JWT_AUDIENCE=syncpay
//
// NEVER use this for real credentials. Keys are dev-only and stored locally.

const crypto = require('node:crypto');
const fs = require('node:fs');
const http = require('node:http');
const path = require('node:path');

const KEY_DIR = path.join(__dirname, '..', '.local-keys');
const PRIVATE_FILE = path.join(KEY_DIR, 'dev-jwt-private.pem');
const PUBLIC_FILE = path.join(KEY_DIR, 'dev-jwt-public.pem');
const ISSUER = 'syncpay-dev';
const AUDIENCE = 'syncpay';
const KID = 'syncpay-dev-key-1';

function ensureKeys() {
  fs.mkdirSync(KEY_DIR, { recursive: true, mode: 0o700 });
  if (fs.existsSync(PRIVATE_FILE) && fs.existsSync(PUBLIC_FILE)) {
    return {
      privateKey: crypto.createPrivateKey(fs.readFileSync(PRIVATE_FILE)),
      publicKey: crypto.createPublicKey(fs.readFileSync(PUBLIC_FILE)),
    };
  }
  const { privateKey, publicKey } = crypto.generateKeyPairSync('rsa', {
    modulusLength: 2048,
    publicKeyEncoding: { type: 'spki', format: 'pem' },
    privateKeyEncoding: { type: 'pkcs8', format: 'pem' },
  });
  fs.writeFileSync(PRIVATE_FILE, privateKey, { mode: 0o600 });
  fs.writeFileSync(PUBLIC_FILE, publicKey, { mode: 0o644 });
  return {
    privateKey: crypto.createPrivateKey(privateKey),
    publicKey: crypto.createPublicKey(publicKey),
  };
}

function jwkFor(publicKey) {
  const jwk = publicKey.export({ format: 'jwk' });
  return { kty: jwk.kty, n: jwk.n, e: jwk.e, kid: KID, use: 'sig', alg: 'RS256' };
}

function b64url(input) {
  return Buffer.from(input).toString('base64url');
}

function mint({ sub = 'dev-operator', role = 'ADMIN', scope, ttl = 3600, name, email }) {
  const { privateKey } = ensureKeys();
  const now = Math.floor(Date.now() / 1000);
  const header = { alg: 'RS256', typ: 'JWT', kid: KID };
  const payload = {
    iss: ISSUER,
    aud: AUDIENCE,
    sub,
    iat: now,
    exp: now + Number(ttl),
    role,
    scope: scope || defaultScope(role),
    ...(name ? { name } : {}),
    ...(email ? { email } : {}),
  };
  const signingInput = `${b64url(JSON.stringify(header))}.${b64url(JSON.stringify(payload))}`;
  const signature = crypto.sign('sha256', Buffer.from(signingInput), {
    key: privateKey,
    padding: crypto.constants.RSA_PKCS1_PADDING,
  });
  return `${signingInput}.${signature.toString('base64url')}`;
}

function defaultScope(role) {
  switch (role) {
    case 'ADMIN': return 'syncpay:ops syncpay:metrics:read syncpay:admin';
    case 'MERCHANT': return 'syncpay:merchant';
    default: return 'syncpay:customer';
  }
}

function arg(name, fallback) {
  const i = process.argv.indexOf(`--${name}`);
  return i >= 0 && process.argv[i + 1] ? process.argv[i + 1] : fallback;
}

const command = process.argv[2];

if (command === 'serve') {
  const { publicKey } = ensureKeys();
  const port = Number(arg('port', '8799'));
  const jwks = { keys: [jwkFor(publicKey)] };
  http.createServer((req, res) => {
    if (req.url === '/.well-known/jwks.json') {
      res.writeHead(200, { 'content-type': 'application/json' });
      res.end(JSON.stringify(jwks));
    } else {
      res.writeHead(404); res.end('not found');
    }
  }).listen(port, () => {
    console.log(`Dev JWKS endpoint: http://localhost:${port}/.well-known/jwks.json`);
    console.log(`Set SYNCPAY_JWKS_URI=http://localhost:${port}/.well-known/jwks.json`);
    console.log(`Set SYNCPAY_JWT_ISSUER=${ISSUER} and SYNCPAY_JWT_AUDIENCE=${AUDIENCE}`);
  });
} else if (command === 'mint') {
  const token = mint({
    sub: arg('sub', 'dev-operator'),
    role: arg('role', 'ADMIN'),
    scope: arg('scope'),
    ttl: arg('ttl', '3600'),
    name: arg('name'),
    email: arg('email'),
  });
  console.log(token);
} else {
  console.error('Usage: node scripts/mint-dev-token.js <serve|mint> [options]');
  console.error('  serve [--port 8799]');
  console.error('  mint --role ADMIN|CUSTOMER|MERCHANT [--sub id] [--scope "..."] [--ttl seconds]');
  process.exit(2);
}
