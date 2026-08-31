#!/usr/bin/env bash
set -euo pipefail

# 1. Update docs/CHANGELOG.md
cat << 'EOF' > docs/CHANGELOG.md
# Cobass DAW — Official Changelog & Release Notes

All notable changes to the **Cobass** digital audio workstation project are documented in this file.

---

## [2.1.0] — 2026-08-31

### 🏗️ Complete Subsystem Decoupling & Architecture Refactor (Phases 1–5)

#### Phase 1: Model Purification & Sequencer Domain Logic Extraction
- **Extracted `MidiTransformEngine.java`**: Relocated all domain-level MIDI manipulation algorithms from `ClipItem.java` into `com.maxica.cobass.sequencer.MidiTransformEngine`:
  - `splitNoteAt()`, `chop()`, `glue()`, `stampChord()`, `duplicateSelected()`, `transpose()`, `legato()`, `humanize()`, `strum()`, `quantizeAdvanced()`, `applyCrescendo()`, `compressVelocities()`, and `invertVelocities()`.
- **Pure POJO `ClipItem.java`**: Stripped 15+ algorithmic methods out of the data model, achieving single-responsibility compliance and zero boundary violations under `tools/module_check.py`.
- **Updated Consumers**: Refactored `PianoRollCanvasView.java` and `PianoRollEditorDialog.java` to call `MidiTransformEngine`.

#### Phase 2: Wave Editor & Audio DSP Decomposition
- **Extracted `AudioFileDecoder.java`**: Encapsulated Android `MediaExtractor` + `MediaCodec` asynchronous decoding into 32-bit float PCM buffers with automated sample rate conversion (`resamplePcm()`).
- **Extracted `MicrophoneRecorder.java`**: Decoupled `AudioRecord` thread loop, buffer accumulation, and live VU meter peak callbacks.
- **Extracted `TransientDetector.java`**: Relocated real-time spectral flux onset detection and zero-crossing search algorithm into dedicated audio domain services.
- **Extracted `WaveformDspProcessor.java`**: Isolated non-destructive and destructive offline DSP operations:
  - Granular pitch shifting ($\pm 24\text{ st}$) & WSOLA time stretching ($0.5\times$ to $2.0\times$).
  - Turntable brake / tape stop deceleration curves ($250\text{ ms}$ & $600\text{ ms}$).
  - Classic tape varispeed pitch/speed linking.
  - $20\text{ Hz}$ DC offset high-pass filter, phase inversion ($\varnothing$), peak normalization ($0\text{ dB}$, $-1\text{ dB}$, $-3\text{ dB}$, $-6\text{ dB}$), and 44-byte RIFF/WAVE header file generation.
- **Streamlined Wave Views**: Reduced `WaveEditorDialog.java` and `WaveEditorCanvasView.java` by over 500 lines.

#### Phase 3: Piano Roll & Studio Dialog Extraction
- **Extracted `PianoRollHistoryManager.java`**: Isolated 50-step deep-copy undo/redo transaction stack for note snapshots into `com.maxica.cobass.sequencer`.
- **Created Standalone Sub-Dialog Components**:
  - `SnapStudioDialog.java`: Straight ($1/1\dots 1/32$), Triplet ($1/4\text{T}\dots 1/32\text{T}$), Dotted, and Free grid selection.
  - `ScaleStudioDialog.java`: 12 Root keys and 6 modal scales (Major, Minor, Dorian, Pentatonic, Chromatic).
  - `ChordStudioDialog.java`: 9 Chord interval presets (Major, Minor, 7th, Maj7, Sus4, Dim, Aug, Add9).
  - `PianoRollZoomDialog.java`: 2D horizontal time zoom and vertical keybed height sliders.
  - `MidiTransformDialog.java`: Articulations (Legato, Strum), Humanize jitter, Groove Quantize with Swing %, Velocity Compression, and Crescendo curves.
- **Streamlined `PianoRollEditorDialog.java`**: Reduced from $\approx 800$ lines down to $\approx 240$ lines.

#### Phase 4: Arranger & MainActivity Decoupling
- **Extracted `ArrangerHistoryManager.java` & `ArrangerSnapEngine.java`**: Isolated timeline clip state snapshots and magnetic snap calculations.
- **Extracted `PresetUnpacker.java`**: Decoupled initial factory preset unpacking from `MainActivity.java`.
- **Extracted `InstrumentBrowserDialog.java` & `TrackInspectorDialog.java`**: Extracted modal instrument selection and track inspector properties from `MainActivity.java`.
- **Streamlined `MainActivity.java` & `ArrangerTimelineView.java`**: Reduced monolithic God Activity complexity by $\approx 350$ lines.

#### Phase 5: Plugin Host & UI Dialog Modularization
- **Extracted `FxPluginBrowserDialog.java`**: Isolated 8-slot insert FX selection.
- **Extracted `PluginPresetDialog.java`**: Encapsulated preset browser, patch loader, and patch exporter for user `.cobasspatch` files.
- **Extracted `PluginControlFactory.java`**: Decoupled dynamic UI view construction (Rotary knobs, LED switches, stepped choice selectors).
- **Streamlined `FxRackDialog.java` & `PluginUiDialog.java`**: Focused strictly on parameter routing and real-time DSP telemetry.

---

## [2.0.0] — 2026-08-31

### 🎹 Synthesizer Subsystem v2.0 (Flagship Upgrade)
- Anti-Aliasing PolyBLEP Oscillator Suite (`PolyBlepOscillator.hpp`).
- Zero-Delay Feedback (ZDF) 4-Pole Moog Ladder & 2-Pole SVF Filter Suite (`ZdfFilter.hpp`).
- Dual Exponential ADSR Envelopes & Multi-Waveform LFO Engine (`ADSR.hpp`, `LFO.hpp`).
- Intelligent Voice Stealing & Legato Portamento Glide Engine.
- Flagship Hyperion Synth v2 with 7-Voice Supersaw Unison & Cross-FM.
- 60 FPS Interactive Oscilloscope, Filter Magnitude Curve, and ADSR HUD (`SynthVisualizerView.java`).

---

## [1.5.0] — Wave Editor & Non-Destructive Audio Suite
- Multi-tier Mipmapped Peak Caches (1x, 8x, 64x, 512x, 4096x).
- Spectral Flux Transient Detector & Auto-Slice to Arranger timeline.
- WSOLA Granular Time-Stretching & Pitch Shifting (+/- 24 semitones).
- Snap-to-Zero Crossing engine (+/- 128 sample window).
- SAF Audio File Importer (.wav, .mp3, .flac, .ogg, .m4a) & in-dialog mic recording.

---

## [1.0.0] — Initial Production Release
- Pure CLI No-Gradle Architecture (Python build orchestrators, AAPT2, D8, Clang++ C++20).
- Ultra-Low Latency AAudio Real-Time Engine with SPSC lock-free command queues.
- Cubase-Inspired Arranger & Piano Roll with Scale-Fold keybeds and chord stamper.
- Studio Mixer Console with 4-Band Parametric EQ, Compressor, Algorithmic Reverb, Delay, and Master Limiter.
EOF

# 2. Create push.sh
cat << 'EOF' > push.sh
#!/usr/bin/env bash
# ==============================================================================
# Cobass Production Validation, Backup & Git Push Pipeline (No-Gradle)
# Usage: ./push.sh ["Optional commit message"]
# ==============================================================================
set -euo pipefail

COMMIT_MSG="${1:-"refactor: complete subsystem modularization and domain logic extraction (v2.1.0)"}"

echo "======================================================================"
echo "          COBASS PRODUCTION VALIDATION & DEPLOYMENT PIPELINE          "
echo "======================================================================"

echo "==> [1/7] Running Toolchain Diagnostics..."
python3 tools/doctor.py

echo "==> [2/7] Verifying Architectural Module Boundaries..."
python3 tools/module_check.py

echo "==> [3/7] Building Native Engine, Plugins & Release APK..."
./build.sh

echo "==> [4/7] Validating Output APK Integrity..."
python3 tools/release_check.py out/apk/Cobass-release.apk

echo "==> [5/7] Generating Source Archive Backup..."
if [ -f "backup.sh" ]; then
    ./backup.sh
fi

echo "==> [6/7] Updating LLM Context Bundle..."
if [ -f "tools/bundle_llm.py" ]; then
    python3 tools/bundle_llm.py --out llm_context.md || true
fi

echo "==> [7/7] Staging Changes and Pushing to Git Remote..."
git add .

if git diff-index --quiet HEAD --; then
    echo "  [*] No changes detected to commit."
else
    git commit -m "$COMMIT_MSG"
    echo -e "\033[92m[✓] Committed:\033[0m $COMMIT_MSG"
fi

CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "main")
echo "  [*] Pushing to origin/$CURRENT_BRANCH..."
git push origin "$CURRENT_BRANCH"

echo "======================================================================"
echo -e "\033[92m[✓] SUCCESS: Build verified, backup created, and branch pushed!\033[0m"
echo "======================================================================"
EOF

chmod +x push.sh
echo "[+] CHANGELOG.md updated and push.sh created."