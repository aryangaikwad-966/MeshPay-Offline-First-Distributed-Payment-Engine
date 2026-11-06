export type DeviceRecord = {
  deviceId: string;
  signer: CryptoKey;
  publicKey: string;
  registeredVpas: string[];
  createdAt: string;
};

export function openClientDb(): Promise<IDBDatabase>;
export function deviceNeedsRegistration(device: DeviceRecord | null, vpa: string): boolean;
export function getDeviceRecord(): Promise<DeviceRecord | null>;
export function ensureDeviceIdentity(): Promise<DeviceRecord>;
export function registerDeviceForVpa(vpa: string): Promise<DeviceRecord>;
export function revokeDeviceKeys(): Promise<void>;
