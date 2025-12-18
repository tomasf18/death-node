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

echo "==== Converting line endings to Unix format ===="

# Verifica se dos2unix está instalado
if ! command -v dos2unix &> /dev/null; then
  echo "WARNING: dos2unix not found. Skipping conversion."
  echo "Install with: sudo apt-get install dos2unix"
else
  for dest in "${MODULES[@]}"; do
    DEST_PATH="$SHARED_ROOT/$dest"
    echo "Converting files in $dest..."

    # Converte ficheiros de texto comuns (ignora binários)
    find "$DEST_PATH" -type f \( \
      -name "*.sh" -o \
      -name "*.properties" -o \
      -name "*.yml" -o \
      -name "*.yaml" -o \
      -name "*.xml" -o \
      -name "*.conf" -o \
      -name "*.cfg" -o \
      -name "*.txt" -o \
      -name "*.json" -o \
      -name "*.sql" -o \
      -name "*.env" -o \
      -name "Dockerfile" -o \
      -name "Makefile" \
    \) -exec dos2unix {} \; 2>/dev/null

    # Dá permissões de execução aos scripts
    find "$DEST_PATH" -type f -name "*.sh" -exec chmod +x {} \;
  done
  echo "Line endings converted successfully"
fi

echo "==== Done ===="