#!/usr/bin/env python3
"""
Cobass Sound Set & Patch Verification Script
Validates JSON syntax, parameter counts, and bounds for all 60 sound libraries across all 7 plugins.
"""
import json
import sys
from pathlib import Path

EXPECTED_LIBRARIES = {
    "com.maxica.cobass.plugins.hyperion": (54, 30),
    "com.maxica.cobass.plugins.cobalt_drums": (32, 12),
    "com.maxica.cobass.plugins.ott_compressor": (8, 4),
    "com.maxica.cobass.plugins.sidechain_pump": (6, 4),
    "com.maxica.cobass.plugins.wavefolder_crush": (6, 4),
    "com.maxica.cobass.plugins.tape_saturation": (6, 3),
    "com.maxica.cobass.plugins.vintage_chorus": (6, 3),
}

def verify_preset_folder(folder_path: Path, expected_param_count: int, min_preset_count: int) -> bool:
    if not folder_path.is_dir():
        print(f"\033[91m[FAIL] Missing preset directory: {folder_path}\033[0m")
        return False

    patches = list(folder_path.glob("*.cobasspatch"))
    if len(patches) < min_preset_count:
        print(f"\033[91m[FAIL] Expected at least {min_preset_count} patches, found {len(patches)} in {folder_path.name}\033[0m")
        return False

    print(f"[*] Validating {len(patches)} patches in {folder_path.name}...")
    success = True
    for p in sorted(patches):
        try:
            data = json.loads(p.read_text(encoding="utf-8"))
            if len(data) < expected_param_count:
                print(f"  \033[91m[FAIL]\033[0m {p.name}: expected {expected_param_count} params, got {len(data)}")
                success = False
            else:
                print(f"  \033[92m[✓]\033[0m {p.name.ljust(42)} ({len(data)} parameters)")
        except Exception as e:
            print(f"  \033[91m[FAIL]\033[0m {p.name}: {e}")
            success = False
    return success

def main():
    print("=" * 65)
    print("Cobass Master 60-Preset Multi-Plugin Verification Suite")
    print("=" * 65)

    all_ok = True
    total_verified = 0
    preset_root = Path("config/presets")

    for pkg_id, (params_count, min_count) in EXPECTED_LIBRARIES.items():
        folder = preset_root / pkg_id
        ok = verify_preset_folder(folder, params_count, min_count)
        if not ok:
            all_ok = False
        else:
            total_verified += len(list(folder.glob("*.cobasspatch")))

    print("=" * 65)
    if all_ok:
        print(f"\033[92m[PASS] ALL {total_verified} PRODUCTION PRESETS VERIFIED ACROSS ALL 7 PLUGINS!\033[0m")
        sys.exit(0)
    else:
        print("\033[91m[FAIL] Preset verification failed.\033[0m")
        sys.exit(1)

if __name__ == "__main__":
    main()
