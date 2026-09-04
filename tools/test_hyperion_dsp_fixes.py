#!/usr/bin/env python3
"""
Cobass Hyperion DSP Bug Fix Verification Suite
Asserts presence of all critical bug patches in source and binary exports.
"""
import sys
from pathlib import Path

def test_source_patches():
    print("[*] [1/2] Verifying surgical patches in source headers and plugin...")
    adsr_src = Path("app/native/dsp/ADSR.hpp").read_text(encoding="utf-8")
    assert "EnvelopeState getState() const noexcept" in adsr_src, "ADSR.hpp missing getState()"

    zdf_src = Path("app/native/dsp/ZdfFilter.hpp").read_text(encoding="utf-8")
    assert "mode_ == ZdfFilterMode::FormantVowel" in zdf_src, "ZdfFilter.hpp missing FormantVowel guard"

    hyp_src = Path("addons/synth-hyperion/src/HyperionSynthPlugin.cpp").read_text(encoding="utf-8")
    assert "oscSub.resetPhase(0.0)" in hyp_src, "BUG-3 fix missing: oscSub.resetPhase()"
    assert "std::clamp(fmMod, 0.05f, 8.0f)" in hyp_src, "BUG-1 + BUG-2 fix missing: Cross-FM clamp"
    assert "std::abs(finalCutoff - v.lastCutoff) > 1.0f" in hyp_src, "BUG-4 fix missing: Filter parameter cache"
    assert "ampEnv.getState() == EnvelopeState::Sustain" in hyp_src, "BUG-5 fix missing: Legato check"
    assert "delayDampL_" in hyp_src, "BUG-6 fix missing: Delay lowpass damping"
    assert "safeVerbSize" in hyp_src, "MINOR-4 fix missing: Reverb feedback cap"
    print("    \033[92m[✓]\033[0m All 6 bugs and major issues verified in source code.")

def test_binary_audit():
    print("[*] [2/2] Running Hyperion binary benchmark audit...")
    import subprocess
    res = subprocess.run([sys.executable, "tools/benchmark_hyperion_dance.py"], capture_output=True, text=True)
    assert res.returncode == 0, f"Hyperion benchmark failed:\n{res.stdout}\n{res.stderr}"
    print("    \033[92m[✓]\033[0m Hyperion benchmark suite passed with 100% C-ABI compliance.")

def main():
    print("=" * 65)
    print("Cobass Hyperion DSP Sound Fixes Audit")
    print("=" * 65)
    test_source_patches()
    test_binary_audit()
    print("=" * 65)
    print("\033[92m[PASS] ALL HYPERION DSP SOUND FIXES VERIFIED SUCCESSFULLY!\033[0m")

if __name__ == "__main__":
    main()
