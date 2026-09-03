# Architecture & Specification: Musically Intelligent C++ Note Transformation Subsystem

---

## 1. Executive Summary & Musical Philosophy

The **Cobass C++ Note Transformation Subsystem** (`NoteTransformEngine`) is an algorithmic, musically constrained MIDI transformation module designed to convert simple drawn melodies or chord progressions into rich, expressive, and genre-authentic musical variations.

Unlike naive randomizers that produce dissonant jumps and chaotic timing, this engine applies **formal music theory constraints, voice-leading rules, metric weight hierarchies, and Markovian motion** to ensure every mutation is strictly musical and aesthetically pleasing.

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                        COBASS NOTE TRANSFORMATION PIPELINE                             │
└────────────────────────────────────────────────────────────────────────────────────────┘
                                           │
  [Step 1: Input Melody / Line]            │  e.g., Simple sustained root notes or 4-bar lead
                                           ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                                 TRANSFORM STUDIO DIALOG                                │
│                                                                                        │
│  [Source Snapshot] ──▶ [Musical Context Guard] ──▶ [Transformation Pass] ──▶ [Audition]│
│                              │                           │                             │
│                      • Key & Scale Snap          • Rhythmic Chopper                    │
│                      • Metric Accent Bias        • Markov Melodic Walk                 │
│                      • Voice Leading Limits      • Harmonic Voicing                    │
│                      • Chord Tonal Weighting     • Arpeggiator / Ornaments             │
└────────────────────────────────────────────────┬───────────────────────────────────────┘
                                                 │ Stackable / Seed Variations
                                                 ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                 DUAL INTEGRATION: PIANO ROLL & ARRANGER TIMELINE                       │
│                                                                                        │
│  • Piano Roll: Micro-level transform on note selections or clip body.                  │
│  • Arranger:   Macro-level transform across multiple clips or whole track arrangements.│
│  • Morph Slider (0% Original ──────────────▶ 100% Transformed) with Instant Rollback.  │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Mathematical & Music Theory Foundations

To guarantee that generated variations sound cohesive, the C++ engine enforces four constraint layers:

### 2.1 Scale & Degree Tonal Hierarchy
Every pitch operation is bounded by the active musical key $(K)$ and scale mode $(S)$. Scale degrees are assigned probability weights based on harmonic stability:
* **Tonic ($1\text{st}$) & Dominant ($5\text{th}$)**: Highest harmonic weight ($40\%$) — prioritized on downbeats (Beats 1 & 3).
* **Mediant ($3\text{rd}$) & Subdominant ($4\text{th}$)**: Intermediate weight ($35\%$) — establishes major/minor flavor.
* **Tension Tones ($2\text{nd}, 6\text{th}, 7\text{th}$)**: Passing notes ($25\%$) — preferred on weak beats/offbeats.
* **Non-Diatonic chromatic tones**: Strictly redirected to the nearest consonant diatonic interval unless "Color Notes / Blue Notes" mode is enabled.

$$\text{Weight}(d) = \begin{cases} 
1.0 & \text{if } d \in \{1, 5\} \text{ on strong metric beats} \\
0.75 & \text{if } d \in \{3, 4, 6\} \\
0.40 & \text{if } d \in \{2, 7\} \text{ (passing)} \\
0.0 & \text{if } d \notin S \text{ (unless explicitly allowed)}
\end{cases}$$

### 2.2 Voice-Leading & Stepwise Proximity
When generating pitch variation, the engine penalizes wide leaps:
* **Stepwise motion ($\pm 1 \text{ or } \pm 2$ scale steps)**: $70\%$ probability (fluent melodic flow).
* **Small skips ($\pm 3\text{rd} \text{ or } \pm 4\text{th}$)**: $20\%$ probability.
* **Octave / Perfect 5th leaps**: $10\%$ probability, with an enforced automatic direction change on the subsequent note to resolve tension.

### 2.3 Metric Hierarchy (Metric Grid Weighting)
Timing subdivisions are evaluated against standard 4/4 or 3/4 bar structures:
* **Strong Beats (1.1, 1.3)**: High velocity ($85\text{--}110$), longer gate lengths ($75\%\text{--}95\%$).
* **Weak Beats (1.2, 1.4)**: Moderate velocity ($70\text{--}90$).
* **Offbeats (1.1.2, 1.1.4)**: Lower velocity ($50\text{--}75$), syncopated accents, ghost hits.

### 2.4 Controlled Stochasticity (Reproducible Seeded PRNG)
Every transform pass accepts an explicit `uint32_t seed`. Changing the seed provides infinite musical alternatives; resetting or dialing down the **Morph Slider** seamlessly interpolates back to the exact initial notes.

---

## 3. Transformation Tool Suite

The engine provides 8 specialized algorithmic modules:

| Tool Mode | Musical Function | How It Works Under the Hood |
| :--- | :--- | :--- |
| **1. 🎲 Markov Melodic Wander** | Evolves a simple sustained note into an evolving lead/bassline. | Traverses scale degrees using a 2nd-order Markov chain weighted by pitch proximity and metric accents. |
| **2. ✂️ Rhythmic Dice & Slicer** | Slices long sustained notes into grooving rhythms (Trap, House, Drill, Funk). | Subdivides note durations using Euclidean pulses ($K/N$) or rhythmic groove masks while preserving overall note bounds. |
| **3. 🔄 Motific Mirror & Inversion** | Classic Bach / Neo-classical motif development. | Performs pitch axis reflection (Melodic Inversion), horizontal time reversal (Retrograde), or Retrograde-Inversion within the active scale. |
| **4. 🌊 Euclidean Density Fill** | Injects algorithmic groove patterns and ghost notes into gaps. | Calculates optimal mathematical spacing of pulses across empty beats without overlapping existing downbeat notes. |
| **5. 🎸 Smart Harmonizer & Voicing** | Converts single melody lines into 2-part harmonies, triads, or lush 7th chords. | Adds parallel diatonic 3rds, 6ths, or 4-note chord voicings (Drop-2 / Drop-3) strictly mapped to the project scale. |
| **6. ✨ Rhythmic Ornamentation** | Adds human flair, expression, and speed rolls. | Injects grace notes, trills, mordents, guitar strums, and ratchet rolls ($2\times, 3\times, 4\times$) on phrase endings or pickup beats. |
| **7. ⏳ Time Warp & Metric Phasing** | Alters phrase pacing and rhythmic feel. | Applies Augmentation ($2\times$), Diminution ($0.5\times$), Triplet Compression, or Golden Ratio rhythmic deceleration. |
| **8. 🎚️ Dynamic Velocity & Groove Staging** | Brings mechanical notes to life. | Applies parabolic crescendos, decrescendos, human Gaussian jitter ($\pm 8\text{ ticks}, \pm 12\text{ vel}$), and analog swing groove templates. |

---

## 4. Architectural Design & Native C++20 Core

```
native/
└── sequencer/
    ├── NoteTransformEngine.hpp    <-- Pure C++20 transformation orchestrator
    ├── ScaleDefinitions.hpp       <-- Microtonal & 24+ standard scale bitmasks
    ├── VoiceLeadingRules.hpp      <-- Interval cost functions & harmonic resolution
    └── RhythmGrooveTemplates.hpp  <-- Metric grids & Euclidean generators
```

### 4.1 Native Data Structures

```cpp
// Core representation of a musical note event
struct TransformNote {
    int32_t pitch;              // MIDI pitch 0-127
    float velocity;             // Normalized velocity (0.05f to 1.0f)
    int64_t startOffsetTicks;   // Position relative to clip start
    int64_t lengthTicks;        // Note duration in ticks
    bool isSelected;            // Selection flag
    bool isMuted;               // Mute flag
};

// Scale & Harmonic Context
struct MusicalContext {
    int32_t rootKey = 0;        // 0 = C, 1 = C#, ... 11 = B
    int32_t scaleType = 0;      // 0=Chromatic, 1=Major, 2=Minor, 3=Dorian, etc.
    int32_t timeSigNumerator = 4;
    int32_t timeSigDenominator = 4;
    int32_t ticksPerBeat = 480; // PPQ
};

// Transformation Configuration
struct TransformOptions {
    uint32_t toolMode;          // Wander, Slice, Mirror, Ornament, Harmonize, etc.
    float intensity;            // 0.0f (Light) to 1.0f (Extreme)
    float morphBlend;           // 0.0f (Original) to 1.0f (Transformed)
    uint32_t seed;              // Random seed for reproducibility
    bool lockDownbeats;         // Keep beat 1 and beat 3 anchor pitches intact
    bool lockDurations;         // Keep note start/end boundaries unchanged
    bool snapToScale;           // Force all modified pitches to scale degrees
    int32_t targetSubdivision;  // 120 (1/16), 240 (1/8), 160 (1/8T), etc.
    float swingPercent;         // 0% to 75%
    int32_t harmonyInterval;    // +3rd, +4th, +5th, +6th, Octave, Triad, 7th
};
```

### 4.2 Core Transformation Engine Class (`NoteTransformEngine.hpp`)

```cpp
class NoteTransformEngine {
public:
    // Pure function: transforms input notes without side effects (Thread-safe, Zero allocation)
    static std::vector<TransformNote> transform(
        const std::vector<TransformNote>& inputNotes,
        const MusicalContext& context,
        const TransformOptions& options
    );

    // Specific algorithmic transforms
    static void applyMelodicWander(std::vector<TransformNote>& notes, const MusicalContext& ctx, const TransformOptions& opt);
    static void applyRhythmicSlice(std::vector<TransformNote>& notes, const MusicalContext& ctx, const TransformOptions& opt);
    static void applyMotificMirror(std::vector<TransformNote>& notes, const MusicalContext& ctx, const TransformOptions& opt);
    static void applyEuclideanFill(std::vector<TransformNote>& notes, const MusicalContext& ctx, const TransformOptions& opt);
    static void applyHarmonizer(std::vector<TransformNote>& notes, const MusicalContext& ctx, const TransformOptions& opt);
    static void applyOrnamentation(std::vector<TransformNote>& notes, const MusicalContext& ctx, const TransformOptions& opt);
    static void applyGrooveAndHumanize(std::vector<TransformNote>& notes, const MusicalContext& ctx, const TransformOptions& opt);

    // Linear interpolation between Original and Transformed state
    static std::vector<TransformNote> blendNotes(
        const std::vector<TransformNote>& original,
        const std::vector<TransformNote>& transformed,
        float morphBlend
    );
};
```

---

## 5. Non-Destructive Morphing & Stackable Pipeline

To enable non-destructive experimentation, the engine maintains an immutable **Original Reference State** and computes a real-time **Transformed State**:

```
[Original Notes] ───┐
                    ├───▶ [Blend / Morph Engine (0%..100%)] ───▶ [Audition / Viewport]
[Transformed]    ───┘                      │
                                           ├── At 0%:   Pure original note line
                                           ├── At 50%:  Subtle melodic variation, gentle groove
                                           └── At 100%: Full algorithmic reimagining
```

* **Stackable Operations**: The user can commit a transform pass (which pushes a deep snapshot to `PianoRollHistoryManager` / `ArrangerHistoryManager`), then immediately apply a secondary transform (e.g., *Rhythmic Slice* $\to$ *Harmonize in 3rds* $\to$ *Humanize*).
* **Rollback & A/B Comparison**: A single tap on `[A / B]` or `[↶ Reset]` instantly restores the pre-transform clip without degrading project state.

---

## 6. UI & Interaction Design

### 6.1 Unified `TransformStudioDialog` Wireframe

```
+---------------------------------------------------------------------------------------------------+
| ⚡ MUSICAL NOTE TRANSFORMATION STUDIO                                                    [✕ CLOSE] |
+---------------------------------------------------------------------------------------------------+
| TARGET: [● Selected Notes (8)]  [○ Entire Clip Body] | SCALE: [🎹 D Minor (Aeolian)] [🔒 LOCK SCALE]|
+---------------------------------------------------------------------------------------------------+
| SELECT ALGORITHM:                                                                                 |
| [🎲 Melodic Wander]  [✂️ Rhythmic Slicer]  [🔄 Motif Mirror]  [🎸 Harmonizer]                     |
| [🌊 Euclidean Fill]  [✨ Ornamentations]  [⏳ Time Warp]     [🎚️ Humanize/Groove]                |
+---------------------------------------------------------------------------------------------------+
| PARAMETERS & CONSTRAINTS:                                                                         |
|                                                                                                   |
| Variation Intensity: Medium (40%)                                                                 |
| [---------●------------------------------------] 40%                                              |
|                                                                                                   |
| Morph / Blend (Original ➔ Transformed):                                                            |
| [----------------------------●-----------------] 75%                                              |
|                                                                                                   |
| Rhythmic Subdivision:       Harmonic Intervals:           Humanize & Swing:                       |
| [1/16 Note (Straight) ▾]    [Diatonic 3rds + 5ths ▾]      [Swing: 20%] [Jitter: ±8 ticks]         |
|                                                                                                   |
| PRESERVATION LOCKS:                                                                               |
| [✓ 🔒 Lock Strong Downbeats]  [✓ 🔒 Preserve Durations]  [✓ 🔒 Strict Scale Snap]                   |
+---------------------------------------------------------------------------------------------------+
| REAL-TIME AUDITION & ROLLBACK:                                                                    |
| [🎲 NEW SEED] [A / B COMPARE] [▶ PLAY AUDITION] [■ STOP]                                          |
+---------------------------------------------------------------------------------------------------+
| [ ↶ RESET TO ORIGINAL ]                             [ 💾 COMMIT & APPLY TO TIMELINE ]              |
+---------------------------------------------------------------------------------------------------+
```

### 6.2 Dual Access Points in DAW

1. **In Piano Roll Editor (`dialog_piano_roll.xml`)**:
   - Tapping the existing `[⚡ FX]` or new `[⚡ TRANSFORM]` button opens `TransformStudioDialog` scoped to the current active selection (or the entire clip if no notes are highlighted).
   - Real-time visual ghost preview of prospective notes directly on the Piano Roll grid before commit.

2. **In Arranger View (`activity_main.xml`)**:
   - Selecting one or multiple MIDI clips and tapping `[⚡ TRANSFORM]` opens `TransformStudioDialog` in **Batch Macro Mode**, allowing phrase-wide variations across multiple tracks simultaneously (e.g., generating matching basslines or rhythmic variations across all synth tracks).

---

## 7. Implementation Roadmap & Phasing

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                   NOTE TRANSFORMATION ENGINE ROADMAP                             │
└──────────────────────────────────────────────────────────────────────────────────┘
   │
   ├── PHASE 1: C++20 Core Math & Theory Library
   │     ├── Scale definitions (24 modes) & diatonic interval snapping
   │     ├── Markov chain melodic wander generator
   │     └── Metric weighting matrix (PPQ-480 downbeat/offbeat hierarchy)
   │
   ├── PHASE 2: Rhythm & Motif DSP Algorithms
   │     ├── Euclidean rhythmic chopper with velocity weighting
   │     ├── Melodic inversion, retrograde, and motific mirror reflections
   │     └── Diatonic harmonizer & Drop-2 chord voicing generator
   │
   ├── PHASE 3: Expressive Ornamentation & Humanize Engine
   │     ├── Grace notes, trills, and ratchet rolls
   │     ├── Gaussian timing jitter and dynamic velocity curves
   │     └── Continuous morph/blend interpolator (Original ↔ Transformed)
   │
   ├── PHASE 4: JNI Bridge & Native Integration
   │     ├── JNI bindings in AudioEngineNative & jni_bridge.cpp
   │     └── Transactional safety integration with Undo/Redo history stacks
   │
   └── PHASE 5: UI Studio Dialog & Dual Host Access
         ├── TransformStudioDialog with live seed generator and A/B compare
         ├── Piano Roll Editor integration & real-time canvas preview
         └── Arranger View batch clip transformation integration
```

---

## 8. Verification & Musical Quality Matrix

| Test Case | Expected Musical Outcome | Validation Metric |
| :--- | :--- | :--- |
| **Monophonic Root Line $\to$ Markov Wander** | Generates fluent lead with step-wise motion; tonic/fifth anchored on Beat 1 & 3. | $0\%$ out-of-scale notes; $\ge 70\%$ stepwise interval transitions. |
| **Sustained Chords $\to$ Rhythmic Slicer** | Sustained pad diced into 1/16th funk rhythm with accented downbeats. | Note boundaries strictly snapped to active grid; no overlapping note tails. |
| **Melodic Line $\to$ Harmonizer (3rds)** | Single melody doubled with consonant scale-degree thirds. | Correct major/minor 3rd intervals selected according to key center. |
| **Simple Motif $\to$ Inversion / Mirror** | Melodic contour flipped upside down while retaining harmonic resonance. | Pitch delta inverted exactly around key center; timing preserved. |
| **Morph Slider Sweep ($0\% \to 100\%$)** | Smooth, artifact-free musical transition between raw input and transformed output. | Note positions transition predictably without memory leaks or dropped events. |
| **Transactional Rollback** | Tapping Reset or Cancel returns notes to original unmutated state. | Byte-for-byte identity with pre-transform note collection. |

---

*This specification serves as the blueprint for implementing the C++20 musical transformation subsystem within Cobass.*


# Expanded Specification: Note Transformation Modes & Algorithmic Families

---

## 1. Taxonomic Hierarchy of Transform Modes

To ensure musical versatility without chaotic dissonance, the transformation subsystem is organized into **5 core algorithmic families** encompassing **20 distinct, musically constrained transformation engines**:

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                        COBASS NOTE TRANSFORM ENGINE TAXONOMY                           │
└────────────────────────────────────────────────────────────────────────────────────────┘
  │
  ├── 1. MELODIC & TONAL CONTOUR ENGINES
  │     ├── 1.1 Markovian Diatonic Walk (Brownian Motion)
  │     ├── 1.2 Gravitational Pitch Slew (Tonal Attraction to Roots/5ths)
  │     ├── 1.3 Melodic Inversion & Axis Mirror (Contour Inversion)
  │     ├── 1.4 Modal Interchange & Degree Shift (e.g., Natural Minor ➔ Dorian/Phrygian)
  │     └── 1.5 Blue-Note & Tension Injector (Passing Chro-Diatonic Color)
  │
  ├── 2. RHYTHMIC SLICING & METRIC MUTATION ENGINES
  │     ├── 2.1 Euclidean Pulse Carver & Rhythmic Chopper
  │     ├── 2.2 Sub-Step Ratchet Burst & Stutter Shaper
  │     ├── 2.3 Polymetric Grid Slicer (3:4, 5:4, 7:8 Phase Slicing)
  │     ├── 2.4 Metric Augmentation & Diminution (Time Dilation: 0.5x, 1.5x, 2.0x)
  │     └── 2.5 Groove Syncopation & Pocket Swing Displacer
  │
  ├── 3. HARMONIC & POLYPHONIC VOICING ENGINES
  │     ├── 3.1 Intelligent Diatonic Harmonizer (Parallel 3rds, 6ths, Octaves)
  │     ├── 3.2 Chord Voicing & Drop-2 / Drop-3 Spreader
  │     ├── 3.3 Contrary Motion & Counterpoint Generator
  │     ├── 3.4 Root Bassline Extractor & Sub Anchor
  │     └── 3.5 Drone / Pedal Point Sustainer
  │
  ├── 4. ORNAMENTATION & EXPRESSIVE MICRO-TIMING ENGINES
  │     ├── 4.1 Acoustic Guitar Strum & Flam Stagger
  │     ├── 4.2 Baroque Trills & Rapid Mordent Flutters
  │     ├── 4.3 Pentatonic Grace-Note Approacher (Slides & Bends)
  │     ├── 4.4 Parabolic Velocity Curve & Dynamic Swell
  │     └── 4.5 Organic Gaussian Jitter & Humanize Physics
  │
  └── 5. STRUCTURAL MOTIF & PHRASE GENERATIVE ENGINES
        ├── 5.1 Call & Response Splitter (Octave/Timbre Phrasing)
        ├── 5.2 Retrograde & Retrograde-Inversion (Bach/Motif Symmetry)
        ├── 5.3 Ghost-Note Density Sower (Groove Infill)
        └── 5.4 Climax / Apex Envelope Shaper (Melodic Peak Builder)
```

---

## 2. Deep-Dive Specification by Transform Family

---

### Family 1: Melodic & Tonal Contour Engines

#### 1.1 Markovian Diatonic Walk (Brownian Motion)
* **Musical Rationale:** Turns simple sustained notes into fluid, singable melodies using stepwise probabilistic traversal based on the statistical probabilities of classical and contemporary melodies.
* **Algorithmic Mechanics:**
  - Evaluates current note pitch $P_i$ within scale $S$.
  - Generates next pitch $P_{i+1}$ using a 2nd-order Markov Transition Matrix where stepwise motion ($\pm 1, \pm 2$ scale steps) has a $70\%$ probability, consonant leaps ($\pm 3, \pm 4$ scale steps) have a $20\%$ probability, and octave anchors have a $10\%$ probability.
  - Enforces a *Resolution Rule*: Any leap $>4$ semitones must be followed by stepwise motion in the opposite direction (gap-fill melodic principle).
* **Configurable Parameters:**
  - `Walk Step Size` (Small / Stepwise, Balanced, Wide Arpeggiated)
  - `Harmonic Gravity` ($0\%\dots 100\%$ pull toward Root / 5th)
  - `Directional Drift` (Ascending, Neutral, Descending)
* **Musical Example:**
  - *Input:* Sustained `C4` across 2 bars in C Major.
  - *Output:* `C4 ➔ D4 ➔ E4 ➔ G4 ➔ F4 ➔ E4 ➔ D4 ➔ C4` (Fluid melodic arc).

#### 1.2 Gravitational Pitch Slew (Tonal Attraction)
* **Musical Rationale:** Pulls wandering or loose notes toward the nearest high-stability tonal anchors (Tonic $1$, Mediant $3$, Dominant $5$), resolving melodic tension on strong beats.
* **Algorithmic Mechanics:**
  - Calculates the metric weight of each note based on downbeat position.
  - Notes landing on beat 1 or beat 3 are quantized to triad chord tones ($\{1, 3, 5\}$). Weak beat notes are allowed to remain passing tones ($\{2, 4, 6, 7\}$).
* **Configurable Parameters:**
  - `Tonal Center Magnetism` ($0\%\dots 100\%$)
  - `Target Triad Preference` (Tonic Triad, Subdominant, Dominant)
  - `Passing Note Leniency` ($0\%\dots 100\%$)

#### 1.3 Melodic Inversion & Axis Mirror
* **Musical Rationale:** Neo-classical and modal development technique that flips melodic contours upside down while retaining exact rhythmic timing and diatonic scale conformity.
* **Algorithmic Mechanics:**
  - Selects a pitch mirror axis $P_{\text{axis}}$ (either the first note, the melodic median, or the tonic key center).
  - Inverts each pitch: $P_{\text{new}} = P_{\text{axis}} - (P_{\text{original}} - P_{\text{axis}})$.
  - Snaps $P_{\text{new}}$ to the nearest scale tone within the active musical mode.
* **Configurable Parameters:**
  - `Mirror Axis` (First Note, Scale Root, Center Average)
  - `Strict Diatonic Snap` (On/Off)

#### 1.4 Modal Interchange & Degree Shift
* **Musical Rationale:** Instantly recolors the mood of a melody without altering its rhythm (e.g., converting a happy Major line into an emotional Dorian or dark Phrygian melody).
* **Algorithmic Mechanics:**
  - Analyzes the scale degree indices of original notes.
  - Maps each degree $D_i$ to the corresponding degree of the target mode (e.g., Major 3rd $\to$ Minor 3rd, Natural 6th $\to$ Flat 6th).
* **Configurable Parameters:**
  - `Target Mode` (Ionian, Dorian, Phrygian, Lydian, Mixolydian, Aeolian, Locrian, Harmonic Minor, Melodic Minor, Blues, Pentatonics)

#### 1.5 Blue-Note & Tension Injector
* **Musical Rationale:** Adds blues, jazz, or trap flavor by injecting controlled micro-passing chromaticisms (Flat 5th / Sharp 4th, Minor/Major 3rd slides) on unaccented offbeats.
* **Algorithmic Mechanics:**
  - Scans for unaccented offbeat notes leading into roots or 5ths.
  - Injects a passing semitone offset (e.g., $Eb \to E$ in Major, or $F# \to G$ in C Minor) with a short $60\text{ ms}$ duration and lower velocity.
* **Configurable Parameters:**
  - `Color Density` (Sparse $10\%$, Moderate $30\%$, Heavy $60\%$)
  - `Tension Style` (Blues $\flat 5$, Jazz Enclosure, Trap Minor Slide)

---

### Family 2: Rhythmic Slicing & Metric Mutation Engines

#### 2.1 Euclidean Pulse Carver & Rhythmic Chopper
* **Musical Rationale:** Takes sustained pad notes, chords, or long lead tones and dices them into tight, syncopated rhythmic patterns (House, Trap, Future Bass, Afrobeats).
* **Algorithmic Mechanics:**
  - Uses Björklund’s Euclidean distribution formula: distributes $K$ active pulses over $N$ metric grid subdivisions.
  - Downbeat pulses receive full velocity ($100\%$); offbeat pulses receive syncopated ghost velocities ($60\%\text{--}75\%$).
  - Preserves underlying chord pitches and envelope continuity across slices without clipping clicks.
* **Configurable Parameters:**
  - `Pulses (K)` ($1\dots 32$)
  - `Subdivision (N)` ($1/4, 1/8, 1/16, 1/32, \text{Triplets}, \text{Dotted}$)
  - `Rotation Offset` ($-8\dots +8$ steps)
  - `Gate Tightness` ($20\%\dots 95\%$ duty cycle)
* **Musical Example:**
  - *Input:* Sustained 1-bar chord.
  - *Output:* 5 evenly-spaced Euclidean pulses across 16 subdivisions (`[■··■··■··■·■···]`), giving an Afro-Cuban / Reggaeton syncopated rhythm.

#### 2.2 Sub-Step Ratchet Burst & Stutter Shaper
* **Musical Rationale:** Adds fast rhythmic rolls and micro-bursts ($2\times, 3\times, 4\times, 8\times$ speed) to melody ends or transition beats, typical of modern trap hi-hats and drill/glitch melodies.
* **Algorithmic Mechanics:**
  - Selects note tails landing immediately before bar boundaries.
  - Subdivides the final note into $2, 3, 4, \text{ or } 8$ micro-slices.
  - Applies a dynamic velocity ramp (crescendo or decrescendo) across the burst.
* **Configurable Parameters:**
  - `Ratchet Multiplier` ($2\times, 3\times, 4\times, 6\times, 8\times$)
  - `Burst Trigger Chance` ($10\%\dots 100\%$)
  - `Velocity Slope` (Crescendo, Decrescendo, Flat)

#### 2.3 Polymetric Grid Slicer
* **Musical Rationale:** Creates progressive, hypnotic grooves by slicing a 4/4 melody with non-standard metric lengths (e.g., looping 3-step or 5-step rhythmic phrases across standard 16-step bars).
* **Algorithmic Mechanics:**
  - Imposes a polymetric loop length $L \in \{3, 5, 7\}$ over the note stream.
  - Accents step $0$ of each polymeter cycle, causing accents to phase across bar lines.
* **Configurable Parameters:**
  - `Polymeter Base` ($3/16, 5/16, 7/16, 3/8, 5/8$)
  - `Phase Accent Strength` ($0\%\dots 100\%$)

#### 2.4 Metric Augmentation & Diminution
* **Musical Rationale:** Changes the tempo feel of a musical phrase without altering the project BPM (Half-Time or Double-Time conversions).
* **Algorithmic Mechanics:**
  - **Augmentation ($2\times$):** Doubles note offsets and lengths: $T_{\text{new}} = 2 \cdot T_{\text{orig}}$, $L_{\text{new}} = 2 \cdot L_{\text{orig}}$ (Half-Time feel).
  - **Diminution ($0.5\times$):** Halves note offsets and lengths: $T_{\text{new}} = 0.5 \cdot T_{\text{orig}}$, $L_{\text{new}} = 0.5 \cdot L_{\text{orig}}$ (Double-Time speed).
  - **Triplet Compression ($0.667\times$):** Converts straight 16ths into triplet swing phrases.
* **Configurable Parameters:**
  - `Time Ratio` ($0.5\times, 0.667\times, 1.5\times, 2.0\times$)
  - `Anchor Point` (Phrase Start, Center, Phrase End)

#### 2.5 Groove Syncopation & Pocket Swing Displacer
* **Musical Rationale:** Injects human groove, pushing or pulling offbeats to create laid-back hip-hop, Dilla-style swing, or pushed EDM drive.
* **Algorithmic Mechanics:**
  - Identifies offbeat notes (e.g., even-numbered 16th subdivisions).
  - Applies a forward delay: $\Delta T = T_{\text{grid}} \cdot (\text{Swing}\% \cdot 0.333)$.
* **Configurable Parameters:**
  - `Swing Amount` ($0\%\dots 75\%$)
  - `Pocket Bias` (Laid-back / Late vs Rushed / Early)

---

### Family 3: Harmonic & Polyphonic Voicing Engines

#### 3.1 Intelligent Diatonic Harmonizer
* **Musical Rationale:** Turns monophonic melodies into rich multi-voice harmonies where all added notes conform strictly to the key signature.
* **Algorithmic Mechanics:**
  - For each note pitch $P$, computes its scale degree index $D$.
  - Generates parallel voice: $P_{\text{harm}} = \text{ScaleDegreeToMidi}(D + N_{\text{interval}})$.
  - Automatically switches between major and minor 3rds/6ths depending on scale degree.
* **Configurable Parameters:**
  - `Harmony Interval` (Diatonic 3rd Above/Below, 6th Above/Below, 4th/5th Power Harmony, Octave Double)
  - `Velocity Balance` (Relative volume of harmony voice: $50\%\dots 100\%$)

#### 3.2 Chord Voicing & Drop-2 / Drop-3 Spreader
* **Musical Rationale:** Takes clustered block chords and opens them up across the stereo and frequency spectrum for a professional, spacious studio sound.
* **Algorithmic Mechanics:**
  - Analyzes active polyphonic notes at each time slice.
  - Sorts notes by pitch. In **Drop-2 Mode**, the second note from the top is dropped by one octave ($-12\text{ semitones}$). In **Drop-3 Mode**, the third note from the top is dropped.
* **Configurable Parameters:**
  - `Voicing Type` (Closed Block, Drop-2, Drop-3, Spread Octave, Open Quartal)
  - `Root Bass Extension` (Adds octave sub-bass root note below chord)

#### 3.3 Contrary Motion & Counterpoint Generator
* **Musical Rationale:** Generates an independent, complementary secondary melody that moves in the opposite pitch direction of the main melody, following classical counterpoint voice-leading rules.
* **Algorithmic Mechanics:**
  - When the lead melody moves upwards ($\Delta P > 0$), the countermelody moves downwards ($\Delta P < 0$) to consonant scale intervals (3rds, 6ths, 5ths).
  - Avoids parallel octaves and fifths.
* **Configurable Parameters:**
  - `Counterpoint Density` (1:1 Note-for-Note, 2:1 Double-speed, Syncopated Offbeat)
  - `Voice Separation` (Low Register vs High Register)

#### 3.4 Root Bassline Extractor & Sub Anchor
* **Musical Rationale:** Listens to an active chord progression or melody and automatically synthesizes a matching bassline locked to the chord roots in the $30\text{--}80\text{ Hz}$ sub range.
* **Algorithmic Mechanics:**
  - Extracts the lowest harmonic pitch of each bar or beat.
  - Transposes it down to Octaves 1–2 (`MIDI 24–48`).
  - Applies rhythmic pattern styles (Sustained Whole Note, 8th-note Driving Bass, Offbeat Pumper).
* **Configurable Parameters:**
  - `Rhythmic Style` (Sustained Pedals, Driving 8ths, Syncopated Offbeats)
  - `Target Octave` (`C1–C2` or `C2–C3`)

---

### Family 4: Ornamentation & Expressive Micro-Timing Engines

#### 4.1 Acoustic Guitar Strum & Flam Stagger
* **Musical Rationale:** De-quantizes simultaneous chord notes to simulate the mechanical strum of a plectrum across guitar strings or drum flams.
* **Algorithmic Mechanics:**
  - Takes all notes sharing a common start tick.
  - Sorts notes by pitch and offsets each successive note by a staggered micro-delay ($\Delta t = 5\dots 35\text{ ms}$).
* **Configurable Parameters:**
  - `Strum Direction` (Down-Strum, Up-Strum, Alternating)
  - `Strum Speed / Spread` ($5\text{ ms}\dots 60\text{ ms}$)
  - `Velocity Taper` (First string loud $\to$ last string soft)

#### 4.2 Baroque Trills & Rapid Mordent Flutters
* **Musical Rationale:** Adds rapid, decorative pitch oscillations between a main note and its upper/lower diatonic scale neighbor.
* **Algorithmic Mechanics:**
  - Replaces a sustained note's body with rapid $1/32\text{nd}$ or $1/64\text{th}$ note toggles between degree $D$ and $D+1$.
* **Configurable Parameters:**
  - `Trill Speed` ($1/16, 1/32, 1/64, \text{Triplets}$)
  - `Trill Type` (Upper Trill, Lower Mordent, Turn / Gruppetto)
  - `Trill Placement` (Start of note, Whole note body, End of note)

#### 4.3 Pentatonic Grace-Note Approacher
* **Musical Rationale:** Emulates vocal, saxophone, or synth lead pitch glides where the target note is preceded by a fast pickup note from a half-step below or scale step above.
* **Algorithmic Mechanics:**
  - Spawns a $30\text{--}60\text{ ms}$ ghost note immediately prior to accented notes, resolving directly into the target pitch.
* **Configurable Parameters:**
  - `Approach Direction` (From Below, From Above, Scale Step)
  - `Grace Note Length` ($20\text{ ms}\dots 80\text{ ms}$)

#### 4.4 Parabolic Velocity Curve & Dynamic Swell
* **Musical Rationale:** Shapes the emotional energy of a phrase using mathematically smooth volume dynamics.
* **Algorithmic Mechanics:**
  - Maps note velocities along continuous mathematical curves:
    $$\text{Linear Crescendo:} \quad V(t) = V_{\text{start}} + t \cdot (V_{\text{end}} - V_{\text{start}})$$
    $$\text{Parabolic Arch / Swell:} \quad V(t) = V_{\text{base}} + 4 \cdot \Delta V \cdot t(1 - t)$$
    $$\text{S-Curve Exponential:} \quad V(t) = \frac{1}{1 + e^{-k(t - 0.5)}}$$
* **Configurable Parameters:**
  - `Curve Shape` (Linear Ramp, Parabolic Dome/Arch, Exponential Swell, Inverted Bowl)
  - `Min / Max Velocity Boundaries` ($1\dots 127$)

#### 4.5 Organic Gaussian Jitter & Humanize Physics
* **Musical Rationale:** Replaces rigid computer quantization with the natural micro-imperfections of a live human session player.
* **Algorithmic Mechanics:**
  - Adds zero-mean Gaussian distributed perturbations to timing and velocity:
    $$\text{Tick}_{\text{new}} = \text{Tick} + \mathcal{N}(0, \sigma_{\text{time}}^2), \quad \text{Vel}_{\text{new}} = \text{Vel} + \mathcal{N}(0, \sigma_{\text{vel}}^2)$$
* **Configurable Parameters:**
  - `Timing Jitter (Sigma)` ($\pm 0\dots \pm 25\text{ ticks}$)
  - `Velocity Jitter (Sigma)` ($\pm 0\%\dots \pm 25\%$)

---

### Family 5: Structural Motif & Phrase Generative Engines

#### 5.1 Call & Response Splitter
* **Musical Rationale:** Splits a monophonic melody into two interacting musical characters: a "Call" phrase in one octave/register followed by a "Response" phrase in another.
* **Algorithmic Mechanics:**
  - Splits phrase by bars or rhythmic gaps ($>1\text{ beat}$).
  - Even phrases remain at original register; odd phrases are transposed by $\pm 12\text{ semitones}$ and given a distinct velocity/gate profile.
* **Configurable Parameters:**
  - `Split Interval` (Every Bar, Every 2 Bars, Detected Silence Gaps)
  - `Response Octave Shift` ($-12, +12, \text{Fifth } +7$)

#### 5.2 Retrograde & Retrograde-Inversion
* **Musical Rationale:** Mathematical musical symmetry used in classical serialism and film scoring.
* **Algorithmic Mechanics:**
  - **Retrograde:** Reverses notes in time: $T_{\text{new}} = T_{\text{phrase\_end}} - T_{\text{original\_end}}$.
  - **Retrograde-Inversion:** Reverses notes in time *and* inverts pitch contour around key center.
* **Configurable Parameters:**
  - `Transformation Mode` (Retrograde, Retrograde-Inversion)
  - `Preserve Velocity Hierarchy` (Keep downbeat accents stationary or reverse them with notes)

#### 5.3 Ghost-Note Density Sower
* **Musical Rationale:** Injects rhythmic groove infills into empty spaces between lead notes without cluttering the main melody.
* **Algorithmic Mechanics:**
  - Detects rests $\ge 1\text{ beat}$.
  - Fills rests with low-velocity ($30\text{--}55$) octave or scale-fifth ghost taps aligned to the active swing grid.
* **Configurable Parameters:**
  - `Fill Density` (Sparse, Medium, Busy)
  - `Ghost Velocity Cap` ($20\dots 60$)

#### 5.4 Climax / Apex Envelope Shaper
* **Musical Rationale:** Directs the contour of a 4-bar or 8-bar phrase toward an intentional musical climax point (apex).
* **Algorithmic Mechanics:**
  - Defines an apex position (e.g., bar 3.3).
  - Gradually scales pitch and velocity upwards leading to the apex, then resolves downward to the tonic.
* **Configurable Parameters:**
  - `Apex Position` ($25\%, 50\%, 75\%$ of phrase duration)
  - `Climax Pitch Lift` ($+0\dots +12\text{ semitones}$)

---

## 3. Unified Mode Parameter Matrix

| Transform Mode | Parameter 1 | Parameter 2 | Parameter 3 | Parameter 4 |
| :--- | :--- | :--- | :--- | :--- |
| **1.1 Markov Walk** | Step Size ($1\dots 5$) | Gravity ($0\dots 100\%$) | Drift ($\uparrow, \leftrightarrow, \downarrow$) | Leap Resolution (On/Off) |
| **1.2 Gravitation** | Center Pull ($0\dots 100\%$) | Target Triad ($1\text{st}, 4\text{th}, 5\text{th}$) | Weak Leniency ($0\dots 100\%$) | Downbeat Lock (On/Off) |
| **1.3 Motif Mirror** | Mirror Axis (Root, Avg, 1st) | Scale Snap (On/Off) | Range Clamp (Low/High) | Preserve Durations |
| **1.4 Modal Shift** | Target Scale (24 Modes) | Pitch Center Offset | Degree Match Mode | Root Re-anchor |
| **1.5 Tension/Blue** | Injection Rate ($10\dots 60\%$) | Style (Blues $\flat 5$, Jazz, Trap) | Micro-Gate ($20\dots 80\text{ms}$) | Accented Beats Only |
| **2.1 Euclidean Chop** | Pulses $K$ ($1\dots 32$) | Grid $N$ ($1/4\dots 1/32$) | Rotation ($-8\dots +8$) | Duty Gate ($20\dots 95\%$) |
| **2.2 Ratchet Burst** | Rolls ($2\times, 3\times, 4\times, 8\times$) | Chance ($10\dots 100\%$) | Velocity Ramp Slope | Trigger Position |
| **2.3 Polymetric** | Phase Length ($3, 5, 7$) | Grid Subdivision | Accent Gain ($0\dots 100\%$) | Boundary Warp |
| **2.4 Time Warp** | Ratio ($0.5\times\dots 2.0\times$) | Anchor (Start, Center, End) | Triplet Phase | Overlap Resolution |
| **2.5 Pocket Swing** | Swing ($0\dots 75\%$) | Push/Pull (Late/Early) | Grid Resolution | Gaussian Drift |
| **3.1 Harmonizer** | Interval ($\pm 3, \pm 6, 8\text{va}$) | Scale Mode | Voice Velocity ($50\dots 100\%$) | Octave Spread |
| **3.2 Chord Voicer** | Voicing (Drop-2, Drop-3) | Sub Root Add (On/Off) | Width Spread | Stagger Delay |
| **3.3 Counterpoint** | Density (1:1, 2:1, Sync) | Register (Below/Above) | Consonance Filter | Motion Law |
| **3.4 Bass Extractor** | Style (Pedal, 8ths, Offbeat) | Octave (`C1–C2`) | Gate Length | Velocity Weight |
| **4.1 Guitar Strum** | Direction ($\downarrow, \uparrow, \updownarrow$) | Stagger ($5\dots 60\text{ms}$) | Velocity Taper | Human Jitter |
| **4.2 Trills** | Speed ($1/16\dots 1/64$) | Type (Upper, Mordent) | Trigger Window | Velocity Accent |
| **4.3 Grace Notes** | Approach ($\uparrow, \downarrow$) | Duration ($20\dots 80\text{ms}$) | Interval Range | Trigger Threshold |
| **4.4 Velocity Curve** | Curve (Linear, Parabolic) | Min Velocity ($1\dots 127$) | Max Velocity ($1\dots 127$) | Cycle Frequency |
| **4.5 Humanize** | Timing Jitter ($\pm\text{Ticks}$) | Velocity Jitter ($\pm\%$) | Duration Variance | Ghost Note Add |
| **5.1 Call/Response** | Split Frequency (Bar/Rest) | Response Pitch ($\pm 12, +7$) | Velocity Contrast | Pan Separation |
| **5.2 Retrograde** | Mode (Retro, Retro-Inv) | Mirror Axis | Velocity Tracking | Phrase Lock |
| **5.3 Ghost Infill** | Density (Sparse, Busy) | Max Ghost Vel ($20\dots 60$) | Octave Anchor | Swing Snap |
| **5.4 Climax Shaper** | Apex Position ($25\dots 75\%$) | Pitch Peak ($+0\dots +12$) | Velocity Peak | Curve Curvature |

---

## 4. Continuous Morph & Interpolation Mechanics

Every transform mode supports continuous, non-destructive **Morph / Blend ($0.0 \to 1.0$)**:

```
                ┌──────────────────────────────────────────────────────────┐
  Original      │                                                          │  Fully Transformed
  Note State    │                  MORPH SLIDER: 0% -> 100%                │  Target State
  (Reference)   │                                                          │  (Computed)
                └─────────────────────────────┬────────────────────────────┘
                                              │
                    ┌─────────────────────────┴─────────────────────────┐
                    ▼                                                   ▼
         [PITCH MORPH ENGINE]                                 [TIMING MORPH ENGINE]
  Continuous scale-degree shifting                     Linear tick interpolation:
  towards target note:                                 Tick(m) = Tick_orig + m * (Tick_target - Tick_orig)
  Pitch(m) = Snap(Pitch_orig + m * deltaPitch)
                    ▲                                                   ▲
                    └─────────────────────────┬─────────────────────────┘
                                              │
                                    [VELOCITY MORPH ENGINE]
                             Vel(m) = Vel_orig + m * (Vel_target - Vel_orig)
```

1. **At $0\%$ Blend:** Output equals original reference notes bit-for-bit.
2. **At $50\%$ Blend:** Pitches remain close to original melody lines, but rhythmic subdivisions, groove swing, and subtle harmony accents begin to emerge.
3. **At $100\%$ Blend:** Complete mathematical transformation according to the selected mode.

---

## 5. Preset Recipe Stacks (Multi-Mode Macros)

Transform modes can be stacked into production macros:

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                              COMPOUND PRODUCTION RECIPES                               │
├─────────────────────────┬──────────────────────────────────────────────────────────────┤
│ Recipe Name             │ Staged Execution Pipeline                                    │
├─────────────────────────┼──────────────────────────────────────────────────────────────┤
│ Future Bass Chords      │ 1. [Drop-2 Voicing] ➔ 2. [Euclidean 1/16th Slice] ➔ 3. [Strum]│
├─────────────────────────┼──────────────────────────────────────────────────────────────┤
│ Trap Lead Evolution     │ 1. [Markov Walk] ➔ 2. [Ratchet Bursts] ➔ 3. [Blue Slide]    │
├─────────────────────────┼──────────────────────────────────────────────────────────────┤
│ Liquid DnB Roller       │ 1. [1/8th Slice] ➔ 2. [Bass Extractor] ➔ 3. [Pocket Swing]   │
├─────────────────────────┼──────────────────────────────────────────────────────────────┤
│ Neo-Classical Motif     │ 1. [Motif Mirror] ➔ 2. [Harmonizer 3rds] ➔ 3. [Trill Ends]   │
├─────────────────────────┼──────────────────────────────────────────────────────────────┤
│ Human Soul Keys         │ 1. [Gaussian Humanize] ➔ 2. [Guitar Strum] ➔ 3. [Swell Arch] │
└─────────────────────────┴──────────────────────────────────────────────────────────────┘
```

---

## 6. Verification and Acceptance Testing Matrix

| Mode Family | Verification Criteria | Deterministic Test Pass Condition |
| :--- | :--- | :--- |
| **Melodic Walk** | Traverses pitches without generating out-of-scale notes. | $100\%$ scale conformity on all output pitches across 24 scale modes. |
| **Euclidean Slice** | Dices sustained notes into $K$ pulses across $N$ grid slots. | Sum of slice durations equals original note span; pulse offsets match Björklund algorithm. |
| **Harmonizer** | Adds parallel voices conforming to active scale degrees. | Added voices maintain correct major/minor third intervals without pitch collisions. |
| **Guitar Strum** | Offsets chord note start ticks incrementally. | $\text{StartTick}(N+1) = \text{StartTick}(N) + \Delta t$; all note durations maintained. |
| **Morph Interpolation**| Morph slider sweeps $0.0 \leftrightarrow 1.0$ without dropped notes or memory allocations. | Note count remains bounded; continuous values interpolate linearly. |
| **Undo / Rollback** | Reset or cancel restores exact original note state. | Deep memory comparison of `startTick`, `lengthTicks`, `pitch`, and `velocity` passes $100\%$. |



# Expanded Specification: Advanced Melodic & Tonal Contour Engines (Family 1 Expansion)

To elevate the **Cobass Note Transformation Subsystem** into an elite melodic generator capable of classical counterpoint, modern modal jazz, cinematic themes, and electronic hooks, **10 additional specialized Melodic & Tonal Contour Engines** are specified below.

---

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│               EXPANDED MELODIC & TONAL CONTOUR ENGINES (FAMILY 1)                      │
├────────────────────────────────────────────────────────────────────────────────────────┤
│  [1.1] Markovian Diatonic Walk (Brownian Motion)                                       │
│  [1.2] Gravitational Pitch Slew (Tonal Attraction to Roots/5ths)                      │
│  [1.3] Melodic Inversion & Axis Mirror (Contour Inversion)                             │
│  [1.4] Modal Interchange & Degree Shift                                                │
│  [1.5] Blue-Note & Tension Injector (Passing Chro-Diatonic Color)                      │
│  ────────────────────────────────── NEW ENGINES ──────────────────────────────────────  │
│  [1.6] Schenkerian Linear Progression & Leading-Tone Magnet (Voice-Leading Resolution) │
│  [1.7] Bebop Enclosure & Chromatic Approach Engine (Jazz/Funk Ornamentation)           │
│  [1.8] Compound Melody & Linear Polyphony Weaving (Bach/Arp Splitter)                  │
│  [1.9] Bartók Pitch Wedge (Symmetrical Interval Constriction & Expansion)             │
│  [1.10] Diatonic Cascade & Passing-Run Interpolator (Melodic Gap Fill)                 │
│  [1.11] Spline/Bézier Melodic Contour Sculptor (Smooth Geometric Pitch Shaping)       │
│  [1.12] Messiaen Symmetrical Axis & Sieve Shifter (Synthetic Modes of Limited Trans.)  │
│  [1.13] Microtonal / Maqam & Raga Inflector (Quarter-Tone Expressive Bending)          │
│  [1.14] Melodic Climax Architect & Golden Ratio Apex Shaper (Arch Formulation)         │
│  [1.15] Scale-Degree Arpeggiator & Octave Stacker (Broken Chord Weaver)               │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Detailed Specifications: Engines 1.6 to 1.15

---

### 1.6 Schenkerian Linear Progression & Leading-Tone Magnet
* **Musical Rationale:** Based on Heinrich Schenker’s theory of fundamental linear progressions (*Zug*), this engine ensures that melodies move toward structural harmonic resolution goals (e.g., scale degree $\hat{3} \to \hat{2} \to \hat{1}$ over a tonic cadence, or $\hat{7} \to \hat{1}$ leading-tone pulls).
* **Algorithmic Mechanics:**
  - Analyzes the phrase ending or downbeat targets.
  - Identifies unstable "tendency tones" (Scale degrees $\hat{7}, \hat{4}, \hat{6}$).
  - Forces strict voice-leading resolution:
    $$\hat{7} \text{ (Leading Tone)} \xrightarrow{\text{Resolves UP}} \hat{1} \text{ (Tonic)}$$
    $$\hat{4} \text{ (Subdominant)} \xrightarrow{\text{Resolves DOWN}} \hat{3} \text{ (Mediant)}$$
    $$\hat{6} \text{ (Submediant)} \xrightarrow{\text{Resolves DOWN}} \hat{5} \text{ (Dominant)}$$
  - Generates smooth downward diatonic step-lines ($5\text{-}4\text{-}3\text{-}2\text{-}1$ or $3\text{-}2\text{-}1$) to land cleanly on key cadential beats.
* **Configurable Parameters:**
  - `Resolution Strength` ($0\%\dots 100\%$)
  - `Target Cadence Type` (Authentic $\hat{7} \to \hat{1}$, Plagal $\hat{4} \to \hat{1}$, Half-Cadence $\to \hat{5}$)
  - `Linear Line Length` (3-step $\hat{3}\text{-}\hat{2}\text{-}\hat{1}$, 5-step $\hat{5}\text{-}\hat{4}\text{-}\hat{3}\text{-}\hat{2}\text{-}\hat{1}$)
* **Concrete Example:**
  - *Input:* Unresolved, floating notes `[E4, A4, B4, F4]` in C Major.
  - *Output:* `[E4, G4, F4 ➔ E4, B4 ➔ C5]` (Tendency tones $F \to E$ and $B \to C$ resolve smoothly).

---

### 1.7 Bebop Enclosure & Chromatic Approach Engine
* **Musical Rationale:** Emulates bebop and neo-soul horn solos where target chord tones are anticipated and emphasized by "surrounding" them with diatonic above and chromatic below approach notes.
* **Algorithmic Mechanics:**
  - Identifies target downbeat chord notes (Roots, 3rds, 5ths, 7ths).
  - Prepends a 2-note or 3-note micro-enclosure pattern in the $1/16\text{th}$ or $1/32\text{nd}$ space preceding the target:
    $$\text{Upper Diatonic Neighbor} \xrightarrow{} \text{Lower Chromatic Neighbor} \xrightarrow{} \text{Target Tone}$$
    $$\text{Example (Target C): } D \text{ (diatonic above)} \to B \text{ (half-step below)} \to C \text{ (Target)}$$
* **Configurable Parameters:**
  - `Enclosure Pattern` (Diatonic Above + Chromatic Below, Double Chromatic Below, Pivot Surrounds)
  - `Approach Note Velocity` ($40\%\dots 85\%$ of target note)
  - `Time Span` ($1/16\text{th}, 1/32\text{nd}, \text{Triplet pickup}$)
* **Concrete Example:**
  - *Input:* Simple downbeat `G4` on Beat 1.
  - *Output:* `[A4 (1/16 pickup) ➔ F#4 (1/16 pickup) ➔ G4 (Beat 1)]`.

---

### 1.8 Compound Melody & Linear Polyphony Weaving
* **Musical Rationale:** Emulates J.S. Bach’s solo string works (e.g., Cello Suites) and classical arpeggios where a single monophonic instrument creates the psychological illusion of two simultaneous voices (a low bass pedal line and a high singing melody).
* **Algorithmic Mechanics:**
  - Splits a single incoming note line into two alternating registers:
    - **Voice A (Bass Anchor):** Downbeats and lower register ($C2\text{--}E3$), sustained/rhythmic pedal point.
    - **Voice B (Upper Melodic Line):** Offbeats and upper register ($G3\text{--}C5$), moving scalar contour.
  - Interweaves the two voices into a continuous, single-track monophonic stream without overlapping note collisions.
* **Configurable Parameters:**
  - `Register Split Distance` ($12, 19, 24\text{ semitones}$)
  - `Bass Anchor Frequency` (Every Beat, Every Half-Bar, Every Bar)
  - `Upper Voice Motion` (Stepwise Contour, Arpeggiated, Triplet Flutter)
* **Concrete Example:**
  - *Input:* Single sustained `C3` chord tone.
  - *Output:* Alternating `[C2 (bass) ➔ G3 (melody) ➔ C2 (bass) ➔ E4 (melody) ➔ C2 ➔ D4 ➔ C2 ➔ C4]`.

---

### 1.9 Bartók Pitch Wedge (Interval Constriction & Expansion)
* **Musical Rationale:** Modern symmetrical pitch technique pioneered by Béla Bartók and widely used in film suspense scoring. Notes either fan outward from a central unisson ("Expanding Wedge") or converge inwards from wide intervals toward a single pitch ("Constricting Wedge").
* **Algorithmic Mechanics:**
  - **Expanding Wedge:** Begins at central pitch $P_0$, alternating notes branch progressively wider according to scale degrees:
    $$\{P_0, \quad P_0 + 1, \quad P_0 - 1, \quad P_0 + 2, \quad P_0 - 2, \quad P_0 + 3, \quad P_0 - 3, \dots\}$$
  - **Constricting Wedge:** Begins at wide outer boundaries and contracts symmetrically toward a shared center tone.
* **Configurable Parameters:**
  - `Wedge Direction` (Expanding Outward $\prec$, Constricting Inward $\succ$, Hourglass $\bowtie$)
  - `Step Rate` (Diatonic Scale Steps vs Symmetrical Chromatic Steps)
  - `Max Interval Span` ($1\text{ Octave}, 2\text{ Octaves}, \text{Tritone Clamp}$)
* **Concrete Example:**
  - *Input:* 8 steady $1/8\text{th}$ notes on `A4`.
  - *Output (Expanding):* `[A4 ➔ B4 ➔ G4 ➔ C5 ➔ F4 ➔ D5 ➔ E4 ➔ E5]` (Diatonic expanding fan).

---

### 1.10 Diatonic Cascade & Passing-Run Interpolator
* **Musical Rationale:** Eliminates unnatural, static gaps between widely separated melody notes by automatically generating fluent scalar runs, waterfalls, and flourishes connecting the endpoints.
* **Algorithmic Mechanics:**
  - Scans for pitch intervals $\ge 5\text{ semitones}$ between consecutive notes $N_1$ and $N_2$.
  - Calculates the diatonic distance in scale degrees: $\Delta D = D_2 - D_1$.
  - Fills the temporal gap with rapid intermediate diatonic passing notes ($1/16\text{th}$ or $1/32\text{nd}$ notes) creating an ascending/descending harp-like cascade.
* **Configurable Parameters:**
  - `Run Subdivision` ($1/16\text{th}, 1/32\text{nd}, \text{Triplets}, \text{Quintuplets}$)
  - `Cascade Direction` (Direct Straight Run, Up-and-Over Arched Cascade, Zig-Zag Wave)
  - `Velocity Taper` (Accelerando / Crescendo leading to arrival note)
* **Concrete Example:**
  - *Input:* `C4` on Beat 1 leading to `G5` on Beat 3.
  - *Output:* `C4 ➔ [D4, E4, F4, G4, A4, B4, C5, D5, E5, F5 (rapid run)] ➔ G5 (accented arrival)`.

---

### 1.11 Spline / Bézier Melodic Contour Sculptor
* **Musical Rationale:** Allows the composer to sculpt the macro-contour of an entire phrase using smooth mathematical geometric curves (Cubic Bézier, Hermite Spline, Parabolic Dome, Sine Wave).
* **Algorithmic Mechanics:**
  - Defines a continuous 2D pitch trajectory curve: $P(t) = \text{Bézier}(t; \mathbf{P}_0, \mathbf{P}_1, \mathbf{P}_2, \mathbf{P}_3)$.
  - Samples the curve at note onset timestamps $t_i$.
  - Snaps continuous pitch values $P(t_i)$ to the active scale grid while preserving the macro melodic shape.
* **Configurable Parameters:**
  - `Curve Preset` (Arch/Dome $\frown$, Inverted Valley $\smile$, S-Curve Wave $\sim$, Exponential Ramp $\nearrow$)
  - `Control Point Handles` (Adjustable curve apex and curvature tension)
  - `Pitch Range Ceiling & Floor` (`C3` to `C6`)
* **Concrete Example:**
  - *Input:* Monotonous flat pitch line.
  - *Output:* Pitches reshape into a smooth, expressive melodic hill peaking gracefully in Bar 3 before descending.

---

### 1.12 Messiaen Symmetrical Axis & Sieve Shifter
* **Musical Rationale:** Utilizes Olivier Messiaen’s "Modes of Limited Transposition" and Iannis Xenakis’ Sieve Theory to create avant-garde, futuristic, and otherworldly cinematic textures that remain strictly consonant within symmetrical harmonic systems.
* **Algorithmic Mechanics:**
  - Applies symmetrical scale matrices:
    - **Mode 1 (Whole Tone):** Equal 2-semitone symmetrical spacing.
    - **Mode 2 (Octatonic / Diminished):** Alternating half-step / whole-step ($[1, 2, 1, 2, 1, 2, 1, 2]$).
    - **Mode 3 (Augmented Symmetrical):** $[2, 1, 1, 2, 1, 1, 2, 1, 1]$.
  - Rotates notes around symmetrical interval axes without tonic center degradation.
* **Configurable Parameters:**
  - `Symmetrical Matrix` (Octatonic Half-Whole, Octatonic Whole-Half, Whole Tone, Prometheus Hexachord, Enigmatic Scale)
  - `Axis Step Interval` ($\pm\text{Tritone}, \pm\text{Major 3rd}, \pm\text{Minor 3rd}$)

---

### 1.13 Microtonal / Maqam & Raga Inflector
* **Musical Rationale:** Infuses melodies with non-Western micro-intervals, neutral thirds ($350\text{ cents}$), and microtonal vocal bends found in Middle Eastern Maqam (Bayati, Rast, Hijaz) and Indian Classical Ragas (Bhairav, Todi).
* **Algorithmic Mechanics:**
  - Identifies target scale degrees (typically degrees $\hat{3}, \hat{6}, \hat{7}$).
  - Injects subtle pitch-bend metadata ($+50\text{ cents}$ / quarter-tone offsets) or creates dual-microtonal passing notes that simulate fretless pitch articulation.
* **Configurable Parameters:**
  - `Tuning System` (Quarter-tone 24-EDO, Arabic Maqam Rast $\hat{3}/\hat{7}$ half-flat, Maqam Bayati $\hat{2}$ half-flat, Turkish Makam Usul, Just Intonation 7-limit)
  - `Micro-Bend Duration` ($40\text{ ms}\dots 180\text{ ms}$)
  - `Microtonal Intensity` ($0\%\dots 100\%$)

---

### 1.14 Melodic Climax Architect & Golden Ratio Apex Shaper
* **Musical Rationale:** Automatically structures phrase contours so that the highest pitch, strongest dynamic accent, and peak harmonic density align with the **Golden Ratio point ($\Phi \approx 0.618$)** of the phrase (e.g., Beat 3 of Bar 3 in a 4-bar phrase), mimicking master-level songcraft.
* **Algorithmic Mechanics:**
  - Calculates the total phrase duration $T_{\text{total}}$.
  - Sets apex timestamp $T_{\text{apex}} = T_{\text{start}} + 0.618 \cdot T_{\text{total}}$.
  - Modulates pitches and velocities:
    $$P(t) = P_{\text{base}} + \Delta P_{\text{climax}} \cdot \exp\left(-\frac{(t - T_{\text{apex}})^2}{2\sigma^2}\right)$$
    $$V(t) = V_{\text{base}} + \Delta V_{\text{accent}} \cdot \exp\left(-\frac{(t - T_{\text{apex}})^2}{2\sigma^2}\right)$$
* **Configurable Parameters:**
  - `Apex Location` (Golden Ratio $61.8\%$, Late Peak $75\%$, Midpoint $50\%$)
  - `Climax Pitch Lift` ($+4\dots +19\text{ semitones}$)
  - `Tension Curve Tightness` (Sharp Peak vs Broad Plateau)

---

### 1.15 Scale-Degree Arpeggiator & Octave Stacker
* **Musical Rationale:** Weaves chords or melodic notes into interlocking multi-octave arpeggio figures across selectable directional patterns (Up, Down, Up/Down, Convergent, Spiral).
* **Algorithmic Mechanics:**
  - Takes active chord tones or melody intervals.
  - Spreads them across selectable octave ranges ($1\dots 4\text{ octaves}$).
  - Executes mathematical pattern steps (e.g., $1\text{-}3\text{-}5\text{-}8$, $1\text{-}5\text{-}3\text{-}8$, Order-of-Arrival, Ping-Pong) aligned to the rhythmic grid.
* **Configurable Parameters:**
  - `Arp Mode` (Up, Down, Up-Down Inclusive, Down-Up, Inside-Out, Random Walk)
  - `Octave Range` ($1, 2, 3, 4\text{ Octaves}$)
  - `Note Gate Length` ($10\%\dots 150\%$ for Legato Overlap)
  - `Note Repeat / Ratchet` ($1\times, 2\times, 3\times$)

---

## 3. Updated Parameter & Capability Matrix (Engines 1.1 – 1.15)

| Engine ID & Name | Primary Parameter | Secondary Parameter | Tertiary Parameter | Guard / Constraint Rule |
| :--- | :--- | :--- | :--- | :--- |
| **1.1 Markov Walk** | Step Size ($1\dots 5$) | Gravity ($0\dots 100\%$) | Drift ($\uparrow, \leftrightarrow, \downarrow$) | Stepwise resolution after leaps $>4$ st |
| **1.2 Gravitation** | Center Pull ($0\dots 100\%$) | Target Triad ($1, 4, 5$) | Weak Leniency ($0\dots 100\%$) | Beat 1 & 3 locked to triad degrees |
| **1.3 Motif Mirror** | Mirror Axis (Root, Avg, 1st) | Strict Diatonic Snap | Range Boundary Clamp | Diatonic scale tonality preserved |
| **1.4 Modal Shift** | Target Scale (24 Modes) | Root Offset | Degree Match Mode | Rhythmic timing remains 100% frozen |
| **1.5 Tension/Blue** | Injection Rate ($10\dots 60\%$) | Style (Blues, Trap, Jazz) | Micro-Gate ($20\dots 80\text{ms}$) | Non-diatonic notes placed only on offbeats |
| **1.6 Schenker Lead**| Cadence Target ($\hat{1}, \hat{3}, \hat{5}$) | Line Length ($3\text{ or } 5\text{ steps}$) | Pull Force ($0\dots 100\%$) | Strict tendency tone resolution ($\hat{7}\to\hat{1}, \hat{4}\to\hat{3}$) |
| **1.7 Bebop Enclosure**| Pattern (Above/Below/Both)| Approach Velocity ($40\dots 85\%$)| Subdivision ($1/16, 1/32$)| Enclosure notes must resolve directly to target |
| **1.8 Compound Poly** | Register Split ($12\dots 24\text{st}$) | Bass Frequency (Beat/Bar) | Melodic Motion Style | Zero polyphonic voice overlap (mono stream) |
| **1.9 Bartók Wedge**  | Direction ($\prec, \succ, \bowtie$) | Step Mode (Diatonic/Chrom) | Interval Ceiling Clamp | Symmetrical interval preservation |
| **1.10 Cascade Run**  | Subdivision ($1/16, 1/32, \text{Tri}$) | Cascade Contour Shape | Velocity Accelerando | Direct scale-degree path between endpoints |
| **1.11 Bézier Spline** | Curve Shape Preset | Curvature Tension | Pitch Ceiling/Floor | Output sample points snapped to active scale |
| **1.12 Messiaen Axis** | Symmetrical Matrix Type | Axis Shift Angle | Cluster Thickness | Symmetry maintained across octaves |
| **1.13 Maqam/Raga**   | Micro-Tuning Preset | Pitch Bend Depth (cents) | Bend Rate ($40\dots 180\text{ms}$) | Base 12-TET scale degrees unaltered |
| **1.14 Golden Apex**  | Apex Position ($50\dots 75\%$) | Peak Pitch Lift ($+0\dots +19$) | Accent Curve Tension | Phrase climax matches mathematical $\Phi$ point |
| **1.15 Arp Stacker**  | Arp Pattern Direction | Octave Spread ($1\dots 4$) | Gate Length ($10\dots 150\%$) | Strict chord-tone harmonic matching |

---

## 4. Compound Melodic Presets (Combining New Engines)

These advanced contour engines can be chained to produce specific genre styles:

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                         EXPANDED MELODIC PRODUCTION MACROS                             │
├─────────────────────────┬──────────────────────────────────────────────────────────────┤
│ Preset Name             │ Execution Pipeline                                           │
├─────────────────────────┼──────────────────────────────────────────────────────────────┤
│ Neo-Soul D'Angelo Lead  │ 1. [Bebop Enclosures] ➔ 2. [Blue Tension] ➔ 3. [Gaussian Jitter]│
├─────────────────────────┼──────────────────────────────────────────────────────────────┤
│ Bach Solfeggietto Run   │ 1. [Cascade Interpolator] ➔ 2. [Compound Polyphony] ➔ 3. [$\hat{7}\to\hat{1}$]│
├─────────────────────────┼──────────────────────────────────────────────────────────────┤
│ Cinematic Hero Theme    │ 1. [Golden Apex Shaper] ➔ 2. [Bézier Arch] ➔ 3. [Harmonizer] │
├─────────────────────────┼──────────────────────────────────────────────────────────────┤
│ Cyberpunk Industrial Arp│ 1. [Messiaen Octatonic] ➔ 2. [Bartók Wedge] ➔ 3. [Ratchet]   │
├─────────────────────────┼──────────────────────────────────────────────────────────────┤
│ Silk Road Modal Fantasy │ 1. [Maqam Inflector] ➔ 2. [Markov Walk] ➔ 3. [Grace Approacher]│
└─────────────────────────┴──────────────────────────────────────────────────────────────┘
```

---

## 5. Verification Metrics for New Engines

1. **Voice-Leading Compliance ($\ge 98\%$):** Tendency tones ($\hat{7}, \hat{4}$) in Engine 1.6 resolve strictly to their harmonic targets without parallel unisons.
2. **Symmetrical Exactness ($100\%$):** Bartók Wedge (1.9) and Messiaen Symmetrical Axis (1.12) produce balanced interval expansions around the designated mirror center.
3. **Compound Monophonic Integrity ($100\%$):** Compound Melody Weaving (1.8) produces zero overlapping note timestamps, guaranteeing compatibility with monophonic synth patches.
4. **Golden Ratio Timing Precision ($\pm 2\text{ ticks}$):** The climax peak in Engine 1.14 aligns with $t = T_{\text{start}} + 0.618 \cdot T_{\text{total}}$.





# Advanced Melodic & Harmonic Transformation Subsystem
## Next-Generation Algorithmic Architecture & Music Theory Blueprint

---

# 1. Executive Vision & Next-Gen Capabilities

Building upon the established foundational operators in `NoteTransformEngine`, this plan specifies the **Next-Generation Advanced Melodic Transformation Suite**. It introduces higher-order generative grammars, geometric music theory, strict counterpoint solvers, and continuous melodic cross-morphing into the Cobass DAW ecosystem.

```
┌────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                              NEXT-GEN ADVANCED MELODY TRANSFORMATION SUITE                             │
├───────────────────────────────────┬────────────────────────────────────┬───────────────────────────────┤
│ 1. GENERATIVE GRAMMARS & FRACTALS │ 2. TONNETZ & NEO-RIEMANNIAN TONALITY│ 3. MELODIC MORPH INTERPOLATION│
├───────────────────────────────────┼────────────────────────────────────┼───────────────────────────────┤
│ • L-System Melodic Branching      │ • Tonnetz 2D Torus Traversal       │ • Melody A ➔ Melody B Morph   │
│ • Wolfram 1D Cellular Automata    │ • P, L, R Chordal Transformations  │ • Spline Vector Trajectory    │
│ • Brownian Fractional Drift (1/f) │ • Dynamic Harmonic Tension Curves  │ • Continuous Latent Warping   │
├───────────────────────────────────┼────────────────────────────────────┼───────────────────────────────┤
│ 4. SPECIES COUNTERPOINT & CANON   │ 5. METRIC MODULATION & PHASING     │ 6. DYNAMIC XENHARMONICS       │
├───────────────────────────────────┼────────────────────────────────────┼───────────────────────────────┤
│ • Strict Fuxian Counterpoint (1st │ • Reichian Minimalist Phase Shift  │ • Scala (.scl) Tuning Engine  │
│   & 2nd Species)                  │ • Nested Polyrhythmic Phasing      │ • Bohlen-Pierce / 19-EDO      │
│ • Crab Canon / Cancrizans Mirror  │ • Golden Ratio Metric Dilation     │ • Dynamic Microtonal Bending  │
└───────────────────────────────────┴────────────────────────────────────┴───────────────────────────────┘
```

---

# 2. Mathematical & Music Theory Foundations

## 2.1 Neo-Riemannian Tonnetz & Harmonic Gravity Fields

### A. The 2D Tonnetz Torus
Traditional tonal analysis operates within scalar degree constraints. Advanced harmonic transformation maps melodies and chords onto the **Euler-Riemann Tonnetz** (a 2D geometric simplicial complex where vertices are pitch classes, edges are consonant intervals: Minor 3rd, Major 3rd, and Perfect 5th):

```
       (Ab) ──── (C) ──── (E) ──── (G#)
        /  \     /  \     /  \     /  \
      (F) ── (A) ── (C#) ── (F) ── (A)
      /  \   /  \   /  \   /  \   /  \
    (D) ── (F#) ── (Bb) ── (D) ── (F#)
```

Every melodic triad or line is transformed via three fundamental involutions:
1. **Parallel ($P$)**: Inverts the mode between Major and Minor while keeping the root and fifth stationary:
   $$P(\langle R, R+4, R+7 \rangle) = \langle R, R+3, R+7 \rangle$$
2. **Leading-Tone Exchange ($L$)**: Inverts a Major triad across its major third, moving the root down a semitone to become the third of a Minor triad:
   $$L(\langle R, R+4, R+7 \rangle) = \langle R+4, R+7, R+11 \rangle$$
3. **Relative ($R$)**: Swaps a triad with its relative minor/major by shifting the fifth up two semitones:
   $$R(\langle R, R+4, R+7 \rangle) = \langle R+9, R, R+4 \rangle$$

### B. Continuous Harmonic Tension & Resolution Vector Field
A phrase's harmonic trajectory is evaluated as a continuous scalar potential $T(t) \in [0.0, 1.0]$. The engine adjusts pitch gravity dynamically according to a user-drawn or mathematically computed **Tension Arc**:
- **High Tension Zone ($T(t) \to 1.0$)**: Favors tritones, sharp elevenths, minor ninths, and whole-tone clusters.
- **Resolution Cadence ($T(t) \to 0.0$)**: Strongly pulls all active voices into root-position tonic or octave-fifth unisons.

---

## 2.2 Lindenmayer Systems (L-Systems) & Generative Melodic Grammar

L-Systems generate organic, self-similar, fractal melodic lines through iterative string rewriting over a musical alphabet:

$$\Sigma = \{ F, +, -, [, ] \}$$

- **$F$**: Generate a note event (length $\Delta t$).
- **$+$**: Shift scale degree $+1$ (diatonic step up).
- **$-$**: Shift scale degree $-1$ (diatonic step down).
- **$[$**: Push current pitch, velocity, and time state onto branch stack.
- **$]$**: Pop state from branch stack (creates musical motif echoes / call-and-response).

### Production Rule Example (Fibonacci Melodic Tree):
- **Axiom (Seed)**: $\omega = F$
- **Rule 1**: $F \to F[+F][-F]F$
- **Iteration 1**: $F[+F][-F]F$
- **Iteration 2**: $F[+F][-F]F[+F[+F][-F]F][-F[+F][-F]F]F[+F][-F]F$

This formal grammar guarantees melodic self-similarity across measures while maintaining thematic unity.

---

## 2.3 1D Cellular Automata (Wolfram Rules for Rhythmic & Pitch Grids)

Melodies and rhythmic slices are seeded using elementary 1D Cellular Automata over $N$ metric steps. Given a state cell $c_i^{(t)} \in \{0, 1\}$, the next generation is determined by rule function $f$:

$$c_i^{(t+1)} = f\left(c_{i-1}^{(t)}, c_i^{(t)}, c_{i+1}^{(t)}\right)$$

- **Rule 30 (Aperiodic Chaos)**: Produces complex, non-repeating syncopations and stochastic pitch deviations.
- **Rule 110 (Turing-Complete Self-Organization)**: Creates evolving melodic motifs that balance structure with unexpected variation.
- **Rule 90 (Sierpiński Fractal)**: Yields symmetrically self-similar rhythmic rolls and hockets.

---

## 2.4 Continuous Melodic Morphing & Vector Spline Interpolation

Given two musical phrases—**Source Melody $\mathcal{A}$** and **Target Melody $\mathcal{B}$**—the engine computes a continuous parametric morph path $\mathcal{M}(\alpha)$ for $\alpha \in [0.0, 1.0]$:

```
Melody A (Input Lead):   [ C4 ─── E4 ─── G4 ─────────── C5 ] (Bar 1)
                                  │ (α = 0.5 Morph)
Intermediate State:      [ C4 ─ D4 ─ E4 ─ F#4 ─ G4 ─ B4 ─ C5 ]
                                  │ (α = 1.0)
Melody B (Target Theme): [ G4 ─────── F#4 ────── D4 ─── B3 ] (Bar 4)
```

### Optimal Transport & Wasserstein Metric Pitch-Time Alignment:
1. **Time Warping**: Computes Dynamic Time Warping (DTW) to establish correspondence between onsets in $\mathcal{A}$ and $\mathcal{B}$.
2. **Pitch Path Interpolation**: Linear or cubic Bézier interpolation in scale-degree space:
   $$P_{\text{morph}}(\alpha) = \text{Snap}_{\mathcal{S}}\Big((1 - \alpha) \cdot P_{\mathcal{A}} + \alpha \cdot P_{\mathcal{B}}\Big)$$
3. **Rhythmic Subdivision Morphing**: Uses Björklund Euclidean density crossfades to morph note densities smoothly across bars.

---

## 2.5 Strict Fuxian Species Counterpoint Solver

When generating secondary voices or harmonies, the engine implements a rule-based constraint satisfaction solver based on Johann Joseph Fux’s *Gradus ad Parnassum*:

```
Rule Set:
├── 1. Motion Law: Avoid parallel 5ths and parallel octaves (Cost = ∞).
├── 2. Voice Independence: Maximize contrary and oblique motion (Cost = 0).
├── 3. Stepwise Dominance: Disallow consecutive leaps in the same direction (Cost = 50).
├── 4. Consonance Grid: Downbeats strictly restricted to 3rds, 6ths, 5ths, 8ves (No 2nds, 7ths, or Tritones).
└── 5. Cadential Resolution: Penultimate note must be a leading tone resolving by half-step to the tonic.
```

---

# 3. Next-Gen Transformation Tool Catalog

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                        NEXT-GENERATION ADVANCED OPERATOR SUITE                         │
├──────────────────────────┬──────────────────────────┬──────────────────────────────────┤
│ 1. GENERATIVE GRAMMARS   │ 2. TONNETZ & HARMONICS   │ 3. COUNTERPOINT & CANONS         │
├──────────────────────────┼──────────────────────────┼──────────────────────────────────┤
│ • L-System Branching     │ • Neo-Riemannian (P,L,R) │ • Species Counterpoint Solver    │
│ • Wolfram Rule Automata  │ • Harmonic Tension Field │ • Crab Canon (Cancrizans)        │
│ • 1/f Pink Noise Drift   │ • Tonnetz Torus Wander   │ • Mensuration / Proportion Canon │
├──────────────────────────┼──────────────────────────┼──────────────────────────────────┤
│ 4. MORPHING & WARPING    │ 5. REICHIAN MINIMALISM   │ 6. XENHARMONICS & SCALA          │
├──────────────────────────┼──────────────────────────┼──────────────────────────────────┤
│ • Melody A ➔ B Cross-Morph│ • Reich Phase Shifting   │ • Scala Microtonal Retuning      │
│ • Spline Vector Warp     │ • Metric Modulation Phasing│ • Bohlen-Pierce & 19-EDO       │
│ • Brownian Fractal Fill  │ • Fibonacci Time Dilation│ • Dynamic Micro-Pitch Sweeps     │
└──────────────────────────┴──────────────────────────┴──────────────────────────────────┘
```

---

## Group 1: Generative Grammars & Fractals

### 1.1 L-System Melodic Tree Branching (`L_SYSTEM_BRANCH`)
* **Behavior**: Takes a simple motif and applies an L-System production rule recursively, generating recursive musical ornaments, fractal arpeggios, and melodic offshoots.
* **Parameters**:
  - `Axiom Type`: Linear, Binary Branch, Fractal Tree
  - `Recursion Depth`: $1\dots 4$ iterations
  - `Scale Degree Step`: $\pm 1, \pm 2, \pm 3$ diatonic steps

### 1.2 Wolfram Cellular Automata Sequence (`WOLFRAM_AUTOMATA`)
* **Behavior**: Generates syncopated rhythms, note densities, and pitch sequences using 1D Cellular Automata evolution.
* **Parameters**:
  - `Wolfram Rule`: Rule 30 (Chaos), Rule 90 (Sierpiński), Rule 110 (Complex Evolving)
  - `Step Resolution`: $1/16, 1/32, \text{Triplets}$
  - `Target Metric`: Trigger Mask, Pitch Offset, or Velocity Map

### 1.3 1/f Pink Noise Fractal Drift (`PINK_NOISE_WALK`)
* **Behavior**: Simulates natural, organic musical phrasing using a Voss-McCartney $1/f$ pink noise generator (the mathematical signature of natural speech, classical melodies, and ocean waves).

---

## Group 2: Tonnetz & Neo-Riemannian Harmonic Engines

### 2.1 Neo-Riemannian P-L-R Torus Wander (`NEO_RIEMANNIAN_PLR`)
* **Behavior**: Shifts multi-note chord progressions or melodic arpeggios through continuous geometric cycles on the Tonnetz torus ($P \to L \to R \to P$).
* **Parameters**:
  - `Cycle Sequence`: $PL, PR, LR, PLR$ Hexatonic cycles
  - `Voice Leading Constraint`: Maximum parsimony (minimal semitone displacement)

### 2.2 Harmonic Tension & Gravity Warper (`HARMONIC_TENSION_WARP`)
* **Behavior**: Modulates note pitches according to a configurable tension curve across the measure or phrase.
* **Parameters**:
  - `Tension Curve`: Exponential Build, Parabolic Peak, Sawtooth Drop
  - `Consonance Anchor`: Root Key vs Modal Mediant

---

## Group 3: Melodic Cross-Morphing & Spline Vectors

### 3.1 Melody A ➔ Melody B Vector Morph (`MELODY_CROSS_MORPH`)
* **Behavior**: Takes the notes of the active clip (Melody A) and seamlessly interpolates pitch, rhythm, gate, and dynamics towards a secondary reference phrase (Melody B) across a continuous slider ($\alpha: 0\% \to 100\%$).
* **Parameters**:
  - `Target Reference Clip`: Selection from any other Synth/MIDI track
  - `Morph Mode`: Optimal Transport (Wasserstein), Pitch Dominant, Rhythm Dominant

### 3.2 Spline Vector Contour Warper (`SPLINE_CONTOUR_WARP`)
* **Behavior**: Warps a melody’s overall trajectory using an interactive 4-point Cubic Bézier curve without destroying its underlying micro-rhythms.

---

## Group 4: Species Counterpoint & Symmetrical Canons

### 4.1 Automated Species Counterpoint Generator (`SPECIES_COUNTERPOINT`)
* **Behavior**: Takes an existing monophonic melody (the *Cantus Firmus*) and composes a complementary counter-melody in 1st Species ($1:1$ note-for-note) or 2nd Species ($2:1$ two notes per beat), strictly enforcing Fuxian voice-leading laws.

### 4.2 Crab Canon & Mirror Symmetries (`CRAB_CANON_MIRROR`)
* **Behavior**: Composes an interlocking Bach-style crab canon (*Canon Cancrizans*) where Voice 2 plays the exact retrograde-inversion of Voice 1 simultaneously, engineered so that vertical harmonies remain consonant.

### 4.3 Mensuration & Proportion Canon (`PROPORTION_CANON`)
* **Behavior**: Clones the melody into a secondary voice playing at a proportional speed ratio ($3:2$ Augmentation, $4:3$ Polyrhythm) while transposing pitches to consonant interval relations.

---

## Group 5: Reichian Minimalism & Metric Phase-Shifting

### 5.1 Steve Reich Minimalist Phase Shifter (`REICH_PHASE_SHIFT`)
* **Behavior**: Takes a repetitive melodic motif (e.g., *Clapping Music* / *Piano Phase*) and applies incremental micro-subdivision shifts ($\Delta t = 1/16\text{th}$ per $N$ bars) to generate shifting polyrhythmic phase patterns.

### 5.2 Fibonacci Golden-Ratio Time Dilation (`FIBONACCI_TIME_DILATION`)
* **Behavior**: Expands or compresses note durations and interval distances along the Fibonacci sequence ($1, 1, 2, 3, 5, 8, 13, \dots$), creating organic acceleration/deceleration sweeps.

---

## Group 6: Scala Microtonal & Xenharmonic Engines

### 6.1 Scala (.scl) Tuning Engine (`SCALA_MICROTONAL_RETUNE`)
* **Behavior**: Retunes all pitches to historical, non-Western, or experimental xenharmonic temperaments using standard Scala `.scl` tuning definitions (e.g., Werckmeister III, 19-EDO, 31-EDO, Slendro/Pelog, Harry Partch 43-tone).
* **Parameters**:
  - `Tuning Definition`: Scala `.scl` bitmask / frequency cents table
  - `Root Pitch Reference`: A4 = 440 Hz (or custom 432 Hz / 442 Hz)

---

# 4. Native C++20 Header Specification (`AdvancedMelodyEngine.hpp`)

```cpp
#pragma once
#include <cstdint>
#include <vector>
#include <string>
#include <array>
#include <memory>
#include "MusicTheory.hpp"
#include "NoteTransformEngine.hpp"

namespace Cobass::Transform::Advanced {

enum class NextGenOperatorType : uint32_t {
    // Generative Grammars
    L_SYSTEM_BRANCH         = 100,
    WOLFRAM_AUTOMATA        = 101,
    PINK_NOISE_WALK         = 102,

    // Neo-Riemannian & Tonnetz
    NEO_RIEMANNIAN_PLR      = 103,
    HARMONIC_TENSION_WARP   = 104,

    // Melodic Morphing
    MELODY_CROSS_MORPH      = 105,
    SPLINE_CONTOUR_WARP     = 106,

    // Counterpoint & Canons
    SPECIES_COUNTERPOINT    = 107,
    CRAB_CANON_MIRROR       = 108,
    PROPORTION_CANON        = 109,

    // Reichian Phasing
    REICH_PHASE_SHIFT       = 110,
    FIBONACCI_TIME_DILATION = 111,

    // Microtonal Tuning
    SCALA_MICROTONAL_RETUNE = 112
};

struct TonnetzVertex {
    int32_t pitchClass; // 0..11
    int32_t x;          // Horizontal coordinate on Tonnetz plane
    int32_t y;          // Vertical coordinate on Tonnetz plane
};

struct SpeciesCounterpointRuleSet {
    bool allowParallelFifths = false;   // Strict Fux rule
    bool allowParallelOctaves = false;  // Strict Fux rule
    bool preferContraryMotion = true;
    int32_t maxConsecutiveLeaps = 1;
    float dissonanceWeight = 1.0f;
};

struct AdvancedTransformContext {
    MusicalContext baseContext;
    std::vector<NoteEvent> targetReferenceMelody; // For Cross-Morphing
    std::string scalaTuningDefinition;            // For Microtonal Retuning
    SpeciesCounterpointRuleSet counterpointRules;
    float tensionMultiplier = 1.0f;
};

class AdvancedMelodyEngine {
public:
    /**
     * Executes advanced generative and counterpoint transformations.
     */
    static std::vector<NoteEvent> processAdvanced(
        const std::vector<NoteEvent>& sourceNotes,
        const AdvancedTransformContext& context,
        const TransformRecipe& recipe,
        const LockMasks& masks
    );

    // Theoretical Operator Implementations
    static std::vector<NoteEvent> generateLSystem(const std::vector<NoteEvent>& notes, const AdvancedTransformContext& ctx, const TransformRecipe& recipe);
    static std::vector<NoteEvent> applyWolframAutomata(const std::vector<NoteEvent>& notes, const AdvancedTransformContext& ctx, const TransformRecipe& recipe);
    static std::vector<NoteEvent> applyNeoRiemannianPLR(const std::vector<NoteEvent>& notes, const AdvancedTransformContext& ctx, const TransformRecipe& recipe);
    static std::vector<NoteEvent> applyHarmonicTensionWarp(const std::vector<NoteEvent>& notes, const AdvancedTransformContext& ctx, const TransformRecipe& recipe);
    static std::vector<NoteEvent> morphMelodies(const std::vector<NoteEvent>& sourceA, const std::vector<NoteEvent>& targetB, float alpha, const AdvancedTransformContext& ctx);
    static std::vector<NoteEvent> generateSpeciesCounterpoint(const std::vector<NoteEvent>& cantusFirmus, const AdvancedTransformContext& ctx, const TransformRecipe& recipe);
    static std::vector<NoteEvent> applyReichianPhase(const std::vector<NoteEvent>& notes, const AdvancedTransformContext& ctx, const TransformRecipe& recipe);
};

} // namespace Cobass::Transform::Advanced
```

---

# 5. UI & Workflow Design: Advanced Transform Canvas Studio

```
┌──────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│  ⚡ ADVANCED MELODY & COUNTERPOINT STUDIO                                                      [✕ Close] │
├──────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│  CATEGORY: [🧬 GENERATIVE L-SYSTEM]  [🌀 NEO-RIEMANNIAN]  [🔀 A➔B MORPH]  [🎼 COUNTERPOINT]  [🎛 PHASING] │
├──────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│  OPERATOR: [ 🎼 Johann Joseph Fux: 2nd Species Counterpoint Generator ▾ ]                                │
│                                                                                                          │
│  COUNTERPOINT RULE MATRIX:                                                                               │
│  [✓] Disallow Parallel 5ths/8ves  [✓] Prefer Contrary Motion  [✓] Max 1 Consecutive Leap                │
│  • Voice Separation: [ Lower Register (Octave -1) ▾ ]  • Motion Density: [ 2:1 (Two Notes Per Beat) ▾ ]   │
├──────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│  DYNAMIC HARMONIC TENSION CURVE:                                                                         │
│  ┌────────────────────────────────────────────────────────────────────────────────────────────────────┐  │
│  │ Tension: 1.0 │                     /\                                                              │  │
│  │              │                    /  \               /\                                            │  │
│  │              │                   /    \             /  \                                           │  │
│  │ Tension: 0.0 │───●──────────────/──────\───────────/────\──────────●───────────────────────────────│  │
│  │              │  Bar 1          Bar 2 (Build)     Bar 3 (Apex)    Bar 4 (Cadential Resolution)      │  │
│  └────────────────────────────────────────────────────────────────────────────────────────────────────┘  │
├──────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│  PRESERVATION LOCKS:                                                                                     │
│  [🔒 Downbeats]  [🔒 Pitches]  [🔒 Rhythm]  [🔒 Velocities]  [🔒 Sub/Bass]  [🔒 Scale Degree Guard]       │
├──────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│  [👁 LIVE GHOST VIEWPORT: ON]  [▶ AUDITION]  [■ STOP]  [A / B COMPARE]  |  [↶ UNDO]  [💾 COMMIT TO TRACK]│
└──────────────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

# 6. Implementation Phasing & Milestone Roadmap

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                   ADVANCED MELODY TRANSFORMATION IMPLEMENTATION ROADMAP                │
├──────────┬───────────────────────────────────────────┬─────────────────────────────────┤
│ Phase    │ Description                               │ Deliverables / Key Milestones   │
├──────────┼───────────────────────────────────────────┼─────────────────────────────────┤
│ PHASE 1  │ Generative Grammars Core                  │ L-System melodic trees,         │
│          │ (L-Systems, Wolfram Automata, 1/f Pink)   │ Wolfram Rules (30, 90, 110).    │
├──────────┼───────────────────────────────────────────┼─────────────────────────────────┤
│ PHASE 2  │ Neo-Riemannian Tonnetz & Harmonic Tension │ Tonnetz 2D complex, P/L/R       │
│          │                                           │ cycles, dynamic tension spline. │
├──────────┼───────────────────────────────────────────┼─────────────────────────────────┤
│ PHASE 3  │ Melodic Cross-Morphing Engine             │ Melody A ➔ B interpolation,     │
│          │ (Optimal Transport / Wasserstein DTW)     │ Bézier vector trajectory warp.  │
├──────────┼───────────────────────────────────────────┼─────────────────────────────────┤
│ PHASE 4  │ Species Counterpoint & Canon Solver       │ 1st/2nd Species Fuxian solver,  │
│          │                                           │ Crab canon mirror generator.    │
├──────────┼───────────────────────────────────────────┼─────────────────────────────────┤
│ PHASE 5  │ Reichian Phase & Minimalist Modulation    │ Step-phase delay generator,     │
│          │                                           │ Fibonacci time dilation.        │
├──────────┼───────────────────────────────────────────┼─────────────────────────────────┤
│ PHASE 6  │ Scala (.scl) Microtonal Tuning & UI       │ Full Scala parser, advanced     │
│          │ Interactive Studio Dialog Integration     │ studio dialog, APK release.     │
└──────────┴───────────────────────────────────────────┴─────────────────────────────────┘
```

---

## Phase 1: Generative Grammars Core (L-Systems & Automata)
- Implement `LSystemGenerator` supporting recursive grammatical expansions over musical alphabets.
- Implement `WolframAutomata` generating deterministic 1D cellular automata grids for rhythmic and pitch sequences.
- Implement Voss-McCartney $1/f$ Pink Noise stochastic walk.

## Phase 2: Neo-Riemannian Tonnetz & Tension Field
- Implement 2D Tonnetz simplicial complex mapping pitch classes into geometric coordinates.
- Implement Paralog ($P$), Leittonwechsel ($L$), and Relative ($R$) geometric chord transformations.
- Implement continuous mathematical tension field curves $T(t)$ driving voice attraction.

## Phase 3: Continuous Melodic Cross-Morphing
- Implement Dynamic Time Warping (DTW) and Wasserstein optimal transport between two musical clips.
- Implement continuous parameter $\alpha \in [0, 1]$ morphing pitch, timing, and dynamics.
- Implement 4-point Cubic Bézier contour sculpting.

## Phase 4: Fuxian Species Counterpoint & Canon Solvers
- Implement constraint satisfaction solver for 1st Species ($1:1$) and 2nd Species ($2:1$) counterpoint.
- Implement Crab Canon (*Cancrizans*) generator producing symmetrical two-voice contrapuntal themes.
- Implement Mensuration / Proportion Canon generator with metric ratios ($3:2, 4:3$).

## Phase 5: Reichian Minimalism & Phasing
- Implement Steve Reich phase-shifting algorithms ($N$-bar progressive subdivision slip).
- Implement Fibonacci time dilation and contraction operators.

## Phase 6: Scala Microtonal Engine & Studio UI Integration
- Implement Scala `.scl` file parser computing custom frequency cents tables in pure C++20.
- Create `AdvancedMelodyStudioDialog.java` with interactive tension spline canvas and rule matrices.
- Validate build via `./build.sh` under `NO_GRADLE_POLICY.md` and verify zero-allocation real-time safety.





