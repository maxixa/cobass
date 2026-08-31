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
import com.maxica.cobass.model.ClipItem;
import com.maxica.cobass.model.SnapGrid;
import com.maxica.cobass.sequencer.MidiTransformEngine;

public class MidiTransformDialog extends Dialog {

    public interface OnTransformListener {
        void onTransformApplied();
        void onCaptureUndo();
    }

    private final ClipItem clip;
    private final SnapGrid snapGrid;
    private final OnTransformListener listener;

    public MidiTransformDialog(@NonNull Context context, ClipItem clip, SnapGrid snapGrid, OnTransformListener listener) {
        super(context);
        this.clip = clip;
        this.snapGrid = snapGrid != null ? snapGrid : SnapGrid.BEAT_1_4;
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

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
        int selCount = clip != null ? clip.getSelectedNotes().size() : 0;
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
            if (listener != null) listener.onCaptureUndo();
            int count = MidiTransformEngine.legato(clip);
            if (listener != null) listener.onTransformApplied();
            Toast.makeText(getContext(), "Legato applied to " + count + " transition(s)", Toast.LENGTH_SHORT).show();
            dismiss();
        });
        rowArt.addView(btnLegato);

        Button btnStrum = new Button(getContext());
        btnStrum.setText("Guitar Strum");
        btnStrum.setBackgroundColor(Color.parseColor("#242734"));
        btnStrum.setTextColor(Color.WHITE);
        btnStrum.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        btnStrum.setOnClickListener(v -> {
            if (listener != null) listener.onCaptureUndo();
            int count = MidiTransformEngine.strum(clip, 25, true);
            if (listener != null) listener.onTransformApplied();
            Toast.makeText(getContext(), "Strum applied to " + count + " chord note(s)", Toast.LENGTH_SHORT).show();
            dismiss();
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
            if (listener != null) listener.onCaptureUndo();
            int val = Math.max(5, seekHumanize.getProgress());
            MidiTransformEngine.humanize(clip, val, val / 100f);
            if (listener != null) listener.onTransformApplied();
            Toast.makeText(getContext(), "Humanized " + (selCount > 0 ? selCount : (clip != null ? clip.getNotes().size() : 0)) + " note(s)", Toast.LENGTH_SHORT).show();
            dismiss();
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
        btnApplyQuantize.setText("Apply Quantize (" + snapGrid.getLabel() + ")");
        btnApplyQuantize.setBackgroundColor(Color.parseColor("#0A84FF"));
        btnApplyQuantize.setTextColor(Color.WHITE);
        btnApplyQuantize.setOnClickListener(v -> {
            if (listener != null) listener.onCaptureUndo();
            float strength = Math.max(0.1f, seekQuantStr.getProgress() / 100f);
            float swing = seekSwing.getProgress();
            MidiTransformEngine.quantizeAdvanced(clip, snapGrid.getTicks(), strength, swing, true, true);
            if (listener != null) listener.onTransformApplied();
            Toast.makeText(getContext(), "Quantized with " + (int)(strength * 100) + "% strength", Toast.LENGTH_SHORT).show();
            dismiss();
        });
        layout.addView(btnApplyQuantize);

        // 4. Velocity Compression & Curves
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
            if (listener != null) listener.onCaptureUndo();
            MidiTransformEngine.compressVelocities(clip, 0.55f, 0.88f);
            if (listener != null) listener.onTransformApplied();
            Toast.makeText(getContext(), "Velocities compressed (70 - 110)", Toast.LENGTH_SHORT).show();
            dismiss();
        });
        rowCompress.addView(btnCompStudio);

        Button btnFlat100 = new Button(getContext());
        btnFlat100.setText("Flat (100 / 80%)");
        btnFlat100.setBackgroundColor(Color.parseColor("#242734"));
        btnFlat100.setTextColor(Color.WHITE);
        btnFlat100.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        btnFlat100.setOnClickListener(v -> {
            if (listener != null) listener.onCaptureUndo();
            MidiTransformEngine.setAllVelocities(clip, 0.80f);
            if (listener != null) listener.onTransformApplied();
            Toast.makeText(getContext(), "Velocities set to 100", Toast.LENGTH_SHORT).show();
            dismiss();
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
            if (listener != null) listener.onCaptureUndo();
            MidiTransformEngine.applyCrescendo(clip, 0.4f, 1.0f);
            if (listener != null) listener.onTransformApplied();
            dismiss();
        });
        rowCurves.addView(btnCrescendo);

        Button btnDecrescendo = new Button(getContext());
        btnDecrescendo.setText("📉 Decrescendo");
        btnDecrescendo.setBackgroundColor(Color.parseColor("#242734"));
        btnDecrescendo.setTextColor(Color.WHITE);
        btnDecrescendo.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        btnDecrescendo.setOnClickListener(v -> {
            if (listener != null) listener.onCaptureUndo();
            MidiTransformEngine.applyCrescendo(clip, 1.0f, 0.4f);
            if (listener != null) listener.onTransformApplied();
            dismiss();
        });
        rowCurves.addView(btnDecrescendo);

        Button btnInvertVel = new Button(getContext());
        btnInvertVel.setText("⇄ Invert");
        btnInvertVel.setBackgroundColor(Color.parseColor("#242734"));
        btnInvertVel.setTextColor(Color.WHITE);
        btnInvertVel.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.8f));
        btnInvertVel.setOnClickListener(v -> {
            if (listener != null) listener.onCaptureUndo();
            MidiTransformEngine.invertVelocities(clip);
            if (listener != null) listener.onTransformApplied();
            dismiss();
        });
        rowCurves.addView(btnInvertVel);
        layout.addView(rowCurves);

        Button btnCloseDialog = new Button(getContext());
        btnCloseDialog.setText("Close");
        btnCloseDialog.setBackgroundColor(Color.parseColor("#2C2F3C"));
        btnCloseDialog.setTextColor(Color.WHITE);
        btnCloseDialog.setOnClickListener(v -> dismiss());
        layout.addView(btnCloseDialog);

        setContentView(scroll);
        if (getWindow() != null) {
            getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }
}
