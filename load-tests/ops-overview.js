// k6 load test: read-only ops endpoints.
// Requires the Compose "dev" profile (or a JWT in the Authorization header
// when running a secured profile).
//
// Run: k6 run load-tests/ops-overview.js
// Or:  GATEWAY_URL=http://localhost:8080 k6 run load-tests/ops-overview.js

import http from 'k6/http';
import { check, sleep } from 'k6';

const GATEWAY_URL = __ENV.GATEWAY_URL || 'http://localhost:8080';
const TOKEN = __ENV.SYNCPAY_TOKEN || '';

export const options = {
  scenarios: {
    ops_reads: {
      executor: 'constant-vus',
      vus: 10,
      duration: '30s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<800'],
  },
};

const headers = TOKEN ? { Authorization: `Bearer ${TOKEN}` } : {};

const ENDPOINTS = [
  { name: 'payment-ops-overview',  url: `${GATEWAY_URL}/api/payment/ops/overview` },
  { name: 'payment-ops-transactions', url: `${GATEWAY_URL}/api/payment/ops/transactions` },
  { name: 'payment-ops-outbox',    url: `${GATEWAY_URL}/api/payment/ops/outbox` },
  { name: 'saga-ops-overview',     url: `${GATEWAY_URL}/api/saga/ops/overview` },
  { name: 'saga-ops-sagas',        url: `${GATEWAY_URL}/api/saga/ops/sagas` },
];

export default function () {
  for (const ep of ENDPOINTS) {
    const res = http.get(ep.url, { headers, tags: { endpoint: ep.name } });
    check(res, {
      [`${ep.name} returns 2xx`]: (r) => r.status >= 200 && r.status < 300,
    });
  }
  sleep(0.5);
}
