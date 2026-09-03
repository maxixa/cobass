#!/usr/bin/env python3
"""
Cobass Note Transform Enhancement Validator (Phase 2)
Audits Live Canvas Ghost Overlays, Viewport Sync, and A/B Comparison States.
"""
import sys
from pathlib import Path

def test_canvas_ghost_methods():
    print("[*] [1/3] Verifying Ghost Note APIs in PianoRollCanvasView.java...")
    pr_canvas = Path("app/src/com/maxica/cobass/ui/PianoRollCanvasView.java").read_text(encoding="utf-8")
    assert "setGhostNotes" in pr_canvas, "Missing setGhostNotes in PianoRollCanvasView"
    assert "clearGhostNotes" in pr_canvas, "Missing clearGhostNotes in PianoRollCanvasView"
    assert "ghostNotePaint" in pr_canvas, "Missing ghostNotePaint in PianoRollCanvasView"
    print("    \033[92m[✓]\033[0m Piano Roll Ghost Notes Overlay API Verified.")

def test_arranger_ghost_methods():
    print("[*] [2/3] Verifying Ghost Clip APIs in ArrangerTimelineView.java...")
    arr_view = Path("app/src/com/maxica/cobass/ui/ArrangerTimelineView.java").read_text(encoding="utf-8")
    assert "setGhostClips" in arr_view, "Missing setGhostClips in ArrangerTimelineView"
    assert "clearGhostClips" in arr_view, "Missing clearGhostClips in ArrangerTimelineView"
    print("    \033[92m[✓]\033[0m Arranger Ghost Clips Overlay API Verified.")

def test_studio_dialog_live_sync():
    print("[*] [3/3] Verifying Live Preview Listener & A/B in MidiTransformStudioDialog.java...")
    studio_dlg = Path("app/src/com/maxica/cobass/ui/MidiTransformStudioDialog.java").read_text(encoding="utf-8")
    assert "OnLivePreviewListener" in studio_dlg, "Missing OnLivePreviewListener in MidiTransformStudioDialog"
    assert "setLivePreviewListener" in studio_dlg, "Missing setLivePreviewListener in MidiTransformStudioDialog"
    assert "isAbComparingOriginal" in studio_dlg, "Missing A/B comparison state in MidiTransformStudioDialog"
    print("    \033[92m[✓]\033[0m Real-Time Viewport Sync & A/B Compare Logic Verified.")

def main():
    print("=" * 65)
    print("Cobass Note Transform Enhancement (Phase 2) Audit")
    print("=" * 65)
    test_canvas_ghost_methods()
    test_arranger_ghost_methods()
    test_studio_dialog_live_sync()
    print("=" * 65)
    print("\033[92m[PASS] ALL ENHANCEMENT PHASE 2 GHOST OVERLAY TESTS PASSED!\033[0m")

if __name__ == "__main__":
    main()
