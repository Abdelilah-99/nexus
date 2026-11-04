#!/usr/bin/env bash
# Use the existing Docker daemon on hosts where privileged DinD cannot start.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
for tool in docker ip python3; do
    command -v "$tool" >/dev/null || { echo "Missing command: $tool" >&2; exit 1; }
done
for secret in jenkins-admin-password buy01-env gateway-keystore.p12 gateway-keystore.crt gateway-keystore.key; do
    test -s "jenkins-lab/jenkins-secrets/$secret" || { echo "Missing Jenkins secret: $secret" >&2; exit 1; }
done
socket_endpoint=${DOCKER_HOST:-$(docker context inspect --format '{{.Endpoints.docker.Host}}')}
case "$socket_endpoint" in
    unix://*) socket_path=${socket_endpoint#unix://} ;;
    *) echo 'This launcher requires a local Unix Docker socket.' >&2; exit 1 ;;
esac
test -S "$socket_path" || { echo 'The selected Docker socket does not exist.' >&2; exit 1; }
# Rootless published ports need a routable host address, rather than host-gateway.
test_host=${JENKINS_TEST_HOST:-$(ip -4 route get 1.1.1.1 | awk '{for(i=1;i<=NF;i++) if($i=="src") {print $(i+1); exit}}')}
python3 - "$test_host" <<'PY'
import ipaddress, sys
ipaddress.IPv4Address(sys.argv[1])
PY
mkdir -p .local
umask 077
printf 'JENKINS_DOCKER_SOCKET_HOST=%s\nJENKINS_TEST_HOST=%s\n' "$socket_path" "$test_host" > .local/jenkins-host.env
docker volume inspect jenkins-lab_jenkins_home >/dev/null 2>&1 || docker volume create jenkins-lab_jenkins_home >/dev/null
docker network inspect jenkins-lab_jenkins-network >/dev/null 2>&1 || docker network create jenkins-lab_jenkins-network >/dev/null
compose=(docker compose --env-file .local/jenkins-host.env -p jenkins-lab -f jenkins-lab/compose.host.yml)
"${compose[@]}" config --quiet
"${compose[@]}" up --no-deps -d --build --wait --wait-timeout 600 jenkins
echo 'Jenkins is ready at http://localhost:8085. Staging uses https://localhost:4300 after a successful main deployment.'
