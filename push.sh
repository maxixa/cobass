#!/usr/bin/env bash
# ==============================================================================
# Cobass Production Validation, Backup & Git Push Pipeline (No-Gradle)
# Usage: ./push.sh ["Optional commit message"]
# ==============================================================================
set -euo pipefail

COMMIT_MSG="${1:-"refactor: complete subsystem modularization and domain logic extraction (v2.1.0)"}"

echo "======================================================================"
echo "          COBASS PRODUCTION VALIDATION & DEPLOYMENT PIPELINE          "
echo "======================================================================"

echo "==> [1/7] Running Toolchain Diagnostics..."
python3 tools/doctor.py

echo "==> [2/7] Verifying Architectural Module Boundaries..."
python3 tools/module_check.py

echo "==> [3/7] Building Native Engine, Plugins & Release APK..."
./build.sh

echo "==> [4/7] Validating Output APK Integrity..."
python3 tools/release_check.py out/apk/Cobass-release.apk

echo "==> [5/7] Generating Source Archive Backup..."
if [ -f "backup.sh" ]; then
    ./backup.sh
fi

echo "==> [6/7] Updating LLM Context Bundle..."
if [ -f "tools/bundle_llm.py" ]; then
    python3 tools/bundle_llm.py --out llm_context.md || true
fi

echo "==> [7/7] Staging Changes and Pushing to Git Remote..."
git add .

if git diff-index --quiet HEAD --; then
    echo "  [*] No changes detected to commit."
else
    git commit -m "$COMMIT_MSG"
    echo -e "\033[92m[✓] Committed:\033[0m $COMMIT_MSG"
fi

CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "main")
echo "  [*] Pushing to origin/$CURRENT_BRANCH..."
git push origin "$CURRENT_BRANCH"

echo "======================================================================"
echo -e "\033[92m[✓] SUCCESS: Build verified, backup created, and branch pushed!\033[0m"
echo "======================================================================"
