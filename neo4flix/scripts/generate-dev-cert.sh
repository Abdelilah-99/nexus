#!/usr/bin/env sh
# Creates a self-signed certificate for local HTTPS (https://localhost).
# Browsers will warn because no public authority signed it; in production use Let's Encrypt (see README).
set -eu
DIR="$(cd "$(dirname "$0")/.." && pwd)/certs"
mkdir -p "$DIR"
if [ -f "$DIR/fullchain.pem" ] && [ "${1:-}" != "--force" ]; then
  echo "Certificate already exists in $DIR (use --force to replace it)"
  exit 0
fi
openssl req -x509 -nodes -newkey rsa:2048 -days 365 \
  -keyout "$DIR/privkey.pem" -out "$DIR/fullchain.pem" \
  -subj "/CN=localhost/O=Neo4flix dev" \
  -addext "subjectAltName=DNS:localhost,IP:127.0.0.1"
chmod 600 "$DIR/privkey.pem"
echo "Created $DIR/fullchain.pem and $DIR/privkey.pem"
