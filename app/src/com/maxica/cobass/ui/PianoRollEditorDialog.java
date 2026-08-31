package com.maxica.cobass.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import com.maxica.cobass.R;
import com.maxica.cobass.audio.AudioEngineNative;
import com.maxica.cobass.model.ClipItem;
import com.maxica.cobass.model.MusicalScale;
import com.maxica.cobass.model.SnapGrid;
import com.maxica.cobass.model.ToolMode;
import com.maxica.cobass.sequencer.MidiTransformEngine;
import com.maxica.cobass.sequencer.PianoRollHistoryManager;

import java.util.List;

public class PianoRollEditorDialog extends Dialog {

    private final ClipItem clip;
    private final Runnable onDismissCallback;
    private PianoRollCanvasView pianoRollCanvas;

    private final Handler transportHandler = new Handler(Looper.getMainLooper());
    private boolean isLooping = true;
    private boolean isFollowingPlayhead = true;
    private boolean isRunning = true;

    private final PianoRollHistoryManager historyManager = new PianoRollHistoryManager();

    private Button btnPlay;
    private Button btnStop;
    private Button btnLoop;
    private Button btnFollow;
    private Button btnUndo;
    private Button btnRedo;

    private Button btnSnapGrid;
    private Button btnScale;
    private Button btnScaleFold;
    private Button btnScaleSnap;
    private Button btnChord;

    private Button btnPencil;
    private Button btnBrush;
    private Button btnSplit;
    private Button btnGlue;
    private Button btnChop;
    private Button btnSelect;
    private Button btnErase;

    private static final int COLOR_BG_IDLE = Color.parseColor("#242734");
    private static final int COLOR_BG_ACTIVE = Color.parseColor("#16385C");
    private static final int COLOR_ACCENT_BLUE = Color.parseColor("#0A84FF");
    private static final int COLOR_TEXT_DIM = Color.parseColor("#8E8E93");
    private static final String[] ROOT_NAMES = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};

    public PianoRollEditorDialog(@NonNull Context context, ClipItem clip, Runnable onDismissCallback) {
        super(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        this.clip = clip;
        this.onDismissCallback = onDismissCallback;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_piano_roll);

        if (getWindow() != null) {
            getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.parseColor("#121316")));
        }

        TextView txtClipTitle = findViewById(R.id.txtPianoClipTitle);
        txtClipTitle.setText("Piano: " + clip.getName());

        pianoRollCanvas = findViewById(R.id.pianoRollCanvas);
        pianoRollCanvas.setClip(clip);

        pianoRollCanvas.setEventListener(new PianoRollCanvasView.OnPianoRollEventListener() {
            @Override
            public void onNoteAudition(int note, float velocity, boolean isNoteOn) {
                if (AudioEngineNative.isLoaded()) {
                    if (isNoteOn) {
                        AudioEngineNative.nativeNoteOn(clip.getTrackId(), note, velocity);
                    } else {
                        AudioEngineNative.nativeNoteOff(clip.getTrackId(), note);
                    }
                }
            }

            @Override
            public void onNotesChanged() {
                syncClipNotesToNative();
            }

            @Override
            public void onTransactionCommitted(List<ClipItem.Note> preSnapshot) {
                historyManager.pushUndoState(preSnapshot);
                updateUndoRedoButtonStates();
            }

            @Override
            public void onLoopRangeChanged(long startTick, long endTick, boolean enabled) {
                if (AudioEngineNative.isLoaded()) {
                    AudioEngineNative.nativeSetLoop(clip.getStartTick() + startTick, clip.getStartTick() + endTick, enabled);
                }
            }
        });

        // 1. Undo & Redo Controls
        btnUndo = findViewById(R.id.btnPrUndo);
        btnRedo = findViewById(R.id.btnPrRedo);

        btnUndo.setOnClickListener(v -> performUndo());
        btnRedo.setOnClickListener(v -> performRedo());
        updateUndoRedoButtonStates();

        // 2. Transport Group
        btnPlay = findViewById(R.id.btnPrPlay);
        btnStop = findViewById(R.id.btnPrStop);
        btnLoop = findViewById(R.id.btnPrLoop);
        btnFollow = findViewById(R.id.btnPrFollow);
        Button btnZoomSettings = findViewById(R.id.btnPrZoomSettings);

        updatePlayButtonState();
        updateLoopButtonState();
        updateFollowButtonState();

        btnPlay.setOnClickListener(v -> {
            if (AudioEngineNative.isLoaded()) {
                long clipLoopStart = clip.getStartTick() + (pianoRollCanvas != null ? pianoRollCanvas.getLoopStart() : 0);
                long clipLoopEnd = clip.getStartTick() + (pianoRollCanvas != null ? pianoRollCanvas.getLoopEnd() : clip.getLengthTicks());
                AudioEngineNative.nativeSetLoop(clipLoopStart, clipLoopEnd, isLooping);
                AudioEngineNative.nativeTransportPlayFromStart();
                updatePlayButtonState();
            }
        });

        if (btnStop != null) {
            btnStop.setOnClickListener(v -> {
                if (AudioEngineNative.isLoaded()) {
                    AudioEngineNative.nativeTransportStop();
                    long resetTick = clip.getStartTick() + (pianoRollCanvas != null ? pianoRollCanvas.getLoopStart() : 0);
                    if (pianoRollCanvas != null) {
                        pianoRollCanvas.setPlayheadState(resetTick, false);
                    }
                    updatePlayButtonState();
                }
            });
        }

        btnLoop.setOnClickListener(v -> {
            isLooping = !isLooping;
            updateLoopButtonState();
            if (AudioEngineNative.isLoaded()) {
                long clipLoopStart = clip.getStartTick() + (pianoRollCanvas != null ? pianoRollCanvas.getLoopStart() : 0);
                long clipLoopEnd = clip.getStartTick() + (pianoRollCanvas != null ? pianoRollCanvas.getLoopEnd() : clip.getLengthTicks());
                AudioEngineNative.nativeSetLoop(clipLoopStart, clipLoopEnd, isLooping);
            }
        });

        btnFollow.setOnClickListener(v -> {
            isFollowingPlayhead = !isFollowingPlayhead;
            pianoRollCanvas.setFollowPlayhead(isFollowingPlayhead);
            updateFollowButtonState();
        });

        btnZoomSettings.setOnClickListener(v -> {
            new PianoRollZoomDialog(getContext(), pianoRollCanvas.getPixelsPerTick(), pianoRollCanvas.getNoteRowHeight(),
                (timeScale, pitchScale) -> pianoRollCanvas.set2DZoom(timeScale, pitchScale)).show();
        });

        // 3. Snap Grid Selector
        btnSnapGrid = findViewById(R.id.btnPrSnapGrid);
        if (btnSnapGrid != null) {
            btnSnapGrid.setText("Snap: " + pianoRollCanvas.getSnapGrid().getLabel());
            btnSnapGrid.setOnClickListener(v -> {
                new SnapStudioDialog(getContext(), pianoRollCanvas.getSnapGrid(), grid -> {
                    pianoRollCanvas.setSnapGrid(grid);
                    btnSnapGrid.setText("Snap: " + grid.getLabel());
                }).show();
            });
        }

        // 4. Drawing & Manipulation Tool Palette
        btnPencil = findViewById(R.id.btnPrToolPencil);
        btnBrush = findViewById(R.id.btnPrToolBrush);
        btnSplit = findViewById(R.id.btnPrToolSplit);
        btnGlue = findViewById(R.id.btnPrToolGlue);
        btnChop = findViewById(R.id.btnPrToolChop);
        btnSelect = findViewById(R.id.btnPrToolSelect);
        btnErase = findViewById(R.id.btnPrToolErase);

        btnPencil.setOnClickListener(v -> {
            pianoRollCanvas.setActiveChordIntervals(null);
            updateChordButtonState();
            setToolMode(ToolMode.PENCIL);
        });

        if (btnBrush != null) btnBrush.setOnClickListener(v -> setToolMode(ToolMode.BRUSH));
        if (btnSplit != null) btnSplit.setOnClickListener(v -> setToolMode(ToolMode.SPLIT));
        if (btnGlue != null) {
            btnGlue.setOnClickListener(v -> {
                setToolMode(ToolMode.GLUE);
                historyManager.captureUndoPoint(clip);
                int merged = MidiTransformEngine.glue(clip);
                if (merged > 0) {
                    syncClipNotesToNative();
                    pianoRollCanvas.invalidate();
                    updateUndoRedoButtonStates();
                    Toast.makeText(getContext(), "Glued " + merged + " note transition(s)", Toast.LENGTH_SHORT).show();
                }
            });
        }
        if (btnChop != null) {
            btnChop.setOnClickListener(v -> {
                setToolMode(ToolMode.CHOP);
                historyManager.captureUndoPoint(clip);
                int count = MidiTransformEngine.chop(clip, pianoRollCanvas.getSnapGrid().getTicks());
                if (count > 0) {
                    syncClipNotesToNative();
                    pianoRollCanvas.invalidate();
                    updateUndoRedoButtonStates();
                    Toast.makeText(getContext(), "Chopped into " + count + " slices", Toast.LENGTH_SHORT).show();
                }
            });
        }
        btnSelect.setOnClickListener(v -> setToolMode(ToolMode.SELECT));
        btnErase.setOnClickListener(v -> setToolMode(ToolMode.ERASER));

        setToolMode(ToolMode.PENCIL);

        // 5. Scale Intelligence & Chord Stamper Controls
        btnScale = findViewById(R.id.btnPrScale);
        btnScaleFold = findViewById(R.id.btnPrScaleFold);
        btnScaleSnap = findViewById(R.id.btnPrScaleSnap);
        btnChord = findViewById(R.id.btnPrChord);

        updateScaleButtonState();
        updateFoldButtonState();
        updateScaleSnapButtonState();
        updateChordButtonState();

        if (btnScale != null) {
            btnScale.setOnClickListener(v -> {
                new ScaleStudioDialog(getContext(), pianoRollCanvas.getMusicalScale(), pianoRollCanvas.getRootKey(),
                    (scale, rootKey) -> {
                        pianoRollCanvas.setMusicalScale(scale);
                        pianoRollCanvas.setRootKey(rootKey);
                        updateScaleButtonState();
                    }).show();
            });
        }

        if (btnScaleFold != null) {
            btnScaleFold.setOnClickListener(v -> {
                boolean nextState = !pianoRollCanvas.isScaleFolded();
                pianoRollCanvas.setScaleFolded(nextState);
                updateFoldButtonState();
                Toast.makeText(getContext(), nextState ? "Scale Fold: ON (In-scale keys only)" : "Scale Fold: OFF", Toast.LENGTH_SHORT).show();
            });
        }

        if (btnScaleSnap != null) {
            btnScaleSnap.setOnClickListener(v -> {
                boolean nextState = !pianoRollCanvas.isScaleSnapLocked();
                pianoRollCanvas.setScaleSnapLocked(nextState);
                updateScaleSnapButtonState();
                Toast.makeText(getContext(), nextState ? "Scale Snap: ON (Pitch lock active)" : "Scale Snap: OFF", Toast.LENGTH_SHORT).show();
            });
        }

        if (btnChord != null) {
            btnChord.setOnClickListener(v -> {
                new ChordStudioDialog(getContext(), (label, intervals) -> {
                    pianoRollCanvas.setActiveChordIntervals(intervals);
                    if (intervals != null) {
                        setToolMode(ToolMode.PENCIL);
                        Toast.makeText(getContext(), "Chord Stamper Active: " + label, Toast.LENGTH_SHORT).show();
                    }
                    updateChordButtonState();
                }).show();
            });
        }

        // 6. Selection & Clipboard Actions
        Button btnSelectAll = findViewById(R.id.btnPrSelectAll);
        Button btnDeselect = findViewById(R.id.btnPrDeselect);
        Button btnDuplicate = findViewById(R.id.btnPrDuplicate);
        Button btnMuteNote = findViewById(R.id.btnPrMuteNote);
        Button btnDeleteNotes = findViewById(R.id.btnPrDeleteNotes);

        btnSelectAll.setOnClickListener(v -> {
            clip.selectAll(true);
            pianoRollCanvas.invalidate();
        });

        btnDeselect.setOnClickListener(v -> {
            clip.selectAll(false);
            pianoRollCanvas.invalidate();
        });

        btnDuplicate.setOnClickListener(v -> {
            historyManager.captureUndoPoint(clip);
            List<ClipItem.Note> duplicated = MidiTransformEngine.duplicateSelected(clip, pianoRollCanvas.getSnapGrid().getTicks());
            if (!duplicated.isEmpty()) {
                syncClipNotesToNative();
                pianoRollCanvas.invalidate();
                updateUndoRedoButtonStates();
                Toast.makeText(getContext(), "Duplicated " + duplicated.size() + " note(s)", Toast.LENGTH_SHORT).show();
            } else {
                updateUndoRedoButtonStates();
                Toast.makeText(getContext(), "Select note(s) first", Toast.LENGTH_SHORT).show();
            }
        });

        btnMuteNote.setOnClickListener(v -> {
            historyManager.captureUndoPoint(clip);
            clip.toggleMuteSelected();
            syncClipNotesToNative();
            pianoRollCanvas.invalidate();
            updateUndoRedoButtonStates();
        });

        btnDeleteNotes.setOnClickListener(v -> {
            historyManager.captureUndoPoint(clip);
            clip.deleteSelected();
            syncClipNotesToNative();
            pianoRollCanvas.invalidate();
            updateUndoRedoButtonStates();
        });

        // 7. MIDI Transform Studio
        Button btnTransform = findViewById(R.id.btnPrTransform);
        btnTransform.setOnClickListener(v -> {
            new MidiTransformDialog(getContext(), clip, pianoRollCanvas.getSnapGrid(), new MidiTransformDialog.OnTransformListener() {
                @Override
                public void onTransformApplied() {
                    syncClipNotesToNative();
                    pianoRollCanvas.invalidate();
                    updateUndoRedoButtonStates();
                }

                @Override
                public void onCaptureUndo() {
                    historyManager.captureUndoPoint(clip);
                }
            }).show();
        });

        // 8. Transposition & Close
        Button btnOctUp = findViewById(R.id.btnPrOctUp);
        btnOctUp.setOnClickListener(v -> {
            historyManager.captureUndoPoint(clip);
            MidiTransformEngine.transpose(clip, 12);
            syncClipNotesToNative();
            pianoRollCanvas.invalidate();
            updateUndoRedoButtonStates();
        });

        Button btnOctDown = findViewById(R.id.btnPrOctDown);
        btnOctDown.setOnClickListener(v -> {
            historyManager.captureUndoPoint(clip);
            MidiTransformEngine.transpose(clip, -12);
            syncClipNotesToNative();
            pianoRollCanvas.invalidate();
            updateUndoRedoButtonStates();
        });

        Button btnClose = findViewById(R.id.btnPrClose);
        btnClose.setOnClickListener(v -> dismiss());

        startPlayheadLoop();
    }

    private void updatePlayButtonState() {
        if (btnPlay == null) return;
        boolean playing = AudioEngineNative.isLoaded() && AudioEngineNative.nativeIsPlaying();
        btnPlay.setText(playing ? "⏸" : "▶");
        btnPlay.setTextColor(playing ? Color.parseColor("#30D158") : Color.parseColor("#FFFFFF"));
        btnPlay.setBackgroundColor(playing ? Color.parseColor("#1B4D2E") : Color.parseColor("#163824"));
    }

    private void updateLoopButtonState() {
        if (btnLoop == null) return;
        btnLoop.setBackgroundColor(isLooping ? COLOR_BG_ACTIVE : COLOR_BG_IDLE);
        btnLoop.setTextColor(isLooping ? COLOR_ACCENT_BLUE : COLOR_TEXT_DIM);
    }

    private void updateFollowButtonState() {
        if (btnFollow == null) return;
        btnFollow.setBackgroundColor(isFollowingPlayhead ? COLOR_BG_ACTIVE : COLOR_BG_IDLE);
        btnFollow.setTextColor(isFollowingPlayhead ? COLOR_ACCENT_BLUE : COLOR_TEXT_DIM);
    }

    private void updateScaleButtonState() {
        if (btnScale == null || pianoRollCanvas == null) return;
        String root = ROOT_NAMES[pianoRollCanvas.getRootKey()];
        MusicalScale s = pianoRollCanvas.getMusicalScale();
        btnScale.setText("🎹 " + root + " " + s.name().replace("_", " "));
    }

    private void updateFoldButtonState() {
        if (btnScaleFold == null || pianoRollCanvas == null) return;
        boolean folded = pianoRollCanvas.isScaleFolded();
        btnScaleFold.setText(folded ? "FOLD: ON" : "FOLD: OFF");
        btnScaleFold.setTextColor(folded ? Color.parseColor("#30D158") : COLOR_TEXT_DIM);
        btnScaleFold.setBackgroundColor(folded ? Color.parseColor("#163824") : COLOR_BG_IDLE);
    }

    private void updateScaleSnapButtonState() {
        if (btnScaleSnap == null || pianoRollCanvas == null) return;
        boolean snap = pianoRollCanvas.isScaleSnapLocked();
        btnScaleSnap.setText(snap ? "SNAP: ON" : "SNAP: OFF");
        btnScaleSnap.setTextColor(snap ? Color.parseColor("#30D158") : COLOR_TEXT_DIM);
        btnScaleSnap.setBackgroundColor(snap ? Color.parseColor("#163824") : COLOR_BG_IDLE);
    }

    private void updateChordButtonState() {
        if (btnChord == null || pianoRollCanvas == null) return;
        boolean hasChord = pianoRollCanvas.getActiveChordIntervals() != null;
        btnChord.setText(hasChord ? "🎸 CHORD: ON" : "🎸 CHORD");
        btnChord.setTextColor(hasChord ? Color.parseColor("#FFD60A") : COLOR_ACCENT_BLUE);
        btnChord.setBackgroundColor(hasChord ? Color.parseColor("#4D3814") : COLOR_BG_IDLE);
    }

    private void setToolMode(ToolMode mode) {
        if (pianoRollCanvas != null) pianoRollCanvas.setToolMode(mode);

        if (btnPencil != null) {
            btnPencil.setBackgroundColor(mode == ToolMode.PENCIL ? COLOR_BG_ACTIVE : COLOR_BG_IDLE);
            btnPencil.setTextColor(mode == ToolMode.PENCIL ? COLOR_ACCENT_BLUE : COLOR_TEXT_DIM);
        }
        if (btnBrush != null) {
            btnBrush.setBackgroundColor(mode == ToolMode.BRUSH ? COLOR_BG_ACTIVE : COLOR_BG_IDLE);
            btnBrush.setTextColor(mode == ToolMode.BRUSH ? COLOR_ACCENT_BLUE : COLOR_TEXT_DIM);
        }
        if (btnSplit != null) {
            btnSplit.setBackgroundColor(mode == ToolMode.SPLIT ? COLOR_BG_ACTIVE : COLOR_BG_IDLE);
            btnSplit.setTextColor(mode == ToolMode.SPLIT ? COLOR_ACCENT_BLUE : COLOR_TEXT_DIM);
        }
        if (btnGlue != null) {
            btnGlue.setBackgroundColor(mode == ToolMode.GLUE ? COLOR_BG_ACTIVE : COLOR_BG_IDLE);
            btnGlue.setTextColor(mode == ToolMode.GLUE ? COLOR_ACCENT_BLUE : COLOR_TEXT_DIM);
        }
        if (btnChop != null) {
            btnChop.setBackgroundColor(mode == ToolMode.CHOP ? COLOR_BG_ACTIVE : COLOR_BG_IDLE);
            btnChop.setTextColor(mode == ToolMode.CHOP ? COLOR_ACCENT_BLUE : COLOR_TEXT_DIM);
        }
        if (btnSelect != null) {
            btnSelect.setBackgroundColor(mode == ToolMode.SELECT ? COLOR_BG_ACTIVE : COLOR_BG_IDLE);
            btnSelect.setTextColor(mode == ToolMode.SELECT ? COLOR_ACCENT_BLUE : COLOR_TEXT_DIM);
        }
        if (btnErase != null) {
            btnErase.setBackgroundColor(mode == ToolMode.ERASER ? Color.parseColor("#4D1C1E") : COLOR_BG_IDLE);
            btnErase.setTextColor(mode == ToolMode.ERASER ? Color.parseColor("#FF453A") : COLOR_TEXT_DIM);
        }
    }

    private void performUndo() {
        if (historyManager.undo(clip)) {
            syncClipNotesToNative();
            pianoRollCanvas.invalidate();
            updateUndoRedoButtonStates();
            Toast.makeText(getContext(), "↶ Undo", Toast.LENGTH_SHORT).show();
        }
    }

    private void performRedo() {
        if (historyManager.redo(clip)) {
            syncClipNotesToNative();
            pianoRollCanvas.invalidate();
            updateUndoRedoButtonStates();
            Toast.makeText(getContext(), "↷ Redo", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateUndoRedoButtonStates() {
        if (btnUndo != null) {
            btnUndo.setEnabled(historyManager.canUndo());
            btnUndo.setAlpha(historyManager.canUndo() ? 1.0f : 0.35f);
        }
        if (btnRedo != null) {
            btnRedo.setEnabled(historyManager.canRedo());
            btnRedo.setAlpha(historyManager.canRedo() ? 1.0f : 0.35f);
        }
    }

    private void startPlayheadLoop() {
        transportHandler.post(new Runnable() {
            @Override
            public void run() {
                if (isRunning && AudioEngineNative.isLoaded()) {
                    boolean playing = AudioEngineNative.nativeIsPlaying();
                    long currentTick = AudioEngineNative.nativeGetCurrentTick();
                    if (btnPlay != null) {
                        updatePlayButtonState();
                    }
                    if (pianoRollCanvas != null) {
                        pianoRollCanvas.setPlayheadState(currentTick, playing);
                    }
                }
                if (isRunning) transportHandler.postDelayed(this, 16);
            }
        });
    }

    private void syncClipNotesToNative() {
        if (!AudioEngineNative.isLoaded()) return;
        AudioEngineNative.nativeClearClipNotes(clip.getId());
        for (ClipItem.Note n : clip.getNotes()) {
            if (!n.isMuted) {
                AudioEngineNative.nativeAddNoteToClip(clip.getId(), n.note, n.velocity, n.startOffsetTicks, n.lengthTicks);
            }
        }
    }

    @Override
    public void dismiss() {
        isRunning = false;
        transportHandler.removeCallbacksAndMessages(null);
        if (pianoRollCanvas != null) {
            pianoRollCanvas.stopAudition();
        }
        super.dismiss();
        if (onDismissCallback != null) onDismissCallback.run();
    }
}
