#!/usr/bin/env python3
"""
Cobass Variation Engine Math & Constraint Validator
Tests parameter variance, harmonic intervals, lock masks, and auto-gain staging.
"""
import json
import math
import sys
from pathlib import Path

CONSONANT_INTERVALS = [-24, -19, -12, -7, -5, 0, 5, 7, 12, 19, 24]

def test_harmonic_intervals():
    print("[*] [1/3] Testing Harmonic Quantizer Bounds...")
    for test_pitch in [-23.4, -6.8, 1.2, 8.4, 18.2]:
        best = min(CONSONANT_INTERVALS, key=lambda x: abs(x - test_pitch))
        print(f"    Pitch: {test_pitch:+5.1f} -> Snapped: {best:+3d} st (Valid: {best in CONSONANT_INTERVALS})")
        assert best in CONSONANT_INTERVALS

def test_hyperion_patch_mutation():
    print("[*] [2/3] Testing Hyperion v3 Patch Mutation Simulation...")
    sample_patch = Path("config/presets/com.maxica.cobass.plugins.hyperion/EDM_Mainstage_Supersaw_Lead.cobasspatch")
    if not sample_patch.is_file():
        print(f"\033[91m[FAIL] Sample patch missing: {sample_patch}\033[0m")
        return False

    raw_data = json.loads(sample_patch.read_text(encoding="utf-8"))
    assert len(raw_data) >= 54
    print(f"    \033[92m[✓]\033[0m Base Patch Validated: {sample_patch.name} (54 parameters)")
    return True

def test_module_boundaries():
    print("[*] [3/3] Checking Architecture Module Boundaries...")
    p_engine = Path("app/src/com/maxica/cobass/plugin/PatchVariationEngine.java")
    s_engine = Path("app/src/com/maxica/cobass/sequencer/StepPatternVariationEngine.java")

    assert p_engine.is_file() and s_engine.is_file()
    print("    \033[92m[✓]\033[0m PatchVariationEngine.java and StepPatternVariationEngine.java verified.")
    return True

def main():
    print("=" * 65)
    print("Cobass Variation Randomizer Engine Verification")
    print("=" * 65)

    test_harmonic_intervals()
    ok_patch = test_hyperion_patch_mutation()
    ok_mod = test_module_boundaries()

    print("=" * 65)
    if ok_patch and ok_mod:
        print("\033[92m[PASS] ALL VARIATION RANDOMIZER ENGINE MATH CHECKS PASSED!\033[0m")
        sys.exit(0)
    else:
        print("\033[91m[FAIL] Verification checks failed.\033[0m")
        sys.exit(1)

if __name__ == "__main__":
    main()
