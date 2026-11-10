#!/usr/bin/env bash
# Build the Buy02 images and push them to the Nexus Docker hosted repository.
# Base images are pulled through the Nexus Docker group (Docker Hub proxy).
#
#   nexus/docker-publish.sh                      # every service
#   nexus/docker-publish.sh order-service gateway
#
# Backend jars must be built first (mvn package); the frontend needs dist/ (npm run build).
# Needs NEXUS_USERNAME and NEXUS_PASSWORD.
set -euo pipefail

cd "$(dirname "$0")/.."

PUSH_REGISTRY="${NEXUS_DOCKER_PUSH:-localhost:8082}"   # buy02-docker-hosted
PULL_REGISTRY="${NEXUS_DOCKER_PULL:-localhost:8083}"   # buy02-docker-group
: "${NEXUS_USERNAME:?Set NEXUS_USERNAME}" "${NEXUS_PASSWORD:?Set NEXUS_PASSWORD}"

# Image tags follow the Maven version, plus the commit for exact traceability
VERSION="${VERSION:-$(mvn -q -N help:evaluate -Dexpression=project.version -DforceStdout)}"
COMMIT="$(git rev-parse --short HEAD)"

services=("$@")
[ ${#services[@]} -gt 0 ] || services=(discovery gateway user-service product-service media-service order-service frontend)

for registry in "$PUSH_REGISTRY" "$PULL_REGISTRY"; do
    echo "$NEXUS_PASSWORD" | docker login "$registry" -u "$NEXUS_USERNAME" --password-stdin > /dev/null
done

for service in "${services[@]}"; do
    image="$PUSH_REGISTRY/buy02/$service"
    tags=(-t "$image:$VERSION" -t "$image:$COMMIT")
    # Only releases move "latest", so it always points at a stable version
    [[ $VERSION == *-SNAPSHOT ]] || tags+=(-t "$image:latest")

    docker build --pull --build-arg BASE_REGISTRY="$PULL_REGISTRY" \
        --label org.opencontainers.image.version="$VERSION" \
        --label org.opencontainers.image.revision="$COMMIT" \
        "${tags[@]}" "$service"
    docker push --all-tags "$image"
    echo "pushed $image:$VERSION ($COMMIT)"
done
