#!/usr/bin/env bash
# ==============================================================================
# Cobass DAW - LLM Subsystem Context Bundler
# Usage:
#   ./bundle.sh              -> Generates focused synth_context.md (Default)
#   ./bundle.sh synth        -> Generates synth_context.md
#   ./bundle.sh pianoroll    -> Generates pianoroll_context.md
#   ./bundle.sh arranger     -> Generates arranger_context.md
#   ./bundle.sh wave         -> Generates wave_context.md
#   ./bundle.sh transform    -> Generates transform_context.md
#   ./bundle.sh all          -> Generates full llm_context.md
# ==============================================================================
set -euo pipefail

TARGET="${1:-synth}"
OUTPUT_FILE="${TARGET}_context.md"

if [ "$TARGET" = "all" ]; then
    OUTPUT_FILE="llm_context.md"
fi

echo "======================================================================"
echo "   COBASS LLM CONTEXT BUNDLER — TARGET: [${TARGET^^}]                 "
echo "======================================================================"

python3 - "$TARGET" "$OUTPUT_FILE" << 'EOF'
import sys
import os
from pathlib import Path
from datetime import datetime

target = sys.argv[1].lower()
out_path = Path(sys.argv[2])
root = Path(".").resolve()

# ------------------------------------------------------------------------------
# SUBSYSTEM FILE MANIFESTS
# ------------------------------------------------------------------------------
SUBSYSTEM_FILES = {
    "synth": [
        # Architecture & Specs
        "docs/plugin-synth-fx_doc.md",
        "docs/synth-V2.md",
        "app/native/include/CobassPluginABI.h",

        # Native Plugin Host Engine
        "app/native/plugin/PluginDescriptor.hpp",
        "app/native/plugin/PluginInstance.hpp",
        "app/native/plugin/PluginLoader.hpp",
        "app/native/plugin/PluginChain.hpp",

        # Native DSP Synth & Voice Modules
        "app/native/dsp/AudioNode.hpp",
        "app/native/dsp/Track.hpp",
        "app/native/dsp/SynthTrack.hpp",
        "app/native/dsp/SynthVoice.hpp",
        "app/native/dsp/PolyBlepOscillator.hpp",
        "app/native/dsp/ZdfFilter.hpp",
        "app/native/dsp/ADSR.hpp",
        "app/native/dsp/LFO.hpp",
        "app/native/dsp/FormantFilter.hpp",
        "app/native/dsp/CombFilter.hpp",
        "app/native/dsp/Wavefolder.hpp",
        "app/native/dsp/StepSequencerTrack.hpp",

        # C++ Synth Addon Plugins
        "addons/synth-hyperion/src/HyperionSynthPlugin.cpp",
        "addons/synth-cobalt-drums/src/CobaltDrumsPlugin.cpp",
        "addons/synth-cobalt-drums/src/KickVoice.hpp",
        "addons/synth-cobalt-drums/src/SnareVoice.hpp",
        "addons/synth-cobalt-drums/src/ClapVoice.hpp",
        "addons/synth-cobalt-drums/src/HiHatVoice.hpp",
        "addons/synth-cobalt-drums/src/TomVoice.hpp",
        "addons/synth-cobalt-drums/src/PercVoice.hpp",

        # Java Plugin Management & Variation Engine
        "app/src/com/maxica/cobass/model/PluginDescriptorItem.java",
        "app/src/com/maxica/cobass/model/PluginParamItem.java",
        "app/src/com/maxica/cobass/model/PluginSlotItem.java",
        "app/src/com/maxica/cobass/plugin/PluginHostManager.java",
        "app/src/com/maxica/cobass/plugin/PatchVariationEngine.java",
        "app/src/com/maxica/cobass/plugin/PluginApkInstaller.java",

        # Java Synth UI & Controls
        "app/src/com/maxica/cobass/ui/PluginUiDialog.java",
        "app/src/com/maxica/cobass/ui/SynthVisualizerView.java",
        "app/src/com/maxica/cobass/ui/RotaryKnobView.java",
        "app/src/com/maxica/cobass/ui/PluginControlFactory.java",
        "app/src/com/maxica/cobass/ui/PluginPresetDialog.java",
        "app/src/com/maxica/cobass/ui/VariationStudioDialog.java",
        "app/src/com/maxica/cobass/ui/InstrumentBrowserDialog.java",
        "app/src/com/maxica/cobass/ui/CobassTheme.java",
        "app/src/com/maxica/cobass/ui/CobassButton.java",
        "app/src/com/maxica/cobass/ui/CobassInteraction.java",

        # UI Layouts
        "app/res/layout/dialog_plugin_host.xml",

        # Verification & Benchmark Suites
        "tools/build_addons.py",
        "tools/benchmark_hyperion_dance.py",
        "tools/verify_drum_synth.py",
        "tools/test_hyperion_dsp_fixes.py",
        "tools/test_variation_engine.py"
    ],
    "pianoroll": [
        "plan/pianoroll.md",
        "app/src/com/maxica/cobass/ui/PianoRollCanvasView.java",
        "app/src/com/maxica/cobass/ui/PianoRollEditorDialog.java",
        "app/src/com/maxica/cobass/ui/PianoRollZoomDialog.java",
        "app/src/com/maxica/cobass/ui/ScaleStudioDialog.java",
        "app/src/com/maxica/cobass/ui/ChordStudioDialog.java",
        "app/src/com/maxica/cobass/ui/SnapStudioDialog.java",
        "app/src/com/maxica/cobass/sequencer/PianoRollHistoryManager.java",
        "app/src/com/maxica/cobass/model/ClipItem.java",
        "app/src/com/maxica/cobass/model/MusicalScale.java",
        "app/src/com/maxica/cobass/model/SnapGrid.java",
        "app/res/layout/dialog_piano_roll.xml"
    ],
    "arranger": [
        "plan/arrange-V2.Md",
        "app/src/com/maxica/cobass/ui/ArrangerTimelineView.java",
        "app/src/com/maxica/cobass/ui/TrackInspectorDialog.java",
        "app/src/com/maxica/cobass/sequencer/ArrangerHistoryManager.java",
        "app/src/com/maxica/cobass/sequencer/ArrangerSnapEngine.java",
        "app/src/com/maxica/cobass/model/ClipItem.java",
        "app/src/com/maxica/cobass/model/TrackItem.java",
        "app/native/sequencer/Sequencer.hpp",
        "app/native/sequencer/Transport.hpp",
        "app/native/sequencer/Clip.hpp",
        "app/res/layout/activity_main.xml"
    ],
    "wave": [
        "plan/WaveEditV1.md",
        "app/src/com/maxica/cobass/ui/WaveEditorCanvasView.java",
        "app/src/com/maxica/cobass/ui/WaveEditorDialog.java",
        "app/native/dsp/AudioTrack.hpp",
        "app/res/layout/dialog_wave_editor.xml"
    ],
    "transform": [
        "plan/transform.md",
        "app/native/sequencer/NoteTransformEngine.hpp",
        "app/native/sequencer/MusicTheory.hpp",
        "app/src/com/maxica/cobass/sequencer/MidiTransformEngine.java",
        "app/src/com/maxica/cobass/sequencer/NoteTransformPipeline.java",
        "app/src/com/maxica/cobass/ui/MidiTransformStudioDialog.java",
        "app/src/com/maxica/cobass/model/TransformRecipeItem.java",
        "app/src/com/maxica/cobass/model/TransformLockMasks.java",
        "app/res/layout/dialog_midi_transform_studio.xml"
    ]
}

def clean_content(content: str) -> str:
    content = content.replace("\r\n", "\n")
    lines = [line.rstrip() for line in content.split("\n")]
    cleaned = []
    consecutive_blanks = 0
    for line in lines:
        if not line.strip():
            consecutive_blanks += 1
            if consecutive_blanks <= 1:
                cleaned.append("")
        else:
            consecutive_blanks = 0
            cleaned.append(line)
    return "\n".join(cleaned).strip()

def collect_files(target: str) -> list[Path]:
    if target in SUBSYSTEM_FILES:
        files = []
        for rel_str in SUBSYSTEM_FILES[target]:
            p = root / rel_str
            if p.is_file():
                files.append(p)
            else:
                print(f"  [!] Notice: Optional file not found: {rel_str}")
        return files
    else:
        # Full project scan (excluding binaries, caches, outputs)
        excluded_dirs = {"out", "backups", "build_tmp", ".git", "__pycache__", "libs/cache", "libs/downloaded", "libs/exploded", "app/lib"}
        allowed_exts = {".java", ".cpp", ".hpp", ".h", ".c", ".py", ".toml", ".json", ".xml", ".sh", ".md"}
        files = []
        for p in root.rglob("*"):
            if not p.is_file():
                continue
            if any(part in excluded_dirs for part in p.parts):
                continue
            if p.suffix in allowed_exts and not p.name.endswith(".cobasspatch"):
                files.append(p)
        return sorted(files)

target_files = collect_files(target)
print(f"[*] Bundling {len(target_files)} targeted files for subsystem [{target}]...")

total_chars = 0
total_lines = 0

with open(out_path, "w", encoding="utf-8") as out:
    out.write(f"# Cobass Codebase Context Bundle: [{target.upper()}]\n\n")
    out.write(f"- **Generated on:** {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
    out.write(f"- **Subsystem Focus:** `{target}`\n")
    out.write(f"- **Included File Count:** {len(target_files)}\n\n")
    out.write("---\n\n## Included File Manifest\n```text\n")
    for f in target_files:
        out.write(f"• {f.relative_to(root)}\n")
    out.write("```\n\n---\n\n## Source Code Contents\n\n")

    for idx, f in enumerate(target_files, 1):
        rel = f.relative_to(root)
        try:
            raw = f.read_text(encoding="utf-8", errors="replace")
            cleaned = clean_content(raw)
            lines = len(cleaned.splitlines())
            chars = len(cleaned)
            total_lines += lines
            total_chars += chars

            ext = f.suffix.lstrip(".")
            lang = "cpp" if ext in ("cpp", "hpp", "h", "c") else ("java" if ext == "java" else ("xml" if ext == "xml" else ("python" if ext == "py" else "")))

            out.write(f"### File [{idx}/{len(target_files)}]: `{rel}`\n")
            out.write(f"```{lang}\n")
            out.write(cleaned)
            out.write("\n```\n\n---\n\n")
        except Exception as e:
            print(f"  [!] Failed to bundle {rel}: {e}")

est_tokens = int(total_chars / 3.8)
kb_size = out_path.stat().st_size / 1024

print("=" * 65)
print(f"\033[92m[✓] BUNDLE READY: {out_path.name}\033[0m")
print(f"    Target Scope:     {target.upper()}")
print(f"    File Size:        {kb_size:.1f} KB")
print(f"    Total Lines:      {total_lines:,}")
print(f"    Estimated Tokens: ~{est_tokens:,} tokens")
print("=" * 65)
EOF