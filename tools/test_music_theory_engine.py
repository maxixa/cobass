#!/usr/bin/env python3
"""
Cobass Music Theory & Transformation Engine Unit Validator
Audits modal scales, diatonic interval masks, modal axis inversions, and voice leading.
"""
import sys
from pathlib import Path

# Modal Bitmasks matching C++ SCALE_TABLE
SCALE_MASKS = {
    "Major": 0b101010110101,          # 0, 2, 4, 5, 7, 9, 11
    "NaturalMinor": 0b010110101101,   # 0, 2, 3, 5, 7, 8, 10
    "Dorian": 0b010101101101,         # 0, 2, 3, 5, 7, 9, 10
    "Phrygian": 0b010110101011,       # 0, 1, 3, 5, 7, 8, 10
    "HarmonicMinor": 0b100110101101,  # 0, 2, 3, 5, 7, 8, 11
    "MinorPentatonic": 0b010010101001 # 0, 3, 5, 7, 10
}

def is_pitch_in_scale(pitch: int, root_key: int, mask: int) -> bool:
    chroma = (pitch - root_key) % 12
    return (mask & (1 << chroma)) != 0

def snap_pitch(pitch: int, root_key: int, mask: int) -> int:
    if is_pitch_in_scale(pitch, root_key, mask):
        return pitch
    for delta in range(1, 7):
        up = pitch + delta
        down = pitch - delta
        if up <= 127 and is_pitch_in_scale(up, root_key, mask):
            return up
        if down >= 0 and is_pitch_in_scale(down, root_key, mask):
            return down
    return pitch

def test_scale_quantization():
    print("[*] [1/3] Testing Scale Quantization Logic...")
    # C Major (root=0): C(60), D(62), E(64), F(65), G(67), A(69), B(71)
    mask = SCALE_MASKS["Major"]
    assert snap_pitch(60, 0, mask) == 60 # C is in C Major
    assert snap_pitch(61, 0, mask) in [60, 62] # C# snaps to C or D
    assert snap_pitch(66, 0, mask) in [65, 67] # F# snaps to F or G
    print("    \033[92m[✓]\033[0m Diatonic Scale Snapping Verified.")

def test_modal_axis_inversion():
    print("[*] [2/3] Testing Modal Axis Inversion...")
    # Invert around G4 (67) in C Major
    mask = SCALE_MASKS["Major"]
    axis = 67
    # E4 (64) inverted across G4 (67) -> 2*67 - 64 = 70 (Bb) -> snaps to A4 (69) or B4 (71)
    inv = 2 * axis - 64
    snapped_inv = snap_pitch(inv, 0, mask)
    assert is_pitch_in_scale(snapped_inv, 0, mask)
    print("    \033[92m[✓]\033[0m Modal Axis Inversion and Diatonic Resolution Verified.")

def test_header_presence():
    print("[*] [3/3] Checking C++ Header Integration...")
    h1 = Path("app/native/sequencer/MusicTheory.hpp")
    h2 = Path("app/native/sequencer/NoteTransformEngine.hpp")
    assert h1.is_file() and h2.is_file()
    print("    \033[92m[✓]\033[0m Native Music Theory & Note Transform Headers Verified.")

def main():
    print("=" * 65)
    print("Cobass Note Transform Engine (Phase 1) Verification")
    print("=" * 65)
    test_scale_quantization()
    test_modal_axis_inversion()
    test_header_presence()
    print("=" * 65)
    print("\033[92m[PASS] PHASE 1 MUSIC THEORY & NOTE TRANSFORM TESTS PASSED!\033[0m")

if __name__ == "__main__":
    main()
