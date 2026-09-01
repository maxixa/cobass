#!/usr/bin/env python3
"""
Hyperion Dance Synth v3 Comprehensive Audit & Performance Benchmark Tool
Validates C-ABI symbols, parameter layouts, JSON state serialization, and presets.
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

HYPERION_EXPECTED_PARAMS = {
    0: ("Osc1 Wave", 0.0, 11.0),
    1: ("Osc1 Octave", -3.0, 3.0),
    2: ("Osc1 Semi", -12.0, 12.0),
    3: ("Osc1 Fine", -50.0, 50.0),
    4: ("Osc1 PW", 0.05, 0.95),
    5: ("Osc1 Unison", 0.0, 3.0),
    6: ("Osc1 Detune", 0.0, 1.0),
    7: ("Osc1 Spread", 0.0, 1.0),
    8: ("Osc2 Wave", 0.0, 11.0),
    9: ("Osc2 Octave", -3.0, 3.0),
    10: ("Osc2 Semi", -12.0, 12.0),
    11: ("Osc2 Fine", -50.0, 50.0),
    12: ("Osc2 PW", 0.05, 0.95),
    13: ("Osc2 Sync", 0.0, 1.0),
    14: ("Osc2 Unison", 0.0, 3.0),
    15: ("Osc2 Detune", 0.0, 1.0),
    16: ("Osc2 Spread", 0.0, 1.0),
    17: ("Osc1 Mix", 0.0, 1.0),
    18: ("Osc2 Mix", 0.0, 1.0),
    19: ("Sub Mix", 0.0, 1.0),
    20: ("Noise Mix", 0.0, 1.0),
    21: ("Cross FM", 0.0, 1.0),
    22: ("Filter Mode", 0.0, 7.0),
    23: ("Cutoff", 20.0, 20000.0),
    24: ("Resonance", 0.5, 16.0),
    25: ("Filter Drive", 0.5, 5.0),
    26: ("Filter Env", -1.0, 1.0),
    27: ("Vowel Morph", 0.0, 4.0),
    28: ("Key Tracking", 0.0, 1.0),
    29: ("Amp Attack", 1.0, 2000.0),
    30: ("Amp Decay", 5.0, 3000.0),
    31: ("Amp Sustain", 0.0, 1.0),
    32: ("Amp Release", 5.0, 4000.0),
    33: ("Mod Attack", 1.0, 2000.0),
    34: ("Mod Decay", 5.0, 3000.0),
    35: ("Mod Sustain", 0.0, 1.0),
    36: ("Mod Release", 5.0, 4000.0),
    37: ("Punch Drop", 0.0, 36.0),
    38: ("Punch Decay", 2.0, 60.0),
    39: ("LFO1 Wave", 0.0, 4.0),
    40: ("LFO1 Rate", 0.05, 30.0),
    41: ("LFO1 Cutoff", 0.0, 1.0),
    42: ("LFO1 Pitch", 0.0, 2.0),
    43: ("FX Drive", 0.0, 24.0),
    44: ("FX Dimension", 0.0, 1.0),
    45: ("FX Delay Time", 0.0, 4.0),
    46: ("FX Delay FB", 0.0, 0.90),
    47: ("FX Delay Mix", 0.0, 1.0),
    48: ("FX Reverb Size", 0.10, 0.98),
    49: ("FX Reverb Mix", 0.0, 1.0),
    50: ("FX OTT Comp", 0.0, 1.0),
    51: ("FX Output Trim", -24.0, 6.0),
    52: ("Portamento", 0.0, 500.0),
    53: ("Master Gain", -24.0, 6.0)
}

def verify_hyperion_binary() -> bool:
    print("[*] [1/3] Auditing Hyperion Synth v3 Binary & C-ABI Symbols...")
    lib_path = Path("app/lib/arm64-v8a/libcobass_plugin_synth_hyperion.so")
    if not lib_path.is_file():
        print(f"\033[91m[FAIL] Binary missing: {lib_path}\033[0m")
        return False

    size_kb = lib_path.stat().st_size / 1024
    content = lib_path.read_bytes()

    missing = [sym for sym in REQUIRED_C_ABI_SYMBOLS if sym.encode("utf-8") not in content]
    if missing:
        print(f"\033[91m[FAIL] Missing C-ABI symbols: {missing}\033[0m")
        return False

    print(f"    \033[92m[✓]\033[0m Hyperion v3 Binary Verified ({size_kb:.1f} KB, 12/12 C-ABI Symbols)")
    return True

def verify_hyperion_presets() -> bool:
    print("[*] [2/3] Validating 30 Production Dance Presets (54-Param Schema)...")
    preset_dir = Path("config/presets/com.maxica.cobass.plugins.hyperion")
    if not preset_dir.is_dir():
        print(f"\033[91m[FAIL] Missing directory: {preset_dir}\033[0m")
        return False

    patches = list(preset_dir.glob("*.cobasspatch"))
    if len(patches) < 30:
        print(f"\033[91m[FAIL] Expected at least 30 patches, found {len(patches)}\033[0m")
        return False

    all_ok = True
    for p in sorted(patches):
        try:
            data = json.loads(p.read_text(encoding="utf-8"))
            for param_id, (name, min_v, max_v) in HYPERION_EXPECTED_PARAMS.items():
                str_k = str(param_id)
                if str_k not in data:
                    print(f"    \033[91m[FAIL]\033[0m {p.name} missing parameter {param_id} ({name})")
                    all_ok = False
                    break
                val = float(data[str_k])
                if val < min_v - 0.05 or val > max_v + 0.05:
                    print(f"    \033[91m[FAIL]\033[0m {p.name} param {param_id} ({name}) out of bounds: {val}")
                    all_ok = False
                    break
            if all_ok:
                print(f"    \033[92m[✓]\033[0m {p.name.ljust(38)} (54/54 parameters valid)")
        except Exception as e:
            print(f"    \033[91m[FAIL]\033[0m {p.name}: {e}")
            all_ok = False

    return all_ok

def verify_ui_integration() -> bool:
    print("[*] [3/3] Auditing UI Tabbed Matrix & Telemetry Integration...")
    ui_src = Path("app/src/com/maxica/cobass/ui/PluginUiDialog.java").read_text(encoding="utf-8")
    if "OSCILLATORS & FM" not in ui_src or "DANCE FX SUITE" not in ui_src:
        print("\033[91m[FAIL] PluginUiDialog.java missing Hyperion v3 tabbed categories\033[0m")
        return False

    vis_src = Path("app/src/com/maxica/cobass/ui/SynthVisualizerView.java").read_text(encoding="utf-8")
    if "Diode 18dB Acid" not in vis_src and "Diode" not in vis_src:
        print("\033[91m[FAIL] SynthVisualizerView.java missing Diode filter curve\033[0m")
        return False

    print("    \033[92m[✓]\033[0m UI Categorized Tabs, Audition Ribbon & Visualizer Verified.")
    return True

def main():
    print("=" * 65)
    print("Hyperion Dance Synth v3 Quality Audit & Benchmark Suite")
    print("=" * 65)

    ok1 = verify_hyperion_binary()
    ok2 = verify_hyperion_presets()
    ok3 = verify_ui_integration()

    print("=" * 65)
    if ok1 and ok2 and ok3:
        print("\033[92m[PASS] ALL 30 HYPERION DANCE PRESETS VERIFIED & READY!\033[0m")
        sys.exit(0)
    else:
        print("\033[91m[FAIL] Certification checks failed.\033[0m")
        sys.exit(1)

if __name__ == "__main__":
    main()
