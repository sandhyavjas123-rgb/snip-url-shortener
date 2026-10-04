import http from 'k6/http'
import { check, sleep } from 'k6'

const baseUrl = __ENV.BASE_URL || 'http://localhost:3000'
const profile = __ENV.PROFILE || 'smoke'

export const options = profile === 'load'
  ? { stages: [{ duration: '30s', target: 50 }, { duration: '1m', target: 100 }, { duration: '20s', target: 0 }], thresholds: { http_req_failed: ['rate<0.01'], http_req_duration: ['p(95)<400'] } }
  : { vus: 3, duration: '10s', thresholds: { http_req_failed: ['rate<0.01'], http_req_duration: ['p(95)<750'] } }

export function setup() {
  const code = `load${Date.now().toString().slice(-4)}`
  const response = http.post(`${baseUrl}/api/links`, JSON.stringify({ url: 'https://example.com/performance', customAlias: code }), { headers: { 'Content-Type': 'application/json' } })
  check(response, { 'link created': r => r.status === 201 })
  return { code: response.json('code') }
}

export default function (data) {
  const response = http.get(`${baseUrl}/r/${data.code}`, { redirects: 0 })
  check(response, { 'redirect returned': r => r.status === 302 })
  sleep(0.2)
}

