package com.maxica.cobass.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import com.maxica.cobass.audio.AudioEngineNative;
import com.maxica.cobass.model.PluginDescriptorItem;
import com.maxica.cobass.plugin.PatchVariationEngine;

import java.util.ArrayDeque;
import java.util.Deque;

public class VariationStudioDialog extends Dialog {

    public interface OnVariationAppliedListener {
        void onVariationCommitted();
    }

    private final int trackId;
    private final int slotIndex;
    private final PluginDescriptorItem descriptor;
    private final OnVariationAppliedListener listener;
    private final PluginPresetDialog.OnPresetActionListener presetActionListener;

    private float intensity = 0.35f; // Default: 35% (Medium)
    private boolean snapHarmonics = true;
    private boolean autoGainStage = true;
    private final PatchVariationEngine.LockMasks lockMasks = new PatchVariationEngine.LockMasks();

    private final Deque<String> stateHistory = new ArrayDeque<>();
    private String currentStateJson = "{}";

    public VariationStudioDialog(
        @NonNull Context context,
        int trackId,
        int slotIndex,
        PluginDescriptorItem descriptor,
        OnVariationAppliedListener listener,
        PluginPresetDialog.OnPresetActionListener presetActionListener
    ) {
        super(context);
        this.trackId = trackId;
        this.slotIndex = slotIndex;
        this.descriptor = descriptor;
        this.listener = listener;
        this.presetActionListener = presetActionListener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        if (AudioEngineNative.isLoaded()) {
            currentStateJson = AudioEngineNative.nativeGetPluginStateJson(trackId, slotIndex);
        }

        ScrollView scroll = new ScrollView(getContext());
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.parseColor("#171922"));
        layout.setPadding(28, 20, 28, 20);
        scroll.addView(layout);

        // Header Title
        TextView title = new TextView(getContext());
        title.setText("🎲 Intelligent Patch Variation Studio");
        title.setTextColor(Color.parseColor("#FFD60A"));
        title.setTextSize(16f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(title);

        TextView subTitle = new TextView(getContext());
        subTitle.setText("Plugin: " + (descriptor != null ? descriptor.getName() : "Synthesizer Engine"));
        subTitle.setTextColor(Color.parseColor("#8E8E93"));
        subTitle.setTextSize(11f);
        subTitle.setPadding(0, 4, 0, 12);
        layout.addView(subTitle);

        // 1. Intensity Slider & Tier Indicator
        TextView txtIntensity = new TextView(getContext());
        txtIntensity.setText("Variation Intensity: Medium (35%)");
        txtIntensity.setTextColor(Color.WHITE);
        txtIntensity.setTextSize(12f);
        txtIntensity.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(txtIntensity);

        SeekBar seekIntensity = new SeekBar(getContext());
        seekIntensity.setMax(95); // 5% to 100%
        seekIntensity.setProgress(30);
        seekIntensity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                intensity = (5 + p) / 100.0f;
                String tierLabel;
                if (intensity <= 0.15f) tierLabel = String.format("Light / Humanize (%d%%)", (int)(intensity * 100));
                else if (intensity <= 0.45f) tierLabel = String.format("Medium / Musical Evolve (%d%%)", (int)(intensity * 100));
                else if (intensity <= 0.75f) tierLabel = String.format("Strong / Sound Redesign (%d%%)", (int)(intensity * 100));
                else tierLabel = String.format("Extreme / Wild Mutate (%d%%)", (int)(intensity * 100));
                txtIntensity.setText("Variation Intensity: " + tierLabel);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        layout.addView(seekIntensity);

        // 2. Sectional Module Locks
        TextView txtLocks = new TextView(getContext());
        txtLocks.setText("MODULE LOCK MASKS (Protect sections from mutation)");
        txtLocks.setTextColor(Color.parseColor("#8E8E93"));
        txtLocks.setTextSize(11f);
        txtLocks.setPadding(0, 12, 0, 6);
        txtLocks.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(txtLocks);

        LinearLayout rowLock1 = new LinearLayout(getContext());
        rowLock1.setOrientation(LinearLayout.HORIZONTAL);
        addLockToggle(rowLock1, "Oscillators", () -> lockMasks.lockOscillators = !lockMasks.lockOscillators, lockMasks.lockOscillators);
        addLockToggle(rowLock1, "Filter / Cutoff", () -> lockMasks.lockFilter = !lockMasks.lockFilter, lockMasks.lockFilter);
        layout.addView(rowLock1);

        LinearLayout rowLock2 = new LinearLayout(getContext());
        rowLock2.setOrientation(LinearLayout.HORIZONTAL);
        rowLock2.setPadding(0, 4, 0, 4);
        addLockToggle(rowLock2, "Envelopes", () -> lockMasks.lockEnvelopes = !lockMasks.lockEnvelopes, lockMasks.lockEnvelopes);
        addLockToggle(rowLock2, "LFO / Mods", () -> lockMasks.lockLfo = !lockMasks.lockLfo, lockMasks.lockLfo);
        layout.addView(rowLock2);

        LinearLayout rowLock3 = new LinearLayout(getContext());
        rowLock3.setOrientation(LinearLayout.HORIZONTAL);
        rowLock3.setPadding(0, 0, 0, 8);
        addLockToggle(rowLock3, "Studio FX Rack", () -> lockMasks.lockFx = !lockMasks.lockFx, lockMasks.lockFx);
        addLockToggle(rowLock3, "Master / Glide", () -> lockMasks.lockMaster = !lockMasks.lockMaster, lockMasks.lockMaster);
        layout.addView(rowLock3);

        // 3. Musical Constraint Toggles
        LinearLayout rowConstraints = new LinearLayout(getContext());
        rowConstraints.setOrientation(LinearLayout.HORIZONTAL);
        rowConstraints.setPadding(0, 4, 0, 10);

        Button btnHarmonics = new Button(getContext());
        btnHarmonics.setText(snapHarmonics ? "✓ Snap Harmonics (0, ±7, ±12st)" : "✗ Free Unquantized Pitch");
        btnHarmonics.setTextSize(10f);
        btnHarmonics.setBackgroundColor(snapHarmonics ? Color.parseColor("#163824") : Color.parseColor("#2C2F3C"));
        btnHarmonics.setTextColor(snapHarmonics ? Color.parseColor("#30D158") : Color.WHITE);
        btnHarmonics.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        btnHarmonics.setOnClickListener(v -> {
            snapHarmonics = !snapHarmonics;
            btnHarmonics.setText(snapHarmonics ? "✓ Snap Harmonics (0, ±7, ±12st)" : "✗ Free Unquantized Pitch");
            btnHarmonics.setBackgroundColor(snapHarmonics ? Color.parseColor("#163824") : Color.parseColor("#2C2F3C"));
            btnHarmonics.setTextColor(snapHarmonics ? Color.parseColor("#30D158") : Color.WHITE);
        });
        rowConstraints.addView(btnHarmonics);

        Button btnAutoGain = new Button(getContext());
        btnAutoGain.setText(autoGainStage ? "✓ Headroom Auto-Trim" : "✗ Manual Gain Staging");
        btnAutoGain.setTextSize(10f);
        btnAutoGain.setBackgroundColor(autoGainStage ? Color.parseColor("#163824") : Color.parseColor("#2C2F3C"));
        btnAutoGain.setTextColor(autoGainStage ? Color.parseColor("#30D158") : Color.WHITE);
        LinearLayout.LayoutParams agLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        agLp.setMargins(6, 0, 0, 0);
        btnAutoGain.setLayoutParams(agLp);
        btnAutoGain.setOnClickListener(v -> {
            autoGainStage = !autoGainStage;
            btnAutoGain.setText(autoGainStage ? "✓ Headroom Auto-Trim" : "✗ Manual Gain Staging");
            btnAutoGain.setBackgroundColor(autoGainStage ? Color.parseColor("#163824") : Color.parseColor("#2C2F3C"));
            btnAutoGain.setTextColor(autoGainStage ? Color.parseColor("#30D158") : Color.WHITE);
        });
        rowConstraints.addView(btnAutoGain);
        layout.addView(rowConstraints);

        // 4. Action Buttons (Mutate, Rollback, Save)
        Button btnMutate = new Button(getContext());
        btnMutate.setText("🎲 MUTATE VARIATION");
        btnMutate.setTextSize(12f);
        btnMutate.setTypeface(null, android.graphics.Typeface.BOLD);
        btnMutate.setBackgroundColor(Color.parseColor("#0A84FF"));
        btnMutate.setTextColor(Color.WHITE);
        btnMutate.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 88));
        btnMutate.setOnClickListener(v -> performMutation());
        layout.addView(btnMutate);

        LinearLayout rowSubActions = new LinearLayout(getContext());
        rowSubActions.setOrientation(LinearLayout.HORIZONTAL);
        rowSubActions.setPadding(0, 6, 0, 10);

        Button btnRollback = new Button(getContext());
        btnRollback.setText("↶ Rollback Previous");
        btnRollback.setTextSize(10f);
        btnRollback.setBackgroundColor(Color.parseColor("#2C2F3C"));
        btnRollback.setTextColor(Color.WHITE);
        btnRollback.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        btnRollback.setOnClickListener(v -> performRollback());
        rowSubActions.addView(btnRollback);

        Button btnSaveNew = new Button(getContext());
        btnSaveNew.setText("💾 Save As Preset");
        btnSaveNew.setTextSize(10f);
        btnSaveNew.setBackgroundColor(Color.parseColor("#163824"));
        btnSaveNew.setTextColor(Color.parseColor("#30D158"));
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        saveLp.setMargins(6, 0, 0, 0);
        btnSaveNew.setLayoutParams(saveLp);
        btnSaveNew.setOnClickListener(v -> {
            if (presetActionListener != null && descriptor != null) {
                PluginPresetDialog.showSaveDialog(getContext(), descriptor, presetActionListener);
            }
        });
        rowSubActions.addView(btnSaveNew);
        layout.addView(rowSubActions);

        // 5. In-Dialog Audition Ribbon
        LinearLayout audRow = new LinearLayout(getContext());
        audRow.setOrientation(LinearLayout.HORIZONTAL);
        audRow.setPadding(0, 4, 0, 10);

        if (descriptor != null && descriptor.getPluginId().contains("drums")) {
            addAuditionPad(audRow, "▶ Kick", 36, Color.parseColor("#0A84FF"));
            addAuditionPad(audRow, "▶ Snare", 38, Color.parseColor("#FF9F0A"));
            addAuditionPad(audRow, "▶ Clap", 39, Color.parseColor("#30D158"));
            addAuditionPad(audRow, "▶ Cl.Hat", 42, Color.parseColor("#BF5AF2"));
            addAuditionPad(audRow, "▶ Op.Hat", 46, Color.parseColor("#FF453A"));
            addAuditionPad(audRow, "▶ Cowbell", 56, Color.parseColor("#FFD60A"));
        } else {
            addAuditionPad(audRow, "▶ C2 Sub", 36, Color.parseColor("#0A84FF"));
            addAuditionPad(audRow, "▶ C3 Bass", 48, Color.parseColor("#30D158"));
            addAuditionPad(audRow, "▶ C4 Pluck", 60, Color.parseColor("#FF9F0A"));
            addAuditionPad(audRow, "▶ C5 Lead", 72, Color.parseColor("#BF5AF2"));
        }
        layout.addView(audRow);

        Button btnDone = new Button(getContext());
        btnDone.setText("Done");
        btnDone.setBackgroundColor(Color.parseColor("#242734"));
        btnDone.setTextColor(Color.WHITE);
        btnDone.setOnClickListener(v -> dismiss());
        layout.addView(btnDone);

        setContentView(scroll);
        if (getWindow() != null) {
            getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    private void addLockToggle(LinearLayout parent, String name, Runnable onToggle, boolean initial) {
        Button btn = new Button(getContext());
        updateLockBtn(btn, name, initial);
        btn.setTextSize(10f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 75, 1f);
        lp.setMargins(0, 0, 4, 0);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(v -> {
            onToggle.run();
            boolean isLocked = btn.getText().toString().contains("LOCKED");
            updateLockBtn(btn, name, !isLocked);
        });
        parent.addView(btn);
    }

    private void updateLockBtn(Button btn, String name, boolean locked) {
        btn.setText(locked ? ("🔒 " + name + " (LOCKED)") : ("🔓 " + name));
        btn.setBackgroundColor(locked ? Color.parseColor("#3D1C22") : Color.parseColor("#20232E"));
        btn.setTextColor(locked ? Color.parseColor("#FF453A") : Color.parseColor("#8E8E93"));
    }

    private void addAuditionPad(LinearLayout parent, String label, int midiNote, int color) {
        Button btn = new Button(getContext());
        btn.setText(label);
        btn.setTextSize(9f);
        btn.setTypeface(null, android.graphics.Typeface.BOLD);
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundColor(color);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 68, 1.0f);
        lp.setMargins(0, 0, 4, 0);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(v -> {
            if (AudioEngineNative.isLoaded()) {
                AudioEngineNative.nativeNoteOn(trackId, midiNote, 1.0f);
            }
        });
        parent.addView(btn);
    }

    private void performMutation() {
        if (!AudioEngineNative.isLoaded() || descriptor == null) return;
        if (currentStateJson != null && !currentStateJson.isEmpty()) {
            if (stateHistory.size() >= 20) stateHistory.removeLast();
            stateHistory.push(currentStateJson);
        }

        String mutated = PatchVariationEngine.mutatePatch(
            descriptor,
            currentStateJson,
            intensity,
            lockMasks,
            snapHarmonics,
            autoGainStage
        );

        currentStateJson = mutated;
        AudioEngineNative.nativeSetPluginStateJson(trackId, slotIndex, mutated);

        if (listener != null) {
            listener.onVariationCommitted();
        }
        Toast.makeText(getContext(), String.format("🎲 Mutated (%d%% variance)", (int)(intensity * 100)), Toast.LENGTH_SHORT).show();
    }

    private void performRollback() {
        if (stateHistory.isEmpty() || !AudioEngineNative.isLoaded()) {
            Toast.makeText(getContext(), "No previous mutation state to rollback", Toast.LENGTH_SHORT).show();
            return;
        }

        String previous = stateHistory.pop();
        currentStateJson = previous;
        AudioEngineNative.nativeSetPluginStateJson(trackId, slotIndex, previous);

        if (listener != null) {
            listener.onVariationCommitted();
        }
        Toast.makeText(getContext(), "↶ Rolled back to previous patch state", Toast.LENGTH_SHORT).show();
    }
}
