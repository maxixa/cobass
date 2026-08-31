#!/usr/bin/env bash
# ==============================================================================
# Restores the project from a given backup archive.
# Usage: ./restore.sh backups/beatforge_backup_YYYYMMDD_HHMMSS.tar.gz
# ==============================================================================
set -euo pipefail

if [ $# -ne 1 ]; then
    echo "Usage: $0 <path-to-backup-tar.gz>"
    exit 1
fi

ARCHIVE="$1"
if [ ! -f "$ARCHIVE" ]; then
    echo "Error: Archive file '$ARCHIVE' does not exist."
    exit 1
fi

echo "==> [BeatForge] Restoring from $ARCHIVE..."
tar -xzf "$ARCHIVE"
echo "==> [BeatForge] Restore complete."
