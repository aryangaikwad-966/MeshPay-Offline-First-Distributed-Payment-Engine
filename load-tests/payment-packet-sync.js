// k6 load test: payment packet synchronization endpoint.
//
// Generates a signed/encrypted packet for each VU iteration using a
// pre-created packet template. k6 cannot perform RSA-OAEP/AES-GCM itself, so
// create packet JSON files first with the Node helper:
//
//   node scripts/create-payment-packet.js
//   # writes /tmp/meshpay-packet-<uuid>.json
//
// Then point PACKET_FILE at the generated file. Note: each packet is a
// one-time credential (sender nonce replay protection), so this test is
// useful for duplicate/replay behavior and throughput of identical packets,
// not for unique successful submissions. To generate many unique packets,
// loop the helper script and pass a directory via PACKET_DIR.
//
// Run: PACKET_FILE=/tmp/meshpay-packet-xxx.json k6 run load-tests/payment-packet-sync.js
// Or:  GATEWAY_URL=http://localhost:8080 PACKET_FILES=/tmp/p1.json,/tmp/p2.json k6 run load-tests/payment-packet-sync.js

import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';

const GATEWAY_URL = __ENV.GATEWAY_URL || 'http://localhost:8080';
const TOKEN = __ENV.MESHPAY_TOKEN || '';

const headers = Object.assign(
  { 'Content-Type': 'application/json' },
  TOKEN ? { Authorization: `Bearer ${TOKEN}` } : {},
);

let packets;
if (__ENV.PACKET_FILES) {
  packets = new SharedArray('packets', function () {
    return __ENV.PACKET_FILES.split(',').map((f) => JSON.parse(open(f.trim())));
  });
} else if (__ENV.PACKET_FILE) {
  packets = [JSON.parse(open(__ENV.PACKET_FILE))];
} else {
  throw new Error('Set PACKET_FILE or PACKET_FILES (comma-separated JSON packet paths).');
}

export const options = {
  scenarios: {
    packet_sync: {
      executor: 'ramping-vus',
      startVUs: 1,
      stages: [
        { duration: '10s', target: 5 },
        { duration: '20s', target: 20 },
        { duration: '10s', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<2000'],
  },
};

export default function () {
  // Round-robin through provided packets; identical packets exercise the
  // idempotency/duplicate path, unique packets exercise fresh ingestion.
  const packet = packets[(__ITER + __VU) % packets.length];
  const res = http.post(
    `${GATEWAY_URL}/api/payment/packets/sync`,
    JSON.stringify(packet),
    { headers, tags: { endpoint: 'packet-sync' } },
  );
  check(res, {
    'sync returns 2xx': (r) => r.status >= 200 && r.status < 300,
  });
  sleep(0.2);
}
