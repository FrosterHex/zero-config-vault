#!/usr/bin/env bash
# ==============================================================================
# Zero-Config Vault - Automated End-to-End Verification Test Suite (Bash)
# ==============================================================================

set -u

BASE_URL="${BASE_URL:-http://localhost:8080}"
API_KEY="${API_KEY:-sk-dev-key-001}"
ADMIN_KEY="${ADMIN_KEY:-sk-admin-master-key}"

TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
CYAN='\033[0;36m'
WHITE='\033[1;37m'
GRAY='\033[0;90m'
NC='\033[0m'

header() {
  echo -e "\n${CYAN}=================================================================${NC}"
  echo -e "  $1"
  echo -e "${CYAN}=================================================================${NC}"
}

report_result() {
  local test_name="$1"
  local status="$2"
  local details="$3"

  TOTAL_TESTS=$((TOTAL_TESTS + 1))
  if [ "$status" -eq 0 ]; then
    PASSED_TESTS=$((PASSED_TESTS + 1))
    echo -e "  ${GREEN}[PASS]${NC} ${WHITE}${test_name}${NC}"
    [ -n "$details" ] && echo -e "         ${GRAY}${details}${NC}"
  else
    FAILED_TESTS=$((FAILED_TESTS + 1))
    echo -e "  ${RED}[FAIL]${NC} ${WHITE}${test_name}${NC}"
    [ -n "$details" ] && echo -e "         ${RED}ERROR: ${details}${NC}"
  fi
}

header "Zero-Config Vault: Automated Verification Suite (Bash)"
echo -e "${GRAY}Target Server : ${BASE_URL}${NC}"
echo -e "${GRAY}API Key       : ${API_KEY:0:10}...${NC}"
echo -e "${GRAY}Started At    : $(date)${NC}"

# 1. Test OpenAPI Docs
DOCS_CODE=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/v3/api-docs" 2>/dev/null || echo "000")
if [ "$DOCS_CODE" -eq 200 ]; then
  report_result "Test 1: OpenAPI Documentation Endpoint (/v3/api-docs)" 0 "HTTP 200 OK"
else
  report_result "Test 1: OpenAPI Documentation Endpoint (/v3/api-docs)" 1 "HTTP ${DOCS_CODE}"
fi

# 2. Test Security Filter Rejection (HTTP 401)
UNAUTH_CODE=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/api/v1/vault/stats" 2>/dev/null || echo "000")
if [ "$UNAUTH_CODE" -eq 401 ]; then
  report_result "Test 2: Security Filter Rejection on Missing Key" 0 "HTTP 401 Unauthorized"
else
  report_result "Test 2: Security Filter Rejection on Missing Key" 1 "Expected 401, got ${UNAUTH_CODE}"
fi

# Unique application name
APP_ID="bash-test-$RANDOM"
ENV_ID="staging"
SECRET_VAL="super-secret-bash-test-key-999"

# 3. Create Revision v1
CREATE_RESP=$(curl -s -X POST "${BASE_URL}/api/v1/vault/config" \
  -H "X-API-Key: ${API_KEY}" \
  -H "Content-Type: application/json" \
  -d '{
    "applicationName": "'"${APP_ID}"'",
    "environment": "'"${ENV_ID}"'",
    "payload": {
      "server": { "port": 8080, "host": "bash.node.internal" },
      "database": { "host": "mongo-primary", "port": 27017 },
      "secure_settings": { "api_secret": "'"${SECRET_VAL}"'" }
    },
    "tags": { "tier": "test" }
  }')

V1_VER=$(echo "$CREATE_RESP" | grep -o '"version":[0-9]*' | cut -d':' -f2 || echo "0")
V1_HASH=$(echo "$CREATE_RESP" | grep -o '"documentHash":"[^"]*"' | cut -d'"' -f4 || echo "")

if [ "$V1_VER" -eq 1 ] && [ ${#V1_HASH} -eq 128 ]; then
  report_result "Test 3: Create Revision v1 with SHA-512 Hash" 0 "v1 created, Hash: ${V1_HASH:0:16}... (Length: ${#V1_HASH})"
else
  report_result "Test 3: Create Revision v1 with SHA-512 Hash" 1 "Response: ${CREATE_RESP}"
fi

# 4. Retrieve Active Config & Transparent Decryption
GET_RESP=$(curl -s -X GET "${BASE_URL}/api/v1/vault/config/${APP_ID}/${ENV_ID}" \
  -H "X-API-Key: ${API_KEY}")

if echo "$GET_RESP" | grep -q "${SECRET_VAL}"; then
  report_result "Test 4: Retrieve Configuration & Transparent Decryption" 0 "secure_settings decrypted successfully"
else
  report_result "Test 4: Retrieve Configuration & Transparent Decryption" 1 "Decrypted secret not matched"
fi

# 5. Dot-Notation Path Traversal
PATH_VAL=$(curl -s -X GET "${BASE_URL}/api/v1/vault/config/${APP_ID}/${ENV_ID}/search?path=server.host" \
  -H "X-API-Key: ${API_KEY}")

if echo "$PATH_VAL" | grep -q "bash.node.internal"; then
  report_result "Test 5: Dot-Notation Path Traversal ('server.host')" 0 "Extracted: ${PATH_VAL}"
else
  report_result "Test 5: Dot-Notation Path Traversal ('server.host')" 1 "Value mismatch: ${PATH_VAL}"
fi

# 6. Multi-Format Exporter (YAML & .env)
YAML_RESP=$(curl -s -X GET "${BASE_URL}/api/v1/vault/config/${APP_ID}/${ENV_ID}/export?format=yaml" -H "X-API-Key: ${API_KEY}")
ENV_RESP=$(curl -s -X GET "${BASE_URL}/api/v1/vault/config/${APP_ID}/${ENV_ID}/export?format=env" -H "X-API-Key: ${API_KEY}")

if echo "$YAML_RESP" | grep -q "server:" && (echo "$ENV_RESP" | grep -q "server.port=" || echo "$ENV_RESP" | grep -q "SERVER_PORT="); then
  report_result "Test 6: Multi-Format Exporter (YAML & .env)" 0 "YAML & ENV content validated"
else
  report_result "Test 6: Multi-Format Exporter (YAML & .env)" 1 "Export response invalid"
fi

# 7. Temporal Version Increment (v2)
UPDATE_RESP=$(curl -s -X POST "${BASE_URL}/api/v1/vault/config" \
  -H "X-API-Key: ${API_KEY}" \
  -H "Content-Type: application/json" \
  -d '{
    "applicationName": "'"${APP_ID}"'",
    "environment": "'"${ENV_ID}"'",
    "payload": {
      "server": { "port": 9090, "host": "bash.node.internal" },
      "database": { "host": "mongo-primary", "port": 27017 },
      "secure_settings": { "api_secret": "'"${SECRET_VAL}"'" },
      "new_flag": true
    }
  }')

V2_VER=$(echo "$UPDATE_RESP" | grep -o '"version":[0-9]*' | cut -d':' -f2 || echo "0")
V2_HASH=$(echo "$UPDATE_RESP" | grep -o '"documentHash":"[^"]*"' | cut -d'"' -f4 || echo "")

if [ "$V2_VER" -eq 2 ] && [ "$V2_HASH" != "$V1_HASH" ]; then
  report_result "Test 7: GitOps Temporal Immutability Increment (v2)" 0 "v2 created, distinct hash"
else
  report_result "Test 7: GitOps Temporal Immutability Increment (v2)" 1 "Expected v2, got: ${UPDATE_RESP}"
fi

# 8. Structural Diff Engine
DIFF_RESP=$(curl -s -X GET "${BASE_URL}/api/v1/vault/config/${APP_ID}/${ENV_ID}/diff?v1=1&v2=2" \
  -H "X-API-Key: ${API_KEY}")

if echo "$DIFF_RESP" | grep -q "modified" || echo "$DIFF_RESP" | grep -q "added"; then
  report_result "Test 8: Structural Diff Engine (/diff?v1=1&v2=2)" 0 "Structural differences verified"
else
  report_result "Test 8: Structural Diff Engine (/diff?v1=1&v2=2)" 1 "Diff response invalid: ${DIFF_RESP}"
fi

# 9. Atomic Rollback to v1 (v3 created)
ROLLBACK_RESP=$(curl -s -X POST "${BASE_URL}/api/v1/vault/config/${APP_ID}/${ENV_ID}/rollback/1" \
  -H "X-API-Key: ${API_KEY}")

V3_VER=$(echo "$ROLLBACK_RESP" | grep -o '"version":[0-9]*' | cut -d':' -f2 || echo "0")
if [ "$V3_VER" -eq 3 ] && echo "$ROLLBACK_RESP" | grep -q "8080"; then
  report_result "Test 9: Atomic Rollback Engine (Target v1 -> Creates v3)" 0 "Version 3 created, port 8080 restored"
else
  report_result "Test 9: Atomic Rollback Engine (Target v1 -> Creates v3)" 1 "Rollback failed: ${ROLLBACK_RESP}"
fi

# 10. Aggregation Pipeline Telemetry (/stats)
STATS_RESP=$(curl -s -X GET "${BASE_URL}/api/v1/vault/stats" -H "X-API-Key: ${API_KEY}")
if echo "$STATS_RESP" | grep -q "totalApplications"; then
  report_result "Test 10: Aggregation Pipeline System Telemetry (/stats)" 0 "Stats telemetry active"
else
  report_result "Test 10: Aggregation Pipeline System Telemetry (/stats)" 1 "Stats failed: ${STATS_RESP}"
fi

# Summary
header "Verification Summary"
echo -e "  ${WHITE}Total Tests Executed : ${TOTAL_TESTS}${NC}"
echo -e "  ${GREEN}Passed Tests         : ${PASSED_TESTS}${NC}"
if [ "$FAILED_TESTS" -gt 0 ]; then
  echo -e "  ${RED}Failed Tests         : ${FAILED_TESTS}${NC}"
  exit 1
else
  echo -e "  ${GREEN}All verification checks passed with 100% success!${NC}"
  exit 0
fi
