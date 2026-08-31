#!/usr/bin/env python3
"""
Cobass Pre-Release Validation & APK Inspection Report
"""
import sys
import zipfile
from pathlib import Path

def validate_apk(apk_path: Path):
    if not apk_path.exists():
        print(f"\033[91mError: APK not found at {apk_path}\033[0m")
        sys.exit(1)

    print("=" * 65)
    print(f"Cobass Release APK Report: {apk_path.name}")
    print("=" * 65)

    size_mb = apk_path.stat().st_size / (1024 * 1024)
    print(f"File Size:            {size_mb:.2f} MB ({apk_path.stat().st_size:,} bytes)")

    has_dex = False
    has_native_lib = False
    has_manifest = False
    dex_count = 0

    with zipfile.ZipFile(apk_path, "r") as zf:
        for info in zf.infolist():
            name = info.filename
            if name.endswith(".dex"):
                has_dex = True
                dex_count += 1
            elif name == "AndroidManifest.xml":
                has_manifest = True
            elif name == "lib/arm64-v8a/libcobass_audio.so":
                has_native_lib = True
                print(f"Native Audio Lib:     {name} ({info.file_size / 1024:.1f} KB)")

    print(f"Classes DEX Count:    {dex_count}")
    print(f"Manifest Present:     {'✓' if has_manifest else '✗'}")
    print(f"Native DSP Present:   {'✓' if has_native_lib else '✗'}")
    print("=" * 65)

    if has_dex and has_manifest and has_native_lib:
        print("\033[92mRELEASE VALIDATION PASSED: APK is ready for deployment.\033[0m")
        sys.exit(0)
    else:
        print("\033[91mRELEASE VALIDATION FAILED: Missing essential APK components.\033[0m")
        sys.exit(1)

if __name__ == "__main__":
    target = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("out/apk/Cobass-release.apk")
    validate_apk(target)
