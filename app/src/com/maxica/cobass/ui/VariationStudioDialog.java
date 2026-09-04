package com.maxica.cobass.ui;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
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

        float density = getContext().getResources().getDisplayMetrics().density;

        if (AudioEngineNative.isLoaded()) {
            currentStateJson = AudioEngineNative.nativeGetPluginStateJson(trackId, slotIndex);
        }

        LinearLayout content = new LinearLayout(getContext());
        content.setOrientation(LinearLayout.VERTICAL);

        // 1. Intensity Slider & Tier Indicator
        TextView txtIntensity = new TextView(getContext());
        txtIntensity.setText("Variation Intensity: Medium (35%)");
        CobassTypography.applyBody(txtIntensity);
        txtIntensity.setTextColor(CobassTheme.TEXT_PRIMARY);
        txtIntensity.setTypeface(null, android.graphics.Typeface.BOLD);
        content.addView(txtIntensity);

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
        content.addView(seekIntensity);

        // 2. Sectional Module Locks
        TextView txtLocks = new TextView(getContext());
        txtLocks.setText("MODULE LOCK MASKS (Protect sections from mutation)");
        CobassTypography.applyCaption(txtLocks);
        txtLocks.setPadding(0, Math.round(CobassSpacing.SPACE_MD * density), 0, Math.round(CobassSpacing.SPACE_XS * density));
        content.addView(txtLocks);

        LinearLayout rowLock1 = new LinearLayout(getContext());
        rowLock1.setOrientation(LinearLayout.HORIZONTAL);
        addLockToggle(rowLock1, "Oscillators", () -> lockMasks.lockOscillators = !lockMasks.lockOscillators, lockMasks.lockOscillators);
        addLockToggle(rowLock1, "Filter / Cutoff", () -> lockMasks.lockFilter = !lockMasks.lockFilter, lockMasks.lockFilter);
        content.addView(rowLock1);

        LinearLayout rowLock2 = new LinearLayout(getContext());
        rowLock2.setOrientation(LinearLayout.HORIZONTAL);
        rowLock2.setPadding(0, Math.round(2 * density), 0, Math.round(2 * density));
        addLockToggle(rowLock2, "Envelopes", () -> lockMasks.lockEnvelopes = !lockMasks.lockEnvelopes, lockMasks.lockEnvelopes);
        addLockToggle(rowLock2, "LFO / Mods", () -> lockMasks.lockLfo = !lockMasks.lockLfo, lockMasks.lockLfo);
        content.addView(rowLock2);

        LinearLayout rowLock3 = new LinearLayout(getContext());
        rowLock3.setOrientation(LinearLayout.HORIZONTAL);
        rowLock3.setPadding(0, 0, 0, Math.round(CobassSpacing.SPACE_SM * density));
        addLockToggle(rowLock3, "Studio FX Rack", () -> lockMasks.lockFx = !lockMasks.lockFx, lockMasks.lockFx);
        addLockToggle(rowLock3, "Master / Glide", () -> lockMasks.lockMaster = !lockMasks.lockMaster, lockMasks.lockMaster);
        content.addView(rowLock3);

        // 3. Musical Constraint Toggles
        LinearLayout rowConstraints = new LinearLayout(getContext());
        rowConstraints.setOrientation(LinearLayout.HORIZONTAL);
        rowConstraints.setPadding(0, Math.round(2 * density), 0, Math.round(CobassSpacing.SPACE_MD * density));

        Button btnHarmonics = new Button(getContext());
        btnHarmonics.setText(snapHarmonics ? "✓ Snap Harmonics" : "✗ Free Pitch");
        CobassButton.apply(btnHarmonics, snapHarmonics ? CobassButton.Variant.SUCCESS : CobassButton.Variant.SECONDARY, CobassButton.Size.STANDARD);
        LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        hLp.rightMargin = Math.round(2 * density);
        btnHarmonics.setLayoutParams(hLp);
        btnHarmonics.setOnClickListener(v -> {
            snapHarmonics = !snapHarmonics;
            btnHarmonics.setText(snapHarmonics ? "✓ Snap Harmonics" : "✗ Free Pitch");
            CobassButton.apply(btnHarmonics, snapHarmonics ? CobassButton.Variant.SUCCESS : CobassButton.Variant.SECONDARY, CobassButton.Size.STANDARD);
        });
        rowConstraints.addView(btnHarmonics);

        Button btnAutoGain = new Button(getContext());
        btnAutoGain.setText(autoGainStage ? "✓ Headroom Auto-Trim" : "✗ Manual Gain");
        CobassButton.apply(btnAutoGain, autoGainStage ? CobassButton.Variant.SUCCESS : CobassButton.Variant.SECONDARY, CobassButton.Size.STANDARD);
        LinearLayout.LayoutParams agLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        agLp.leftMargin = Math.round(2 * density);
        btnAutoGain.setLayoutParams(agLp);
        btnAutoGain.setOnClickListener(v -> {
            autoGainStage = !autoGainStage;
            btnAutoGain.setText(autoGainStage ? "✓ Headroom Auto-Trim" : "✗ Manual Gain");
            CobassButton.apply(btnAutoGain, autoGainStage ? CobassButton.Variant.SUCCESS : CobassButton.Variant.SECONDARY, CobassButton.Size.STANDARD);
        });
        rowConstraints.addView(btnAutoGain);
        content.addView(rowConstraints);

        // 4. Action Buttons (Mutate, Rollback, Save)
        Button btnMutate = new Button(getContext());
        btnMutate.setText("🎲 MUTATE VARIATION");
        CobassButton.apply(btnMutate, CobassButton.Variant.PRIMARY, CobassButton.Size.LARGE);
        LinearLayout.LayoutParams mtrLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnMutate.setLayoutParams(mtrLp);
        btnMutate.setOnClickListener(v -> performMutation());
        content.addView(btnMutate);

        LinearLayout rowSubActions = new LinearLayout(getContext());
        rowSubActions.setOrientation(LinearLayout.HORIZONTAL);
        rowSubActions.setPadding(0, Math.round(CobassSpacing.SPACE_SM * density), 0, Math.round(CobassSpacing.SPACE_MD * density));

        Button btnRollback = new Button(getContext());
        btnRollback.setText("↶ Rollback Previous");
        CobassButton.apply(btnRollback, CobassButton.Variant.SECONDARY, CobassButton.Size.STANDARD);
        LinearLayout.LayoutParams rbLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        rbLp.rightMargin = Math.round(2 * density);
        btnRollback.setLayoutParams(rbLp);
        btnRollback.setOnClickListener(v -> performRollback());
        rowSubActions.addView(btnRollback);

        Button btnSaveNew = new Button(getContext());
        btnSaveNew.setText("💾 Save Preset");
        CobassButton.apply(btnSaveNew, CobassButton.Variant.SUCCESS, CobassButton.Size.STANDARD);
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        saveLp.leftMargin = Math.round(2 * density);
        btnSaveNew.setLayoutParams(saveLp);
        btnSaveNew.setOnClickListener(v -> {
            if (presetActionListener != null && descriptor != null) {
                PluginPresetDialog.showSaveDialog(getContext(), descriptor, presetActionListener);
            }
        });
        rowSubActions.addView(btnSaveNew);
        content.addView(rowSubActions);

        // 5. In-Dialog Audition Ribbon
        LinearLayout audRow = new LinearLayout(getContext());
        audRow.setOrientation(LinearLayout.HORIZONTAL);
        audRow.setPadding(0, 0, 0, Math.round(CobassSpacing.SPACE_SM * density));

        if (descriptor != null && descriptor.getPluginId().contains("drums")) {
            addAuditionPad(audRow, "▶ Kick", 36, CobassTheme.DRUM_PALETTE[0]);
            addAuditionPad(audRow, "▶ Snare", 38, CobassTheme.DRUM_PALETTE[1]);
            addAuditionPad(audRow, "▶ Clap", 39, CobassTheme.DRUM_PALETTE[5]);
            addAuditionPad(audRow, "▶ Cl.Hat", 42, CobassTheme.DRUM_PALETTE[2]);
            addAuditionPad(audRow, "▶ Op.Hat", 46, CobassTheme.DRUM_PALETTE[3]);
            addAuditionPad(audRow, "▶ Cowbell", 56, CobassTheme.DRUM_PALETTE[6]);
        } else {
            addAuditionPad(audRow, "▶ C2 Sub", 36, CobassTheme.ACCENT_PRIMARY);
            addAuditionPad(audRow, "▶ C3 Bass", 48, CobassTheme.ACCENT_SUCCESS);
            addAuditionPad(audRow, "▶ C4 Pluck", 60, CobassTheme.ACCENT_ORANGE);
            addAuditionPad(audRow, "▶ C5 Lead", 72, CobassTheme.ACCENT_PURPLE);
        }
        content.addView(audRow);

        LinearLayout root = CobassDialogShell.buildRootContainer(
            getContext(),
            "🎲 Intelligent Patch Variation",
            "Plugin: " + (descriptor != null ? descriptor.getName() : "Synthesizer Engine"),
            content,
            v -> dismiss()
        );

        setContentView(root);
        CobassDialogShell.configureWindow(this);
    }

    private void addLockToggle(LinearLayout parent, String name, Runnable onToggle, boolean initial) {
        float density = getContext().getResources().getDisplayMetrics().density;
        Button btn = new Button(getContext());
        updateLockBtn(btn, name, initial);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(Math.round(2 * density), 0, Math.round(2 * density), 0);
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
        CobassButton.apply(btn, locked ? CobassButton.Variant.DANGER : CobassButton.Variant.SECONDARY, CobassButton.Size.COMPACT);
    }

    private void addAuditionPad(LinearLayout parent, String label, int midiNote, int color) {
        float density = getContext().getResources().getDisplayMetrics().density;
        Button btn = new Button(getContext());
        btn.setText(label);
        CobassButton.apply(btn, CobassButton.Variant.SECONDARY, CobassButton.Size.COMPACT);
        btn.setTextColor(color);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        lp.setMargins(0, 0, Math.round(2 * density), 0);
        btn.setLayoutParams(lp);
        CobassInteraction.attachAuditionTouch(btn, trackId, midiNote, 1.0f);
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
