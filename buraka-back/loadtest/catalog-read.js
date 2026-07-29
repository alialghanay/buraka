import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:8080';

export const options = {
  scenarios: {
    catalog_load: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 50 },
        { duration: '1m', target: 150 },
        { duration: '30s', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
  },
};

export default function () {
  const roll = Math.random();
  if (roll < 0.7) {
    const id = 1 + Math.floor(Math.random() * 200);
    const res = http.get(`${BASE_URL}/api/products/product-${id}`);
    check(res, { 'detail 200': (r) => r.status === 200 });
  } else {
    const page = Math.floor(Math.random() * 10);
    const res = http.get(`${BASE_URL}/api/products?page=${page}&size=20`);
    check(res, { 'list 200': (r) => r.status === 200 });
  }
  sleep(0.1);
}
