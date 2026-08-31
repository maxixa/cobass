#!/usr/bin/env bash
# ==============================================================================
# Cobass Main Build Orchestrator (No Gradle Build System)
# ==============================================================================
set -euo pipefail

echo "==> [Cobass] Step 1: Environment & Toolchain Diagnostics"
python3 tools/doctor.py

echo "==> [Cobass] Step 2: Architecture Boundary Enforcement"
python3 tools/module_check.py

echo "==> [Cobass] Step 3: Resolving Maven Dependencies"
python3 tools/lib_resolver.py \
  --spec config/deps.toml \
  --lock libs/resolved.lock.json \
  --out libs/resolved.json

echo "==> [Cobass] Step 4: Downloading & Verifying Artifacts"
python3 tools/lib_downloader.py \
  --lock libs/resolved.lock.json \
  --cache libs/cache \
  --out libs/downloaded

echo "==> [Cobass] Step 5: Expanding AARs & Building Dependency Graph"
python3 tools/aar_expander.py \
  --in-dir libs/downloaded \
  --out-dir libs/exploded \
  --generated out/generated \
  --app-lib app/lib

echo "==> [Cobass] Step 6: Compiling Low-Latency Native Audio Engine (C++20 AAudio)"
python3 tools/native_build.py \
  --out app/lib \
  --variant release

echo "==> [Cobass] Step 7: Packaging, Aligning & Signing Release APK (API 34)"
python3 tools/build_apk.py \
  --variant release \
  --out out/apk

echo "==> [Cobass] Step 8: Pre-Release Validation & Integrity Verification"
python3 tools/release_check.py out/apk/Cobass-release.apk

echo "==> [Cobass] Complete No-Gradle Build Pipeline Finished Successfully."
