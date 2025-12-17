#!/bin/bash
set -e

JAVA_BIN=$(readlink -f "$(which java)")

echo "[+] A aplicar capabilities ao Java: $JAVA_BIN"
sudo setcap cap_net_raw,cap_net_admin=eip "$JAVA_BIN"

echo "[✓] Capabilities aplicadas com sucesso"
getcap "$JAVA_BIN"
