# Comprehensive Architectural Enhancement Plan: Wave Editor Subsystem

---

# 1. Executive Summary & Architectural Gap Analysis

The current **Cobass Wave Editor** (`WaveEditorCanvasView.java` & `WaveEditorDialog.java`) provides basic destructive operations (in-place reverse, normalization, $\pm3\text{ dB}$ gain steps, and basic linear trim handles). To elevate it to the level of mobile DAW sample editors like Cubase AudioWarp, FL Studio Edison, and Logic Pro Audio Editor, this enhancement plan establishes a responsive, non-destructive, high-performance audio editing suite.

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                            CURRENT VS TARGET DAW MATRIX                          │
├─────────────────────────┬────────────────────────────┬───────────────────────────┤
│ Capability              │ Current State              │ Target Production State   │
├─────────────────────────┼────────────────────────────┼───────────────────────────┤
│ Viewport & Touch Nav    │ Static single view, no     │ 2D Pinch-to-Zoom, inertia │
│                         │ zoom, no timeline ruler    │ pan, zero-latency playhead│
├─────────────────────────┼────────────────────────────┼───────────────────────────┤
│ Waveform Rendering      │ Linear subsampling         │ Multi-tier Mipmapped Peak │
│                         │ (flickers on large files)  │ Cache (Min/Max/RMS peaks) │
├─────────────────────────┼────────────────────────────┼───────────────────────────┤
│ Editing & Selection     │ Global start/end trim only │ Box region selection,     │
│                         │                            │ non-destructive crop, cut │
├─────────────────────────┼────────────────────────────┼───────────────────────────┤
│ Dynamics & Envelopes    │ None                       │ Interactive Fade In / Out │
│                         │                            │ Bézier curves & DC filter │
├─────────────────────────┼────────────────────────────┼───────────────────────────┤
│ Transient & Slicing     │ None                       │ Spectral flux transient   │
│                         │                            │ detector & slice export   │
├─────────────────────────┼────────────────────────────┼───────────────────────────┤
│ Pitch / Time Processing │ None (fixed playback rate) │ High-quality Resampling,  │
│                         │                            │ Pitch Shift (±24 st), FX  │
├─────────────────────────┼────────────────────────────┼───────────────────────────┤
│ History & I/O           │ None (permanent mutation)  │ 50-step Deep Undo/Redo &  │
│                         │                            │ SAF WAV/MP3/FLAC importer │
└─────────────────────────┴────────────────────────────┴───────────────────────────┘
```

---

# 2. Complete Wave Editor Feature Roadmap

```
+-----------------------------------------------------------------------------------+
|                        WAVE EDITOR ENHANCEMENT ROADMAP                            |
+-----------------------------------------------------------------------------------+
|  PHASE 1: Viewport, Mipmapped Peak Rendering & In-Dialog Transport Playhead       |
|  PHASE 2: Non-Destructive Trimming, Fade Curves & Zero-Crossing Snap              |
|  PHASE 3: Region Selection, Clipboard & Destructive/Non-Destructive DSP Toolkit   |
|  PHASE 4: Transient Detection, Dynamic Beat Slicing & Auto-Slice to Arranger      |
|  PHASE 5: Time Stretch, Pitch Transpose Engine & Tape Stop / Scratch FX           |
|  PHASE 6: External Audio File Importer / Mic Recording & Local Undo/Redo Stack   |
+-----------------------------------------------------------------------------------+
```

---

## Phase 1: Viewport, Mipmapped Peak Rendering & In-Dialog Transport

### 1.1 Multi-Level Mipmapped Peak Cache (`WaveformPeakCache`)
* **Problem:** Rendering large multi-megabyte audio files linearly on every `onDraw()` frame causes UI thread stutter and battery drain.
* **Solution:** Generate a pyramid mipmap cache containing pairs of `[MinSample, MaxSample, RmsPower]` at levels $\times 1$, $\times 8$, $\times 64$, $\times 512$, and $\times 4096$:
  $$\text{RMS} = \sqrt{\frac{1}{N}\sum_{i=0}^{N-1} x[i]^2}$$
  - Zooming out renders from $\times 512$ or $\times 4096$ peak caches in $O(1)$ constant time.
  - Zooming into individual sample points ($\times 1$) renders distinct sample dots connected with cubic Bézier interpolation.

### 1.2 2D Multi-Touch Zoom & Inertia Panning
* Independent horizontal time scale (`pixelsPerSample` from $0.0001\text{ px}$ to $50.0\text{ px/sample}$).
* Two-finger pinch-to-zoom centered precisely at the touch focal point.
* Single-finger swipe in **Navigate Mode** with momentum damping.

### 1.3 In-Dialog Real-Time Transport & Scrubbing
* Synchronized **▶ Play**, **⏸ Pause**, and **🔁 Loop Region** buttons directly in the top action bar of `WaveEditorDialog`.
* Moving red needle cursor linked directly to `AudioEngineNative.nativeGetTrackPlaybackPosition()`.
* **Audio Scrubbing:** Touching and dragging the playhead plays micro-loops ($20\text{--}50\text{ ms}$) in real time to locate precise zero-crossings or clicks.

---

## Phase 2: Trimming, Fade Curves & Zero-Crossing Snap Engine

```
       Fade-In Handle [◢]                            Fade-Out Handle [◣]
       +---------------------------------------------------------------+
       | /~~~~~~\                                             /~~~~~~\ |
0 dB   |/        \               WAVEFORM BODY               /        \|
       |          \_________________________________________/          |
       +---------------------------------------------------------------+
     [ |< ] Trim Start                                   Trim End [ >| ]
```

### 2.1 Dual-Handle Fade Envelopes (Fade-In / Fade-Out)
* Interactive top handles on the canvas for **Fade-In** (left) and **Fade-Out** (right).
* Dynamic curve interpolation selection:
  1. **Linear:** $y(t) = t$
  2. **Equal Power ($3\text{ dB}$ curve):** $y(t) = \sin\left(\frac{\pi}{2} t\right)$
  3. **Logarithmic / S-Curve:** $y(t) = \frac{1}{1 + e^{-k(t - 0.5)}}$

### 2.2 Snap-to-Zero Crossing Algorithm
* Prevents clicks and DC pops when slicing or trimming.
* When moving trim or slice handles, the engine searches within a $\pm 128\text{ sample}$ window for the nearest zero-crossing point where:
  $$x[i] \le 0 \quad \text{and} \quad x[i+1] > 0 \quad \text{or} \quad |x[i]| \to \min$$

---

## Phase 3: Region Selection & Advanced DSP Toolkit

```
+-------------------------------------------------------------------------------+
|  TOOLS: [🔲 Select] [✂️ Slice] [🔍 Zoom] | GAIN: [Norm 0dB] [-1dB] [Gain ±1dB]  |
|  DSP:   [Reverse] [Invert Ø] [Silence] [DC Filter] [Fade In/Out] [Stereo Split]|
+-------------------------------------------------------------------------------+
```

### 3.1 Advanced Region Selection Box
* Drag across the waveform in **Select Mode** to highlight a specific time selection region $[T_{\text{start}}, T_{\text{end}}]$.
* Quick Context Actions:
  - **Crop:** Discards everything outside the selection and anchors the new boundaries.
  - **Cut / Copy / Paste:** Extract audio fragments into an in-memory sample clipboard.
  - **Silence (Mute Region):** Zeroes out the selected region ($x[i] = 0$) with a $2\text{ ms}$ anti-click crossfade.

### 3.2 Offline Processing Suite

| DSP Algorithm | Formula / Operation | Practical Benefit |
| :--- | :--- | :--- |
| **Peak / RMS Normalize** | $x_{\text{new}}[i] = x[i] \cdot \frac{\text{TargetPeak}}{\max(|x|)}$ (Targets: $0\text{ dB}$, $-1\text{ dB}$, $-3\text{ dB}$, RMS $-14\text{ LUFS}$) | Consistent track gain staging |
| **Phase Invert ($\varnothing$)** | $x_{\text{new}}[i] = -1.0 \cdot x[i]$ | Resolves phase cancellation with live kicks/snares |
| **DC Offset Removal** | $y[i] = x[i] - x[i-1] + 0.995 \cdot y[i-1]$ ($20\text{ Hz}$ high-pass filter) | Restores headroom consumed by hardware bias |
| **Stereo to Mono / Split** | $M = 0.5 \cdot (L + R)$, $S = 0.5 \cdot (L - R)$ | Clean sub-bass mono collapse & stereo widening |

---

## Phase 4: Transient Detection & Slicing Engine

```
Audio:      |/\  |/\    |/\        |/\  |/\    |/\        |/\
            |  \/|  \  /|  \      /|  \/|  \  /|  \      /|  \
Flux:       ▲    ▲     ▲          ▲    ▲     ▲          ▲
Transients: [T1] [T2]  [T3]       [T4] [T5]  [T6]       [T7]
```

### 4.1 Real-Time Spectral Flux Transient Detection
* Break audio into overlapping FFT frames ($256\text{ samples}$ / $5.3\text{ ms}$ @ $48\text{ kHz}$).
* Compute high-frequency content (HFC) or spectral flux:
  $$\text{Flux}(t) = \sum_{k=0}^{K-1} H\big(|X(t, k)| - |X(t-1, k)|\big), \quad H(x) = \frac{x + |x|}{2}$$
* Peaks in the flux curve exceeding the **Sensitivity Threshold Slider** spawn draggable **Slice Markers** (vertical dashed yellow lines).

### 4.2 Slicing Output Destinations
1. **Slice to Arranger Track:** Slices the single audio clip into separate adjacent `ClipItem`s on the arranger timeline, snapped precisely to grid beats.
2. **Export Slices as Files:** Saves each sliced drum hit as `sample_01.wav`, `sample_02.wav`, etc., to the device storage.

---

## Phase 5: Time Stretching, Pitch Transposing & Tape FX

### 5.1 Real-Time Pitch & Formant Transpose (Native DSP)
* **Repitch Mode (Tape Style):** Alters pitch and speed simultaneously via cubic Hermite / Lagrange sample interpolation:
  $$\text{Pitch Ratio} = 2^{\frac{\Delta\text{Semitones}}{12}}$$
* **Pitch Shift with Length Preservation:** Granular WSOLA (Waveform Similarity Overlap-Add) engine to transpose pitch $\pm 24\text{ semitones}$ while keeping timing locked to project BPM.

### 5.2 Classic Tape Stop & Vinyl Scratch FX
* Generates an exponential deceleration curve from $1.0\times$ speed down to $0.0\times$ over a selectable duration ($1/4\text{ bar}$ to $2\text{ bars}$) to emulate a hardware turntable brake.

---

## Phase 6: I/O, Storage Access Framework & Undo/Redo Engine

### 6.1 Transactional Deep Undo/Redo Stack
* Lightweight history manager (`WaveHistoryManager`):
  - Stores memory-efficient differential PCM chunks or circular snapshots up to 30 steps.
  - Dedicated **↶ Undo** and **↷ Redo** buttons on the top toolbar.

### 6.2 Storage Access Framework (SAF) Audio Importer & Microphone Recorder
* **Direct Import:** Native Android SAF file picker supporting `.wav`, `.mp3`, `.flac`, `.ogg`, `.aac`, and `.m4a` with automated decoding via `android.media.MediaExtractor` + `MediaCodec` into 32-bit float PCM.
* **In-Dialog Mic Record:** Record live audio directly into the active clip with automatic latency compensation.

---

# 3. Target UI Wireframe & Layout Blueprint

### `dialog_wave_editor.xml`

```
+---------------------------------------------------------------------------------------------------+
| [✕ Close]  Wave: 808 Kick 01   | [▶ Play] [■ Stop] [🔁 Loop] | [↶ Undo] [↷ Redo] | [💾 Save / Export] |
+---------------------------------------------------------------------------------------------------+
| TOOLS: [🔲 Select] [✂️ Slice] [🔍 Zoom] [⇄ Slip] | DSP: [⚡ GAIN ▾] [FADE ▾] [TRANSPOSE ▾] [TRANSIENT ▾] |
+---------------------------------------------------------------------------------------------------+
| TIME RULER (Samples / Seconds / Bars.Beats)                                      [Zoom: 100%] [1:1] |
| 00:00.000             00:00.500             00:01.000             00:01.500             00:02.000 |
+---------------------------------------------------------------------------------------------------+
|  Fade-In [◢]                                                                     Fade-Out [◣]     |
| +-----------------------------------------------------------------------------------------------+ |
| |       /\           |                   /\                          |            /\            | |
| |      /  \          |  SELECTED REGION /  \                         |           /  \           | |
| |-----/----\---------|=================/----\========================|----------/----\----------| |
| |    /      \        |  (Blue Shade)  /      \                       |         /      \         | |
| |   /        \       |               /        \                      |        /        \        | |
| +-----------------------------------------------------------------------------------------------+ |
|  [ |< ] Trim Start   [T1] Slice Marker   [T2] Slice Marker   [Playhead Needle | ] Trim End [ >| ] |
+---------------------------------------------------------------------------------------------------+
| BOTTOM STATUS & INFO BAR                                                                          |
| [Length: 2.000s (96,000 spls)] [Peak: -0.2 dBFS] [RMS: -14.2 dB] [Selection: 480ms] [Snap: Zero Ø]|
+---------------------------------------------------------------------------------------------------+
```

---

# 4. Data Model & Architecture Extensions

### 4.1 Audio Sample Data Container (`ClipItem.java`)

```java
public class ClipItem {
    // PCM float buffer (normalized -1.0f to 1.0f)
    private float[] sampleData = null;
    private int sampleRate = 48000;
    private int channels = 1;

    // Non-destructive playback envelope bounds
    private float trimStartFraction = 0.0f;  // 0.0 to 1.0
    private float trimEndFraction = 1.0f;    // 0.0 to 1.0
    private float fadeInDurationSec = 0.0f;
    private float fadeOutDurationSec = 0.0f;
    private int fadeCurveType = 1; // 0=Linear, 1=Equal Power, 2=Logarithmic

    // Transient slices
    private final List<Long> sliceSampleOffsets = new ArrayList<>();

    public float[] getSampleData() { return sampleData; }
    public void setSampleData(float[] data) { this.sampleData = data; }
    
    public int getEffectiveStartSample() {
        return sampleData != null ? (int)(trimStartFraction * (sampleData.length / channels)) : 0;
    }
    public int getEffectiveEndSample() {
        return sampleData != null ? (int)(trimEndFraction * (sampleData.length / channels)) : 0;
    }
}
```

### 4.2 Multi-Scale Waveform Mipmap Renderer (`WaveformMipmap.hpp` / Java equivalent)

```java
public static class WaveformMipmap {
    public float[] minPeaks;
    public float[] maxPeaks;
    public float[] rmsPeaks;
    public int downsampleFactor; // 1, 8, 64, 512, 4096

    public static WaveformMipmap build(float[] source, int factor) {
        int outLen = (source.length + factor - 1) / factor;
        WaveformMipmap m = new WaveformMipmap();
        m.minPeaks = new float[outLen];
        m.maxPeaks = new float[outLen];
        m.rmsPeaks = new float[outLen];
        m.downsampleFactor = factor;

        for (int i = 0; i < outLen; i++) {
            int start = i * factor;
            int end = Math.min(source.length, start + factor);
            float minVal = 1.0f, maxVal = -1.0f, sumSq = 0.0f;
            for (int s = start; s < end; s++) {
                float v = source[s];
                if (v < minVal) minVal = v;
                if (v > maxVal) maxVal = v;
                sumSq += v * v;
            }
            m.minPeaks[i] = minVal;
            m.maxPeaks[i] = maxVal;
            m.rmsPeaks[i] = (float) Math.sqrt(sumSq / (end - start));
        }
        return m;
    }
}
```

### 4.3 Native Audio Track Dynamic Sample Streaming (`AudioTrack.hpp`)

Enhance the C++ `AudioTrack` playback loop to apply trim points, pitch shift, and fade envelopes with sub-sample cubic interpolation:

```cpp
void render(float* outStereoBuffer, int32_t numFrames) override {
    if (isMuted_ || !isPlaying_ || sampleData_.empty()) return;

    const size_t startFrame = static_cast<size_t>(trimStartRatio_ * (sampleData_.size() / channels_));
    const size_t endFrame   = static_cast<size_t>(trimEndRatio_ * (sampleData_.size() / channels_));
    const size_t totalPlayFrames = (endFrame > startFrame) ? (endFrame - startFrame) : 0;

    for (int32_t i = 0; i < numFrames; ++i) {
        if (playbackIndex_ >= totalPlayFrames) {
            if (isLooping_) {
                playbackIndex_ = 0.0;
            } else {
                isPlaying_ = false;
                break;
            }
        }

        const size_t currentFrame = startFrame + static_cast<size_t>(playbackIndex_);
        
        // Compute Fade In / Fade Out Envelope Gain
        float envGain = calculateFadeGain(currentFrame - startFrame, totalPlayFrames);

        float sL = sampleData_[currentFrame * channels_] * envGain;
        float sR = (channels_ > 1) ? (sampleData_[currentFrame * channels_ + 1] * envGain) : sL;

        tempBuffer_[i * 2]     = sL;
        tempBuffer_[i * 2 + 1] = sR;
        playbackIndex_ += playbackPitch_;
    }

    applyFxAndGain(tempBuffer_.data(), numFrames);
    for (int32_t i = 0; i < numFrames * 2; ++i) {
        outStereoBuffer[i] += tempBuffer_[i];
    }
}
```

---

# 5. Implementation Priority Matrix

| Phase | Milestone | Complexity | Impact |
| :--- | :--- | :--- | :--- |
| **P1** | **2D Pinch Zoom + Mipmapped Peak Rendering + Transport Playhead** | Medium | 🔥 High (Essential navigation & smooth rendering) |
| **P2** | **Undo/Redo History Stack + Region Selection (Crop / Silence / Cut)** | Medium | 🔥 High (Core editing usability) |
| **P3** | **Interactive Fade In / Fade Out Bézier Handles & Snap-to-Zero Crossing** | Medium | ⚡ High (Click-free loop & boundary editing) |
| **P4** | **Transient Detection Studio & Slicing to Arranger Clips** | High | 🥁 High (Drum loop chopping & sampling) |
| **P5** | **Storage Access Framework Audio File Importer (`.wav`, `.mp3`, `.flac`)** | Medium | 🎵 High (External audio import) |
| **P6** | **WSOLA Time-Stretching, Pitch Shifter ($\pm 24\text{ st}$) & Tape Stop FX** | High | ✨ Medium (Creative sound design) |