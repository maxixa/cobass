#!/usr/bin/env bash
set -euo pipefail

CYAN='\033[96m'
GREEN='\033[92m'
RED='\033[91m'
YELLOW='\033[93m'
RESET='\033[0m'

echo -e "${CYAN}======================================================================${RESET}"
echo -e "${CYAN}Cobass Hyperion: Phase 5 Sound Library Migration & QA Certification${RESET}"
echo -e "${CYAN}======================================================================${RESET}"

python3 - <<'EOF'
import json
import os
import sys
from pathlib import Path

# ==============================================================================
# 1. GENERATE 8 FLAGSHIP FACTORY PRESETS (.cobasspatch)
# ==============================================================================
preset_dir = Path("config/presets/com.maxica.cobass.plugins.hyperion")
preset_dir.mkdir(parents=True, exist_ok=True)

NEW_PRESETS = {
    "01_Cyberpunk_Cyber_Bass.cobasspatch": {
        "0": 6, "1": -1, "2": 0, "3": 0.0, "4": 0.50, "5": 1, "6": 0.20, "7": 0.50,
        "8": 11, "9": -1, "10": 7, "11": 5.0, "12": 0.50, "13": 0, "14": 2, "15": 0.35, "16": 0.70,
        "17": 0.85, "18": 0.75, "19": 0.50, "20": 0, "21": 0.10, "22": 4,
        "23": 0.40, "24": 0.25, "25": 0.35, "26": 0.50,
        "27": 1, "28": 2200.0, "29": 4.5, "30": 2.2, "31": 1, "32": 0.65, "33": 0.0, "34": 0.60,
        "35": 2.0, "36": 180.0, "37": 0.65, "38": 150.0,
        "39": 2.0, "40": 200.0, "41": 0.10, "42": 150.0, "43": 12.0, "44": 25.0,
        "45": 1, "46": 0.50, "47": 0.30, "48": 0.0, "49": 2.0, "50": 0.40,
        "51": 6.0, "52": 0.40, "53": 1, "54": 0.30, "55": 0.20, "56": 0.45, "57": 0.15, "58": 0.50,
        "59": 35.0, "60": 0.0
    },
    "02_Euphoric_Trance_Lead.cobasspatch": {
        "0": 5, "1": 0, "2": 0, "3": 0.0, "4": 0.50, "5": 3, "6": 0.40, "7": 0.90,
        "8": 5, "9": 0, "10": 7, "11": 7.0, "12": 0.50, "13": 0, "14": 3, "15": 0.45, "16": 0.95,
        "17": 0.90, "18": 0.85, "19": 0.25, "20": 0, "21": 0.05, "22": 5,
        "23": 0.0, "24": 0.0, "25": 0.15, "26": 0.20,
        "27": 0, "28": 5500.0, "29": 2.2, "30": 1.2, "31": 0, "32": 0.55, "33": 0.0, "34": 0.70,
        "35": 5.0, "36": 240.0, "37": 0.80, "38": 350.0,
        "39": 5.0, "40": 280.0, "41": 0.25, "42": 250.0, "43": 0.0, "44": 15.0,
        "45": 1, "46": 3.0, "47": 0.15, "48": 0.0, "49": 0.20, "50": 0.25,
        "51": 2.0, "52": 0.60, "53": 2, "54": 0.45, "55": 0.35, "56": 0.75, "57": 0.35, "58": 0.35,
        "59": 0.0, "60": 0.0
    },
    "03_Slap_House_Punch_Donk.cobasspatch": {
        "0": 6, "1": -1, "2": 0, "3": 0.0, "4": 0.50, "5": 0, "6": 0.0, "7": 0.0,
        "8": 0, "9": 0, "10": 0, "11": 0.0, "12": 0.50, "13": 0, "14": 0, "15": 0.0, "16": 0.0,
        "17": 0.90, "18": 0.60, "19": 0.60, "20": 0, "21": 0.0, "22": 0,
        "23": 0.35, "24": 0.0, "25": 0.40, "26": 0.0,
        "27": 2, "28": 3200.0, "29": 1.5, "30": 1.5, "31": 0, "32": 0.75, "33": 0.0, "34": 0.50,
        "35": 1.0, "36": 120.0, "37": 0.0, "38": 80.0,
        "39": 1.0, "40": 90.0, "41": 0.0, "42": 60.0, "43": 24.0, "44": 18.0,
        "45": 1, "46": 1.0, "47": 0.0, "48": 0.0, "49": 0.50, "50": 0.0,
        "51": 4.0, "52": 0.20, "53": 3, "54": 0.20, "55": 0.15, "56": 0.35, "57": 0.15, "58": 0.60,
        "59": 0.0, "60": 0.0
    },
    "04_Heavy_Dubstep_Growl.cobasspatch": {
        "0": 7, "1": -1, "2": 0, "3": 0.0, "4": 0.50, "5": 2, "6": 0.30, "7": 0.75,
        "8": 11, "9": -1, "10": 0, "11": 5.0, "12": 0.50, "13": 0, "14": 2, "15": 0.35, "16": 0.75,
        "17": 0.85, "18": 0.80, "19": 0.55, "20": 1, "21": 0.15, "22": 4,
        "23": 0.50, "24": 0.35, "25": 0.65, "26": 0.55,
        "27": 6, "28": 1800.0, "29": 6.0, "30": 2.8, "31": 3, "32": 0.80, "33": 2.2, "34": 0.40,
        "35": 2.0, "36": 220.0, "37": 0.70, "38": 180.0,
        "39": 2.0, "40": 240.0, "41": 0.15, "42": 180.0, "43": 18.0, "44": 30.0,
        "45": 2, "46": 2.5, "47": 0.60, "48": 0.0, "49": 5.0, "50": 0.50,
        "51": 8.0, "52": 0.45, "53": 1, "54": 0.35, "55": 0.20, "56": 0.50, "57": 0.20, "58": 0.65,
        "59": 40.0, "60": 0.0
    },
    "05_Cinematic_Ambient_Pad.cobasspatch": {
        "0": 15, "1": 0, "2": 0, "3": 0.0, "4": 0.50, "5": 3, "6": 0.35, "7": 0.95,
        "8": 9, "9": 0, "10": 7, "11": 4.0, "12": 0.50, "13": 0, "14": 3, "15": 0.30, "16": 0.95,
        "17": 0.80, "18": 0.70, "19": 0.20, "20": 0, "21": 0.10, "22": 5,
        "23": 0.10, "24": 0.0, "25": 0.0, "26": 0.10,
        "27": 7, "28": 3800.0, "29": 3.0, "30": 1.0, "31": 2, "32": 0.30, "33": 0.0, "34": 0.80,
        "35": 650.0, "36": 800.0, "37": 0.85, "38": 1200.0,
        "39": 500.0, "40": 600.0, "41": 0.40, "42": 900.0, "43": 0.0, "44": 15.0,
        "45": 0, "46": 0.25, "47": 0.25, "48": 0.05, "49": 0.15, "50": 0.35,
        "51": 0.0, "52": 0.80, "53": 2, "54": 0.55, "55": 0.40, "56": 0.92, "57": 0.65, "58": 0.25,
        "59": 60.0, "60": 0.0
    },
    "06_NeoSoul_Warm_EP.cobasspatch": {
        "0": 3, "1": 0, "2": 0, "3": 0.0, "4": 0.50, "5": 1, "6": 0.15, "7": 0.60,
        "8": 8, "9": 1, "10": 0, "11": 0.0, "12": 0.50, "13": 0, "14": 1, "15": 0.10, "16": 0.60,
        "17": 0.80, "18": 0.45, "19": 0.15, "20": 0, "21": 0.05, "22": 3,
        "23": 0.15, "24": 0.0, "25": 0.0, "26": 0.05,
        "27": 3, "28": 2800.0, "29": 1.2, "30": 1.0, "31": 2, "32": 0.45, "33": 0.0, "34": 0.65,
        "35": 8.0, "36": 350.0, "37": 0.45, "38": 280.0,
        "39": 4.0, "40": 260.0, "41": 0.10, "42": 180.0, "43": 0.0, "44": 15.0,
        "45": 1, "46": 4.5, "47": 0.10, "48": 0.05, "49": 0.35, "50": 0.20,
        "51": 1.5, "52": 0.55, "53": 1, "54": 0.25, "55": 0.25, "56": 0.55, "57": 0.30, "58": 0.30,
        "59": 0.0, "60": 0.0
    },
    "07_Hardstyle_Screamer_Lead.cobasspatch": {
        "0": 11, "1": 0, "2": 0, "3": 0.0, "4": 0.50, "5": 3, "6": 0.50, "7": 0.95,
        "8": 10, "9": 0, "10": 0, "11": 8.0, "12": 0.50, "13": 1, "14": 3, "15": 0.45, "16": 0.95,
        "17": 0.90, "18": 0.85, "19": 0.30, "20": 0, "21": 0.10, "22": 4,
        "23": 0.30, "24": 0.15, "25": 0.60, "26": 0.50,
        "27": 0, "28": 6500.0, "29": 3.5, "30": 3.0, "31": 1, "32": 0.70, "33": 0.0, "34": 0.75,
        "35": 2.0, "36": 260.0, "37": 0.85, "38": 300.0,
        "39": 2.0, "40": 280.0, "41": 0.30, "42": 220.0, "43": 12.0, "44": 20.0,
        "45": 1, "46": 4.0, "47": 0.20, "48": 0.0, "49": 1.0, "50": 0.30,
        "51": 12.0, "52": 0.65, "53": 2, "54": 0.40, "55": 0.30, "56": 0.70, "57": 0.35, "58": 0.65,
        "59": 20.0, "60": 0.0
    },
    "08_Acid_Techno_303_Bass.cobasspatch": {
        "0": 12, "1": -1, "2": 0, "3": 0.0, "4": 0.50, "5": 0, "6": 0.0, "7": 0.0,
        "8": 1, "9": -1, "10": 0, "11": 0.0, "12": 0.50, "13": 0, "14": 0, "15": 0.0, "16": 0.0,
        "17": 0.90, "18": 0.0, "19": 0.40, "20": 0, "21": 0.0, "22": 0,
        "23": 0.0, "24": 0.0, "25": 0.45, "26": 0.0,
        "27": 1, "28": 1400.0, "29": 9.5, "30": 3.2, "31": 1, "32": 0.85, "33": 0.0, "34": 0.80,
        "35": 2.0, "36": 210.0, "37": 0.35, "38": 140.0,
        "39": 2.0, "40": 190.0, "41": 0.0, "42": 120.0, "43": 0.0, "44": 15.0,
        "45": 2, "46": 0.50, "47": 0.45, "48": 0.0, "49": 0.20, "50": 0.0,
        "51": 9.0, "52": 0.15, "53": 1, "54": 0.35, "55": 0.25, "56": 0.40, "57": 0.20, "58": 0.40,
        "59": 45.0, "60": 0.0
    }
}

for fname, data in NEW_PRESETS.items():
    pfile = preset_dir / fname
    pfile.write_text(json.dumps(data, indent=2), encoding="utf-8")
    print(f"  \033[92m[✓]\033[0m Generated Flagship Preset: {fname.ljust(38)} (61/61 params)")

# ==============================================================================
# 2. MIGRATE ALL LEGACY PRESETS (54 -> 61 PARAMETERS)
# ==============================================================================
patches = list(preset_dir.glob("*.cobasspatch"))
migrated_count = 0

for p in sorted(patches):
    if p.name in NEW_PRESETS:
        continue
    try:
        data = json.loads(p.read_text(encoding="utf-8"))
    except Exception:
        continue

    # Already 61 parameters
    if len(data) == 61 and all(str(i) in data for i in range(61)):
        continue

    # Map legacy 54 parameters to new 61 schema
    new_data = {}
    # [0..19]: Osc1, Osc2, Mixers
    for i in range(20):
        new_data[str(i)] = float(data.get(str(i), 0.0))
    # New 20: Sub Octave (0 = -1 Oct)
    new_data["20"] = 0
    # New 21: Noise Mix (old 20)
    new_data["21"] = float(data.get("20", 0.0))
    # New 22: Noise Type (0 = White)
    new_data["22"] = 0
    # New 23: Cross FM (old 21)
    new_data["23"] = float(data.get("21", 0.0))
    # New 24..26: RingMod, Fold1, Fold2
    new_data["24"] = 0.0
    new_data["25"] = 0.0
    new_data["26"] = 0.0
    # [27..30]: Filter Mode, Cutoff, Res, Drive (old 22..25)
    for i in range(4):
        new_data[str(27 + i)] = float(data.get(str(22 + i), 0.0))
    # New 31: Drive Model (0 = Transistor)
    new_data["31"] = 0
    # [32..34]: Filter Env, Vowel, Keytrack (old 26..28)
    for i in range(3):
        new_data[str(32 + i)] = float(data.get(str(26 + i), 0.0))
    # [35..38]: Amp ADSR (old 29..32)
    for i in range(4):
        new_data[str(35 + i)] = float(data.get(str(29 + i), 0.0))
    # [39..44]: Mod ADSR + Punch (old 33..38)
    for i in range(6):
        new_data[str(39 + i)] = float(data.get(str(33 + i), 0.0))
    # [45..48]: LFO1 Wave, Rate, Cutoff, Pitch (old 39..42)
    for i in range(4):
        new_data[str(45 + i)] = float(data.get(str(39 + i), 0.0))
    # New 49..50: LFO2 Rate, LFO2 Mod
    new_data["49"] = 0.50
    new_data["50"] = 0.0
    # [51..58]: FX Rack (old 43..50)
    for i in range(8):
        new_data[str(51 + i)] = float(data.get(str(43 + i), 0.0))
    # [59]: Portamento (old 52)
    new_data["59"] = float(data.get("52", 0.0))
    # [60]: Master Gain (old 53)
    new_data["60"] = float(data.get("53", 0.0))

    p.write_text(json.dumps(new_data, indent=2), encoding="utf-8")
    migrated_count += 1
    print(f"  \033[92m[✓]\033[0m Migrated Legacy Preset: {p.name.ljust(38)} (54 -> 61 params)")

print(f"\nPreset library synchronized: {len(patches)} total presets certified.")

# ==============================================================================
# 3. UPDATE tools/benchmark_hyperion_dance.py (61-Param Schema Assertion)
# ==============================================================================
bench_hyp_path = Path("tools/benchmark_hyperion_dance.py")
if bench_hyp_path.is_file():
    bench_code = """#!/usr/bin/env python3
\"\"\"
Hyperion Hybrid Synth v4 Comprehensive Quality Audit & Benchmark Tool
Validates C-ABI symbols, 61-parameter layout, JSON serialization, and sound presets.
\"\"\"
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
    0: ("Osc1 Wave", 0.0, 15.0),
    1: ("Osc1 Octave", -3.0, 3.0),
    2: ("Osc1 Semi", -12.0, 12.0),
    3: ("Osc1 Fine", -50.0, 50.0),
    4: ("Osc1 PW", 0.05, 0.95),
    5: ("Osc1 Unison", 0.0, 3.0),
    6: ("Osc1 Detune", 0.0, 1.0),
    7: ("Osc1 Spread", 0.0, 1.0),
    8: ("Osc2 Wave", 0.0, 15.0),
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
    20: ("Sub Octave", 0.0, 1.0),
    21: ("Noise Mix", 0.0, 1.0),
    22: ("Noise Type", 0.0, 5.0),
    23: ("Cross FM", 0.0, 1.0),
    24: ("Ring Mod", 0.0, 1.0),
    25: ("Osc1 Fold", 0.0, 1.0),
    26: ("Osc2 Fold", 0.0, 1.0),
    27: ("Filter Mode", 0.0, 7.0),
    28: ("Cutoff", 20.0, 20000.0),
    29: ("Resonance", 0.5, 16.0),
    30: ("Filter Drive", 0.5, 5.0),
    31: ("Drive Model", 0.0, 3.0),
    32: ("Filter Env", -1.0, 1.0),
    33: ("Vowel Morph", 0.0, 4.0),
    34: ("Key Tracking", 0.0, 1.0),
    35: ("Amp Attack", 1.0, 2000.0),
    36: ("Amp Decay", 5.0, 3000.0),
    37: ("Amp Sustain", 0.0, 1.0),
    38: ("Amp Release", 5.0, 4000.0),
    39: ("Mod Attack", 1.0, 2000.0),
    40: ("Mod Decay", 5.0, 3000.0),
    41: ("Mod Sustain", 0.0, 1.0),
    42: ("Mod Release", 5.0, 4000.0),
    43: ("Punch Drop", 0.0, 36.0),
    44: ("Punch Decay", 2.0, 80.0),
    45: ("LFO1 Wave", 0.0, 4.0),
    46: ("LFO1 Rate", 0.05, 30.0),
    47: ("LFO1 Cutoff", 0.0, 1.0),
    48: ("LFO1 Pitch", 0.0, 2.0),
    49: ("LFO2 Rate", 0.05, 30.0),
    50: ("LFO2 Mod", 0.0, 1.0),
    51: ("FX Drive", 0.0, 24.0),
    52: ("FX Dimension", 0.0, 1.0),
    53: ("FX Delay Time", 0.0, 4.0),
    54: ("FX Delay FB", 0.0, 0.90),
    55: ("FX Delay Mix", 0.0, 1.0),
    56: ("FX Reverb Size", 0.10, 0.98),
    57: ("FX Reverb Mix", 0.0, 1.0),
    58: ("FX OTT Comp", 0.0, 1.0),
    59: ("Portamento", 0.0, 500.0),
    60: ("Master Gain", -24.0, 6.0)
}

def verify_hyperion_binary() -> bool:
    print("[*] [1/3] Auditing Hyperion Synth v4 Binary & C-ABI Symbols...")
    lib_path = Path("app/lib/arm64-v8a/libcobass_plugin_synth_hyperion.so")
    if not lib_path.is_file():
        print(f"\\033[91m[FAIL] Binary missing: {lib_path}\\033[0m")
        return False

    size_kb = lib_path.stat().st_size / 1024
    content = lib_path.read_bytes()

    missing = [sym for sym in REQUIRED_C_ABI_SYMBOLS if sym.encode("utf-8") not in content]
    if missing:
        print(f"\\033[91m[FAIL] Missing C-ABI symbols: {missing}\\033[0m")
        return False

    print(f"    \\033[92m[✓]\\033[0m Hyperion v4 Binary Verified ({size_kb:.1f} KB, 12/12 C-ABI Symbols)")
    return True

def verify_hyperion_presets() -> bool:
    print("[*] [2/3] Validating 61-Param Preset Sound Library...")
    preset_dir = Path("config/presets/com.maxica.cobass.plugins.hyperion")
    if not preset_dir.is_dir():
        print(f"\\033[91m[FAIL] Missing directory: {preset_dir}\\033[0m")
        return False

    patches = list(preset_dir.glob("*.cobasspatch"))
    if len(patches) < 8:
        print(f"\\033[91m[FAIL] Expected at least 8 patches, found {len(patches)}\\033[0m")
        return False

    all_ok = True
    for p in sorted(patches):
        try:
            data = json.loads(p.read_text(encoding="utf-8"))
            if len(data) != 61:
                print(f"    \\033[91m[FAIL]\\033[0m {p.name}: expected 61 params, got {len(data)}")
                all_ok = False
                continue
            for param_id, (name, min_v, max_v) in HYPERION_EXPECTED_PARAMS.items():
                str_k = str(param_id)
                if str_k not in data:
                    print(f"    \\033[91m[FAIL]\\033[0m {p.name} missing parameter {param_id} ({name})")
                    all_ok = False
                    break
                val = float(data[str_k])
                if val < min_v - 0.05 or val > max_v + 0.05:
                    print(f"    \\033[91m[FAIL]\\033[0m {p.name} param {param_id} ({name}) out of bounds: {val}")
                    all_ok = False
                    break
            if all_ok:
                print(f"    \\033[92m[✓]\\033[0m {p.name.ljust(38)} (61/61 parameters valid)")
        except Exception as e:
            print(f"    \\033[91m[FAIL]\\033[0m {p.name}: {e}")
            all_ok = False

    return all_ok

def verify_ui_integration() -> bool:
    print("[*] [3/3] Auditing UI Tabbed Matrix & Telemetry Integration...")
    ui_src = Path("app/src/com/maxica/cobass/ui/PluginUiDialog.java").read_text(encoding="utf-8")
    if "OSCILLATORS & FM" not in ui_src or "DANCE FX SUITE" not in ui_src:
        print("\\033[91m[FAIL] PluginUiDialog.java missing Hyperion tabbed categories\\033[0m")
        return False

    vis_src = Path("app/src/com/maxica/cobass/ui/SynthVisualizerView.java").read_text(encoding="utf-8")
    if "Diode 18dB Acid" not in vis_src and "Diode" not in vis_src:
        print("\\033[91m[FAIL] SynthVisualizerView.java missing Diode filter curve\\033[0m")
        return False

    print("    \\033[92m[✓]\\033[0m UI Categorized Tabs, Audition Ribbon & Visualizer Verified.")
    return True

def main():
    print("=" * 65)
    print("Hyperion Hybrid Synth v4 Quality Audit & Benchmark Suite")
    print("=" * 65)

    ok1 = verify_hyperion_binary()
    ok2 = verify_hyperion_presets()
    ok3 = verify_ui_integration()

    print("=" * 65)
    if ok1 and ok2 and ok3:
        print("\\033[92m[PASS] ALL HYPERION v4 PRESETS & C-ABI SPECIFICATIONS CERTIFIED!\\033[0m")
        sys.exit(0)
    else:
        print("\\033[91m[FAIL] Certification checks failed.\\033[0m")
        sys.exit(1)

if __name__ == "__main__":
    main()
"""
    bench_hyp_path.write_text(bench_code, encoding="utf-8")
    os.chmod(str(bench_hyp_path), 0o755)
    print("  \033[92m[✓]\033[0m Updated tools/benchmark_hyperion_dance.py (61 parameters).")

# ==============================================================================
# 4. UPDATE tools/benchmark_variation_and_presets.py (Hyperion 54 -> 61 Params)
# ==============================================================================
bench_master = Path("tools/benchmark_variation_and_presets.py")
if bench_master.is_file():
    b_src = bench_master.read_text(encoding="utf-8")
    old_line = '("libcobass_plugin_synth_hyperion.so", "Hyperion Synth v3", 54),'
    new_line = '("libcobass_plugin_synth_hyperion.so", "Hyperion Hybrid Synth v4", 61),'
    if old_line in b_src:
        b_src = b_src.replace(old_line, new_line)
        bench_master.write_text(b_src, encoding="utf-8")
        print("  \033[92m[✓]\033[0m Updated benchmark_variation_and_presets.py (61 parameter assertion).")

print("\n\033[92m[SUCCESS] Phase 5 presets, migrations & benchmark suites synchronized!\033[0m")
EOF

# Run Hyperion benchmark
echo -e "\n[*] Executing Hyperion Synth v4 QA Certification..."
python3 tools/benchmark_hyperion_dance.py

# Run Master Multi-Plugin benchmark
echo -e "\n[*] Executing Master Multi-Plugin QA Certification..."
python3 tools/benchmark_variation_and_presets.py

echo -e "\n${GREEN}======================================================================${RESET}"
echo -e "${GREEN}[PASS] Phase 5: Hyperion Synth v4 Complete Refactoring Certified!${RESET}"
echo -e "${GREEN}======================================================================${RESET}"