# Cobass — Low-Latency Modular Android DAW

**Cobass** (`com.maxica.cobass`) is a professional, Cubase-inspired digital audio workstation for Android built with **C++20**, **AAudio**, and a **pure standalone CLI toolchain (No Gradle)**.

---

## Key Architecture & Features

- **No-Gradle Architecture**: Fully deterministic builds powered by Python orchestrators, `aapt2`, `d8`, `javac`, and `clang++`.
- **Low-Latency AAudio Engine**: Real-time rendering loop with lock-free Single-Producer Single-Consumer (SPSC) ring buffers.
- **Modular Plugin Engine**: Extensible C-ABI plugin host supporting polyphonic synths, drum machines, and dynamic insert FX:
  - **Hyperion Dance Synth v3**: 12 dance wavetables, independent dual-unison (up to 16 voices per voice), attack punch pitch envelope, TB-303 18dB diode acid ladder, 3-peak formant vowel filter, and 5-stage internal dance studio FX rack.
  - **Cobalt Drum Machine Synth**: 8 dedicated analog/FM physical modeling voice engines with choke groups and master drive.
  - **OTT Multiband Dynamics**: 3-band upward/downward compressor with 4th-order Linkwitz-Riley crossovers.
  - **Sidechain Envelope Pumper**: Tempo-synced ducking curves with low-band frequency-split filtering.
  - **Analog Wavefolder & Crusher**: West-Coast trigonometric wavefolding with asymmetric bias and sample decimation.
  - **Vintage Analog Chorus & Tape Saturator**: Stereo BBD analog chorus and tube/tape saturation.
- **Intelligent Variation Randomizer Engine**:
  - Controlled Gaussian parameter variation from **Light** ($5\%$) to **Extreme** ($85\%$).
  - Sectional module lock masks (`[🔒 Oscillators]`, `[🔒 Filter]`, `[🔒 Envelopes]`, `[🔒 LFO]`, `[🔒 FX]`, `[🔒 Master]`).
  - Harmonic consonancy snapping and headroom auto-gain staging protection.
  - Polymetric Step Sequencer groove variation generator with velocity, sub-ratchet ($1\times\dots 8\times$), and nudge mutation.
- **60-Preset Sound Library**: 30 Hyperion dance patches, 12 Cobalt drum kits, and 18 insert FX presets.
- **Polymetric Step Sequencer**: 64-step matrix with sub-step ratchets, micro-timing nudges, Euclidean generator, and 1-tap pattern baking to arranger clips.
- **Cubase-Style Arranger**: Dual-axis zoom, multi-clip marquee selection, track reparenting, slip editing, dual-edge trimming, and transactional undo/redo.
- **Studio MIDI Piano Roll**: Scale-fold keybed, scale-snap intelligence, chord stamper presets, velocity automation, and note chop/slice tools.
- **Non-Destructive Wave Editor**: Mipmapped peak caches, spectral flux transient detection, zero-crossing snapping, WSOLA time-stretching, and pitch shifting.
- **Mixing Console**: 32-track bus mixer, 4-band parametric EQ, studio compressor, algorithmic reverb, stereo delay, and master brickwall limiter.

---

## Prerequisites

- **Python 3.10+**
- **Android SDK** (API Level 34 Platform, Build-Tools 34.0.0+)
- **Android NDK** (Clang with C++20 support) or **Termux Native Clang** on Android
- Java Development Kit (**JDK 17**)

Check your environment anytime with:
```bash
python3 tools/doctor.py
```

---

## Build Instructions

### Quick Incremental Build:
```bash
./build.sh
```

### Full Clean & Dependency Resolution Build:
```bash
./buildfull.sh
```

The signed release APK will be generated at `out/apk/Cobass-release.apk`.

---

## License & Policy

Built under the strict **No-Gradle Architecture Policy** (`NO_GRADLE_POLICY.md`). All rights reserved.
