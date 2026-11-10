#!/usr/bin/env bash
# Install Nexus Repository Manager directly on a Linux host (no Docker) and run it
# as a systemd service under a dedicated, unprivileged "nexus" account.
#
#   sudo nexus/install-native.sh            # installs NEXUS_VERSION below
#   sudo NEXUS_VERSION=3.98.1-01 nexus/install-native.sh
#
# Root is only needed to create the account, the directories and the unit file;
# the Nexus process itself always runs as "nexus".
set -euo pipefail

NEXUS_VERSION="${NEXUS_VERSION:-3.98.1-01}"
NEXUS_ARCHIVE="nexus-${NEXUS_VERSION}-linux-x86_64.tar.gz"
NEXUS_URL="${NEXUS_URL:-https://download.sonatype.com/nexus/3/${NEXUS_ARCHIVE}}"
INSTALL_DIR=/opt/nexus
DATA_DIR=/opt/sonatype-work/nexus3

if [ "$(id -u)" -ne 0 ]; then
    echo "Run with sudo: the installer creates a system account and a systemd unit." >&2
    exit 1
fi

# 1. Dedicated system account: no login shell, no password, home in the data dir.
if ! id nexus > /dev/null 2>&1; then
    useradd --system --user-group --home-dir "$DATA_DIR" --shell /usr/sbin/nologin nexus
fi

# 2. Download and unpack the release (it bundles a supported JDK).
workdir=$(mktemp -d)
trap 'rm -rf "$workdir"' EXIT
curl -fL --retry 3 -o "$workdir/$NEXUS_ARCHIVE" "$NEXUS_URL"
curl -fL --retry 3 -o "$workdir/$NEXUS_ARCHIVE.sha256" "$NEXUS_URL.sha256"
(cd "$workdir" && echo "$(cut -d' ' -f1 < "$NEXUS_ARCHIVE.sha256")  $NEXUS_ARCHIVE" | sha256sum -c -)
tar -xzf "$workdir/$NEXUS_ARCHIVE" -C "$workdir"

mkdir -p /opt "$DATA_DIR"
rm -rf "/opt/nexus-${NEXUS_VERSION}"
mv "$workdir/nexus-${NEXUS_VERSION}" "/opt/nexus-${NEXUS_VERSION}"
ln -sfn "/opt/nexus-${NEXUS_VERSION}" "$INSTALL_DIR"

# 3. Program files stay owned by root (read-only for nexus); only the data dir is writable.
chown -R root:root "/opt/nexus-${NEXUS_VERSION}"
chown -R nexus:nexus "$DATA_DIR"
chmod 750 "$DATA_DIR"
echo 'run_as_user="nexus"' > "$INSTALL_DIR/bin/nexus.rc"

# 4. systemd unit: the service runs as nexus with a hardened sandbox.
cat > /etc/systemd/system/nexus.service <<UNIT
[Unit]
Description=Nexus Repository Manager
After=network-online.target
Wants=network-online.target

[Service]
Type=forking
User=nexus
Group=nexus
LimitNOFILE=65536
ExecStart=${INSTALL_DIR}/bin/nexus start
ExecStop=${INSTALL_DIR}/bin/nexus stop
Restart=on-abort
NoNewPrivileges=true
ProtectSystem=full
ProtectHome=true
PrivateTmp=true
ReadWritePaths=${DATA_DIR}

[Install]
WantedBy=multi-user.target
UNIT

systemctl daemon-reload
systemctl enable --now nexus

echo "Nexus ${NEXUS_VERSION} is starting as user 'nexus' on http://localhost:8081"
echo "Initial admin password (appears after the first start): ${DATA_DIR}/admin.password"
echo "Check the process owner with: ps -o user= -C java"
