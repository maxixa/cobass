Here is a comprehensive analysis of the current **Cobass** Arranger subsystem along with a complete architectural enhancement plan to elevate the Arranger to full Cubase-level workflow maturity.

---

# 1. Current State & Architectural Gap Analysis

### Current Arranger Capabilities:
- **Arranger Viewport (`ArrangerTimelineView.java`)**:
  - Left track header column and top bar/beat ruler with loop region indicators.
  - Basic clip rendering (rounded rectangles with clip title and minimal note/waveform representations).
  - Basic single-clip touch drag (horizontal time move only) and right-edge resize.
  - Basic Pencil creation, Split (razor at tap), and Eraser.
  - Two-finger pan scrolling.
- **Model & Native Sequencer (`ClipItem.java`, `Sequencer.hpp`, `Transport.hpp`)**:
  - Multi-track clip container with dynamic clip addition/removal.
  - Native playhead tracking and loop range synchronization.

### Critical Gaps & Incomplete Features:
| Area | Current Limitation | Target DAW Standard (Cubase-Inspired) |
| :--- | :--- | :--- |
| **Clip Selection & Clipboard** | Single clip selection only; no marquee box; no multi-clip drag, batch mute, or batch delete. | Multi-clip marquee box selection; multi-clip drag across tracks/time; duplicate (`DUPL`), batch mute (`MUTE`), batch quantize, and copy/paste. |
| **Track-to-Track Dragging** | Moving clips only modifies `startTick` horizontally; clips cannot be dragged vertically to change tracks. | Dynamic track reparenting with automatic track type compatibility check (Synth $\leftrightarrow$ Synth, Audio $\leftrightarrow$ Audio). |
| **Boundary Trimming & Slip Edit** | Only right-edge resize is implemented. | Dual-edge trimming (Left & Right boundary handles) and Slip Editing (shift content within clip boundaries). |
| **Track Headers & Inspector** | Left track headers only show track name and badge; no interaction. | Interactive Track Header: Inline Mute (**M**), Solo (**S**), Record Arm, Track Color, Track Settings/FX shortcut, and Track Add/Delete popup. |
| **Zooming & Viewport Nav** | Panning only via 2-finger pan; no pinch-to-zoom for horizontal time or vertical track height. | 2D Pinch-to-Zoom (`ScaleGestureDetector` handling horizontal tick zoom and vertical track height zoom simultaneously) + toolbar zoom presets. |
| **Undo / Redo History** | Arranger actions (move, trim, split, delete, create) are permanent and lack an undo stack. | Transactional Deep Undo/Redo stack (`ArrangerHistoryManager`) mirroring the Piano Roll's 50-step transaction history. |
| **Advanced Tools & Glue** | No Glue/Merge tool, No Duplicate action, No Loop-to-Selection shortcut in the main bar. | **Glue Tool** (merge adjacent clips on the same track), **Duplicate** (`Ctrl+D` / button), **Snap to Events** (magnetic snap to neighboring clips). |

---

# 2. Complete Arranger Feature Roadmap

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                             ARRANGER ENHANCEMENT ROADMAP                         │
└──────────────────────────────────────────────────────────────────────────────────┘
   │
   ├── PHASE 1: 2D Pinch-to-Zoom & Navigation Engine
   │     ├── Dual-axis ScaleGestureDetector (Time Scale + Track Height)
   │     └── Time Ruler scrubbing & Loop point snap handles
   │
   ├── PHASE 2: Multi-Clip Marquee Selection & Clipboard Actions
   │     ├── Touch Marquee selection box across multiple tracks
   │     ├── Batch Duplicate, Batch Mute/Unmute, and Batch Delete
   │     └── Arranger Undo/Redo transactional stack
   │
   ├── PHASE 3: Multi-Axis Dragging & Track Reparenting
   │     ├── Vertical track reassignment during drag
   │     ├── Track type compatibility validation (Synth vs Audio)
   │     └── Visual ghost preview during movement
   │
   ├── PHASE 4: Dual-Edge Trimming, Slip Editing & Glue Tool
   │     ├── Left-edge start trim handle with offset preservation
   │     ├── Right-edge end trim handle
   │     └── Glue/Merge tool for combining contiguous clips into a single clip
   │
   ├── PHASE 5: Interactive Track Headers & Mini Mixer Inspector
   │     ├── Inline [M], [S], [FX], and [Color] buttons per track row
   │     ├── Track order reordering and Track options dialog
   │     └── Direct track volume/pan mini sliders
   │
   └── PHASE 6: Advanced Grid & Magnetic Snap Engine
         ├── Triplet & Dotted grid subdivisions (1/4T, 1/8T, 1/16T)
         └── Magnetic snap-to-events (snapping clip edges to adjacent clips & markers)
```

---

# 3. Detailed Technical Architecture & Implementation Plan

## Phase 1: 2D Multi-Touch Zoom & Viewport Engine

### 1.1 Viewport Transformation State
Add independent horizontal (`pixelsPerTick`) and vertical (`trackHeight`) scaling factors managed by a dual-axis gesture listener:

```java
// Viewport Configuration
private float pixelsPerTick = 0.25f;  // Range: 0.05f (overview) to 1.5f (detail)
private float trackHeight = 120f;     // Range: 60f (compact) to 220f (tall)
private float scrollX = 0f;
private float scrollY = 0f;
```

### 1.2 Two-Finger Gesture Distinction
- **Distance Change ($> 15\%$ delta)** $\to$ Pinch-to-Zoom:
  - If horizontal span delta $>$ vertical span delta: Adjust `pixelsPerTick` centered around pinch focal tick.
  - If vertical span delta $>$ horizontal span delta: Adjust `trackHeight` centered around pinch focal track.
- **Distance Stable ($\le 15\%$ delta)** $\to$ 2-Finger Pan:
  - Scroll viewport across $X$ and $Y$ simultaneously with inertia damping.

---

## Phase 2: Multi-Clip Marquee Selection & Undo/Redo Stack

### 2.1 Clip Multi-Selection Model
Extend `ClipItem.java`:
```java
public class ClipItem {
    private boolean isSelected = false;
    private boolean isMuted = false;
    
    public boolean isSelected() { return isSelected; }
    public void setSelected(boolean sel) { this.isSelected = sel; }
    public boolean isMuted() { return isMuted; }
    public void setMuted(boolean muted) { this.isMuted = muted; }
    
    public ClipItem copy() {
        ClipItem clone = new ClipItem(id, trackId, startTick, lengthTicks, name, color, type);
        clone.isSelected = this.isSelected;
        clone.isMuted = this.isMuted;
        for (Note n : this.notes) clone.addNote(n.copy());
        if (this.sampleData != null) clone.sampleData = this.sampleData.clone();
        return clone;
    }
}
```

### 2.2 Marquee Interaction Box
- In `ToolMode.SELECT`, dragging on empty canvas area initializes a selection rectangle:
  $$\text{Rect} = [\min(x_1, x_2), \min(y_1, y_2), \max(x_1, x_2), \max(y_1, y_2)]$$
- Every clip whose screen bounding box intersects the marquee rectangle has `clip.setSelected(true)` enabled in real time.

### 2.3 Arranger History Stack (`ArrangerSnapshot`)
Store deep state copies before mutating clip positions, trims, splits, or deletions:
```java
public static class ArrangerSnapshot {
    public final List<ClipItem> clips = new ArrayList<>();
    public ArrangerSnapshot(List<ClipItem> source) {
        for (ClipItem c : source) clips.add(c.copy());
    }
}
private final Deque<ArrangerSnapshot> undoStack = new ArrayDeque<>();
private final Deque<ArrangerSnapshot> redoStack = new ArrayDeque<>();
```

---

## Phase 3: Vertical Track Reparenting & Multi-Axis Dragging

### 3.1 Drag Anchor State Machine
When dragging a selection of clips:
1. Capture all selected clips into an `AnchorClip` list:
   - `initialStartTick`, `initialTrackId`, `initialTrackIndex`.
2. Compute delta tick: $\Delta\text{Ticks} = \text{Snap}(\Delta X / \text{pixelsPerTick})$.
3. Compute delta track row: $\Delta\text{TrackIndex} = \lfloor(Y_{\text{current}} - Y_{\text{start}}) / \text{trackHeight}\rfloor$.
4. Validate track assignment:
   - Target track must exist: $0 \le \text{targetIndex} < \text{tracks.size()}$.
   - Target track type must match clip type (`SYNTH` $\leftrightarrow$ `SYNTH`, `AUDIO` $\leftrightarrow$ `AUDIO`).
5. Render a translucent **Ghost Box Preview** over the prospective destination before touch release.
6. On `ACTION_UP`, commit the new track IDs and tick offsets to the native audio engine via `AudioEngineNative.nativeMoveClip(clipId, newTrackId, newStartTick)`.

---

## Phase 4: Dual-Edge Trimming, Slip Editing & Glue Tool

```
+-------------------------------------------------------------+
| [<] Handle            CLIP CONTENT REGION        Handle [>] |
| Left Trim (Start)                              Right Trim   |
+-------------------------------------------------------------+
```

### 4.1 Boundary Handle Hit Detection
- **Left Edge Handle** ($X_{\text{start}} \le X \le X_{\text{start}} + 24\text{dp}$):
  - Modifies `clip.startTick` and shifts child notes' internal offsets relative to the new origin so timing in project stays stationary.
- **Right Edge Handle** ($X_{\text{end}} - 24\text{dp} \le X \le X_{\text{end}}$):
  - Modifies `clip.lengthTicks` without moving start position.
- **Center Body Drag**:
  - Translates entire clip in time and tracks.

### 4.2 Glue / Merge Tool (`ToolMode.GLUE`)
- Tapping a clip when Glue mode is active merges it with the adjacent following clip on the same track:
  - New clip start = $\min(\text{start}_1, \text{start}_2)$.
  - New clip end = $\max(\text{end}_1, \text{end}_2)$.
  - All notes from clip 2 are offset by $(\text{start}_2 - \text{start}_1)$ and merged into clip 1.
  - Clip 2 is deleted from the engine.

---

## Phase 5: Interactive Track Headers & Mini Inspector

Enhance the left track headers in `ArrangerTimelineView.java` from passive labels to fully interactive control strips:

```
┌────────────────────────────────────────────────────────────┐
│ [COLOR] Track 1: Poly Synth                     [⚙ MORE]   │
│ [ M ] MUTE    [ S ] SOLO    [ FX ] INSERT       VOL: 80%   │
└────────────────────────────────────────────────────────────┘
```

- **[M] Button**: Toggles track mute and syncs to `AudioEngineNative.nativeSetTrackMute()`.
- **[S] Button**: Toggles track solo and syncs to `AudioEngineNative.nativeSetTrackSolo()`.
- **[FX] Button**: Direct trigger opening `FxRackDialog` for that specific track.
- **[⚙] Button**: Opens Track Properties (Rename, Change Color, Delete Track, Reorder Track Up/Down).

---

## Phase 6: Arranger Top Action Bar & Tools UI Update

Update `activity_main.xml` and `MainActivity.java` with a comprehensive DAW toolbar:

```
[COBASS] [↶ Undo] [↷ Redo] | [▶ Play] [■ Stop] [🔁 Loop] [001:01:000] | [🔲 Select] [✏️ Pencil] [✂️ Split] [🩹 Glue] [⌫ Erase] | [ALL] [DUPL] [MUTE] [DEL] | [Snap: 1/4] [🎛 MIXER] [📁 PROJ] [⚙]
```

### Toolbar Action Mapping:
1. **`btnArrUndo` / `btnArrRedo`**: 50-step Arranger timeline history.
2. **`btnArrDuplicate`**: Duplicates selected clips immediately after their boundaries or aligned to the next bar.
3. **`btnArrMuteClip`**: Toggles mute state of selected clips with strikethrough visual indicator.
4. **`btnArrLoopToSel`**: Instantly sets project loop points $[L, R]$ to wrap precisely around the selected clips.
5. **`btnArrGlue`**: Activates Glue mode to combine sliced clips back into cohesive loops.

---

# 4. Implementation Steps Order

1. **Model & Native Extensions**:
   - Add `isSelected`, `isMuted`, `copy()` in `ClipItem.java`.
   - Update `ProjectSerializer.java` to persist clip-level mute flags.
2. **Arranger View Enhancements (`ArrangerTimelineView.java`)**:
   - Implement dual-axis `ScaleGestureDetector` (time zoom + track height zoom).
   - Implement marquee selection rectangle and multi-clip drag.
   - Implement vertical track hopping (reparenting clips between tracks).
   - Implement Left & Right edge trim handles.
   - Implement interactive Track Header buttons (Mute, Solo, FX).
3. **Activity & UI Integration (`activity_main.xml` & `MainActivity.java`)**:
   - Add Arranger Undo/Redo, Duplicate, Mute Clip, Glue Tool, and Loop-To-Selection buttons in the main top bar.
   - Wire up history transaction snapshot recording on all clip mutations.
4. **Verification & Build**:
   - Build via `./build.sh` (enforcing `./tools/doctor.py`, `./tools/module_check.py`, C++20 AAudio engine compilation, AAPT2/D8 packaging).
   - Verify zero boundary violations and test all gesture modes.