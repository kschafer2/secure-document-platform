#!/usr/bin/env bash

# Generates an RS256 JWT for local development using the repository's
# development-only private key.
#
# Usage:
#   ./scripts/generate-dev-jwt.sh <subject> [lifetime-seconds]
#
# Default lifetime: 3600 seconds

base64url() {
    openssl base64 -A \
        | tr '+/' '-_' \
        | tr -d '='
}

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd)"
PRIVATE_KEY="$REPO_ROOT/src/main/resources/dev/auth/dev-private-key.pem"

SUBJECT="${1:-}"
LIFETIME="${2:-3600}"

if [[ -z "$SUBJECT" ]]; then
    echo "Usage: $0 <subject> [lifetime-seconds]" >&2
    exit 1
fi

NOW="$(date +%s)"
EXPIRES_AT="$((NOW + LIFETIME))"

HEADER="$(python3 -c '
import json
print(json.dumps({
    "alg": "RS256",
    "typ": "JWT"
}, separators=(",", ":")))
')"

PAYLOAD="$(python3 - "$SUBJECT" "$NOW" "$EXPIRES_AT" <<'PY'
import json
import sys

subject = sys.argv[1]
issued_at = int(sys.argv[2])
expires_at = int(sys.argv[3])

print(json.dumps({
    "sub": subject,
    "aud": ["secure-document-platform"],
    "iat": issued_at,
    "exp": expires_at
}, separators=(",", ":")))
PY
)"

ENCODED_HEADER="$(printf '%s' "$HEADER" | base64url)"
ENCODED_PAYLOAD="$(printf '%s' "$PAYLOAD" | base64url)"

SIGNING_INPUT="$ENCODED_HEADER.$ENCODED_PAYLOAD"

SIGNATURE="$(
    printf '%s' "$SIGNING_INPUT" \
        | openssl dgst -sha256 -sign "$PRIVATE_KEY" \
        | base64url
)"

TOKEN="$SIGNING_INPUT.$SIGNATURE"

echo "$TOKEN"