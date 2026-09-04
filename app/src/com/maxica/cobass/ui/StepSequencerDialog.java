package com.maxica.cobass.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
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
import com.maxica.cobass.model.PluginDescriptorItem;
import com.maxica.cobass.model.StepPatternItem;
import com.maxica.cobass.model.TrackItem;
import com.maxica.cobass.plugin.PluginHostManager;
import com.maxica.cobass.sequencer.EuclideanGenerator;
import com.maxica.cobass.sequencer.StepPatternBaker;
import com.maxica.cobass.sequencer.StepPatternTransformEngine;
import com.maxica.cobass.sequencer.StepPatternVariationEngine;

import java.util.ArrayDeque;
import java.util.Deque;

public class StepSequencerDialog extends Dialog {

    public interface OnStepSequencerActionListener {
        void onPatternBakeToClip(ClipItem bakedClip);
        void onPatternModified();
    }

    private final TrackItem track;
    private final StepPatternItem pattern;
    private final OnStepSequencerActionListener listener;
    private final Runnable onDismissCallback;

    private StepMatrixCanvasView stepMatrixCanvas;
    private LinearLayout layoutParamLockDrawer;
    private TextView txtParamLockTitle;
    private SeekBar seekStepVelocity, seekStepPitch, seekStepProb;
    private TextView txtStepVelocityVal, txtStepPitchVal, txtStepProbVal, txtStepRatchetVal;
    private Button btnPlay, btnStop, btnLoop, btnUndo, btnRedo;

    private final Handler transportHandler = new Handler(Looper.getMainLooper());
    private boolean isPlaying = false;
    private boolean isLooping = true;
    private boolean isRunning = true;

    private final Deque<StepPatternItem> undoStack = new ArrayDeque<>();
    private final Deque<StepPatternItem> redoStack = new ArrayDeque<>();
    private static final int[] RATCHET_CHOICES = {1, 2, 3, 4, 6, 8};

    public StepSequencerDialog(@NonNull Context context, TrackItem track, StepPatternItem pattern,
                               OnStepSequencerActionListener listener, Runnable onDismissCallback) {
        super(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        this.track = track;
        this.pattern = pattern;
        this.listener = listener;
        this.onDismissCallback = onDismissCallback;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_step_sequencer);

        if (getWindow() != null) {
            getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.parseColor("#121316")));
        }

        TextView txtTitle = findViewById(R.id.txtStepClipTitle);
        txtTitle.setText("Pattern: " + (pattern != null ? pattern.getName() : "Drum Matrix"));

        stepMatrixCanvas = findViewById(R.id.stepMatrixCanvas);
        stepMatrixCanvas.setPattern(pattern);

        layoutParamLockDrawer = findViewById(R.id.layoutParamLockDrawer);
        txtParamLockTitle = findViewById(R.id.txtParamLockTitle);
        seekStepVelocity = findViewById(R.id.seekStepVelocity);
        seekStepPitch = findViewById(R.id.seekStepPitch);
        seekStepProb = findViewById(R.id.seekStepProb);
        txtStepVelocityVal = findViewById(R.id.txtStepVelocityVal);
        txtStepPitchVal = findViewById(R.id.txtStepPitchVal);
        txtStepProbVal = findViewById(R.id.txtStepProbVal);
        txtStepRatchetVal = findViewById(R.id.txtStepRatchetVal);

        Button btnRatchetPrev = findViewById(R.id.btnStepRatchetPrev);
        Button btnRatchetNext = findViewById(R.id.btnStepRatchetNext);
        Button btnDeselect = findViewById(R.id.btnStepDeselect);

        btnUndo = findViewById(R.id.btnStepUndo);
        btnRedo = findViewById(R.id.btnStepRedo);
        btnPlay = findViewById(R.id.btnStepPlay);
        btnStop = findViewById(R.id.btnStepStop);
        btnLoop = findViewById(R.id.btnStepLoop);

        stepMatrixCanvas.setEventListener(new StepMatrixCanvasView.OnStepMatrixEventListener() {
            @Override
            public void onStepToggled(int laneIndex, int stepIndex, boolean active) {
                captureUndoPoint();
                syncStepToNative(laneIndex, stepIndex);
                updateParamLockDrawer();
            }

            @Override
            public void onStepVelocityChanged(int laneIndex, int stepIndex, float velocity) {
                syncStepToNative(laneIndex, stepIndex);
                updateParamLockDrawer();
            }

            @Override
            public void onStepSelected(int laneIndex, int stepIndex) {
                updateParamLockDrawer();
            }

            @Override
            public void onLaneAudition(int laneIndex) {
                if (pattern != null && laneIndex >= 0 && laneIndex < pattern.getLanes().size()) {
                    StepPatternItem.Lane lane = pattern.getLanes().get(laneIndex);
                    if (AudioEngineNative.isLoaded()) {
                        AudioEngineNative.nativeNoteOn(track.getId(), lane.midiNote, 1.0f);
                    }
                }
            }

            @Override
            public void onLaneMuteToggled(int laneIndex, boolean muted) {
                syncLaneParamsToNative(laneIndex);
            }

            @Override
            public void onLaneSoloToggled(int laneIndex, boolean solo) {
                syncLaneParamsToNative(laneIndex);
            }

            @Override
            public void onLaneInspectorRequested(int laneIndex) {
                if (pattern != null && laneIndex >= 0 && laneIndex < pattern.getLanes().size()) {
                    StepPatternItem.Lane lane = pattern.getLanes().get(laneIndex);
                    new StepLaneInspectorDialog(getContext(), lane, l -> {
                        syncLaneParamsToNative(laneIndex);
                        stepMatrixCanvas.invalidate();
                    }).show();
                }
            }

            @Override
            public void onPatternModified() {
                if (listener != null) listener.onPatternModified();
            }
        });

        btnUndo.setOnClickListener(v -> performUndo());
        btnRedo.setOnClickListener(v -> performRedo());
        updateUndoRedoUI();

        btnPlay.setOnClickListener(v -> {
            if (AudioEngineNative.isLoaded()) {
                AudioEngineNative.nativeTransportPlayFromStart();
                updateTransportUI();
            }
        });

        btnStop.setOnClickListener(v -> {
            if (AudioEngineNative.isLoaded()) {
                AudioEngineNative.nativeTransportStop();
                updateTransportUI();
            }
        });

        btnLoop.setOnClickListener(v -> {
            isLooping = !isLooping;
            updateTransportUI();
        });

        Button btnPresets = findViewById(R.id.btnStepPresets);
        if (btnPresets != null) {
            btnPresets.setOnClickListener(v -> showFactoryPresetsDialog());
        }

        Button btnStepSynthParams = findViewById(R.id.btnStepSynthParams);
        if (btnStepSynthParams != null) {
            btnStepSynthParams.setOnClickListener(v -> {
                String synthId = AudioEngineNative.isLoaded() ? AudioEngineNative.nativeGetTrackSynthPluginId(track.getId()) : "";
                if (synthId == null || synthId.isEmpty()) {
                    synthId = "com.maxica.cobass.plugins.cobalt_drums";
                    if (AudioEngineNative.isLoaded()) {
                        AudioEngineNative.nativeSetTrackSynthPlugin(track.getId(), synthId);
                    }
                    track.setInstrumentPluginId(synthId);
                }
                PluginDescriptorItem desc = PluginHostManager.getInstance().findPluginById(synthId);
                if (desc != null) {
                    new PluginUiDialog(getContext(), track.getId(), -1, desc, () -> {
                        if (listener != null) listener.onPatternModified();
                    }).show();
                }
            });
        }

        Button btnEuclid = findViewById(R.id.btnStepEuclidean);
        btnEuclid.setOnClickListener(v -> {
            int laneIdx = Math.max(0, stepMatrixCanvas.getSelectedLaneIndex());
            if (pattern != null && laneIdx < pattern.getLanes().size()) {
                StepPatternItem.Lane lane = pattern.getLanes().get(laneIdx);
                new EuclideanStudioDialog(getContext(), lane, (p, st, rot, vel) -> {
                    captureUndoPoint();
                    EuclideanGenerator.applyEuclideanToLane(lane, p, st, rot, vel);
                    syncAllStepsToNative();
                    stepMatrixCanvas.invalidate();
                }).show();
            }
        });

        Button btnRandomize = findViewById(R.id.btnStepRandomize);
        btnRandomize.setOnClickListener(v -> showGrooveVariationDialog());

        Button btnClear = findViewById(R.id.btnStepClear);
        btnClear.setOnClickListener(v -> {
            captureUndoPoint();
            if (pattern != null) {
                for (StepPatternItem.Lane l : pattern.getLanes()) {
                    StepPatternTransformEngine.clearLane(l);
                }
                syncAllStepsToNative();
                stepMatrixCanvas.invalidate();
                Toast.makeText(getContext(), "Cleared Pattern", Toast.LENGTH_SHORT).show();
            }
        });

        Button btnLength = findViewById(R.id.btnStepLength);
        btnLength.setOnClickListener(v -> {
            if (pattern != null) {
                int nextLen = (pattern.getBaseLength() == 16) ? 32 : (pattern.getBaseLength() == 32 ? 48 : (pattern.getBaseLength() == 48 ? 64 : 16));
                pattern.setBaseLength(nextLen);
                for (StepPatternItem.Lane l : pattern.getLanes()) {
                    l.stepCount = nextLen;
                }
                btnLength.setText(nextLen + " Steps");
                syncAllStepsToNative();
                stepMatrixCanvas.invalidate();
            }
        });

        Button btnBake = findViewById(R.id.btnStepBake);
        btnBake.setOnClickListener(v -> {
            if (pattern != null) {
                ClipItem baked = StepPatternBaker.bakePatternToMidiClip(pattern, track.getId(), 0, pattern.getName());
                if (listener != null && baked != null) {
                    listener.onPatternBakeToClip(baked);
                    Toast.makeText(getContext(), "⚡ Baked pattern into Arranger Clip!", Toast.LENGTH_SHORT).show();
                    dismiss();
                }
            }
        });

        Button btnClose = findViewById(R.id.btnStepClose);
        btnClose.setOnClickListener(v -> dismiss());

        btnDeselect.setOnClickListener(v -> {
            stepMatrixCanvas.setSelectedStep(-1, -1);
            updateParamLockDrawer();
        });

        seekStepVelocity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser) {
                    StepPatternItem.Step step = getSelectedStep();
                    if (step != null) {
                        step.velocity = p / 100.0f;
                        txtStepVelocityVal.setText("Vel: " + p + "%");
                        syncStepToNative(stepMatrixCanvas.getSelectedLaneIndex(), stepMatrixCanvas.getSelectedStepIndex());
                        stepMatrixCanvas.invalidate();
                    }
                }
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        seekStepPitch.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser) {
                    StepPatternItem.Step step = getSelectedStep();
                    if (step != null) {
                        int st = p - 24;
                        step.pitchOffset = st;
                        txtStepPitchVal.setText(String.format("Pitch: %+dst", st));
                        syncStepToNative(stepMatrixCanvas.getSelectedLaneIndex(), stepMatrixCanvas.getSelectedStepIndex());
                        stepMatrixCanvas.invalidate();
                    }
                }
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        seekStepProb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser) {
                    StepPatternItem.Step step = getSelectedStep();
                    if (step != null) {
                        step.probability = p / 100.0f;
                        txtStepProbVal.setText("Prob: " + p + "%");
                        syncStepToNative(stepMatrixCanvas.getSelectedLaneIndex(), stepMatrixCanvas.getSelectedStepIndex());
                        stepMatrixCanvas.invalidate();
                    }
                }
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        btnRatchetNext.setOnClickListener(v -> cycleRatchet(true));
        btnRatchetPrev.setOnClickListener(v -> cycleRatchet(false));

        syncAllStepsToNative();
        startPlayheadTicker();
    }

    private void showGrooveVariationDialog() {
        Dialog dialog = new Dialog(getContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        ScrollView scroll = new ScrollView(getContext());
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.parseColor("#171922"));
        layout.setPadding(28, 20, 28, 20);
        scroll.addView(layout);

        TextView title = new TextView(getContext());
        title.setText("🎲 Intelligent Groove Variation Studio");
        title.setTextColor(Color.parseColor("#30D158"));
        title.setTextSize(16f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(title);

        TextView subTitle = new TextView(getContext());
        subTitle.setText("Humanize velocities, inject ratchets, micro-nudges & probability dynamics");
        subTitle.setTextColor(Color.parseColor("#8E8E93"));
        subTitle.setTextSize(11f);
        subTitle.setPadding(0, 4, 0, 12);
        layout.addView(subTitle);

        final float[] grooveIntensity = {0.25f}; // Default 25%

        TextView txtIntensity = new TextView(getContext());
        txtIntensity.setText("Groove Intensity: Medium (25%)");
        txtIntensity.setTextColor(Color.WHITE);
        txtIntensity.setTextSize(12f);
        txtIntensity.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(txtIntensity);

        SeekBar seekGroove = new SeekBar(getContext());
        seekGroove.setMax(95);
        seekGroove.setProgress(20);
        seekGroove.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                grooveIntensity[0] = (5 + p) / 100.0f;
                String label;
                if (grooveIntensity[0] <= 0.15f) label = String.format("Light / Swing Humanize (%d%%)", (int)(grooveIntensity[0] * 100));
                else if (grooveIntensity[0] <= 0.45f) label = String.format("Medium / Groove Evolve (%d%%)", (int)(grooveIntensity[0] * 100));
                else if (grooveIntensity[0] <= 0.75f) label = String.format("Strong / Ratchet Rolls (%d%%)", (int)(grooveIntensity[0] * 100));
                else label = String.format("Extreme / Wild Fills (%d%%)", (int)(grooveIntensity[0] * 100));
                txtIntensity.setText("Groove Intensity: " + label);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        layout.addView(seekGroove);

        final boolean[] opts = {true, true, true, true}; // Vel, Rolls, Nudge, Prob

        LinearLayout row1 = new LinearLayout(getContext());
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setPadding(0, 10, 0, 4);
        addToggleBtn(row1, "Velocities", () -> opts[0] = !opts[0], opts[0]);
        addToggleBtn(row1, "Sub-Ratchets", () -> opts[1] = !opts[1], opts[1]);
        layout.addView(row1);

        LinearLayout row2 = new LinearLayout(getContext());
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setPadding(0, 0, 0, 12);
        addToggleBtn(row2, "Micro-Nudges", () -> opts[2] = !opts[2], opts[2]);
        addToggleBtn(row2, "Probability", () -> opts[3] = !opts[3], opts[3]);
        layout.addView(row2);

        Button btnApply = new Button(getContext());
        btnApply.setText("🎲 APPLY GROOVE VARIATION");
        btnApply.setTextSize(12f);
        btnApply.setTypeface(null, android.graphics.Typeface.BOLD);
        btnApply.setBackgroundColor(Color.parseColor("#30D158"));
        btnApply.setTextColor(Color.WHITE);
        btnApply.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 88));
        btnApply.setOnClickListener(v -> {
            captureUndoPoint();
            StepPatternVariationEngine.mutateGroove(pattern, grooveIntensity[0], opts[0], opts[1], opts[2], opts[3]);
            syncAllStepsToNative();
            stepMatrixCanvas.invalidate();
            dialog.dismiss();
            Toast.makeText(getContext(), String.format("🎲 Groove Mutated (%d%% intensity)", (int)(grooveIntensity[0] * 100)), Toast.LENGTH_SHORT).show();
        });
        layout.addView(btnApply);

        Button btnCancel = new Button(getContext());
        btnCancel.setText("Cancel");
        btnCancel.setBackgroundColor(Color.parseColor("#2C2F3C"));
        btnCancel.setTextColor(Color.WHITE);
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        layout.addView(btnCancel);

        dialog.setContentView(scroll);
        CobassDialogShell.configureWindow(dialog);
        dialog.show();
    }

    private void addToggleBtn(LinearLayout parent, String name, Runnable toggleAction, boolean initial) {
        Button btn = new Button(getContext());
        btn.setText(initial ? ("✓ " + name) : ("✗ " + name));
        btn.setTextSize(10f);
        btn.setBackgroundColor(initial ? Color.parseColor("#163824") : Color.parseColor("#2C2F3C"));
        btn.setTextColor(initial ? Color.parseColor("#30D158") : Color.parseColor("#8E8E93"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 75, 1f);
        lp.setMargins(0, 0, 4, 0);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(v -> {
            toggleAction.run();
            boolean active = btn.getText().toString().startsWith("✓");
            btn.setText(!active ? ("✓ " + name) : ("✗ " + name));
            btn.setBackgroundColor(!active ? Color.parseColor("#163824") : Color.parseColor("#2C2F3C"));
            btn.setTextColor(!active ? Color.parseColor("#30D158") : Color.parseColor("#8E8E93"));
        });
        parent.addView(btn);
    }

    private StepPatternItem.Step getSelectedStep() {
        if (pattern == null) return null;
        int l = stepMatrixCanvas.getSelectedLaneIndex();
        int s = stepMatrixCanvas.getSelectedStepIndex();
        if (l >= 0 && l < pattern.getLanes().size() && s >= 0 && s < pattern.getLanes().get(l).steps.size()) {
            return pattern.getLanes().get(l).steps.get(s);
        }
        return null;
    }

    private void cycleRatchet(boolean forward) {
        StepPatternItem.Step step = getSelectedStep();
        if (step == null) return;
        int currentR = step.ratchets;
        int idx = 0;
        for (int i = 0; i < RATCHET_CHOICES.length; i++) {
            if (RATCHET_CHOICES[i] == currentR) { idx = i; break; }
        }
        idx = forward ? (idx + 1) % RATCHET_CHOICES.length : (idx - 1 + RATCHET_CHOICES.length) % RATCHET_CHOICES.length;
        step.ratchets = RATCHET_CHOICES[idx];
        txtStepRatchetVal.setText("Roll: " + step.ratchets + "x");
        syncStepToNative(stepMatrixCanvas.getSelectedLaneIndex(), stepMatrixCanvas.getSelectedStepIndex());
        stepMatrixCanvas.invalidate();
    }

    private void updateParamLockDrawer() {
        StepPatternItem.Step step = getSelectedStep();
        if (step != null && step.active) {
            layoutParamLockDrawer.setVisibility(View.VISIBLE);
            int l = stepMatrixCanvas.getSelectedLaneIndex();
            int s = stepMatrixCanvas.getSelectedStepIndex();
            txtParamLockTitle.setText(pattern.getLanes().get(l).name + " • Step " + (s + 1));

            seekStepVelocity.setProgress((int) (step.velocity * 100));
            txtStepVelocityVal.setText("Vel: " + (int)(step.velocity * 100) + "%");

            seekStepPitch.setProgress(step.pitchOffset + 24);
            txtStepPitchVal.setText(String.format("Pitch: %+dst", step.pitchOffset));

            seekStepProb.setProgress((int) (step.probability * 100));
            txtStepProbVal.setText("Prob: " + (int)(step.probability * 100) + "%");

            txtStepRatchetVal.setText("Roll: " + step.ratchets + "x");
        } else {
            layoutParamLockDrawer.setVisibility(View.GONE);
        }
    }

    private void captureUndoPoint() {
        if (pattern == null) return;
        if (undoStack.size() >= 30) undoStack.removeLast();
        undoStack.push(pattern.copy());
        redoStack.clear();
        updateUndoRedoUI();
    }

    private void performUndo() {
        if (undoStack.isEmpty() || pattern == null) return;
        redoStack.push(pattern.copy());
        StepPatternItem prev = undoStack.pop();
        restorePatternState(prev);
        updateUndoRedoUI();
    }

    private void performRedo() {
        if (redoStack.isEmpty() || pattern == null) return;
        undoStack.push(pattern.copy());
        StepPatternItem next = redoStack.pop();
        restorePatternState(next);
        updateUndoRedoUI();
    }

    private void restorePatternState(StepPatternItem src) {
        pattern.setBaseLength(src.getBaseLength());
        pattern.getLanes().clear();
        for (StepPatternItem.Lane l : src.getLanes()) {
            pattern.getLanes().add(l.copy());
        }
        syncAllStepsToNative();
        stepMatrixCanvas.invalidate();
        updateParamLockDrawer();
    }

    private void updateUndoRedoUI() {
        CobassInteraction.applyUndoRedoState(btnUndo, !undoStack.isEmpty());
        CobassInteraction.applyUndoRedoState(btnRedo, !redoStack.isEmpty());
    }

    private void updateTransportUI() {
        if (btnPlay == null) return;
        boolean playing = AudioEngineNative.isLoaded() && AudioEngineNative.nativeIsPlaying();
        CobassInteraction.applyPlayState(btnPlay, playing);
    }

    private void syncStepToNative(int laneIndex, int stepIndex) {
        if (!AudioEngineNative.isLoaded() || pattern == null) return;
        if (laneIndex >= 0 && laneIndex < pattern.getLanes().size()) {
            StepPatternItem.Lane lane = pattern.getLanes().get(laneIndex);
            if (stepIndex >= 0 && stepIndex < lane.steps.size()) {
                StepPatternItem.Step s = lane.steps.get(stepIndex);
                AudioEngineNative.nativeSetStepSequencerStep(
                    track.getId(), laneIndex, stepIndex, s.active, s.velocity,
                    s.pitchOffset, s.gate, s.nudge, s.ratchets, s.probability
                );
            }
        }
    }

    private void syncLaneParamsToNative(int laneIndex) {
        if (!AudioEngineNative.isLoaded() || pattern == null) return;
        if (laneIndex >= 0 && laneIndex < pattern.getLanes().size()) {
            StepPatternItem.Lane lane = pattern.getLanes().get(laneIndex);
            AudioEngineNative.nativeSetStepSequencerLaneParams(
                track.getId(), laneIndex, lane.midiNote, lane.stepCount, lane.subdivision.getTicks(),
                lane.volume, lane.pan, lane.isMuted, lane.isSolo
            );
        }
    }

    private void syncAllStepsToNative() {
        if (!AudioEngineNative.isLoaded() || pattern == null) return;
        for (int l = 0; l < pattern.getLanes().size(); l++) {
            syncLaneParamsToNative(l);
            StepPatternItem.Lane lane = pattern.getLanes().get(l);
            for (int s = 0; s < lane.stepCount; s++) {
                syncStepToNative(l, s);
            }
        }
    }

    private void ensureLanes(int targetCount) {
        if (pattern == null) return;
        while (pattern.getLanes().size() < targetCount) {
            int idx = pattern.getLanes().size();
            String name = "Lane " + (idx + 1);
            int note = 60;
            if (idx == 0) { name = "Kick 808"; note = 36; }
            else if (idx == 1) { name = "Snare 909"; note = 38; }
            else if (idx == 2) { name = "Cl. Hat"; note = 42; }
            else if (idx == 3) { name = "Op. Hat"; note = 46; }
            else if (idx == 4) { name = "Clap"; note = 39; }
            else if (idx == 5) { name = "Cowbell / Perc"; note = 56; }
            pattern.getLanes().add(new StepPatternItem.Lane(idx, name, note, pattern.getBaseLength()));
        }
    }

    private void resetAllSteps() {
        ensureLanes(6);
        for (StepPatternItem.Lane l : pattern.getLanes()) {
            for (StepPatternItem.Step s : l.steps) {
                s.active = false;
                s.ratchets = 1;
                s.nudge = 0.0f;
                s.pitchOffset = 0;
                s.velocity = 0.85f;
                s.probability = 1.0f;
                s.gate = 0.75f;
            }
        }
    }

    private void showFactoryPresetsDialog() {
        Dialog presetDialog = new Dialog(getContext());
        presetDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        ScrollView scroll = new ScrollView(getContext());
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.parseColor("#1C1E26"));
        layout.setPadding(28, 20, 28, 20);
        scroll.addView(layout);

        TextView title = new TextView(getContext());
        title.setText("📁 Multi-Genre Drum Pattern Library");
        title.setTextColor(Color.parseColor("#0A84FF"));
        title.setTextSize(16f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(title);

        TextView subTitle = new TextView(getContext());
        subTitle.setText("Select an authentic polymetric groove with automated ratchets & nudges");
        subTitle.setTextColor(Color.parseColor("#8E8E93"));
        subTitle.setTextSize(11f);
        subTitle.setPadding(0, 4, 0, 14);
        layout.addView(subTitle);

        addPresetOption(layout, presetDialog, "🔥 Atlanta 808 Trap (140 BPM)", () -> loadTrapPreset());
        addPresetOption(layout, presetDialog, "🇬🇧 UK Drill 3:3:2 (142 BPM)", () -> loadDrillPreset());
        addPresetOption(layout, presetDialog, "⚡ 4-on-the-Floor Club / Tech House (126 BPM)", () -> loadHousePreset());
        addPresetOption(layout, presetDialog, "☣️ Heavy Riddim Dubstep (140 BPM)", () -> loadDubstepPreset());
        addPresetOption(layout, presetDialog, "🌌 80s Synthwave Outrun (110 BPM)", () -> loadSynthwavePreset());
        addPresetOption(layout, presetDialog, "🌪️ Liquid Jungle / DnB Break (174 BPM)", () -> loadDnBPreset());
        addPresetOption(layout, presetDialog, "📻 90s Boom Bap Golden Era (92 BPM)", () -> loadBoomBapPreset());

        Button btnCancel = new Button(getContext());
        btnCancel.setText("Cancel");
        btnCancel.setBackgroundColor(Color.parseColor("#2C2F3C"));
        btnCancel.setTextColor(Color.WHITE);
        btnCancel.setOnClickListener(v -> presetDialog.dismiss());
        layout.addView(btnCancel);

        presetDialog.setContentView(scroll);
        CobassDialogShell.configureWindow(presetDialog);
        presetDialog.show();
    }

    private void addPresetOption(LinearLayout parent, Dialog dialog, String name, Runnable action) {
        Button btn = new Button(getContext());
        btn.setText(name);
        btn.setTextSize(11f);
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundColor(Color.parseColor("#242734"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 6, 0, 0);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(v -> {
            captureUndoPoint();
            action.run();
            syncAllStepsToNative();
            stepMatrixCanvas.invalidate();
            dialog.dismiss();
            Toast.makeText(getContext(), "Loaded Pattern: " + name, Toast.LENGTH_SHORT).show();
        });
        parent.addView(btn);
    }

    private void loadTrapPreset() {
        if (pattern == null) return;
        pattern.setName("Atlanta 808 Trap");
        pattern.setBaseLength(16);
        resetAllSteps();

        pattern.getLanes().get(0).name = "Kick 808"; pattern.getLanes().get(0).midiNote = 36;
        pattern.getLanes().get(0).steps.get(0).active = true; pattern.getLanes().get(0).steps.get(0).velocity = 1.0f;
        pattern.getLanes().get(0).steps.get(6).active = true; pattern.getLanes().get(0).steps.get(6).velocity = 0.90f;
        pattern.getLanes().get(0).steps.get(10).active = true; pattern.getLanes().get(0).steps.get(10).velocity = 0.95f;

        pattern.getLanes().get(1).name = "Snare Trap"; pattern.getLanes().get(1).midiNote = 38;
        pattern.getLanes().get(1).steps.get(4).active = true;
        pattern.getLanes().get(1).steps.get(12).active = true;

        pattern.getLanes().get(2).name = "Cl. Hat"; pattern.getLanes().get(2).midiNote = 42;
        for (int i = 0; i < 16; i++) {
            pattern.getLanes().get(2).steps.get(i).active = true;
            pattern.getLanes().get(2).steps.get(i).velocity = (i % 2 == 0) ? 0.85f : 0.60f;
        }
        pattern.getLanes().get(2).steps.get(7).ratchets = 3;
        pattern.getLanes().get(2).steps.get(14).ratchets = 2;
        pattern.getLanes().get(2).steps.get(15).ratchets = 4;

        pattern.getLanes().get(3).name = "Op. Hat"; pattern.getLanes().get(3).midiNote = 46;
        pattern.getLanes().get(3).steps.get(2).active = true;
        pattern.getLanes().get(3).steps.get(10).active = true;

        pattern.getLanes().get(4).name = "Clap"; pattern.getLanes().get(4).midiNote = 39;
        pattern.getLanes().get(4).steps.get(4).active = true;
        pattern.getLanes().get(4).steps.get(12).active = true;

        pattern.getLanes().get(5).name = "Phonk Cowbell"; pattern.getLanes().get(5).midiNote = 56;
        pattern.getLanes().get(5).steps.get(3).active = true;
        pattern.getLanes().get(5).steps.get(11).active = true;
    }

    private void loadDrillPreset() {
        if (pattern == null) return;
        pattern.setName("UK Drill Slide");
        pattern.setBaseLength(16);
        resetAllSteps();

        pattern.getLanes().get(0).name = "Drill Kick"; pattern.getLanes().get(0).midiNote = 36;
        pattern.getLanes().get(0).steps.get(0).active = true;
        pattern.getLanes().get(0).steps.get(5).active = true; pattern.getLanes().get(0).steps.get(5).nudge = 0.05f;
        pattern.getLanes().get(0).steps.get(10).active = true;

        pattern.getLanes().get(1).name = "Drill Snare"; pattern.getLanes().get(1).midiNote = 38;
        pattern.getLanes().get(1).steps.get(6).active = true;
        pattern.getLanes().get(1).steps.get(14).active = true;

        pattern.getLanes().get(2).name = "Cl. Hat"; pattern.getLanes().get(2).midiNote = 42;
        for (int i = 0; i < 16; i++) {
            pattern.getLanes().get(2).steps.get(i).active = true;
            pattern.getLanes().get(2).steps.get(i).velocity = (i % 3 == 0) ? 0.90f : 0.65f;
        }
        pattern.getLanes().get(2).steps.get(7).ratchets = 3;
        pattern.getLanes().get(2).steps.get(11).ratchets = 3;

        pattern.getLanes().get(3).name = "Op. Hat"; pattern.getLanes().get(3).midiNote = 46;
        pattern.getLanes().get(3).steps.get(4).active = true;
        pattern.getLanes().get(3).steps.get(12).active = true;

        pattern.getLanes().get(4).name = "Rimshot"; pattern.getLanes().get(4).midiNote = 37;
        pattern.getLanes().get(4).steps.get(2).active = true;
        pattern.getLanes().get(4).steps.get(8).active = true;
    }

    private void loadHousePreset() {
        if (pattern == null) return;
        pattern.setName("4-on-the-Floor Club");
        pattern.setBaseLength(16);
        resetAllSteps();

        pattern.getLanes().get(0).name = "909 Kick"; pattern.getLanes().get(0).midiNote = 36;
        for (int i = 0; i < 16; i += 4) {
            pattern.getLanes().get(0).steps.get(i).active = true;
            pattern.getLanes().get(0).steps.get(i).velocity = 1.0f;
        }

        pattern.getLanes().get(1).name = "Snare Layer"; pattern.getLanes().get(1).midiNote = 38;
        pattern.getLanes().get(1).steps.get(4).active = true;
        pattern.getLanes().get(1).steps.get(12).active = true;

        pattern.getLanes().get(2).name = "Cl. Hat"; pattern.getLanes().get(2).midiNote = 42;
        for (int i = 0; i < 16; i++) {
            if (i % 4 != 2) {
                pattern.getLanes().get(2).steps.get(i).active = true;
                pattern.getLanes().get(2).steps.get(i).velocity = (i % 2 == 0) ? 0.80f : 0.55f;
                pattern.getLanes().get(2).steps.get(i).nudge = (i % 2 == 1) ? 0.08f : 0.0f;
            }
        }

        pattern.getLanes().get(3).name = "Offbeat Op.Hat"; pattern.getLanes().get(3).midiNote = 46;
        for (int i = 2; i < 16; i += 4) {
            pattern.getLanes().get(3).steps.get(i).active = true;
            pattern.getLanes().get(3).steps.get(i).velocity = 0.90f;
        }

        pattern.getLanes().get(4).name = "Club Clap"; pattern.getLanes().get(4).midiNote = 39;
        pattern.getLanes().get(4).steps.get(4).active = true;
        pattern.getLanes().get(4).steps.get(12).active = true;
    }

    private void loadDubstepPreset() {
        if (pattern == null) return;
        pattern.setName("Heavy Riddim Dubstep");
        pattern.setBaseLength(16);
        resetAllSteps();

        pattern.getLanes().get(0).name = "Riddim Kick"; pattern.getLanes().get(0).midiNote = 36;
        pattern.getLanes().get(0).steps.get(0).active = true;
        pattern.getLanes().get(0).steps.get(14).active = true; pattern.getLanes().get(0).steps.get(14).velocity = 0.70f;

        pattern.getLanes().get(1).name = "200Hz Snare"; pattern.getLanes().get(1).midiNote = 38;
        pattern.getLanes().get(1).steps.get(8).active = true; pattern.getLanes().get(1).steps.get(8).velocity = 1.0f;

        pattern.getLanes().get(2).name = "Tick Hat"; pattern.getLanes().get(2).midiNote = 42;
        for (int i = 2; i < 16; i += 2) {
            pattern.getLanes().get(2).steps.get(i).active = true;
            pattern.getLanes().get(2).steps.get(i).velocity = 0.75f;
        }

        pattern.getLanes().get(4).name = "FM Glitch"; pattern.getLanes().get(4).midiNote = 48;
        pattern.getLanes().get(4).steps.get(3).active = true;
        pattern.getLanes().get(4).steps.get(11).active = true;

        pattern.getLanes().get(5).name = "Gun-Cock Rim"; pattern.getLanes().get(5).midiNote = 37;
        pattern.getLanes().get(5).steps.get(7).active = true;
        pattern.getLanes().get(5).steps.get(15).active = true;
    }

    private void loadSynthwavePreset() {
        if (pattern == null) return;
        pattern.setName("80s Synthwave Outrun");
        pattern.setBaseLength(16);
        resetAllSteps();

        pattern.getLanes().get(0).name = "80s Kick"; pattern.getLanes().get(0).midiNote = 36;
        pattern.getLanes().get(0).steps.get(0).active = true;
        pattern.getLanes().get(0).steps.get(6).active = true;
        pattern.getLanes().get(0).steps.get(8).active = true;
        pattern.getLanes().get(0).steps.get(14).active = true;

        pattern.getLanes().get(1).name = "Gated Snare"; pattern.getLanes().get(1).midiNote = 38;
        pattern.getLanes().get(1).steps.get(4).active = true;
        pattern.getLanes().get(1).steps.get(12).active = true;

        pattern.getLanes().get(2).name = "Analog Hat"; pattern.getLanes().get(2).midiNote = 42;
        for (int i = 0; i < 16; i++) {
            pattern.getLanes().get(2).steps.get(i).active = true;
            pattern.getLanes().get(2).steps.get(i).velocity = (i % 4 == 0) ? 0.90f : 0.65f;
        }

        pattern.getLanes().get(5).name = "80s Tom"; pattern.getLanes().get(5).midiNote = 45;
        pattern.getLanes().get(5).steps.get(13).active = true; pattern.getLanes().get(5).steps.get(13).pitchOffset = 4;
        pattern.getLanes().get(5).steps.get(14).active = true; pattern.getLanes().get(5).steps.get(14).pitchOffset = 0;
        pattern.getLanes().get(5).steps.get(15).active = true; pattern.getLanes().get(5).steps.get(15).pitchOffset = -5;
    }

    private void loadDnBPreset() {
        if (pattern == null) return;
        pattern.setName("Liquid Jungle Break");
        pattern.setBaseLength(16);
        resetAllSteps();

        pattern.getLanes().get(0).name = "Break Kick"; pattern.getLanes().get(0).midiNote = 36;
        pattern.getLanes().get(0).steps.get(0).active = true;
        pattern.getLanes().get(0).steps.get(10).active = true;

        pattern.getLanes().get(1).name = "Crack Snare"; pattern.getLanes().get(1).midiNote = 38;
        pattern.getLanes().get(1).steps.get(4).active = true;
        pattern.getLanes().get(1).steps.get(7).active = true; pattern.getLanes().get(1).steps.get(7).velocity = 0.45f;
        pattern.getLanes().get(1).steps.get(12).active = true;

        pattern.getLanes().get(2).name = "Ride / Hat"; pattern.getLanes().get(2).midiNote = 42;
        for (int i = 0; i < 16; i++) {
            pattern.getLanes().get(2).steps.get(i).active = true;
            pattern.getLanes().get(2).steps.get(i).velocity = (i % 2 == 0) ? 0.85f : 0.60f;
        }

        pattern.getLanes().get(3).name = "Op. Hat"; pattern.getLanes().get(3).midiNote = 46;
        pattern.getLanes().get(3).steps.get(2).active = true;
        pattern.getLanes().get(3).steps.get(6).active = true;
        pattern.getLanes().get(3).steps.get(14).active = true;
    }

    private void loadBoomBapPreset() {
        if (pattern == null) return;
        pattern.setName("90s Boom Bap");
        pattern.setBaseLength(16);
        resetAllSteps();

        pattern.getLanes().get(0).name = "Boom Kick"; pattern.getLanes().get(0).midiNote = 36;
        pattern.getLanes().get(0).steps.get(0).active = true;
        pattern.getLanes().get(0).steps.get(3).active = true; pattern.getLanes().get(0).steps.get(3).nudge = 0.08f;
        pattern.getLanes().get(0).steps.get(10).active = true; pattern.getLanes().get(0).steps.get(10).nudge = 0.05f;

        pattern.getLanes().get(1).name = "Bap Snare"; pattern.getLanes().get(1).midiNote = 38;
        pattern.getLanes().get(1).steps.get(4).active = true;
        pattern.getLanes().get(1).steps.get(11).active = true; pattern.getLanes().get(1).steps.get(11).velocity = 0.40f;
        pattern.getLanes().get(1).steps.get(12).active = true;

        pattern.getLanes().get(2).name = "Swung Hat"; pattern.getLanes().get(2).midiNote = 42;
        for (int i = 0; i < 16; i += 2) {
            pattern.getLanes().get(2).steps.get(i).active = true;
            pattern.getLanes().get(2).steps.get(i).velocity = 0.90f;
            if (i + 1 < 16) {
                pattern.getLanes().get(2).steps.get(i + 1).active = true;
                pattern.getLanes().get(2).steps.get(i + 1).velocity = 0.65f;
                pattern.getLanes().get(2).steps.get(i + 1).nudge = 0.12f;
            }
        }
    }

    private void startPlayheadTicker() {
        transportHandler.post(new Runnable() {
            @Override
            public void run() {
                if (isRunning && AudioEngineNative.isLoaded()) {
                    boolean playing = AudioEngineNative.nativeIsPlaying();
                    long currentTick = AudioEngineNative.nativeGetCurrentTick();
                    if (stepMatrixCanvas != null) {
                        stepMatrixCanvas.setPlayheadState(currentTick, playing);
                    }
                    updateTransportUI();
                }
                if (isRunning) transportHandler.postDelayed(this, 16);
            }
        });
    }

    @Override
    public void dismiss() {
        isRunning = false;
        transportHandler.removeCallbacksAndMessages(null);
        super.dismiss();
        if (onDismissCallback != null) onDismissCallback.run();
    }
}
