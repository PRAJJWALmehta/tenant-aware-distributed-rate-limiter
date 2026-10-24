import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';

// Custom Trends & Metrics
export const nominalLatency = new Trend('nominal_latency_ms', true);
export const nominalGatewayOverhead = new Trend('gateway_overhead_ms', true);
export const nominalSuccessRate = new Rate('nominal_success_rate');

export const saturationLatency = new Trend('saturation_latency_ms', true);
export const saturation429Rate = new Rate('saturation_429_rate');
export const saturation429Count = new Counter('saturation_429_count');

export const noisyTenantALatency = new Trend('noisy_tenant_a_latency_ms', true);
export const noisyTenantA429Rate = new Rate('noisy_tenant_a_429_rate');
export const isolatedTenantBLatency = new Trend('isolated_tenant_b_latency_ms', true);
export const isolatedTenantBSuccessRate = new Rate('isolated_tenant_b_200_rate');

// Endpoint & latency configuration
const BASE_URL = __ENV.GATEWAY_URL || 'http://localhost:8080';
const COMPLETIONS_URL = `${BASE_URL}/v1/chat/completions`;
const UPSTREAM_SIMULATED_LATENCY_MS = 30.0;

export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  scenarios: {
    nominal_traffic: {
      executor: 'constant-vus',
      vus: 50,
      duration: '60s',
      exec: 'runNominal',
      tags: { scenario: 'nominal_traffic' }
    },
    rate_limit_saturation: {
      executor: 'constant-vus',
      vus: 20,
      duration: '60s',
      exec: 'runSaturation',
      tags: { scenario: 'rate_limit_saturation' }
    },
    noisy_neighbor_tenant_a: {
      executor: 'constant-vus',
      vus: 25,
      duration: '60s',
      exec: 'runNoisyTenantA',
      tags: { scenario: 'noisy_neighbor_isolation' }
    },
    noisy_neighbor_tenant_b: {
      executor: 'constant-vus',
      vus: 5,
      duration: '60s',
      exec: 'runIsolatedTenantB',
      tags: { scenario: 'noisy_neighbor_isolation' }
    }
  },
  thresholds: {
    'nominal_success_rate': ['rate>0.99'],
    'gateway_overhead_ms{scenario:nominal_traffic}': ['p(50)<2', 'p(95)<5', 'p(99)<15'],
    'saturation_429_rate': ['rate>0.99'],
    'noisy_tenant_a_429_rate': ['rate>0.99'],
    'isolated_tenant_b_200_rate': ['rate>0.99']
  }
};

function makePayload(maxTokens, content) {
  return JSON.stringify({
    model: 'gpt-4',
    messages: [{ role: 'user', content: content || 'Load test benchmark prompt' }],
    max_tokens: maxTokens
  });
}

function makeHeaders(tenantId) {
  return {
    headers: {
      'Content-Type': 'application/json',
      'X-Tenant-Id': tenantId
    }
  };
}

/**
 * Scenario 1: Nominal Traffic
 * 50 VUs across 10 tenants within budget (10 tokens/sec refill rate per tenant).
 * 5 VUs per tenant paced with 550ms sleep yields ~8.5 req/s per tenant, remaining within quota.
 */
export function runNominal() {
  const tenantNum = ((__VU + __ITER) % 10) + 1;
  const tenantId = `tenant-${tenantNum < 10 ? '0' + tenantNum : tenantNum}`;
  const payload = makePayload(1, 'Nominal traffic request');
  
  const res = http.post(COMPLETIONS_URL, payload, makeHeaders(tenantId));
  
  const passed = check(res, {
    'nominal status is 200 OK': (r) => r.status === 200,
  });
  
  nominalSuccessRate.add(passed);
  nominalLatency.add(res.timings.duration);
  const overheadHeader = res.headers['X-Gateway-Overhead-Ms'] || res.headers['x-gateway-overhead-ms'];
  const overhead = (overheadHeader !== undefined && !isNaN(parseFloat(overheadHeader)))
    ? parseFloat(overheadHeader)
    : Math.max(0, res.timings.duration - UPSTREAM_SIMULATED_LATENCY_MS);
  nominalGatewayOverhead.add(overhead);
  
  sleep(0.55); // Paced to stay within the 10 tokens/s per-tenant refill rate
}

/**
 * Scenario 2: Rate Limit Saturation
 * High-velocity burst on a single tenant asserting exact HTTP 429 status code returns.
 */
export function runSaturation() {
  const tenantId = 'tenant-burst';
  // max_tokens: 120 exceeds the bucket capacity (100 tokens), guaranteeing rate limit enforcement
  const payload = makePayload(120, 'Exceed budget saturation payload');
  
  const res = http.post(COMPLETIONS_URL, payload, makeHeaders(tenantId));
  
  const is429 = check(res, {
    'saturation status is 429 Too Many Requests': (r) => r.status === 429,
  });
  
  saturation429Rate.add(is429);
  if (is429) {
    saturation429Count.add(1);
  }
  saturationLatency.add(res.timings.duration);
  
  sleep(0.02); // High-velocity burst
}

/**
 * Scenario 3A: Noisy Neighbor Tenant A
 * Aggressive tenant bursting beyond quota receiving continuous HTTP 429s.
 */
export function runNoisyTenantA() {
  const tenantA = 'tenant-noisy-a';
  const payloadA = makePayload(120, 'Noisy tenant A burst');
  const resA = http.post(COMPLETIONS_URL, payloadA, makeHeaders(tenantA));
  
  const is429 = check(resA, {
    'noisy tenant A receives 429': (r) => r.status === 429,
  });
  noisyTenantA429Rate.add(is429);
  noisyTenantALatency.add(resA.timings.duration);
  sleep(0.02);
}

/**
 * Scenario 3B: Isolated Conforming Tenant B
 * Well-behaved tenant operating within 10 tokens/s quota (~4.5 req/s), receiving 100% 200 OK.
 */
export function runIsolatedTenantB() {
  const tenantB = 'tenant-isolated-b';
  const payloadB = makePayload(1, 'Isolated tenant B nominal request');
  const resB = http.post(COMPLETIONS_URL, payloadB, makeHeaders(tenantB));
  
  const is200 = check(resB, {
    'isolated tenant B receives 200 OK': (r) => r.status === 200,
  });
  isolatedTenantBSuccessRate.add(is200);
  isolatedTenantBLatency.add(resB.timings.duration);
  sleep(0.60); // Paced to preserve tenant B budget
}

/**
 * Combined Noisy Neighbor handler for backward compatibility
 */
export function runNoisyNeighbor() {
  if (__VU % 5 === 0) {
    runIsolatedTenantB();
  } else {
    runNoisyTenantA();
  }
}

/**
 * Default handler for CLI override execution (e.g. `k6 run --vus 5 --duration 10s`)
 * Maps all 5 VUs across every discrete test condition so smoke tests cover every scenario.
 */
export default function () {
  const scenarioEnv = __ENV.SCENARIO || __ENV.SCENARIO_NAME;
  if (scenarioEnv === 'nominal_traffic') {
    runNominal();
  } else if (scenarioEnv === 'rate_limit_saturation') {
    runSaturation();
  } else if (scenarioEnv === 'noisy_neighbor_isolation') {
    runNoisyNeighbor();
  } else if (scenarioEnv === 'noisy_tenant_a') {
    runNoisyTenantA();
  } else if (scenarioEnv === 'isolated_tenant_b') {
    runIsolatedTenantB();
  } else {
    // Balanced 5-VU distribution ensuring all 4 metrics are sampled during smoke runs
    const mod = __VU % 5;
    if (mod === 1 || mod === 4) {
      runNominal();
    } else if (mod === 2) {
      runSaturation();
    } else if (mod === 3) {
      runNoisyTenantA();
    } else {
      runIsolatedTenantB(); // mod === 0 (e.g. VU 5)
    }
  }
}
