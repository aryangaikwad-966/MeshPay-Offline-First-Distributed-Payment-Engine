import assert from 'node:assert/strict';
import test from 'node:test';
import { syncResult } from './syncResult.js';

test('403 cannot be interpreted as accepted from a success-looking response body', () => {
  assert.deepEqual(syncResult(false, 403, { status: 'SYNCHRONIZED', message: 'Payment accepted' }), {
    state: 'REJECTED',
    message: 'Not authorized; packet was not accepted.',
  });
});

test('401 is permanent and explicitly reports that the packet was not accepted', () => {
  assert.equal(syncResult(false, 401, { status: 'DUPLICATE' }).state, 'REJECTED');
});

test('temporary server and rate-limit failures remain retryable, never accepted', () => {
  assert.equal(syncResult(false, 503, { status: 'SYNCHRONIZED' }).state, 'RETRY_PENDING');
  assert.equal(syncResult(false, 429, { status: 'SETTLED' }).state, 'RETRY_PENDING');
});

test('settlement is not a synchronization response, even on HTTP 2xx', () => {
  assert.equal(syncResult(true, 200, { status: 'SETTLED' }).state, 'FAILED');
});

test('only backend synchronization states are accepted', () => {
  assert.equal(syncResult(true, 201, { status: 'SYNCHRONIZED' }).state, 'SYNCHRONIZED');
  assert.equal(syncResult(true, 200, { status: 'DUPLICATE' }).state, 'DUPLICATE');
  assert.equal(syncResult(true, 202, { status: 'IN_PROGRESS' }).state, 'IN_PROGRESS');
});
