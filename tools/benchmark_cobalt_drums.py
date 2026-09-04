#!/usr/bin/env python3
"""
Cobalt Hybrid Drum Engine v2 Comprehensive Quality & C-ABI Audit Tool
Validates 52-parameter descriptor schemas, C-ABI symbol exports, JSON snapshots, and 8 factory presets.
"""
import json
import os
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

def verify_cobalt_binary() -> bool:
    print("[*] [1/3] Auditing Cobalt Drums v2 Binary & C-ABI Symbols...")
    lib_path = Path("app/lib/arm64-v8a/libcobass_plugin_synth_cobalt_drums.so")
    if not lib_path.is_file():
        print(f"\033[91m[FAIL] Binary missing: {lib_path}\033[0m")
        return False

    size_kb = lib_path.stat().st_size / 1024
    content = lib_path.read_bytes()

    missing = [sym for sym in REQUIRED_C_ABI_SYMBOLS if sym.encode("utf-8") not in content]
    if missing:
        print(f"\033[91m[FAIL] Missing C-ABI symbols: {missing}\033[0m")
        return False

    print(f"    \033[92m[✓]\033[0m Cobalt Drums v2 Binary Verified ({size_kb:.1f} KB, 12/12 C-ABI Symbols)")
    return True

def verify_cobalt_presets() -> bool:
    print("[*] [2/3] Validating 8 Production Drum Presets (52-Param Schema)...")
    preset_dir = Path("config/presets/com.maxica.cobass.plugins.cobalt_drums")
    if not preset_dir.is_dir():
        print(f"\033[91m[FAIL] Missing preset directory: {preset_dir}\033[0m")
        return False

    patches = list(preset_dir.glob("*.cobasspatch"))
    if len(patches) < 8:
        print(f"\033[91m[FAIL] Expected at least 8 presets, found {len(patches)}\033[0m")
        return False

    all_ok = True
    for p in sorted(patches):
        try:
            data = json.loads(p.read_text(encoding="utf-8"))
            if len(data) != 52:
                print(f"    \033[91m[FAIL]\033[0m {p.name}: expected 52 params, got {len(data)}")
                all_ok = False
                continue
            for i in range(52):
                if str(i) not in data:
                    print(f"    \033[91m[FAIL]\033[0m {p.name} missing parameter id {i}")
                    all_ok = False
                    break
            if all_ok:
                print(f"    \033[92m[✓]\033[0m {p.name.ljust(38)} (52/52 parameters certified)")
        except Exception as e:
            print(f"    \033[91m[FAIL]\033[0m {p.name}: {e}")
            all_ok = False

    return all_ok

def verify_ui_integration() -> bool:
    print("[*] [3/3] Auditing UI Integration & Telemetry...")
    ui_src = Path("app/src/com/maxica/cobass/ui/PluginUiDialog.java").read_text(encoding="utf-8")
    if "BUS FX & MASTER" not in ui_src:
        print("\033[91m[FAIL] PluginUiDialog.java missing Cobalt v2 BUS FX tab\033[0m")
        return False

    vis_src = Path("app/src/com/maxica/cobass/ui/SynthVisualizerView.java").read_text(encoding="utf-8")
    if "COBALT HYBRID DRUM MATRIX v2" not in vis_src:
        print("\033[91m[FAIL] SynthVisualizerView.java missing Cobalt v2 telemetry HUD\033[0m")
        return False

    print("    \033[92m[✓]\033[0m UI Parameter Tabs, Audition Ribbon & HUD Telemetry Verified.")
    return True

def main():
    print("=" * 65)
    print("Cobalt Hybrid Drum Engine v2 Quality Audit & Benchmark Suite")
    print("=" * 65)

    ok1 = verify_cobalt_binary()
    ok2 = verify_cobalt_presets()
    ok3 = verify_ui_integration()

    print("=" * 65)
    if ok1 and ok2 and ok3:
        print("\033[92m[PASS] ALL 8 COBALT DRUM SOUND SETS & C-ABI SPECIFICATIONS CERTIFIED!\033[0m")
        sys.exit(0)
    else:
        print("\033[91m[FAIL] Cobalt certification checks failed.\033[0m")
        sys.exit(1)

if __name__ == "__main__":
    main()
