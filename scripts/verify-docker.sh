#!/usr/bin/env bash
set -euo pipefail

# Color codes
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"
UPSTREAM_URL="${UPSTREAM_URL:-http://localhost:8081}"
TENANT_ID="${TENANT_ID:-test-tenant}"
MAX_WAIT_SECONDS=30

RESPONSE_FILE=""
cleanup() {
  if [ -n "${RESPONSE_FILE:-}" ] && [ -f "${RESPONSE_FILE:-}" ]; then
    rm -f "$RESPONSE_FILE"
  fi
}
trap cleanup EXIT

echo -e "${BLUE}======================================================${NC}"
echo -e "${BLUE}  Tenant-Aware Rate Limiter - Docker Verification     ${NC}"
echo -e "${BLUE}======================================================${NC}"

# Step 0: Check mock upstream health if reachable
echo -e "\n${YELLOW}[1/5] Checking mock upstream on ${UPSTREAM_URL}...${NC}"
UPSTREAM_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "${UPSTREAM_URL}" || echo "000")
if [ "$UPSTREAM_STATUS" = "200" ]; then
  echo -e "${GREEN}✔ Mock upstream reachable on ${UPSTREAM_URL} (HTTP 200)${NC}"
else
  echo -e "${YELLOW}⚠ Mock upstream port ${UPSTREAM_URL} returned status ${UPSTREAM_STATUS}${NC}"
fi

# Step 1: Wait for Gateway to become ready
echo -e "\n${YELLOW}[2/5] Waiting for Gateway health check on ${GATEWAY_URL}/health...${NC}"
READY=false
for i in $(seq 1 "$MAX_WAIT_SECONDS"); do
  STATUS=$(curl -s -o /dev/null -w "%{http_code}" "${GATEWAY_URL}/health" || echo "000")
  if [ "$STATUS" = "200" ]; then
    READY=true
    break
  fi
  sleep 1
done

if [ "$READY" = "false" ]; then
  echo -e "${RED}✖ Gateway failed to become ready within ${MAX_WAIT_SECONDS}s (HTTP ${STATUS})${NC}"
  exit 1
fi

HEALTH_BODY=$(curl -s "${GATEWAY_URL}/health")
if [ "$HEALTH_BODY" = "OK" ]; then
  echo -e "${GREEN}✔ Health check passed: GET /health -> HTTP 200 (Body: ${HEALTH_BODY})${NC}"
else
  echo -e "${RED}✖ Health check unexpected body: '${HEALTH_BODY}'${NC}"
  exit 1
fi

# Step 2: Send valid tenant request (200 OK)
echo -e "\n${YELLOW}[3/5] Testing valid request with tenant '${TENANT_ID}'...${NC}"
PAYLOAD='{"model": "gpt-4", "messages": [{"role": "user", "content": "Hello rate limiter"}], "max_tokens": 15}'

RESPONSE_FILE=$(mktemp)
HTTP_CODE=$(curl -s -o "$RESPONSE_FILE" -w "%{http_code}" \
  -X POST "${GATEWAY_URL}/v1/chat/completions" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: ${TENANT_ID}" \
  -d "$PAYLOAD")

RESPONSE_BODY=$(cat "$RESPONSE_FILE")
rm -f "$RESPONSE_FILE"
RESPONSE_FILE=""

if [ "$HTTP_CODE" = "200" ]; then
  echo -e "${GREEN}✔ Valid request succeeded: HTTP 200 (Body: ${RESPONSE_BODY})${NC}"
else
  echo -e "${RED}✖ Valid request failed: expected HTTP 200, got HTTP ${HTTP_CODE} (Body: ${RESPONSE_BODY})${NC}"
  exit 1
fi

# Step 3: Verify Redis key token deduction
echo -e "\n${YELLOW}[4/5] Checking Redis token deduction for key rate_limit:${TENANT_ID}...${NC}"
REDIS_TOKENS=""
if docker compose exec -T redis redis-cli ping > /dev/null 2>&1; then
  REDIS_TOKENS=$(docker compose exec -T redis redis-cli --raw hget "rate_limit:${TENANT_ID}" tokens 2>/dev/null | tr -d '\r\n')
elif command -v redis-cli > /dev/null 2>&1; then
  REDIS_TOKENS=$(redis-cli -h localhost -p 6379 --raw hget "rate_limit:${TENANT_ID}" tokens 2>/dev/null | tr -d '\r\n')
fi

if [[ "$REDIS_TOKENS" =~ ^[0-9]+$ ]] && [ "$REDIS_TOKENS" -le 85 ]; then
  echo -e "${GREEN}✔ Redis token balance verified: rate_limit:${TENANT_ID} has ${REDIS_TOKENS} tokens remaining (decremented from 100)${NC}"
else
  echo -e "${RED}✖ Redis token balance check failed: expected numeric value <= 85, got '${REDIS_TOKENS}'${NC}"
  exit 1
fi

# Step 4: Trigger rate limit saturation (HTTP 429)
echo -e "\n${YELLOW}[5/5] Testing rate limit budget exhaustion (HTTP 429)...${NC}"
SATURATION_PAYLOAD='{"model": "gpt-4", "messages": [{"role": "user", "content": "Large token request"}], "max_tokens": 120}'

RESPONSE_FILE=$(mktemp)
SAT_CODE=$(curl -s -o "$RESPONSE_FILE" -w "%{http_code}" \
  -X POST "${GATEWAY_URL}/v1/chat/completions" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: ${TENANT_ID}" \
  -d "$SATURATION_PAYLOAD")

SAT_BODY=$(cat "$RESPONSE_FILE")
rm -f "$RESPONSE_FILE"
RESPONSE_FILE=""

if [ "$SAT_CODE" = "429" ]; then
  echo -e "${GREEN}✔ Rate limit enforcement verified: HTTP 429 Too Many Requests (Body: ${SAT_BODY})${NC}"
else
  # Fallback: rapid burst to exhaust remaining capacity
  echo -e "${YELLOW}Single large request returned HTTP ${SAT_CODE}, firing burst to exhaust remaining budget...${NC}"
  BURST_429_SEEN=false
  for i in $(seq 1 15); do
    BURST_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
      -X POST "${GATEWAY_URL}/v1/chat/completions" \
      -H "Content-Type: application/json" \
      -H "X-Tenant-Id: ${TENANT_ID}" \
      -d "$PAYLOAD")
    if [ "$BURST_CODE" = "429" ]; then
      BURST_429_SEEN=true
      break
    fi
  done

  if [ "$BURST_429_SEEN" = "true" ]; then
    echo -e "${GREEN}✔ Rate limit enforcement verified via burst: HTTP 429 received!${NC}"
  else
    echo -e "${RED}✖ Failed to trigger HTTP 429 rate limit (last HTTP code: ${BURST_CODE})${NC}"
    exit 1
  fi
fi

echo -e "\n${GREEN}======================================================${NC}"
echo -e "${GREEN}  All Docker verification tests passed successfully!   ${NC}"
echo -e "${GREEN}======================================================${NC}"
exit 0
