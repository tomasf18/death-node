#!/usr/bin/env bash
set -e

# Change to yours!!!!!!!!!!!!!!!!!!!!!!!!!!!!
SHARED_ROOT="/mnt/c/Users/pitne/Documents/SIRS_shared"

declare -A MODULES=(
  [deathnode-client]="nodeA"
  [deathnode-server]="server"
  [deathnode-database]="database"
  [deathnode-monitor]="monitor"
)

echo "==== Cleaning shared folders ===="

for dest in "${MODULES[@]}"; do
  DEST_PATH="$SHARED_ROOT/$dest"
  mkdir -p "$DEST_PATH"
  rm -rf "$DEST_PATH"/*
  echo "Cleaned $DEST_PATH"
done

echo "==== Building all modules (deathnode-network) ===="

git clean -fdx
mvn clean install -U

echo "==== Copying modules (excluding src) ===="

for module in "${!MODULES[@]}"; do
  DEST="${MODULES[$module]}"
  SRC_PATH="$module"
  DEST_PATH="$SHARED_ROOT/$DEST"

  echo "Copying $module -> $DEST_PATH"

  rsync -av \
    --exclude 'src/' \
    "$SRC_PATH/" \
    "$DEST_PATH/"
done

echo "==== Done ===="
