# Cobass DAW — Official Changelog & Release Notes

All notable changes to the **Cobass** digital audio workstation project are documented in this file.

---

## [2.6.0] — 2026-08-31

### 🎲 Intelligent Variation Randomizer & 60-Preset Sound Library (Phases 1–5)
- **Intelligent Patch Variation Engine (`PatchVariationEngine.java`)**:
  - Controlled Gaussian variance intensity from **Light** ($5\%$) to **Extreme** ($85\%$) with dynamic tier readouts.
  - **6 Sectional Module Lock Masks**: Pin `[🔒 Oscillators]`, `[🔒 Filter]`, `[🔒 Envelopes]`, `[🔒 LFO]`, `[🔒 Studio FX]`, or `[🔒 Master]` to protect core timbres during randomization.
  - **Harmonic Consonancy Quantizer**: Semitone and pitch bend parameters snap automatically to consonant intervals ($\{0, \pm 5, \pm 7, \pm 12, \pm 19, \pm 24\text{ st}\}$).
  - **Headroom Protection Normalizer**: Auto-calculates gain-staging trim compensation when resonance, drive, or unison stacking increases.
- **Step Sequencer Groove Variation Studio (`StepPatternVariationEngine.java`)**:
  - Automated groove mutation for velocity jitter, sub-step ratchets ($1\times\dots 8\times$), micro-timing swing nudges, and probability dynamics.
- **Dedicated UI Dialogs & Action Buttons**:
  - `VariationStudioDialog.java`: Interactive studio with intensity slider, module locks, mutation rollback history, and in-dialog audition pads (`[▶ C2 Sub]`, `[▶ C3 Bass]`, `[▶ C4 Pluck]`, `[▶ C5 Lead]`).
  - Added **`[🎲 VARIATION]`** launcher to `PluginUiDialog.java` action bar.
  - Added **`[🎲 GROOVE VARIATION]`** launcher to `StepSequencerDialog.java`.
- **Complete 60-Preset Multi-Plugin Production Library (`config/presets/`)**:
  - **Hyperion Synth v3 (30 Patches)**: Basses, 808s, Mainstage Leads, Screeches, Plucks, Arps, Pads, and CS-80 Brass.
  - **Cobalt Drum Machine (12 Kits)**: 808 Trap, 909 Techno, Dubstep Riddim, EDM Mainstage, UK Drill, Synthwave, DnB Liquid Jungle, Future Bass, Hardstyle, Industrial, Lo-Fi, Hyperpop.
  - **Insert FX Presets (18 Patches)**: Dedicated sound sets across OTT Multiband Compressor, Sidechain Pump, Wavefolder & Crusher, Tape Saturation, and Vintage Chorus.

---

## [2.5.0] — 2026-08-31

### 🎹 Hyperion Dance Synth v3 (Flagship Upgrade - Phases 1–5)
- 12 Modern Dance Wavetables (Hypersaw, Future Donk, Vowel Talk, Metallic FM, Reese, Hard Sync, Screamer Saw).
- Independent Dual-Oscillator Unison (up to 16 sub-voices per voice with Gaussian detuning and stereo spread).
- Fast Attack Punch Pitch Envelope ($0\dots 36\text{ st}$, $2\dots 60\text{ ms}$).
- ZDF Filter Suite: TB-303 Diode 18dB Acid Ladder, 3-Peak Formant Vowel (A-E-I-O-U), and Tuned Comb Resonator.
- Internal 5-Stage Studio FX Rack: Multi-Stage Wavefolder, Haas Dimension Expander, Ping-Pong Delay, Lush Reverb, and OTT Limiter.

---

## [2.4.0] — 2026-08-31

### ⚡ Advanced DSP Architecture & Multi-Genre Sound Sets (Phases 1–5)
- Multi-Band OTT Dynamics Processor (`addons/fx-ott-compressor`).
- Sidechain Volume Pumper & Envelope Shaper (`addons/fx-sidechain-pump`).
- Nonlinear Wavefolder & Bitcrusher (`addons/fx-wavefolder-crush`).
- 7 Multi-Genre Step Sequencer Pattern Templates.

---

## [2.3.0] — 2026-08-31

### 🥁 Cobalt Drum Machine Synthesizer Subsystem (Phases 1–5)
- 8 Dedicated Native DSP Voice Engines (`addons/synth-cobalt-drums`).
- Choke Group synchronization (Closed Hat chokes Open Hat).
- Master Bus Analog Saturation and 1-Pole Tone Tilt filter ($\pm 6\text{ dB}$).

---

## [2.2.0] — 2026-08-31

### 🥁 Step Sequencer & Drum Machine Subsystem (Phases 1–5)
- 16-lane concurrent multi-pad drum engine (`StepSequencerTrack.hpp`).
- Euclidean Algorithmic Rhythm Generator (`EuclideanGenerator.java`).
- Parameter Lock Studio Drawer with Sub-step Ratchets ($1\times\dots 8\times$).

---

## [2.1.0] — 2026-08-31

### 🏗️ Complete Subsystem Decoupling & Architecture Refactor
- Model Purification (`MidiTransformEngine.java`).
- Wave Editor & Audio DSP Decomposition (`AudioFileDecoder.java`, `TransientDetector.java`, `WaveformDspProcessor.java`).
- Studio Dialog Modularization (`SnapStudioDialog.java`, `ScaleStudioDialog.java`, `ChordStudioDialog.java`).

---

## [2.0.0] — 2026-08-31

### 🎹 Synthesizer Subsystem v2.0
- Anti-Aliasing PolyBLEP Oscillators (`PolyBlepOscillator.hpp`).
- Zero-Delay Feedback (ZDF) 4-Pole Moog Ladder & 2-Pole SVF Filters (`ZdfFilter.hpp`).
