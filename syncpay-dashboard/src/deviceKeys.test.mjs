import test from 'node:test';
import assert from 'node:assert/strict';
import { deviceNeedsRegistration } from './deviceKeys.js';

const device = { deviceId: 'd1', publicKey: 'a2V5', registeredVpas: ['alice@demo'] };

test('unregistered device always needs registration', () => {
  assert.equal(deviceNeedsRegistration(null, 'alice@demo'), true);
  assert.equal(deviceNeedsRegistration(undefined, 'alice@demo'), true);
});

test('registered VPA does not need re-registration', () => {
  assert.equal(deviceNeedsRegistration(device, 'alice@demo'), false);
  assert.equal(deviceNeedsRegistration(device, 'ALICE@DEMO'), false);
});

test('a different VPA needs its own registration', () => {
  assert.equal(deviceNeedsRegistration(device, 'bob@demo'), true);
});

test('device without registeredVpas list needs registration', () => {
  assert.equal(deviceNeedsRegistration({ deviceId: 'd2' }, 'alice@demo'), true);
});
