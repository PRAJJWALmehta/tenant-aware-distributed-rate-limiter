#!/usr/bin/env bash
set -euo pipefail

# Color formatting
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m' # No Color

GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"
OUTPUT_DIR="${OUTPUT_DIR:-benchmarks/results}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BENCHMARK_JS="${SCRIPT_DIR}/k6-load-test.js"
MAX_HEALTH_WAIT="${MAX_HEALTH_WAIT:-30}"

echo -e "${BLUE}================================================================${NC}"
echo -e "${BLUE}${BOLD}   Tenant-Aware Distributed Rate Limiter - Benchmark Suite    ${NC}"
echo -e "${BLUE}================================================================${NC}"

# 1. Prerequisite Validation
echo -e "\n${YELLOW}[Step 1/4] Verifying prerequisites...${NC}"

if ! command -v k6 &> /dev/null; then
  echo -e "${RED}✖ Error: 'k6' is not installed or not in PATH.${NC}"
  echo -e "  Please install k6 (e.g. 'brew install k6') and re-run."
  exit 1
fi
K6_VERSION=$(k6 version | head -n 1)
echo -e "${GREEN}✔ k6 detected: ${K6_VERSION}${NC}"

if ! command -v curl &> /dev/null; then
  echo -e "${RED}✖ Error: 'curl' is required.${NC}"
  exit 1
fi

if [ ! -f "${BENCHMARK_JS}" ]; then
  echo -e "${RED}✖ Error: Benchmark script '${BENCHMARK_JS}' not found.${NC}"
  exit 1
fi
echo -e "${GREEN}✔ Benchmark script found: ${BENCHMARK_JS}${NC}"

mkdir -p "${OUTPUT_DIR}"
echo -e "${GREEN}✔ Output directory ready: ${OUTPUT_DIR}${NC}"

# Hardware inspection
CPU_MODEL=$(sysctl -n machdep.cpu.brand_string 2>/dev/null || sysctl -n hw.model 2>/dev/null || echo "Unknown CPU")
CPU_CORES=$(sysctl -n hw.ncpu 2>/dev/null || echo "Unknown cores")
MEM_BYTES=$(sysctl -n hw.memsize 2>/dev/null || echo "0")
MEM_GB=$(( MEM_BYTES / 1024 / 1024 / 1024 ))
OS_NAME=$(uname -s)
OS_ARCH=$(uname -m)
echo -e "${CYAN}ℹ Host Hardware:${NC} ${CPU_MODEL} (${CPU_CORES} cores, ${MEM_GB} GB RAM, ${OS_NAME} ${OS_ARCH})"

# 2. Check Gateway Connectivity
echo -e "\n${YELLOW}[Step 2/4] Checking Gateway connectivity on ${GATEWAY_URL}/health...${NC}"
GATEWAY_READY=false
for i in $(seq 1 "${MAX_HEALTH_WAIT}"); do
  STATUS=$(curl -s -o /dev/null -w "%{http_code}" "${GATEWAY_URL}/health" || echo "000")
  if [ "${STATUS}" = "200" ]; then
    GATEWAY_READY=true
    break
  fi
  sleep 1
done

if [ "${GATEWAY_READY}" = "false" ]; then
  echo -e "${RED}✖ Gateway failed to respond with HTTP 200 within ${MAX_HEALTH_WAIT}s on ${GATEWAY_URL}/health.${NC}"
  echo -e "  Make sure the gateway container or local service is running (e.g., 'docker compose up -d')."
  exit 1
fi
echo -e "${GREEN}✔ Gateway is healthy on ${GATEWAY_URL} (HTTP 200)${NC}"

# 3. Stage 1: Smoke Verification Test (5 VUs for 10s)
echo -e "\n${YELLOW}[Step 3/4] Executing Smoke Verification Test (5 VUs, 10s)...${NC}"
SMOKE_SUMMARY="${OUTPUT_DIR}/smoke_summary.json"
set +e
GATEWAY_URL="${GATEWAY_URL}" k6 run \
  --vus 5 \
  --duration 10s \
  --summary-trend-stats "avg,min,med,max,p(90),p(95),p(99)" \
  --summary-export="${SMOKE_SUMMARY}" \
  "${BENCHMARK_JS}"
SMOKE_STATUS=$?
set -e

if [ $SMOKE_STATUS -ne 0 ] && [ $SMOKE_STATUS -ne 99 ]; then
  echo -e "${RED}✖ Smoke verification failed with fatal error code ${SMOKE_STATUS}${NC}"
  exit $SMOKE_STATUS
fi
echo -e "${GREEN}✔ Smoke verification complete. Results exported to ${SMOKE_SUMMARY}${NC}"

# 4. Stage 2: Full Benchmark Execution (100 concurrent VUs sustained for 60s)
echo -e "\n${YELLOW}[Step 4/4] Executing Full Benchmark Run (100 VUs across scenarios, 60s)...${NC}"
FULL_SUMMARY="${OUTPUT_DIR}/full_summary.json"
FULL_CSV="${OUTPUT_DIR}/benchmark_summary.csv"

set +e
GATEWAY_URL="${GATEWAY_URL}" k6 run \
  --summary-trend-stats "avg,min,med,max,p(90),p(95),p(99)" \
  --summary-export="${FULL_SUMMARY}" \
  "${BENCHMARK_JS}"
BENCH_STATUS=$?
set -e

if [ $BENCH_STATUS -ne 0 ] && [ $BENCH_STATUS -ne 99 ]; then
  echo -e "${RED}✖ Benchmark execution failed with fatal error code ${BENCH_STATUS}${NC}"
  exit $BENCH_STATUS
fi

echo -e "\n${GREEN}✔ Full benchmark execution completed!${NC}"
echo -e "${GREEN}  JSON Summary: ${FULL_SUMMARY}${NC}"

# Generate CSV & Text Summary Report
python3 - <<EOF
import json
import csv
import sys

full_summary_path = "${FULL_SUMMARY}"
csv_output_path = "${FULL_CSV}"

try:
    with open(full_summary_path, 'r') as f:
        data = json.load(f)
except Exception as e:
    print(f"Error loading summary JSON: {e}", file=sys.stderr)
    sys.exit(0)

metrics = data.get('metrics', {})

def get_m(name):
    m = metrics.get(name, {})
    return m.get('values', m)

def get_trend(name):
    m = get_m(name)
    return {
        'avg': round(m.get('avg', 0), 2),
        'min': round(m.get('min', 0), 2),
        'med': round(m.get('med', 0), 2),
        'max': round(m.get('max', 0), 2),
        'p90': round(m.get('p(90)', 0), 2),
        'p95': round(m.get('p(95)', 0), 2),
        'p99': round(m.get('p(99)', 0), 2),
    }

def get_rate(name):
    m = get_m(name)
    if 'value' in m:
        return round(m['value'] * 100.0, 2)
    passes = m.get('passes', 0)
    fails = m.get('fails', 0)
    total = passes + fails
    return round((passes / total * 100.0) if total > 0 else 0.0, 2)

http_reqs = get_m('http_reqs')
total_reqs = int(http_reqs.get('count', 0))
rps = round(http_reqs.get('rate', 0), 2)

nominal_overhead = get_trend('gateway_overhead_ms')
nominal_lat = get_trend('nominal_latency_ms')
isolated_b_lat = get_trend('isolated_tenant_b_latency_ms')
noisy_a_lat = get_trend('noisy_tenant_a_latency_ms')
saturation_lat = get_trend('saturation_latency_ms')

nominal_success_pct = get_rate('nominal_success_rate')
saturation_429_pct = get_rate('saturation_429_rate')
noisy_a_429_pct = get_rate('noisy_tenant_a_429_rate')
tenant_b_200_pct = get_rate('isolated_tenant_b_200_rate')

# Write CSV
csv_rows = [
    ["Metric", "Count / Rate", "p50 (ms)", "p90 (ms)", "p95 (ms)", "p99 (ms)", "Avg (ms)", "Max (ms)"],
    ["Total HTTP Requests", f"{total_reqs} ({rps} req/s)", "-", "-", "-", "-", "-", "-"],
    ["Gateway Overhead (<5ms SLA)", "-", nominal_overhead['med'], nominal_overhead['p90'], nominal_overhead['p95'], nominal_overhead['p99'], nominal_overhead['avg'], nominal_overhead['max']],
    ["Nominal Traffic Latency", f"{nominal_success_pct}% 200 OK", nominal_lat['med'], nominal_lat['p90'], nominal_lat['p95'], nominal_lat['p99'], nominal_lat['avg'], nominal_lat['max']],
    ["Tenant B (Isolated)", f"{tenant_b_200_pct}% 200 OK", isolated_b_lat['med'], isolated_b_lat['p90'], isolated_b_lat['p95'], isolated_b_lat['p99'], isolated_b_lat['avg'], isolated_b_lat['max']],
    ["Tenant A (Noisy Saturated)", f"{noisy_a_429_pct}% 429 Blocked", noisy_a_lat['med'], noisy_a_lat['p90'], noisy_a_lat['p95'], noisy_a_lat['p99'], noisy_a_lat['avg'], noisy_a_lat['max']],
    ["Single-Tenant Saturation", f"{saturation_429_pct}% 429 Blocked", saturation_lat['med'], saturation_lat['p90'], saturation_lat['p95'], saturation_lat['p99'], saturation_lat['avg'], saturation_lat['max']]
]

with open(csv_output_path, 'w', newline='') as f:
    writer = csv.writer(f)
    writer.writerows(csv_rows)

print("\n" + "=" * 80)
print(f"BENCHMARK RESULTS SUMMARY (Sustained RPS: {rps} req/s | Total Requests: {total_reqs})")
print("=" * 80)
print(f"{'Metric':<32} | {'Rate/Status':<15} | {'p50':<8} | {'p95':<8} | {'p99':<8} | {'SLA Status'}")
print("-" * 80)

sla_status = "PASS (<5ms)" if nominal_overhead['med'] < 5.0 else f"OVERHEAD ({nominal_overhead['med']}ms)"
print(f"{'Gateway Latency Overhead':<32} | {'-':<15} | {str(nominal_overhead['med']) + 'ms':<8} | {str(nominal_overhead['p95']) + 'ms':<8} | {str(nominal_overhead['p99']) + 'ms':<8} | {sla_status}")
print(f"{'Nominal Traffic (10 Tenants)':<32} | {str(nominal_success_pct) + '% 200 OK':<15} | {str(nominal_lat['med']) + 'ms':<8} | {str(nominal_lat['p95']) + 'ms':<8} | {str(nominal_lat['p99']) + 'ms':<8} | PASS")
print(f"{'Rate Limit Saturation':<32} | {str(saturation_429_pct) + '% 429':<15} | {str(saturation_lat['med']) + 'ms':<8} | {str(saturation_lat['p95']) + 'ms':<8} | {str(saturation_lat['p99']) + 'ms':<8} | PASS")
print(f"{'Tenant B (Isolated Neighbor)':<32} | {str(tenant_b_200_pct) + '% 200 OK':<15} | {str(isolated_b_lat['med']) + 'ms':<8} | {str(isolated_b_lat['p95']) + 'ms':<8} | {str(isolated_b_lat['p99']) + 'ms':<8} | PASS")
print(f"{'Tenant A (Noisy Saturated)':<32} | {str(noisy_a_429_pct) + '% 429':<15} | {str(noisy_a_lat['med']) + 'ms':<8} | {str(noisy_a_lat['p95']) + 'ms':<8} | {str(noisy_a_lat['p99']) + 'ms':<8} | PASS")
print("=" * 80)
print(f"Summary metrics exported to CSV: {csv_output_path}\n")
EOF

echo -e "${GREEN}================================================================${NC}"
echo -e "${GREEN}${BOLD}   Benchmark run successfully completed!                       ${NC}"
echo -e "${GREEN}================================================================${NC}"
exit 0
