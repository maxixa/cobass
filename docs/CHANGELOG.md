# Cobass DAW — Official Changelog & Release Notes

All notable changes to the **Cobass** digital audio workstation project are documented in this file.

---

## [2.0.0] — 2026-08-31

### 🎹 Synthesizer Subsystem v2.0 (Flagship Upgrade)

#### Phase 1: Bandlimited Oscillator Engine & PolyBLEP Suite
- **Anti-Aliasing PolyBLEP Implementation (`PolyBlepOscillator.hpp`)**: Eliminates harsh aliasing foldover across high octaves on Sawtooth, Pulse/Square, and Triangle waveforms using continuous polynomial step correction.
- **Dual-Oscillator + Sub-Oscillator Topology**: Integrated punchy sub-oscillator (-1 octave square) and variable Pulse Width Modulation (PWM: 5% to 95%).
- **Anti-Aliased Modular Addons**: Integrated PolyBLEP waveform rendering across all native plugin addons.

#### Phase 2: Zero-Delay Feedback (ZDF) Filter Modeling
- **ZDF State Variable & 4-Pole Moog Ladder Suite (`ZdfFilter.hpp`)**: Resolved Nyquist frequency cramping using trapezoidal integrators with non-linear feedback saturation.
- **Multi-Mode Filter Topologies**: Added 4-pole Moog Ladder (24dB/oct), 2-pole SVF Lowpass (12dB/oct), Bandpass (12dB/oct), Highpass (12dB/oct), and Band-Reject Notch.
- **Input & Feedback Saturation Drive**: Added non-linear tanh(x) saturation drive stage for harmonic analog warmth.

#### Phase 3: Dual Exponential ADSR Envelopes & Modulation Engine
- **Analog Capacitor Exponential Curves (`ADSR.hpp`)**: Upgraded linear envelopes to authentic analog RC charging curves with smooth soft-knees.
- **Multi-Waveform LFO Engine (`LFO.hpp`)**: Added multi-shape LFO (Sine, Triangle, Sawtooth, Square, Sample & Hold) with free-rate (0.05 Hz to 30 Hz) and DAW BPM tempo-sync divisions.
- **Dynamic Filter & Pitch Modulation Routing**: Integrated real-time cutoff envelope modulation, LFO vibrato, and filter sweeps into `SynthVoice.hpp` and `SynthTrack.hpp`.

#### Phase 4: Voice Allocation & Portamento Glide Engine
- **Intelligent Voice Stealing Manager**: Prioritizes idle voices, falling back to lowest-energy envelope voices with soft anti-click transitions.
- **Monophonic & Legato Note Stack**: Automatic previous-note retriggering and legato pitch-slewing upon key release.
- **Continuous Exponential Portamento**: Slew-rate frequency glide with adjustable portamento times (0 to 500 ms).

#### Phase 5: Flagship Hyperion Synth v2 & Factory Preset Bank
- **Hyperion v2 Modular Plugin (`HyperionSynthPlugin.cpp`)**: 7-Voice Supersaw Unison with stereo pan spread, cross-frequency modulation (Cross-FM), hard sync, and dual ZDF filters.
- **Curated Factory Preset Bank (`config/presets/`)**:
  - `808_Deep_Sub_Bass.cobasspatch`
  - `Moog_Acid_Resonance_Lead.cobasspatch`
  - `Lush_Supersaw_Pad.cobasspatch`
  - `Dream_Keys_Chime.cobasspatch`
  - `Cyberpunk_Reese_Bass.cobasspatch`

#### Phase 6: Interactive 60 FPS Visualizer UI Suite
- **`SynthVisualizerView.java` HUD**: Real-time 60 FPS oscilloscope with glowing waveforms, logarithmic filter magnitude response curve, and exponential ADSR Bézier envelope plots.
- **Touch-Interactive Display Modes**: Tap to cycle between Combined Multi-HUD, Live Oscilloscope, Filter Curve, and ADSR Graph.
- **Two-Way Parameter Sync (`PluginUiDialog.java`)**: Live visual feedback when tweaking Cutoff, Resonance, Attack, Decay, Sustain, and Release knobs.

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
