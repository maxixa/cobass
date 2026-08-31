#!/usr/bin/env bash
# ==============================================================================
# Cobass Main Build Orchestrator (No Gradle Build System)
# ==============================================================================
set -euo pipefail

echo "==> [Cobass] Step 1: Environment & Toolchain Diagnostics"
python3 tools/doctor.py

echo "==> [Cobass] Step 2: Architecture Boundary Enforcement"
python3 tools/module_check.py

echo "==> [Cobass] Step 3: Compiling Modular Plugin Binaries (Addons)"
python3 tools/build_addons.py arm64-v8a

echo "==> [Cobass] Step 4: Compiling Low-Latency Native Audio Engine (C++20 AAudio)"
python3 tools/native_build.py \
  --out app/lib \
  --variant release

echo "==> [Cobass] Step 5: Packaging, Aligning & Signing Release APK (API 34)"
python3 tools/build_apk.py \
  --variant release \
  --out out/apk

echo "==> [Cobass] Step 6: Pre-Release Validation & Integrity Verification"
python3 tools/release_check.py out/apk/Cobass-release.apk

echo "==> [Cobass] Complete No-Gradle Build Pipeline Finished Successfully."
