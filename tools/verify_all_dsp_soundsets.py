#!/usr/bin/env python3
"""
Cobass Master DSP & Multi-Genre Sound Set Verification Tool
Validates all plugin binaries, C-ABI symbol exports, and preset JSON structures.
"""
import json
import os
import sys
from pathlib import Path

REQUIRED_PLUGINS = [
    "libcobass_plugin_synth_hyperion.so",
    "libcobass_plugin_synth_cobalt_drums.so",
    "libcobass_plugin_fx_ott_compressor.so",
    "libcobass_plugin_fx_sidechain_pump.so",
    "libcobass_plugin_fx_wavefolder_crush.so",
    "libcobass_plugin_fx_tape_saturation.so",
    "libcobass_plugin_fx_vintage_chorus.so"
]

C_ABI_SYMBOLS = [
    "cobass_plugin_get_manifest",
    "cobass_plugin_create_instance",
    "cobass_plugin_destroy_instance",
    "cobass_plugin_reset",
    "cobass_plugin_process",
    "cobass_plugin_note_on",
    "cobass_plugin_note_off",
    "cobass_plugin_all_notes_off",
    "cobass_plugin_set_param",
    "cobass_plugin_get_param",
    "cobass_plugin_get_state",
    "cobass_plugin_set_state"
]

def verify_binaries() -> bool:
    print("[*] [1/3] Validating Native Plugin Binaries & C-ABI Symbols...")
    lib_dir = Path("app/lib/arm64-v8a")
    if not lib_dir.is_dir():
        print(f"\033[91m[FAIL] Native lib directory not found: {lib_dir}\033[0m")
        return False

    all_ok = True
    for plugin_so in REQUIRED_PLUGINS:
        so_path = lib_dir / plugin_so
        if not so_path.is_file():
            print(f"  \033[91m[FAIL]\033[0m Missing binary: {plugin_so}")
            all_ok = False
            continue

        size_kb = so_path.stat().st_size / 1024
        content = so_path.read_bytes()
        missing = [sym for sym in C_ABI_SYMBOLS if sym.encode("utf-8") not in content]
        if missing:
            print(f"  \033[91m[FAIL]\033[0m {plugin_so} missing C-ABI symbols: {missing}")
            all_ok = False
        else:
            print(f"  \033[92m[✓]\033[0m {plugin_so.ljust(44)} ({size_kb:.1f} KB) - 12/12 C-ABI Symbols")

    return all_ok

def verify_presets() -> bool:
    print("[*] [2/3] Validating Multi-Genre Preset Libraries...")
    preset_root = Path("config/presets")
    if not preset_root.is_dir():
        print(f"\033[91m[FAIL] Presets root missing: {preset_root}\033[0m")
        return False

    total_patches = 0
    all_ok = True
    for plugin_dir in sorted(preset_root.iterdir()):
        if not plugin_dir.is_dir():
            continue
        patches = list(plugin_dir.glob("*.cobasspatch"))
        print(f"  Directory: {plugin_dir.name} ({len(patches)} patches)")
        for p in patches:
            total_patches += 1
            try:
                data = json.loads(p.read_text(encoding="utf-8"))
                if not data:
                    print(f"    \033[91m[FAIL]\033[0m {p.name}: empty patch data")
                    all_ok = False
                else:
                    print(f"    \033[92m[✓]\033[0m {p.name.ljust(36)} ({len(data)} params)")
            except Exception as e:
                print(f"    \033[91m[FAIL]\033[0m {p.name}: {e}")
                all_ok = False

    print(f"  Total Verified Patches: {total_patches}")
    return all_ok and (total_patches >= 17)

def verify_step_patterns() -> bool:
    print("[*] [3/3] Validating Step Sequencer Multi-Genre Integration...")
    step_seq_src = Path("app/src/com/maxica/cobass/ui/StepSequencerDialog.java").read_text(encoding="utf-8")
    expected_genres = [
        "Atlanta 808 Trap",
        "UK Drill Slide",
        "4-on-the-Floor Club",
        "Heavy Riddim Dubstep",
        "80s Synthwave Outrun",
        "Liquid Jungle Break",
        "90s Boom Bap"
    ]
    all_ok = True
    for g in expected_genres:
        if g not in step_seq_src:
            print(f"  \033[91m[FAIL]\033[0m Missing genre pattern: {g}")
            all_ok = False
        else:
            print(f"  \033[92m[✓]\033[0m Template: {g}")

    return all_ok

def main():
    print("=" * 65)
    print("Cobass Master DSP & Sound Set Certification")
    print("=" * 65)

    ok1 = verify_binaries()
    ok2 = verify_presets()
    ok3 = verify_step_patterns()

    print("=" * 65)
    if ok1 and ok2 and ok3:
        print("\033[92m[PASS] ALL ADVANCED DSP & SOUND SET VALIDATIONS PASSED!\033[0m")
        sys.exit(0)
    else:
        print("\033[91m[FAIL] Validation failed. Check the errors listed above.\033[0m")
        sys.exit(1)

if __name__ == "__main__":
    main()
