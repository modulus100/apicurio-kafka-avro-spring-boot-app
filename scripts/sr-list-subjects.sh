#!/usr/bin/env bash
set -euo pipefail

# Simple helper to get an OAuth2 token from Keycloak (client-credentials)
# and list subjects from Apicurio's Confluent-compatible API v7.
#
# Config via environment variables (with defaults suitable for this repo):
#   KEYCLOAK_URL          (default: http://localhost:8080)
#   REALM                 (default: demo)
#   SR_OIDC_CLIENT_ID     (default: sr-client)
#   SR_OIDC_CLIENT_SECRET (default: gcYKrqUN9o8SrNlndrcrOs0pceQR4HIz)
#   REGISTRY_URL          (default: http://localhost:8081/apis/ccompat/v7)
#
# Usage:
#   scripts/sr-list-subjects.sh              # fetch token and list all subjects
#   SUBJECT=name scripts/sr-list-subjects.sh # list versions and latest for SUBJECT

KEYCLOAK_URL=${KEYCLOAK_URL:-http://localhost:8080}
REALM=${REALM:-demo}
SR_OIDC_CLIENT_ID=${SR_OIDC_CLIENT_ID:-sr-client}
SR_OIDC_CLIENT_SECRET=${SR_OIDC_CLIENT_SECRET:-gcYKrqUN9o8SrNlndrcrOs0pceQR4HIz}
REGISTRY_URL=${REGISTRY_URL:-http://localhost:8081/apis/ccompat/v7}

TOKEN=$(curl -s -X POST \
  -d grant_type=client_credentials \
  -d client_id="$SR_OIDC_CLIENT_ID" \
  -d client_secret="$SR_OIDC_CLIENT_SECRET" \
  "$KEYCLOAK_URL/realms/$REALM/protocol/openid-connect/token" | jq -r .access_token)

if [[ -z "${TOKEN}" || "${TOKEN}" == "null" ]]; then
  echo "Failed to retrieve access token. Check client id/secret and Keycloak URL." >&2
  exit 1
fi

short_token="${TOKEN:0:20}..."
echo "Got token: ${short_token}"

echo "\nSubjects:"
curl -s -H "Authorization: Bearer $TOKEN" \
  "$REGISTRY_URL/subjects" | jq .

if [[ -n "${SUBJECT:-}" ]]; then
  echo "\nVersions for subject: ${SUBJECT}"
  curl -s -H "Authorization: Bearer $TOKEN" \
    "$REGISTRY_URL/subjects/${SUBJECT}/versions" | jq .

  echo "\nLatest schema for subject: ${SUBJECT}"
  curl -s -H "Authorization: Bearer $TOKEN" \
    "$REGISTRY_URL/subjects/${SUBJECT}/versions/latest" | jq .
fi
