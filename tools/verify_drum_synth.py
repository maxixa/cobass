#!/usr/bin/env python3
"""
Cobass Drum Synth & Preset Integrity Validation Tool
Validates C-ABI binary exports, parameter ranges, and preset JSON structures.
"""
import json
import os
import subprocess
import sys
from pathlib import Path

REQUIRED_C_ABI_SYMBOLS = [
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

EXPECTED_PARAMS = {
    0: ("Kit Type", 0.0, 3.0),
    1: ("Master Drive", 0.0, 24.0),
    2: ("Tone Tilt", -6.0, 6.0),
    3: ("Master Out", -24.0, 6.0),
    4: ("Kick Tune", 30.0, 90.0),
    5: ("Kick Decay", 50.0, 1200.0),
    6: ("Kick Drop", 0.0, 1.0),
    7: ("Kick Click", 0.0, 1.0),
    8: ("Kick Sat", 0.0, 1.0),
    9: ("Snare Tune", 120.0, 350.0),
    10: ("Snare Decay", 20.0, 400.0),
    11: ("Snare Snappy", 0.0, 1.0),
    12: ("Snare Wire Dec", 30.0, 600.0),
    13: ("Snare Filter", 1000.0, 10000.0),
    14: ("Clap Tone", 800.0, 4000.0),
    15: ("Clap Spread", 5.0, 30.0),
    16: ("Clap Decay", 50.0, 600.0),
    17: ("Clap Room", 0.0, 1.0),
    18: ("Hat Tone", 4000.0, 14000.0),
    19: ("Cl. Hat Decay", 10.0, 150.0),
    20: ("Op. Hat Decay", 100.0, 1500.0),
    21: ("Hat Choke", 0.0, 1.0),
    22: ("Hat Sizzle", 0.0, 1.0),
    23: ("Tom Tune", 60.0, 400.0),
    24: ("Tom Sweep", -24.0, 24.0),
    25: ("Tom Decay", 50.0, 800.0),
    26: ("Tom FM Depth", 0.0, 1.0),
    27: ("Tom Impact", 0.0, 1.0),
    28: ("Rim Pitch", 800.0, 3000.0),
    29: ("Rim Decay", 5.0, 80.0),
    30: ("Cowbell Tune", 300.0, 1000.0),
    31: ("Cowbell Decay", 30.0, 500.0),
}

def validate_presets() -> bool:
    print("[*] [1/3] Validating Factory Drum Kit Presets...")
    preset_dir = Path("config/presets/com.maxica.cobass.plugins.cobalt_drums")
    if not preset_dir.is_dir():
        print(f"\033[91m[FAIL] Preset directory not found: {preset_dir}\033[0m")
        return False

    patches = list(preset_dir.glob("*.cobasspatch"))
    if len(patches) < 5:
        print(f"\033[91m[FAIL] Expected at least 5 presets, found {len(patches)}\033[0m")
        return False

    for patch in patches:
        try:
            data = json.loads(patch.read_text(encoding="utf-8"))
            for param_id, (p_name, min_v, max_v) in EXPECTED_PARAMS.items():
                str_key = str(param_id)
                if str_key not in data:
                    print(f"\033[91m[FAIL] {patch.name} missing parameter {param_id} ({p_name})\033[0m")
                    return False
                val = float(data[str_key])
                if val < min_v - 0.01 or val > max_v + 0.01:
                    print(f"\033[91m[FAIL] {patch.name} parameter {param_id} ({p_name}) value {val} out of bounds [{min_v}, {max_v}]\033[0m")
                    return False
            print(f"    \033[92m[✓]\033[0m Verified Patch: {patch.name} ({len(data)} parameters)")
        except Exception as e:
            print(f"\033[91m[FAIL] Error parsing {patch.name}: {e}\033[0m")
            return False

    return True

def validate_plugin_binary() -> bool:
    print("[*] [2/3] Validating Native Plugin Binary & C-ABI Exports...")
    lib_path = Path("app/lib/arm64-v8a/libcobass_plugin_synth_cobalt_drums.so")
    if not lib_path.is_file():
        print(f"\033[91m[FAIL] Plugin binary missing at {lib_path}. Run build_addons.py first.\033[0m")
        return False

    size_kb = lib_path.stat().st_size / 1024
    print(f"    Binary Size: {size_kb:.1f} KB")

    # Read binary for symbols
    content = lib_path.read_bytes()
    missing_symbols = []
    for sym in REQUIRED_C_ABI_SYMBOLS:
        if sym.encode("utf-8") not in content:
            missing_symbols.append(sym)

    if missing_symbols:
        print(f"\033[91m[FAIL] Missing C-ABI symbols: {missing_symbols}\033[0m")
        return False

    print(f"    \033[92m[✓]\033[0m All {len(REQUIRED_C_ABI_SYMBOLS)} C-ABI Export Symbols Verified.")
    return True

def validate_step_sequencer_wiring() -> bool:
    print("[*] [3/3] Validating Step Sequencer Integration...")
    step_track_header = Path("app/native/dsp/StepSequencerTrack.hpp").read_text(encoding="utf-8")
    if "customInstrument_" not in step_track_header or "advancePlayback" not in step_track_header:
        print("\033[91m[FAIL] StepSequencerTrack.hpp missing customInstrument_ routing\033[0m")
        return False

    main_act = Path("app/src/com/maxica/cobass/ui/MainActivity.java").read_text(encoding="utf-8")
    if "com.maxica.cobass.plugins.cobalt_drums" not in main_act:
        print("\033[91m[FAIL] MainActivity.java missing Cobalt Drum Synth default binding\033[0m")
        return False

    print("    \033[92m[✓]\033[0m Step Sequencer Native Routing & UI Launchers Verified.")
    return True

def main():
    print("=" * 65)
    print("Cobass Drum Synth Subsystem Verification Suite")
    print("=" * 65)

    ok1 = validate_presets()
    ok2 = validate_plugin_binary()
    ok3 = validate_step_sequencer_wiring()

    print("=" * 65)
    if ok1 and ok2 and ok3:
        print("\033[92m[PASS] ALL DRUM SYNTH VALIDATION CHECKS PASSED SUCCESSFULLY!\033[0m")
        sys.exit(0)
    else:
        print("\033[91m[FAIL] Drum Synth validation failed. Resolve issues above.\033[0m")
        sys.exit(1)

if __name__ == "__main__":
    main()
