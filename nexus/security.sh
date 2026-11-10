#!/usr/bin/env bash
# Role-based access control for the Buy02 repositories. Run after provision.sh.
# Safe to re-run: roles, users and selectors are updated in place.
#
#   NEXUS_ADMIN_PASSWORD=... NEXUS_CI_PASSWORD=... NEXUS_DEV_PASSWORD=... \
#   NEXUS_RM_PASSWORD=... NEXUS_ORDER_PASSWORD=... nexus/security.sh
set -euo pipefail

NEXUS_URL="${NEXUS_URL:-http://localhost:8081}"
API="$NEXUS_URL/service/rest/v1"
: "${NEXUS_ADMIN_PASSWORD:?Set NEXUS_ADMIN_PASSWORD}"
: "${NEXUS_CI_PASSWORD:?Set NEXUS_CI_PASSWORD for the ci-jenkins user}"
: "${NEXUS_DEV_PASSWORD:?Set NEXUS_DEV_PASSWORD for the developer user}"
: "${NEXUS_RM_PASSWORD:?Set NEXUS_RM_PASSWORD for the release-manager user}"
: "${NEXUS_ORDER_PASSWORD:?Set NEXUS_ORDER_PASSWORD for the order-team user}"

call() {
    local method=$1 path=$2 body=${3:-} type=${4:-application/json} status
    status=$(curl -sS -o /tmp/nexus-response.$$ -w '%{http_code}' -u "admin:$NEXUS_ADMIN_PASSWORD" \
        -X "$method" -H "Content-Type: $type" ${body:+--data "$body"} "$API$path")
    if [[ $status != 2* ]]; then
        echo "$method $path failed with HTTP $status:" >&2
        cat /tmp/nexus-response.$$ >&2; echo >&2
        rm -f /tmp/nexus-response.$$
        return 1
    fi
    rm -f /tmp/nexus-response.$$
}

exists() { [ "$(curl -s -o /dev/null -w '%{http_code}' -u "admin:$NEXUS_ADMIN_PASSWORD" "$API$1")" = 200 ]; }

# Built-in privilege granting ACTIONS on one repository, e.g. view maven2 buy02-releases read
view() { echo "nx-repository-view-$1-$2-$3"; }

# role ID DESCRIPTION PRIVILEGE...
role() {
    local id=$1 description=$2; shift 2
    local body
    body=$(jq -nc --arg id "$id" --arg d "$description" '{id: $id, name: $id, description: $d,
        privileges: $ARGS.positional, roles: []}' --args "$@")
    if exists "/security/roles/$id"; then
        call PUT "/security/roles/$id" "$body"; echo "updated  role $id"
    else
        call POST /security/roles "$body"; echo "created  role $id"
    fi
}

# user ID PASSWORD ROLE...
user() {
    local id=$1 password=$2; shift 2
    local body
    body=$(jq -nc --arg id "$id" '{userId: $id, firstName: $id, lastName: "Buy02",
        emailAddress: ($id + "@buy02.local"), status: "active", source: "default",
        roles: $ARGS.positional}' --args "$@")
    # The users endpoint searches by prefix, so match the exact id
    if curl -fsS -u "admin:$NEXUS_ADMIN_PASSWORD" "$API/security/users?userId=$id&source=default" |
       jq -e --arg id "$id" 'any(.[]; .userId == $id)' > /dev/null; then
        call PUT "/security/users/$id" "$body"
        call PUT "/security/users/$id/change-password" "$password" text/plain
        echo "updated  user $id"
    else
        call POST /security/users "$(jq -c --arg p "$password" '. + {password: $p}' <<< "$body")"
        echo "created  user $id"
    fi
}

# ---------------------------------------------------------------------------
# 1. Repository-level roles
# ---------------------------------------------------------------------------
# Developers consume artifacts and images but can never publish.
role buy02-developer "Read Maven artifacts and pull Docker images through the groups" \
    "$(view maven2 buy02-maven-group read)" "$(view maven2 buy02-maven-group browse)" \
    "$(view docker buy02-docker-group read)" "$(view docker buy02-docker-group browse)"

# CI publishes snapshots, releases and images, but cannot delete anything.
role buy02-ci-deployer "Publish Maven artifacts and push Docker images (no delete)" \
    "$(view maven2 buy02-maven-group read)" "$(view maven2 buy02-maven-group browse)" \
    "$(view maven2 buy02-snapshots add)" "$(view maven2 buy02-snapshots edit)" \
    "$(view maven2 buy02-snapshots read)" "$(view maven2 buy02-snapshots browse)" \
    "$(view maven2 buy02-releases add)" "$(view maven2 buy02-releases edit)" \
    "$(view maven2 buy02-releases read)" "$(view maven2 buy02-releases browse)" \
    "$(view docker buy02-docker-hosted add)" "$(view docker buy02-docker-hosted edit)" \
    "$(view docker buy02-docker-hosted read)" "$(view docker buy02-docker-hosted browse)" \
    "$(view docker buy02-docker-group read)" "$(view docker buy02-docker-group browse)"

# Release managers can also delete versions (cleanup, withdrawing a bad release).
role buy02-release-manager "Everything the CI can do, plus deleting artifacts and images" \
    "$(view maven2 buy02-releases '*')" "$(view maven2 buy02-snapshots '*')" \
    "$(view maven2 buy02-maven-group read)" "$(view maven2 buy02-maven-group browse)" \
    "$(view docker buy02-docker-hosted '*')" \
    "$(view docker buy02-docker-group read)" "$(view docker buy02-docker-group browse)"

# ---------------------------------------------------------------------------
# 2. Artifact-level restriction with a content selector
# ---------------------------------------------------------------------------
# The order team may only read order-service artifacts (plus the shared parent pom),
# nothing else from the Buy02 repositories.
selector=buy02-order-service-only
expression='format == "maven2" and (path =^ "/com/buy02/order-service/" or path =^ "/com/buy02/buy-02/")'
selector_body=$(jq -nc --arg n "$selector" --arg e "$expression" \
    '{name: $n, description: "order-service artifacts and the parent pom", expression: $e}')
if exists "/security/content-selectors/$selector"; then
    call PUT "/security/content-selectors/$selector" "$(jq -c 'del(.name)' <<< "$selector_body")"
    echo "updated  content selector $selector"
else
    call POST /security/content-selectors "$selector_body"
    echo "created  content selector $selector"
fi

privilege=buy02-order-service-read
privilege_body=$(jq -nc --arg n "$privilege" --arg s "$selector" '{name: $n,
    description: "Read order-service artifacts in the Buy02 Maven group", actions: ["READ", "BROWSE"],
    format: "maven2", repository: "buy02-maven-group", contentSelector: $s}')
if exists "/security/privileges/$privilege"; then
    call PUT "/security/privileges/repository-content-selector/$privilege" "$privilege_body"
    echo "updated  privilege $privilege"
else
    call POST /security/privileges/repository-content-selector "$privilege_body"
    echo "created  privilege $privilege"
fi

role buy02-order-team "Read only order-service artifacts" "$privilege"

# ---------------------------------------------------------------------------
# 3. Users (one per role); none of them is an administrator
# ---------------------------------------------------------------------------
user ci-jenkins      "$NEXUS_CI_PASSWORD"    buy02-ci-deployer
user developer       "$NEXUS_DEV_PASSWORD"   buy02-developer
user release-manager "$NEXUS_RM_PASSWORD"    buy02-release-manager
user order-team      "$NEXUS_ORDER_PASSWORD" buy02-order-team

echo "Buy02 roles and users are in place."
