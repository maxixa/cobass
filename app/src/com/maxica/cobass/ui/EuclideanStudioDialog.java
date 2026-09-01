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
import androidx.annotation.NonNull;
import com.maxica.cobass.model.StepPatternItem;
import com.maxica.cobass.sequencer.EuclideanGenerator;

public class EuclideanStudioDialog extends Dialog {

    public interface OnEuclideanAppliedListener {
        void onApplied(int pulses, int steps, int rotation, float velocity);
    }

    private final StepPatternItem.Lane lane;
    private final OnEuclideanAppliedListener listener;

    private int pulses = 4;
    private int steps = 16;
    private int rotation = 0;
    private float velocity = 0.85f;

    public EuclideanStudioDialog(@NonNull Context context, StepPatternItem.Lane lane, OnEuclideanAppliedListener listener) {
        super(context);
        this.lane = lane;
        this.steps = lane != null ? lane.stepCount : 16;
        this.pulses = Math.max(1, steps / 4);
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        ScrollView scroll = new ScrollView(getContext());
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.parseColor("#1C1E26"));
        layout.setPadding(28, 20, 28, 20);
        scroll.addView(layout);

        TextView title = new TextView(getContext());
        title.setText("🎲 Euclidean Algorithmic Generator");
        title.setTextColor(Color.parseColor("#FFD60A"));
        title.setTextSize(16f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(title);

        TextView subTitle = new TextView(getContext());
        subTitle.setText("Lane: " + (lane != null ? lane.name : "Active Lane"));
        subTitle.setTextColor(Color.parseColor("#8E8E93"));
        subTitle.setTextSize(11f);
        subTitle.setPadding(0, 4, 0, 14);
        layout.addView(subTitle);

        // Pattern Rhythm Preview Box
        TextView txtPreview = new TextView(getContext());
        txtPreview.setTextSize(14f);
        txtPreview.setTextColor(Color.parseColor("#30D158"));
        txtPreview.setBackgroundColor(Color.parseColor("#14161E"));
        txtPreview.setPadding(12, 10, 12, 10);
        txtPreview.setTypeface(android.graphics.Typeface.MONOSPACE);
        layout.addView(txtPreview);

        // Pulses (Hits)
        TextView txtPulses = new TextView(getContext());
        txtPulses.setText("Hits / Pulses (K): 4");
        txtPulses.setTextColor(Color.WHITE);
        txtPulses.setTextSize(12f);
        txtPulses.setPadding(0, 12, 0, 4);
        layout.addView(txtPulses);

        SeekBar seekPulses = new SeekBar(getContext());
        seekPulses.setMax(steps);
        seekPulses.setProgress(pulses);
        layout.addView(seekPulses);

        // Total Steps
        TextView txtSteps = new TextView(getContext());
        txtSteps.setText("Sequence Length (N): " + steps);
        txtSteps.setTextColor(Color.WHITE);
        txtSteps.setTextSize(12f);
        txtSteps.setPadding(0, 10, 0, 4);
        layout.addView(txtSteps);

        SeekBar seekSteps = new SeekBar(getContext());
        seekSteps.setMax(32);
        seekSteps.setProgress(steps);
        layout.addView(seekSteps);

        // Rotation Shift
        TextView txtRot = new TextView(getContext());
        txtRot.setText("Rotation Offset (S): +0 steps");
        txtRot.setTextColor(Color.WHITE);
        txtRot.setTextSize(12f);
        txtRot.setPadding(0, 10, 0, 4);
        layout.addView(txtRot);

        SeekBar seekRot = new SeekBar(getContext());
        seekRot.setMax(16);
        seekRot.setProgress(8);
        layout.addView(seekRot);

        Runnable updatePreview = () -> {
            boolean[] pattern = EuclideanGenerator.generateEuclideanPattern(pulses, steps, rotation);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < pattern.length; i++) {
                if (i > 0 && i % 4 == 0) sb.append(" ");
                sb.append(pattern[i] ? "■" : "·");
            }
            txtPreview.setText(sb.toString());
            txtPulses.setText("Hits / Pulses (K): " + pulses);
            txtSteps.setText("Sequence Length (N): " + steps);
            txtRot.setText(String.format("Rotation Offset (S): %+d steps", rotation));
        };
        updatePreview.run();

        SeekBar.OnSeekBarChangeListener listenerChange = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                steps = Math.max(1, seekSteps.getProgress());
                seekPulses.setMax(steps);
                pulses = Math.max(0, Math.min(steps, seekPulses.getProgress()));
                rotation = seekRot.getProgress() - 8;
                updatePreview.run();
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        };

        seekPulses.setOnSeekBarChangeListener(listenerChange);
        seekSteps.setOnSeekBarChangeListener(listenerChange);
        seekRot.setOnSeekBarChangeListener(listenerChange);

        Button btnApply = new Button(getContext());
        btnApply.setText("Apply Euclidean Groove");
        btnApply.setBackgroundColor(Color.parseColor("#0A84FF"));
        btnApply.setTextColor(Color.WHITE);
        btnApply.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        btnApply.setOnClickListener(v -> {
            if (listener != null) {
                listener.onApplied(pulses, steps, rotation, velocity);
            }
            dismiss();
        });
        layout.addView(btnApply);

        Button btnCancel = new Button(getContext());
        btnCancel.setText("Cancel");
        btnCancel.setBackgroundColor(Color.parseColor("#2C2F3C"));
        btnCancel.setTextColor(Color.WHITE);
        btnCancel.setOnClickListener(v -> dismiss());
        layout.addView(btnCancel);

        setContentView(scroll);
        if (getWindow() != null) {
            getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }
}
