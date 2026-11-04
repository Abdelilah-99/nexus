#!/bin/bash

set -eu

# The rootless-host profile mounts the existing daemon socket. Match its mapped
# group before su initializes Jenkins' supplementary groups; never chmod the socket.
if [ -n "${JENKINS_DOCKER_SOCKET:-}" ]; then
    [ -S "$JENKINS_DOCKER_SOCKET" ] || { echo "Docker socket is missing" >&2; exit 1; }
    socket_gid=$(stat -c '%g' "$JENKINS_DOCKER_SOCKET")
    socket_group=$(getent group "$socket_gid" | cut -d: -f1 || true)
    if [ -z "$socket_group" ]; then
        socket_group=jenkins-docker-host
        groupadd -g "$socket_gid" "$socket_group"
    fi
    usermod -aG "$socket_group" jenkins
fi

export GITHUB_USERNAME="$(cat /run/secrets/github-username 2>/dev/null || true)"
export GITHUB_PAT="$(cat /run/secrets/github-pat 2>/dev/null || true)"
export JENKINS_ADMIN_PASSWORD="$(cat /run/secrets/jenkins-admin-password)"
export BUY01_ENV_B64="$(base64 -w 0 /run/secrets/buy01-env)"
export GATEWAY_P12_B64="$(base64 -w 0 /run/secrets/gateway-keystore.p12)"
export GATEWAY_CRT_B64="$(base64 -w 0 /run/secrets/gateway-keystore.crt)"
export GATEWAY_KEY_B64="$(base64 -w 0 /run/secrets/gateway-keystore.key)"

sonar_ready=true
for component in product user media order gateway discovery frontend; do
    file="/run/secrets/sonar-${component}-token"
    if [ ! -s "$file" ]; then
        echo "Missing $file: run sonar-init and recreate Jenkins to enable analysis" >&2
        sonar_ready=false
        continue
    fi
    value="$(tr -d '\r\n' < "$file")"
    if [ -z "$value" ]; then
        echo "Empty SonarQube credential for $component; run sonar-init" >&2
        sonar_ready=false
        continue
    fi
    variable="SONAR_${component^^}_TOKEN"
    export "$variable=$value"
    key_file="/run/secrets/sonar-${component}-project-key"
    if [ ! -s "$key_file" ]; then
        echo "Missing $key_file: run sonar-init to resolve the exact project key" >&2
        sonar_ready=false
        continue
    fi
    project_key="$(tr -d '\r\n' < "$key_file")"
    if [[ ! "$project_key" =~ ^[a-zA-Z0-9_.:-]+$ ]]; then
        echo "Invalid project key file for $component" >&2
        exit 1
    fi
    export "SONAR_${component^^}_PROJECT_KEY=$project_key"
done

if [ "$sonar_ready" = false ]; then
    # Omit optional credentials entirely; never substitute empty/fake secrets.
    # JCasC retains credentials already stored in Jenkins' persistent home.
    sed '/# BEGIN SONAR CREDENTIALS/,/# END SONAR CREDENTIALS/d; /# BEGIN SONAR PROJECT KEYS/,/# END SONAR PROJECT KEYS/d; /# BEGIN SONAR SERVER/,$d' \
        /casc/jenkins.yaml > /tmp/jenkins-base.yaml
    chmod 600 /tmp/jenkins-base.yaml
    chown jenkins:jenkins /tmp/jenkins-base.yaml
    export CASC_JENKINS_CONFIG=/tmp/jenkins-base.yaml
fi

# Public repositories need no checkout credential. Keep private-repository PATs
# optional and omit an empty credential rather than sending invalid authentication.
if [ -z "$GITHUB_PAT" ]; then
    public_config=$(mktemp /tmp/jenkins-public.XXXXXX.yaml)
    sed "/# BEGIN GITHUB CREDENTIALS/,/# END GITHUB CREDENTIALS/d; /credentials('github-mr-jenk')/d" \
        "${CASC_JENKINS_CONFIG:-/casc/jenkins.yaml}" > "$public_config"
    chmod 600 "$public_config"
    chown jenkins:jenkins "$public_config"
    export CASC_JENKINS_CONFIG="$public_config"
    echo "Using anonymous checkout; configure a GitHub PAT for a private repository." >&2
fi

exec su -s /bin/bash jenkins -c 'exec /usr/local/bin/jenkins.sh "$@"' -- "$@"
