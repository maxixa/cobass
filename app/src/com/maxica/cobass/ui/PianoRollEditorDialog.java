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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import com.maxica.cobass.R;
import com.maxica.cobass.audio.AudioEngineNative;
import com.maxica.cobass.model.ClipItem;
import com.maxica.cobass.model.MusicalScale;
import com.maxica.cobass.model.SnapGrid;
import com.maxica.cobass.model.ToolMode;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public class PianoRollEditorDialog extends Dialog {

    private final ClipItem clip;
    private final Runnable onDismissCallback;
    private PianoRollCanvasView pianoRollCanvas;

    private final Handler transportHandler = new Handler(Looper.getMainLooper());
    private boolean isLooping = true;
    private boolean isFollowingPlayhead = true;
    private boolean isRunning = true;

    private static final int MAX_UNDO_STACK = 50;
    private final Deque<List<ClipItem.Note>> undoStack = new ArrayDeque<>();
    private final Deque<List<ClipItem.Note>> redoStack = new ArrayDeque<>();

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
                pushUndoState(preSnapshot);
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

        btnZoomSettings.setOnClickListener(v -> show2DZoomDialog());

        // 3. Snap Grid Selector
        btnSnapGrid = findViewById(R.id.btnPrSnapGrid);
        if (btnSnapGrid != null) {
            btnSnapGrid.setText("Snap: " + pianoRollCanvas.getSnapGrid().getLabel());
            btnSnapGrid.setOnClickListener(v -> showSnapStudioDialog());
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
                captureUndoPoint();
                int merged = clip.glue();
                if (merged > 0) {
                    syncClipNotesToNative();
                    pianoRollCanvas.invalidate();
                    Toast.makeText(getContext(), "Glued " + merged + " note transition(s)", Toast.LENGTH_SHORT).show();
                }
            });
        }
        if (btnChop != null) {
            btnChop.setOnClickListener(v -> {
                setToolMode(ToolMode.CHOP);
                captureUndoPoint();
                int count = clip.chop(pianoRollCanvas.getSnapGrid().getTicks());
                if (count > 0) {
                    syncClipNotesToNative();
                    pianoRollCanvas.invalidate();
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

        if (btnScale != null) btnScale.setOnClickListener(v -> showScaleStudioDialog());

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

        if (btnChord != null) btnChord.setOnClickListener(v -> showChordStudioDialog());

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
            captureUndoPoint();
            List<ClipItem.Note> duplicated = clip.duplicateSelected(pianoRollCanvas.getSnapGrid().getTicks());
            if (!duplicated.isEmpty()) {
                syncClipNotesToNative();
                pianoRollCanvas.invalidate();
                Toast.makeText(getContext(), "Duplicated " + duplicated.size() + " note(s)", Toast.LENGTH_SHORT).show();
            } else {
                undoStack.pop();
                updateUndoRedoButtonStates();
                Toast.makeText(getContext(), "Select note(s) first", Toast.LENGTH_SHORT).show();
            }
        });

        btnMuteNote.setOnClickListener(v -> {
            captureUndoPoint();
            clip.toggleMuteSelected();
            syncClipNotesToNative();
            pianoRollCanvas.invalidate();
        });

        btnDeleteNotes.setOnClickListener(v -> {
            captureUndoPoint();
            clip.deleteSelected();
            syncClipNotesToNative();
            pianoRollCanvas.invalidate();
        });

        // 7. MIDI Transform Studio
        Button btnTransform = findViewById(R.id.btnPrTransform);
        btnTransform.setOnClickListener(v -> showMidiTransformStudio());

        // 8. Transposition & Close
        Button btnOctUp = findViewById(R.id.btnPrOctUp);
        btnOctUp.setOnClickListener(v -> {
            captureUndoPoint();
            clip.transpose(12);
            syncClipNotesToNative();
            pianoRollCanvas.invalidate();
        });

        Button btnOctDown = findViewById(R.id.btnPrOctDown);
        btnOctDown.setOnClickListener(v -> {
            captureUndoPoint();
            clip.transpose(-12);
            syncClipNotesToNative();
            pianoRollCanvas.invalidate();
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

    private void captureUndoPoint() {
        if (clip != null) {
            pushUndoState(clip.cloneNotesList());
        }
    }

    private void pushUndoState(List<ClipItem.Note> snapshot) {
        if (snapshot == null) return;
        if (undoStack.size() >= MAX_UNDO_STACK) {
            undoStack.removeLast();
        }
        undoStack.push(snapshot);
        redoStack.clear();
        updateUndoRedoButtonStates();
    }

    private void performUndo() {
        if (undoStack.isEmpty() || clip == null) return;

        redoStack.push(clip.cloneNotesList());
        List<ClipItem.Note> previous = undoStack.pop();
        clip.restoreNotesList(previous);

        syncClipNotesToNative();
        pianoRollCanvas.invalidate();
        updateUndoRedoButtonStates();
        Toast.makeText(getContext(), "↶ Undo", Toast.LENGTH_SHORT).show();
    }

    private void performRedo() {
        if (redoStack.isEmpty() || clip == null) return;

        undoStack.push(clip.cloneNotesList());
        List<ClipItem.Note> next = redoStack.pop();
        clip.restoreNotesList(next);

        syncClipNotesToNative();
        pianoRollCanvas.invalidate();
        updateUndoRedoButtonStates();
        Toast.makeText(getContext(), "↷ Redo", Toast.LENGTH_SHORT).show();
    }

    private void updateUndoRedoButtonStates() {
        if (btnUndo != null) {
            btnUndo.setEnabled(!undoStack.isEmpty());
            btnUndo.setAlpha(undoStack.isEmpty() ? 0.35f : 1.0f);
        }
        if (btnRedo != null) {
            btnRedo.setEnabled(!redoStack.isEmpty());
            btnRedo.setAlpha(redoStack.isEmpty() ? 0.35f : 1.0f);
        }
    }

    private void showSnapStudioDialog() {
        if (pianoRollCanvas == null) return;
        Dialog dialog = new Dialog(getContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        ScrollView scroll = new ScrollView(getContext());
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.parseColor("#1C1E24"));
        layout.setPadding(28, 20, 28, 20);
        scroll.addView(layout);

        TextView title = new TextView(getContext());
        title.setText("🧲 Snap & Quantize Studio");
        title.setTextColor(Color.parseColor("#0A84FF"));
        title.setTextSize(16f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(title);

        TextView subTitle = new TextView(getContext());
        subTitle.setText("Active Grid: " + pianoRollCanvas.getSnapGrid().getLabel());
        subTitle.setTextColor(Color.parseColor("#8E8E93"));
        subTitle.setTextSize(11f);
        subTitle.setPadding(0, 4, 0, 12);
        layout.addView(subTitle);

        TextView sec1 = new TextView(getContext());
        sec1.setText("1. STRAIGHT MUSICAL GRID");
        sec1.setTextColor(Color.WHITE);
        sec1.setTextSize(11f);
        sec1.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(sec1);

        LinearLayout rowStraight = new LinearLayout(getContext());
        rowStraight.setOrientation(LinearLayout.HORIZONTAL);
        rowStraight.setPadding(0, 6, 0, 10);

        SnapGrid[] straights = {SnapGrid.BAR_1, SnapGrid.BEAT_1, SnapGrid.BEAT_1_8TH, SnapGrid.BEAT_1_16TH, SnapGrid.BEAT_1_32ND};
        for (SnapGrid sg : straights) {
            Button btn = new Button(getContext());
            btn.setText(sg.getLabel().replace(" Beat", "").replace(" (Beat)", ""));
            btn.setTextSize(10f);
            boolean isSel = (pianoRollCanvas.getSnapGrid() == sg);
            btn.setBackgroundColor(isSel ? Color.parseColor("#0A84FF") : Color.parseColor("#2C2C2E"));
            btn.setTextColor(Color.WHITE);
            btn.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            btn.setOnClickListener(v -> {
                pianoRollCanvas.setSnapGrid(sg);
                if (btnSnapGrid != null) btnSnapGrid.setText("Snap: " + sg.getLabel());
                dialog.dismiss();
            });
            rowStraight.addView(btn);
        }
        layout.addView(rowStraight);

        TextView sec2 = new TextView(getContext());
        sec2.setText("2. TRIPLET GROOVE GRID");
        sec2.setTextColor(Color.WHITE);
        sec2.setTextSize(11f);
        sec2.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(sec2);

        LinearLayout rowTriplets = new LinearLayout(getContext());
        rowTriplets.setOrientation(LinearLayout.HORIZONTAL);
        rowTriplets.setPadding(0, 6, 0, 10);

        SnapGrid[] triplets = {SnapGrid.TRIPLET_1_4, SnapGrid.TRIPLET_1_8, SnapGrid.TRIPLET_1_16, SnapGrid.TRIPLET_1_32};
        for (SnapGrid sg : triplets) {
            Button btn = new Button(getContext());
            btn.setText(sg.getLabel().replace(" Triplet", "T"));
            btn.setTextSize(10f);
            boolean isSel = (pianoRollCanvas.getSnapGrid() == sg);
            btn.setBackgroundColor(isSel ? Color.parseColor("#0A84FF") : Color.parseColor("#2C2C2E"));
            btn.setTextColor(Color.WHITE);
            btn.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            btn.setOnClickListener(v -> {
                pianoRollCanvas.setSnapGrid(sg);
                if (btnSnapGrid != null) btnSnapGrid.setText("Snap: " + sg.getLabel());
                dialog.dismiss();
            });
            rowTriplets.addView(btn);
        }
        layout.addView(rowTriplets);

        TextView sec3 = new TextView(getContext());
        sec3.setText("3. DOTTED SUBDIVISIONS & FREE");
        sec3.setTextColor(Color.WHITE);
        sec3.setTextSize(11f);
        sec3.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(sec3);

        LinearLayout rowDotted = new LinearLayout(getContext());
        rowDotted.setOrientation(LinearLayout.HORIZONTAL);
        rowDotted.setPadding(0, 6, 0, 10);

        SnapGrid[] dotted = {SnapGrid.DOTTED_1_4, SnapGrid.DOTTED_1_8, SnapGrid.DOTTED_1_16, SnapGrid.OFF};
        for (SnapGrid sg : dotted) {
            Button btn = new Button(getContext());
            btn.setText(sg == SnapGrid.OFF ? "FREE (OFF)" : sg.getLabel().replace(" Dotted", "D"));
            btn.setTextSize(10f);
            boolean isSel = (pianoRollCanvas.getSnapGrid() == sg);
            btn.setBackgroundColor(isSel ? Color.parseColor("#0A84FF") : Color.parseColor("#2C2C2E"));
            btn.setTextColor(Color.WHITE);
            btn.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            btn.setOnClickListener(v -> {
                pianoRollCanvas.setSnapGrid(sg);
                if (btnSnapGrid != null) btnSnapGrid.setText(sg == SnapGrid.OFF ? "Snap: OFF" : ("Snap: " + sg.getLabel()));
                dialog.dismiss();
            });
            rowDotted.addView(btn);
        }
        layout.addView(rowDotted);

        Button btnDone = new Button(getContext());
        btnDone.setText("Done");
        btnDone.setBackgroundColor(Color.parseColor("#3A3A3C"));
        btnDone.setTextColor(Color.WHITE);
        btnDone.setOnClickListener(v -> dialog.dismiss());
        layout.addView(btnDone);

        dialog.setContentView(scroll);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.show();
    }

    private void showScaleStudioDialog() {
        Dialog dialog = new Dialog(getContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        ScrollView scroll = new ScrollView(getContext());
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.parseColor("#1C1E24"));
        layout.setPadding(28, 20, 28, 20);
        scroll.addView(layout);

        TextView title = new TextView(getContext());
        title.setText("🎹 Musical Scale & Keybed Intelligence");
        title.setTextColor(Color.parseColor("#0A84FF"));
        title.setTextSize(16f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(title);

        TextView secRoot = new TextView(getContext());
        secRoot.setText("1. SELECT ROOT KEY");
        secRoot.setTextColor(Color.WHITE);
        secRoot.setTextSize(11f);
        secRoot.setPadding(0, 10, 0, 6);
        secRoot.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(secRoot);

        LinearLayout rowRoots1 = new LinearLayout(getContext());
        rowRoots1.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 6; i++) {
            final int rIdx = i;
            Button btn = new Button(getContext());
            btn.setText(ROOT_NAMES[i]);
            btn.setTextSize(10f);
            boolean isSel = (pianoRollCanvas.getRootKey() == i);
            btn.setBackgroundColor(isSel ? Color.parseColor("#0A84FF") : Color.parseColor("#2C2C2E"));
            btn.setTextColor(Color.WHITE);
            btn.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            btn.setOnClickListener(v -> {
                pianoRollCanvas.setRootKey(rIdx);
                updateScaleButtonState();
                dialog.dismiss();
            });
            rowRoots1.addView(btn);
        }
        layout.addView(rowRoots1);

        LinearLayout rowRoots2 = new LinearLayout(getContext());
        rowRoots2.setOrientation(LinearLayout.HORIZONTAL);
        rowRoots2.setPadding(0, 4, 0, 10);
        for (int i = 6; i < 12; i++) {
            final int rIdx = i;
            Button btn = new Button(getContext());
            btn.setText(ROOT_NAMES[i]);
            btn.setTextSize(10f);
            boolean isSel = (pianoRollCanvas.getRootKey() == i);
            btn.setBackgroundColor(isSel ? Color.parseColor("#0A84FF") : Color.parseColor("#2C2C2E"));
            btn.setTextColor(Color.WHITE);
            btn.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            btn.setOnClickListener(v -> {
                pianoRollCanvas.setRootKey(rIdx);
                updateScaleButtonState();
                dialog.dismiss();
            });
            rowRoots2.addView(btn);
        }
        layout.addView(rowRoots2);

        TextView secScale = new TextView(getContext());
        secScale.setText("2. SELECT MUSICAL SCALE");
        secScale.setTextColor(Color.WHITE);
        secScale.setTextSize(11f);
        secScale.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(secScale);

        for (MusicalScale ms : MusicalScale.values()) {
            Button btn = new Button(getContext());
            btn.setText(ms.getLabel());
            btn.setTextSize(11f);
            boolean isSel = (pianoRollCanvas.getMusicalScale() == ms);
            btn.setBackgroundColor(isSel ? Color.parseColor("#1C385C") : Color.parseColor("#242734"));
            btn.setTextColor(isSel ? Color.parseColor("#0A84FF") : Color.WHITE);
            btn.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            btn.setOnClickListener(v -> {
                pianoRollCanvas.setMusicalScale(ms);
                updateScaleButtonState();
                dialog.dismiss();
            });
            layout.addView(btn);
        }

        Button btnDone = new Button(getContext());
        btnDone.setText("Done");
        btnDone.setBackgroundColor(Color.parseColor("#3A3A3C"));
        btnDone.setTextColor(Color.WHITE);
        btnDone.setOnClickListener(v -> dialog.dismiss());
        layout.addView(btnDone);

        dialog.setContentView(scroll);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.show();
    }

    private void showChordStudioDialog() {
        Dialog dialog = new Dialog(getContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        ScrollView scroll = new ScrollView(getContext());
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.parseColor("#1C1E24"));
        layout.setPadding(28, 20, 28, 20);
        scroll.addView(layout);

        TextView title = new TextView(getContext());
        title.setText("🎸 Chord Stamper Presets");
        title.setTextColor(Color.parseColor("#FFD60A"));
        title.setTextSize(16f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(title);

        TextView subTitle = new TextView(getContext());
        subTitle.setText("Tap a preset then tap on the canvas to stamp full chords");
        subTitle.setTextColor(Color.parseColor("#8E8E93"));
        subTitle.setTextSize(11f);
        subTitle.setPadding(0, 4, 0, 12);
        layout.addView(subTitle);

        Button btnSingle = new Button(getContext());
        btnSingle.setText("Single Note (Off / Default)");
        btnSingle.setBackgroundColor(Color.parseColor("#2C2C2E"));
        btnSingle.setTextColor(Color.WHITE);
        btnSingle.setOnClickListener(v -> {
            pianoRollCanvas.setActiveChordIntervals(null);
            updateChordButtonState();
            dialog.dismiss();
        });
        layout.addView(btnSingle);

        addChordOption(layout, dialog, "Major Triad (1 - 3 - 5)", new int[]{0, 4, 7});
        addChordOption(layout, dialog, "Minor Triad (1 - b3 - 5)", new int[]{0, 3, 7});
        addChordOption(layout, dialog, "Dominant 7th (1 - 3 - 5 - b7)", new int[]{0, 4, 7, 10});
        addChordOption(layout, dialog, "Major 7th (1 - 3 - 5 - 7)", new int[]{0, 4, 7, 11});
        addChordOption(layout, dialog, "Minor 7th (1 - b3 - 5 - b7)", new int[]{0, 3, 7, 10});
        addChordOption(layout, dialog, "Suspended 4th (1 - 4 - 5)", new int[]{0, 5, 7});
        addChordOption(layout, dialog, "Diminished (1 - b3 - b5)", new int[]{0, 3, 6});
        addChordOption(layout, dialog, "Augmented (1 - 3 - #5)", new int[]{0, 4, 8});
        addChordOption(layout, dialog, "Add 9 (1 - 3 - 5 - 9)", new int[]{0, 4, 7, 14});

        Button btnCancel = new Button(getContext());
        btnCancel.setText("Cancel");
        btnCancel.setBackgroundColor(Color.parseColor("#3A3A3C"));
        btnCancel.setTextColor(Color.WHITE);
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        layout.addView(btnCancel);

        dialog.setContentView(scroll);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.show();
    }

    private void addChordOption(LinearLayout layout, Dialog dialog, String label, int[] intervals) {
        Button btn = new Button(getContext());
        btn.setText(label);
        btn.setTextSize(11f);
        btn.setBackgroundColor(Color.parseColor("#242734"));
        btn.setTextColor(Color.WHITE);
        btn.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        btn.setOnClickListener(v -> {
            pianoRollCanvas.setActiveChordIntervals(intervals);
            setToolMode(ToolMode.PENCIL);
            updateChordButtonState();
            dialog.dismiss();
            Toast.makeText(getContext(), "Chord Stamper Active: " + label, Toast.LENGTH_SHORT).show();
        });
        layout.addView(btn);
    }

    // --- MIDI TRANSFORMATION & ADVANCED VELOCITY DYNAMICS ---
    private void showMidiTransformStudio() {
        Dialog dialog = new Dialog(getContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        ScrollView scroll = new ScrollView(getContext());
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.parseColor("#1A1C24"));
        layout.setPadding(28, 20, 28, 20);
        scroll.addView(layout);

        TextView title = new TextView(getContext());
        title.setText("⚡ MIDI & Velocity Transformation Studio");
        title.setTextColor(Color.parseColor("#0A84FF"));
        title.setTextSize(16f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(title);

        TextView subTitle = new TextView(getContext());
        int selCount = clip.getSelectedNotes().size();
        subTitle.setText(selCount > 0 ? "Applying to " + selCount + " selected note(s)" : "Applying to all notes in clip");
        subTitle.setTextColor(Color.parseColor("#8E8E93"));
        subTitle.setTextSize(11f);
        subTitle.setPadding(0, 4, 0, 14);
        layout.addView(subTitle);

        // 1. Articulations
        TextView sec1 = new TextView(getContext());
        sec1.setText("1. ARTICULATIONS & CHORD GLUE");
        sec1.setTextColor(Color.WHITE);
        sec1.setTextSize(12f);
        sec1.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(sec1);

        LinearLayout rowArt = new LinearLayout(getContext());
        rowArt.setOrientation(LinearLayout.HORIZONTAL);
        rowArt.setPadding(0, 6, 0, 10);

        Button btnLegato = new Button(getContext());
        btnLegato.setText("Legato (Tie)");
        btnLegato.setBackgroundColor(Color.parseColor("#0A84FF"));
        btnLegato.setTextColor(Color.WHITE);
        btnLegato.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        btnLegato.setOnClickListener(v -> {
            captureUndoPoint();
            int count = clip.legato();
            syncClipNotesToNative();
            pianoRollCanvas.invalidate();
            Toast.makeText(getContext(), "Legato applied to " + count + " transition(s)", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
        rowArt.addView(btnLegato);

        Button btnStrum = new Button(getContext());
        btnStrum.setText("Guitar Strum");
        btnStrum.setBackgroundColor(Color.parseColor("#242734"));
        btnStrum.setTextColor(Color.WHITE);
        btnStrum.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        btnStrum.setOnClickListener(v -> {
            captureUndoPoint();
            int count = clip.strum(25, true);
            syncClipNotesToNative();
            pianoRollCanvas.invalidate();
            Toast.makeText(getContext(), "Strum applied to " + count + " chord note(s)", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
        rowArt.addView(btnStrum);
        layout.addView(rowArt);

        // 2. Humanize
        TextView sec2 = new TextView(getContext());
        sec2.setText("2. ORGANIC HUMANIZE");
        sec2.setTextColor(Color.WHITE);
        sec2.setTextSize(12f);
        sec2.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(sec2);

        TextView txtHumVal = new TextView(getContext());
        txtHumVal.setText("Jitter: ±15 ticks | ±15% Velocity");
        txtHumVal.setTextColor(Color.parseColor("#0A84FF"));
        txtHumVal.setTextSize(11f);
        layout.addView(txtHumVal);

        SeekBar seekHumanize = new SeekBar(getContext());
        seekHumanize.setMax(50);
        seekHumanize.setProgress(20);
        seekHumanize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                txtHumVal.setText(String.format("Jitter: ±%d ticks | ±%d%% Velocity", Math.max(5, progress), Math.max(5, progress)));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        layout.addView(seekHumanize);

        Button btnApplyHumanize = new Button(getContext());
        btnApplyHumanize.setText("Apply Humanize");
        btnApplyHumanize.setBackgroundColor(Color.parseColor("#30D158"));
        btnApplyHumanize.setTextColor(Color.WHITE);
        btnApplyHumanize.setOnClickListener(v -> {
            captureUndoPoint();
            int val = Math.max(5, seekHumanize.getProgress());
            clip.humanize(val, val / 100f);
            syncClipNotesToNative();
            pianoRollCanvas.invalidate();
            Toast.makeText(getContext(), "Humanized " + (selCount > 0 ? selCount : clip.getNotes().size()) + " note(s)", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
        layout.addView(btnApplyHumanize);

        // 3. Groove Quantize with Swing %
        TextView sec3 = new TextView(getContext());
        sec3.setText("3. GROOVE QUANTIZE & SWING");
        sec3.setTextColor(Color.WHITE);
        sec3.setTextSize(12f);
        sec3.setPadding(0, 14, 0, 4);
        sec3.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(sec3);

        TextView txtQuantStr = new TextView(getContext());
        txtQuantStr.setText("Strength: 100% | Swing: 0%");
        txtQuantStr.setTextColor(Color.parseColor("#0A84FF"));
        txtQuantStr.setTextSize(11f);
        layout.addView(txtQuantStr);

        SeekBar seekQuantStr = new SeekBar(getContext());
        seekQuantStr.setMax(100);
        seekQuantStr.setProgress(100);
        layout.addView(seekQuantStr);

        TextView txtSwing = new TextView(getContext());
        txtSwing.setText("Groove Swing: 0%");
        txtSwing.setTextColor(Color.parseColor("#8E8E93"));
        txtSwing.setTextSize(11f);
        layout.addView(txtSwing);

        SeekBar seekSwing = new SeekBar(getContext());
        seekSwing.setMax(75);
        seekSwing.setProgress(0);
        layout.addView(seekSwing);

        SeekBar.OnSeekBarChangeListener qListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                txtQuantStr.setText(String.format("Strength: %d%% | Swing: %d%%", Math.max(10, seekQuantStr.getProgress()), seekSwing.getProgress()));
                txtSwing.setText(String.format("Groove Swing: %d%%", seekSwing.getProgress()));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        };
        seekQuantStr.setOnSeekBarChangeListener(qListener);
        seekSwing.setOnSeekBarChangeListener(qListener);

        Button btnApplyQuantize = new Button(getContext());
        btnApplyQuantize.setText("Apply Quantize (" + pianoRollCanvas.getSnapGrid().getLabel() + ")");
        btnApplyQuantize.setBackgroundColor(Color.parseColor("#0A84FF"));
        btnApplyQuantize.setTextColor(Color.WHITE);
        btnApplyQuantize.setOnClickListener(v -> {
            captureUndoPoint();
            float strength = Math.max(0.1f, seekQuantStr.getProgress() / 100f);
            float swing = seekSwing.getProgress();
            clip.quantizeAdvanced(pianoRollCanvas.getSnapGrid().getTicks(), strength, swing, true, true);
            syncClipNotesToNative();
            pianoRollCanvas.invalidate();
            Toast.makeText(getContext(), "Quantized with " + (int)(strength * 100) + "% strength", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
        layout.addView(btnApplyQuantize);

        // 4. Velocity Compression, Normalization & Flat Presets
        TextView sec4 = new TextView(getContext());
        sec4.setText("4. VELOCITY COMPRESSION & NORMALIZATION");
        sec4.setTextColor(Color.WHITE);
        sec4.setTextSize(12f);
        sec4.setPadding(0, 14, 0, 4);
        sec4.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(sec4);

        LinearLayout rowCompress = new LinearLayout(getContext());
        rowCompress.setOrientation(LinearLayout.HORIZONTAL);

        Button btnCompStudio = new Button(getContext());
        btnCompStudio.setText("Compress (70 - 110)");
        btnCompStudio.setBackgroundColor(Color.parseColor("#242734"));
        btnCompStudio.setTextColor(Color.WHITE);
        btnCompStudio.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        btnCompStudio.setOnClickListener(v -> {
            captureUndoPoint();
            clip.compressVelocities(0.55f, 0.88f);
            syncClipNotesToNative();
            pianoRollCanvas.invalidate();
            Toast.makeText(getContext(), "Velocities compressed (70 - 110)", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
        rowCompress.addView(btnCompStudio);

        Button btnFlat100 = new Button(getContext());
        btnFlat100.setText("Flat (100 / 80%)");
        btnFlat100.setBackgroundColor(Color.parseColor("#242734"));
        btnFlat100.setTextColor(Color.WHITE);
        btnFlat100.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        btnFlat100.setOnClickListener(v -> {
            captureUndoPoint();
            clip.setAllVelocities(0.80f);
            syncClipNotesToNative();
            pianoRollCanvas.invalidate();
            Toast.makeText(getContext(), "Velocities set to 100", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
        rowCompress.addView(btnFlat100);
        layout.addView(rowCompress);

        LinearLayout rowCurves = new LinearLayout(getContext());
        rowCurves.setOrientation(LinearLayout.HORIZONTAL);
        rowCurves.setPadding(0, 4, 0, 4);

        Button btnCrescendo = new Button(getContext());
        btnCrescendo.setText("📈 Crescendo");
        btnCrescendo.setBackgroundColor(Color.parseColor("#0A84FF"));
        btnCrescendo.setTextColor(Color.WHITE);
        btnCrescendo.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        btnCrescendo.setOnClickListener(v -> {
            captureUndoPoint();
            clip.applyCrescendo(0.4f, 1.0f);
            syncClipNotesToNative();
            pianoRollCanvas.invalidate();
            dialog.dismiss();
        });
        rowCurves.addView(btnCrescendo);

        Button btnDecrescendo = new Button(getContext());
        btnDecrescendo.setText("📉 Decrescendo");
        btnDecrescendo.setBackgroundColor(Color.parseColor("#242734"));
        btnDecrescendo.setTextColor(Color.WHITE);
        btnDecrescendo.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        btnDecrescendo.setOnClickListener(v -> {
            captureUndoPoint();
            clip.applyCrescendo(1.0f, 0.4f);
            syncClipNotesToNative();
            pianoRollCanvas.invalidate();
            dialog.dismiss();
        });
        rowCurves.addView(btnDecrescendo);

        Button btnInvertVel = new Button(getContext());
        btnInvertVel.setText("⇄ Invert");
        btnInvertVel.setBackgroundColor(Color.parseColor("#242734"));
        btnInvertVel.setTextColor(Color.WHITE);
        btnInvertVel.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.8f));
        btnInvertVel.setOnClickListener(v -> {
            captureUndoPoint();
            clip.invertVelocities();
            syncClipNotesToNative();
            pianoRollCanvas.invalidate();
            dialog.dismiss();
        });
        rowCurves.addView(btnInvertVel);
        layout.addView(rowCurves);

        Button btnCloseDialog = new Button(getContext());
        btnCloseDialog.setText("Close");
        btnCloseDialog.setBackgroundColor(Color.parseColor("#2C2F3C"));
        btnCloseDialog.setTextColor(Color.WHITE);
        btnCloseDialog.setOnClickListener(v -> dialog.dismiss());
        layout.addView(btnCloseDialog);

        dialog.setContentView(scroll);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.show();
    }

    private void show2DZoomDialog() {
        Dialog zoomDialog = new Dialog(getContext());
        zoomDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.parseColor("#1C1E24"));
        layout.setPadding(28, 20, 28, 20);

        TextView title = new TextView(getContext());
        title.setText("Piano Roll 2D Zoom Settings");
        title.setTextColor(Color.parseColor("#0A84FF"));
        title.setTextSize(15f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(title);

        TextView txtTime = new TextView(getContext());
        txtTime.setText(String.format("Time Zoom (Horizontal): %.2fx", pianoRollCanvas.getPixelsPerTick()));
        txtTime.setTextColor(Color.WHITE);
        txtTime.setPadding(0, 14, 0, 6);
        layout.addView(txtTime);

        SeekBar seekTime = new SeekBar(getContext());
        seekTime.setMax(100);
        int timeProgress = (int) (((pianoRollCanvas.getPixelsPerTick() - 0.15f) / (1.5f - 0.15f)) * 100f);
        seekTime.setProgress(Math.max(0, Math.min(100, timeProgress)));
        layout.addView(seekTime);

        TextView txtPitch = new TextView(getContext());
        txtPitch.setText(String.format("Key Height (Vertical): %.0f dp", pianoRollCanvas.getNoteRowHeight()));
        txtPitch.setTextColor(Color.WHITE);
        txtPitch.setPadding(0, 14, 0, 6);
        layout.addView(txtPitch);

        SeekBar seekPitch = new SeekBar(getContext());
        seekPitch.setMax(100);
        int pitchProgress = (int) (((pianoRollCanvas.getNoteRowHeight() - 24f) / (64f - 24f)) * 100f);
        seekPitch.setProgress(Math.max(0, Math.min(100, pitchProgress)));
        layout.addView(seekPitch);

        SeekBar.OnSeekBarChangeListener listener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float timeScale = 0.15f + (seekTime.getProgress() / 100.0f) * 1.35f;
                float pitchScale = 24.0f + (seekPitch.getProgress() / 100.0f) * 40.0f;
                txtTime.setText(String.format("Time Zoom (Horizontal): %.2fx", timeScale));
                txtPitch.setText(String.format("Key Height (Vertical): %.0f dp", pitchScale));
                pianoRollCanvas.set2DZoom(timeScale, pitchScale);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        };

        seekTime.setOnSeekBarChangeListener(listener);
        seekPitch.setOnSeekBarChangeListener(listener);

        Button btnReset = new Button(getContext());
        btnReset.setText("Reset to Default (1.0x / 42dp)");
        btnReset.setBackgroundColor(Color.parseColor("#242734"));
        btnReset.setTextColor(Color.WHITE);
        btnReset.setOnClickListener(v -> {
            seekTime.setProgress(22);
            seekPitch.setProgress(45);
            pianoRollCanvas.set2DZoom(0.45f, 42f);
        });
        layout.addView(btnReset);

        Button btnDone = new Button(getContext());
        btnDone.setText("Done");
        btnDone.setBackgroundColor(Color.parseColor("#0A84FF"));
        btnDone.setTextColor(Color.WHITE);
        btnDone.setOnClickListener(v -> zoomDialog.dismiss());
        layout.addView(btnDone);

        zoomDialog.setContentView(layout);
        if (zoomDialog.getWindow() != null) {
            zoomDialog.getWindow().setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            zoomDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        zoomDialog.show();
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
