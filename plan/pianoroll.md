# Production Piano Roll Editor Subsystem Specification

---

## 1. Implementation Status & Feature Matrix

All six phases of the Piano Roll enhancement are fully implemented and verified under the No-Gradle architecture:

```
+-----------------------------------------------------------------------------------+
|                     PIANO ROLL PRODUCTION STATUS MATRIX                           |
+-----------------------------------------------------------------------------------+
| [✓] PHASE 1: Viewport, 2D Pinch-to-Zoom & In-Dialog Transport Playback            |
| [✓] PHASE 2: Multi-Note Marquee Selection & Clipboard (Duplicate / Mute / Delete) |
| [✓] PHASE 3: Musical Scale Intelligence (Scale Fold Keybed, Scale Snap, Chords)   |
| [✓] PHASE 4: Tool Suite (Pencil, Brush, Split/Razor, Glue, Chop/Dice, Eraser)     |
| [✓] PHASE 5: Expressive Velocity Automation (Head Drag, Ramp Swipe, Compression)  |
| [✓] PHASE 6: Transaction Stack (50-Step Deep Undo/Redo) & JSON Project I/O        |
+-----------------------------------------------------------------------------------+
```

---

## 2. Implemented Tool Reference

| Tool Mode | Gesture / Interaction | Musical Action |
| :--- | :--- | :--- |
| **✏️ PENCIL** | Tap empty space / Drag body / Drag edges | Draw note, translate in pitch & time, or dual-edge trim left ($X_{\text{start}}$) and right ($X_{\text{end}}$) edges. |
| **🖌️ BRUSH** | Drag across canvas rows | Paint a continuous stream of quantized notes aligned to active snap grid. |
| **✂️ SPLIT** | Tap on note body | Cut note into two independent segments at nearest grid subdivision. |
| **🩹 GLUE** | Tap on note | Merge contiguous or overlapping notes of identical pitch into a single legato note. |
| **🔪 CHOP** | Tap on note / Click button | Dice sustained notes into rhythmic slices ($1/8, 1/16, \text{Triplets}$). |
| **🔲 SELECT** | Drag empty canvas / Tap notes | Marquee box multi-selection, batch movement, batch duplicate, and batch delete. |
| **⌫ ERASE** | Tap note / Drag across | Delete individual or highlighted notes. |
| **🎸 CHORD** | Tap keybed or grid | Automatically stamp multi-note chords (Major, Minor, 7th, Maj7, Sus4, Add9, etc.). |
| **FOLD KEYBED**| Click `FOLD: ON/OFF` | Hide all out-of-scale keys to fit multiple octaves cleanly on mobile screens. |
| **SCALE SNAP** | Click `SNAP: ON/OFF` | Auto-correct pitch $Y$ coordinates to the active scale tones. |
