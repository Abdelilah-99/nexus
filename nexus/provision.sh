#!/usr/bin/env bash
# Configure a fresh (or existing) Nexus instance for Buy02 through the REST API.
# Safe to re-run: existing repositories are updated in place instead of re-created.
#
#   NEXUS_ADMIN_PASSWORD='new-strong-password' nexus/provision.sh
#
# First run on a new instance: also pass the generated password so the script can
# replace it, e.g. NEXUS_INITIAL_PASSWORD="$(docker exec nexus cat /nexus-data/admin.password)".
set -euo pipefail

NEXUS_URL="${NEXUS_URL:-http://localhost:8081}"
API="$NEXUS_URL/service/rest/v1"
: "${NEXUS_ADMIN_PASSWORD:?Set NEXUS_ADMIN_PASSWORD to the admin password to use}"

# call METHOD PATH [JSON] -> prints the HTTP status, fails on anything but 2xx
call() {
    local method=$1 path=$2 body=${3:-} status
    status=$(curl -sS -o /tmp/nexus-response.$$ -w '%{http_code}' -u "admin:$NEXUS_ADMIN_PASSWORD" \
        -X "$method" -H 'Content-Type: application/json' ${body:+--data "$body"} "$API$path")
    if [[ $status != 2* ]]; then
        echo "$method $path failed with HTTP $status:" >&2
        cat /tmp/nexus-response.$$ >&2; echo >&2
        rm -f /tmp/nexus-response.$$
        return 1
    fi
    rm -f /tmp/nexus-response.$$
}

exists() { [ "$(curl -s -o /dev/null -w '%{http_code}' -u "admin:$NEXUS_ADMIN_PASSWORD" "$API$1")" = 200 ]; }

# repository FORMAT TYPE NAME JSON -> create, or update when it already exists
repository() {
    local format=$1 type=$2 name=$3 body=$4
    if exists "/repositories/$name"; then
        call PUT "/repositories/$format/$type/$name" "$body"
        echo "updated  $format/$type  $name"
    else
        call POST "/repositories/$format/$type" "$body"
        echo "created  $format/$type  $name"
    fi
}

blob_store() {
    if exists "/blobstores/file/$1"; then
        echo "exists   blob store $1"
    else
        call POST /blobstores/file "{\"name\":\"$1\",\"path\":\"$1\"}"
        echo "created  blob store $1"
    fi
}

# ---------------------------------------------------------------------------
# 1. Admin password: replace the generated one on first run
# ---------------------------------------------------------------------------
if [ -n "${NEXUS_INITIAL_PASSWORD:-}" ] &&
   [ "$(curl -s -o /dev/null -w '%{http_code}' -u "admin:$NEXUS_INITIAL_PASSWORD" "$API/status/check")" = 200 ]; then
    curl -fsS -u "admin:$NEXUS_INITIAL_PASSWORD" -X PUT -H 'Content-Type: text/plain' \
        --data "$NEXUS_ADMIN_PASSWORD" "$API/security/users/admin/change-password"
    echo "changed  admin password"
fi
call GET /status/check > /dev/null || { echo "Cannot log in as admin" >&2; exit 1; }

# ---------------------------------------------------------------------------
# 2. Global security
# ---------------------------------------------------------------------------
# Community Edition requires the EULA to be accepted once; only do it when asked to.
if [ "${NEXUS_ACCEPT_EULA:-false}" = true ]; then
    disclaimer=$(curl -fsS -u "admin:$NEXUS_ADMIN_PASSWORD" "$API/system/eula" | jq -r .disclaimer)
    call POST /system/eula "$(jq -nc --arg d "$disclaimer" '{accepted: true, disclaimer: $d}')"
    echo "accepted Community Edition EULA"
fi

# No anonymous reads: every client (developer, Maven, Docker, CI) must authenticate.
call PUT /security/anonymous '{"enabled":false,"userId":"anonymous","realmName":"NexusAuthorizingRealm"}'
echo "disabled anonymous access"

# The Docker Bearer Token realm is required for "docker login" against Nexus.
call PUT /security/realms/active '["NexusAuthenticatingRealm","DockerToken"]'
echo "enabled  realms: local users + Docker bearer token"

# ---------------------------------------------------------------------------
# 3. Blob stores: keep Maven and Docker binaries apart
# ---------------------------------------------------------------------------
blob_store maven-blobs
blob_store docker-blobs

# ---------------------------------------------------------------------------
# 4. Maven repositories
# ---------------------------------------------------------------------------
# Releases are immutable: a version can be uploaded once and never overwritten.
repository maven hosted buy02-releases '{
  "name": "buy02-releases", "online": true,
  "storage": {"blobStoreName": "maven-blobs", "strictContentTypeValidation": true, "writePolicy": "allow_once"},
  "maven": {"versionPolicy": "RELEASE", "layoutPolicy": "STRICT", "contentDisposition": "ATTACHMENT"}
}'

# Snapshots are mutable: each deploy adds a new timestamped build of the same version.
repository maven hosted buy02-snapshots '{
  "name": "buy02-snapshots", "online": true,
  "storage": {"blobStoreName": "maven-blobs", "strictContentTypeValidation": true, "writePolicy": "allow"},
  "maven": {"versionPolicy": "SNAPSHOT", "layoutPolicy": "STRICT", "contentDisposition": "ATTACHMENT"}
}'

# Caching proxy of Maven Central: downloaded once, then served from Nexus.
repository maven proxy maven-central-proxy '{
  "name": "maven-central-proxy", "online": true,
  "storage": {"blobStoreName": "maven-blobs", "strictContentTypeValidation": true},
  "proxy": {"remoteUrl": "https://repo1.maven.org/maven2/", "contentMaxAge": -1, "metadataMaxAge": 1440},
  "negativeCache": {"enabled": true, "timeToLive": 1440},
  "httpClient": {"blocked": false, "autoBlock": true},
  "maven": {"versionPolicy": "RELEASE", "layoutPolicy": "PERMISSIVE", "contentDisposition": "ATTACHMENT"}
}'

# The single URL builds resolve from: our releases and snapshots first, then Central.
repository maven group buy02-maven-group '{
  "name": "buy02-maven-group", "online": true,
  "storage": {"blobStoreName": "maven-blobs", "strictContentTypeValidation": true},
  "group": {"memberNames": ["buy02-releases", "buy02-snapshots", "maven-central-proxy"]}
}'

# ---------------------------------------------------------------------------
# 5. Docker repositories
# ---------------------------------------------------------------------------
# Push target for the Buy02 images, served on its own connector port.
repository docker hosted buy02-docker-hosted '{
  "name": "buy02-docker-hosted", "online": true,
  "storage": {"blobStoreName": "docker-blobs", "strictContentTypeValidation": true, "writePolicy": "allow"},
  "docker": {"v1Enabled": false, "forceBasicAuth": true, "httpPort": 8082}
}'

# Caching proxy of Docker Hub for base images (eclipse-temurin, nginx, mongo...).
repository docker proxy dockerhub-proxy '{
  "name": "dockerhub-proxy", "online": true,
  "storage": {"blobStoreName": "docker-blobs", "strictContentTypeValidation": true},
  "proxy": {"remoteUrl": "https://registry-1.docker.io", "contentMaxAge": 1440, "metadataMaxAge": 1440},
  "negativeCache": {"enabled": true, "timeToLive": 1440},
  "httpClient": {"blocked": false, "autoBlock": true},
  "docker": {"v1Enabled": false, "forceBasicAuth": true},
  "dockerProxy": {"indexType": "HUB"}
}'

# Pull endpoint: our images plus everything from Docker Hub behind one address.
repository docker group buy02-docker-group '{
  "name": "buy02-docker-group", "online": true,
  "storage": {"blobStoreName": "docker-blobs", "strictContentTypeValidation": true},
  "group": {"memberNames": ["buy02-docker-hosted", "dockerhub-proxy"]},
  "docker": {"v1Enabled": false, "forceBasicAuth": true, "httpPort": 8083}
}'

echo "Nexus is provisioned: $NEXUS_URL"
