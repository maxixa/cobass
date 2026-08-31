# Cobass — Low-Latency Modular Android DAW

**Cobass** (`com.maxica.cobass`) is a professional, Cubase-inspired digital audio workstation for Android built entirely with **C++20**, **AAudio**, and a **pure standalone CLI toolchain (No Gradle)**.

---

## Key Architecture & Features

- **No-Gradle Architecture**: Fully deterministic builds powered by Python orchestrators, `aapt2`, `d8`, `javac`, and `clang++`.
- **Low-Latency Engine**: Native C++20 real-time rendering loop with lock-free Single-Producer Single-Consumer (SPSC) ring buffers.
- **Modular Plugin Engine**: Extensible C-ABI plugin host supporting polyphonic synths, dynamic insert FX, and runtime APK sideloading.
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

The signed output APK will be generated at `out/apk/Cobass-release.apk`.

---

## License & Policy

Built under the strict **No-Gradle Architecture Policy** (`NO_GRADLE_POLICY.md`). All rights reserved.
