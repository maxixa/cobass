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

        float density = getContext().getResources().getDisplayMetrics().density;
        LinearLayout content = new LinearLayout(getContext());
        content.setOrientation(LinearLayout.VERTICAL);

        int selCount = clip != null ? clip.getSelectedNotes().size() : 0;

        addSectionHeader(content, "1. ARTICULATIONS & CHORD GLUE");
        LinearLayout rowArt = new LinearLayout(getContext());
        rowArt.setOrientation(LinearLayout.HORIZONTAL);

        Button btnLegato = new Button(getContext());
        btnLegato.setText("Legato (Tie)");
        CobassButton.apply(btnLegato, CobassButton.Variant.PRIMARY, CobassButton.Size.STANDARD);
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l1.rightMargin = Math.round(2 * density);
        btnLegato.setLayoutParams(l1);
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
        CobassButton.apply(btnStrum, CobassButton.Variant.SECONDARY, CobassButton.Size.STANDARD);
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l2.leftMargin = Math.round(2 * density);
        btnStrum.setLayoutParams(l2);
        btnStrum.setOnClickListener(v -> {
            if (listener != null) listener.onCaptureUndo();
            int count = MidiTransformEngine.strum(clip, 25, true);
            if (listener != null) listener.onTransformApplied();
            Toast.makeText(getContext(), "Strum applied to " + count + " chord note(s)", Toast.LENGTH_SHORT).show();
            dismiss();
        });
        rowArt.addView(btnStrum);
        content.addView(rowArt);

        addSectionHeader(content, "2. ORGANIC HUMANIZE");
        CobassSlider.SliderRow humRow = CobassSlider.create(
            getContext(), "Humanize Jitter", "±20 ticks | ±20%", 50, 20, null
        );
        humRow.seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int val = Math.max(5, progress);
                humRow.readoutView.setText(String.format("±%d ticks | ±%d%%", val, val));
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        content.addView(humRow.container);

        Button btnApplyHumanize = new Button(getContext());
        btnApplyHumanize.setText("Apply Humanize");
        CobassButton.apply(btnApplyHumanize, CobassButton.Variant.SUCCESS, CobassButton.Size.STANDARD);
        btnApplyHumanize.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        btnApplyHumanize.setOnClickListener(v -> {
            if (listener != null) listener.onCaptureUndo();
            int val = Math.max(5, humRow.seekBar.getProgress());
            MidiTransformEngine.humanize(clip, val, val / 100f);
            if (listener != null) listener.onTransformApplied();
            Toast.makeText(getContext(), "Humanized " + (selCount > 0 ? selCount : (clip != null ? clip.getNotes().size() : 0)) + " note(s)", Toast.LENGTH_SHORT).show();
            dismiss();
        });
        content.addView(btnApplyHumanize);

        addSectionHeader(content, "3. GROOVE QUANTIZE & SWING");
        CobassSlider.SliderRow qRow = CobassSlider.create(
            getContext(), "Quantize Strength", "100%", 100, 100, null
        );
        qRow.seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                qRow.readoutView.setText(p + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        content.addView(qRow.container);

        Button btnApplyQuantize = new Button(getContext());
        btnApplyQuantize.setText("Apply Quantize (" + snapGrid.getLabel() + ")");
        CobassButton.apply(btnApplyQuantize, CobassButton.Variant.PRIMARY, CobassButton.Size.STANDARD);
        btnApplyQuantize.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        btnApplyQuantize.setOnClickListener(v -> {
            if (listener != null) listener.onCaptureUndo();
            float strength = Math.max(0.1f, qRow.seekBar.getProgress() / 100f);
            MidiTransformEngine.quantizeAdvanced(clip, snapGrid.getTicks(), strength, 0.0f, true, true);
            if (listener != null) listener.onTransformApplied();
            dismiss();
        });
        content.addView(btnApplyQuantize);

        LinearLayout root = CobassDialogShell.buildRootContainer(
            getContext(),
            "⚡ MIDI Quick Transforms",
            selCount > 0 ? ("Scope: " + selCount + " selected note(s)") : "Scope: All notes in clip",
            content,
            v -> dismiss()
        );

        setContentView(root);
        CobassDialogShell.configureWindow(this);
    }

    private void addSectionHeader(LinearLayout parent, String text) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        CobassTypography.applyCaption(tv);
        tv.setTextColor(CobassTheme.TEXT_PRIMARY);
        float density = getContext().getResources().getDisplayMetrics().density;
        tv.setPadding(0, Math.round(CobassSpacing.SPACE_SM * density), 0, Math.round(CobassSpacing.SPACE_XS * density));
        parent.addView(tv);
    }
}
