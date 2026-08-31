#!/usr/bin/env bash
# ==============================================================================
# Creates a timestamped source backup archive (excluding build artifacts).
# ==============================================================================
set -euo pipefail

TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
BACKUP_DIR="backups"
BACKUP_FILE="${BACKUP_DIR}/beatforge_backup_${TIMESTAMP}.tar.gz"

mkdir -p "$BACKUP_DIR"

echo "==> [BeatForge] Creating backup archive: $BACKUP_FILE"

tar --exclude='./out' \
    --exclude='./libs/downloaded' \
    --exclude='./libs/exploded' \
    --exclude='./libs/cache' \
    --exclude='./backups' \
    --exclude='.git' \
    -czf "$BACKUP_FILE" .

echo "==> [BeatForge] Backup complete: $BACKUP_FILE ($(du -h "$BACKUP_FILE" | cut -f1))"
