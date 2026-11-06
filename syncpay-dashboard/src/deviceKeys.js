// Device-managed signing keys: a non-exportable RSA-PSS key pair is generated
// once per browser profile, persisted in IndexedDB (structured-cloned
// CryptoKey), and its public half is registered with the backend per sender
// VPA. Manual PKCS#8 import remains available as a developer override.

export const CLIENT_DB = 'syncpay-offline-client';
export const PACKET_STORE = 'packets';
export const DEVICE_STORE = 'devices';
export const DEVICE_RECORD_KEY = 'default';

export function openClientDb() {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(CLIENT_DB, 2);
    req.onupgradeneeded = () => {
      const d = req.result;
      if (!d.objectStoreNames.contains(PACKET_STORE)) d.createObjectStore(PACKET_STORE, { keyPath: 'packetId' });
      if (!d.objectStoreNames.contains(DEVICE_STORE)) d.createObjectStore(DEVICE_STORE, { keyPath: 'key' });
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

function store(db, name, mode) {
  return db.transaction(name, mode).objectStore(name);
}

export function deviceNeedsRegistration(device, vpa) {
  return !device || !Array.isArray(device.registeredVpas) || !device.registeredVpas.includes(String(vpa).toLowerCase());
}

export async function getDeviceRecord() {
  const d = await openClientDb();
  return new Promise((resolve, reject) => {
    const r = store(d, DEVICE_STORE, 'readonly').get(DEVICE_RECORD_KEY);
    r.onsuccess = () => resolve(r.result || null);
    r.onerror = () => reject(r.error);
  });
}

async function saveDeviceRecord(record) {
  const d = await openClientDb();
  return new Promise((resolve, reject) => {
    const r = store(d, DEVICE_STORE, 'readwrite').put({ key: DEVICE_RECORD_KEY, ...record });
    r.onsuccess = () => resolve();
    r.onerror = () => reject(r.error);
  });
}

export async function deleteDeviceRecord() {
  const d = await openClientDb();
  return new Promise((resolve, reject) => {
    const r = store(d, DEVICE_STORE, 'readwrite').delete(DEVICE_RECORD_KEY);
    r.onsuccess = () => resolve();
    r.onerror = () => reject(r.error);
  });
}

function b64(buffer) {
  const bytes = buffer instanceof Uint8Array ? buffer : new Uint8Array(buffer);
  let s = '';
  bytes.forEach(x => s += String.fromCharCode(x));
  return btoa(s);
}

/**
 * Returns the persisted device identity, generating and storing a fresh
 * non-exportable RSA-PSS signing key on first use. The private key is never
 * exportable; only the SPKI public key leaves the device (for registration).
 */
export async function ensureDeviceIdentity() {
  const existing = await getDeviceRecord();
  if (existing && existing.signer) return existing;
  const pair = await crypto.subtle.generateKey(
    { name: 'RSA-PSS', modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: 'SHA-256' },
    false, ['sign', 'verify']);
  const publicKey = b64(await crypto.subtle.exportKey('spki', pair.publicKey));
  const record = {
    deviceId: crypto.randomUUID(),
    signer: pair.privateKey,
    publicKey,
    registeredVpas: [],
    createdAt: new Date().toISOString(),
  };
  await saveDeviceRecord(record);
  return record;
}

/** Registers the device public key for a sender VPA (idempotent server-side). */
export async function registerDeviceForVpa(vpa) {
  const device = await ensureDeviceIdentity();
  const normalized = String(vpa).toLowerCase();
  if (!deviceNeedsRegistration(device, normalized)) return device;
  const r = await fetch('/api/payment/devices/register', {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ vpa: normalized, deviceId: device.deviceId, publicKey: device.publicKey }),
  });
  if (!r.ok) {
    const data = await r.json().catch(() => ({}));
    throw new Error(data.message || `Device registration failed (HTTP ${r.status})`);
  }
  const updated = { ...device, registeredVpas: [...(device.registeredVpas || []), normalized] };
  await saveDeviceRecord(updated);
  return updated;
}

/** Revokes every registered VPA server-side, then wipes the local identity. */
export async function revokeDeviceKeys() {
  const device = await getDeviceRecord();
  if (device) {
    for (const vpa of device.registeredVpas || []) {
      try {
        await fetch(`/api/payment/devices/${encodeURIComponent(vpa)}/revoke`, {
          method: 'POST', headers: { 'content-type': 'application/json' }, body: '{}',
        });
      } catch { /* best effort: local wipe still proceeds */ }
    }
  }
  await deleteDeviceRecord();
}
