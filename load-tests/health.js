// k6 load test: service health endpoints.
// Run: k6 run load-tests/health.js
// Or:  BASE_URL=http://localhost:8081 k6 run load-tests/health.js

import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost';

export const options = {
  scenarios: {
    health_checks: {
      executor: 'constant-arrival-rate',
      rate: 20,
      timeUnit: '1s',
      duration: '30s',
      preAllocatedVUs: 5,
      maxVUs: 20,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
  },
};

const SERVICES = [
  { name: 'gateway',   health: `${BASE_URL}:8080/actuator/health` },
  { name: 'payment',   health: `${BASE_URL}:8081/actuator/health` },
  { name: 'saga',      health: `${BASE_URL}:8082/actuator/health` },
  { name: 'eureka',    health: `${BASE_URL}:8761/actuator/health` },
  { name: 'dashboard', health: `${BASE_URL}:3001/` },
];

export default function () {
  for (const svc of SERVICES) {
    const res = http.get(svc.health, { tags: { service: svc.name } });
    check(res, {
      [`${svc.name} returns 2xx`]: (r) => r.status >= 200 && r.status < 300,
    });
  }
  sleep(0.2);
}
