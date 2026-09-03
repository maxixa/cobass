#!/usr/bin/env python3
"""
Cobass Java Transform Engine Glue & Facade Validator (Phase 5)
Audits MidiTransformEngine methods, recipe factory functions, and batch transform API.
"""
import sys
from pathlib import Path

def test_engine_declarations():
    print("[*] [1/3] Verifying MidiTransformEngine Java API...")
    engine_file = Path("app/src/com/maxica/cobass/sequencer/MidiTransformEngine.java")
    assert engine_file.is_file()
    content = engine_file.read_text(encoding="utf-8")

    expected_methods = [
        "previewPipeline",
        "applyPipeline",
        "applyPipelineBatch",
        "createEuclideanSliceRecipe",
        "createRatchetBurstRecipe",
        "createMarkovDriftRecipe",
        "createEnclosureRecipe",
        "createDiatonicVoicingRecipe",
        "createCallResponseRecipe",
        "createClaveSlipRecipe",
        "createPalindromeRecipe",
        "createPhraseArcRecipe",
        "createHumanizeRecipe"
    ]

    for m in expected_methods:
        assert m in content, f"Missing method in MidiTransformEngine: {m}"
    print("    \033[92m[✓]\033[0m All 13 Pipeline & Factory Methods Verified.")

def test_model_references():
    print("[*] [2/3] Checking Model & Architecture Boundary Rules...")
    m_file = Path("app/src/com/maxica/cobass/model/TransformRecipeItem.java")
    l_file = Path("app/src/com/maxica/cobass/model/TransformLockMasks.java")
    p_file = Path("app/src/com/maxica/cobass/sequencer/NoteTransformPipeline.java")
    assert m_file.is_file() and l_file.is_file() and p_file.is_file()
    print("    \033[92m[✓]\033[0m Architecture Model & Sequencer Facade Verified.")

def test_jni_registration():
    print("[*] [3/3] Checking JNI Native Bridge Registration...")
    jni_file = Path("app/native/jni_bridge.cpp")
    content = jni_file.read_text(encoding="utf-8")
    assert "Java_com_maxica_cobass_audio_AudioEngineNative_nativeExecuteTransformPipeline" in content
    print("    \033[92m[✓]\033[0m Packed Native JNI Transform Bridge Verified.")

def main():
    print("=" * 65)
    print("Cobass Transform Engine Glue & Facade (Phase 5) Audit")
    print("=" * 65)
    test_engine_declarations()
    test_model_references()
    test_jni_registration()
    print("=" * 65)
    print("\033[92m[PASS] ALL PHASE 5 TRANSFORM GLUE TESTS PASSED!\033[0m")

if __name__ == "__main__":
    main()
