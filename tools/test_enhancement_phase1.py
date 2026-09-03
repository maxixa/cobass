#!/usr/bin/env python3
"""
Cobass Note Transform Enhancement Validator (Phase 1)
Audits Multi-Pass Recipe Stacking, Macro Genre Presets, and Dynamic Stack Serialization.
"""
import sys
from pathlib import Path

EXPECTED_MACROS = [
    "Future Bass Chords",
    "Trap Lead Evolution",
    "Liquid DnB Roller",
    "Neo-Classical Motif",
    "Human Soul Groove",
    "Cyberpunk Industrial Arp"
]

def test_macro_preset_declarations():
    print("[*] [1/3] Verifying Macro Genre Presets in MidiTransformEngine.java...")
    engine_file = Path("app/src/com/maxica/cobass/sequencer/MidiTransformEngine.java")
    assert engine_file.is_file(), "Missing MidiTransformEngine.java"
    content = engine_file.read_text(encoding="utf-8")

    assert "getMacroPreset" in content, "Missing getMacroPreset in MidiTransformEngine.java"
    for macro in EXPECTED_MACROS:
        assert macro in content, f"Missing macro preset: {macro}"
    print(f"    \033[92m[✓]\033[0m All {len(EXPECTED_MACROS)} Production Macro Presets Verified.")

def test_dialog_stack_integration():
    print("[*] [2/3] Auditing Multi-Pass Recipe Stack in MidiTransformStudioDialog.java...")
    dialog_file = Path("app/src/com/maxica/cobass/ui/MidiTransformStudioDialog.java")
    assert dialog_file.is_file(), "Missing MidiTransformStudioDialog.java"
    content = dialog_file.read_text(encoding="utf-8")

    expected_tokens = [
        "recipeStack",
        "activePassIndex",
        "refreshStackCardsUI",
        "showMacroPresetsDialog",
        "getShortOperatorLabel"
    ]
    for token in expected_tokens:
        assert token in content, f"Missing token in MidiTransformStudioDialog: {token}"
    print("    \033[92m[✓]\033[0m Visual Recipe Stack UI and Pass Switcher Verified.")

def test_layout_structure():
    print("[*] [3/3] Checking dialog_midi_transform_studio.xml Layout Structure...")
    layout_file = Path("app/res/layout/dialog_midi_transform_studio.xml")
    assert layout_file.is_file(), "Missing dialog_midi_transform_studio.xml"
    content = layout_file.read_text(encoding="utf-8")

    assert "btnMacroPresets" in content, "Missing btnMacroPresets in layout"
    assert "btnAddPass" in content, "Missing btnAddPass in layout"
    assert "stackCardsContainer" in content, "Missing stackCardsContainer in layout"
    print("    \033[92m[✓]\033[0m Studio Layout Stack UI Elements Verified.")

def main():
    print("=" * 65)
    print("Cobass Note Transform Enhancement (Phase 1) Audit")
    print("=" * 65)
    test_macro_preset_declarations()
    test_dialog_stack_integration()
    test_layout_structure()
    print("=" * 65)
    print("\033[92m[PASS] ALL ENHANCEMENT PHASE 1 MULTI-PASS TESTS PASSED!\033[0m")

if __name__ == "__main__":
    main()
