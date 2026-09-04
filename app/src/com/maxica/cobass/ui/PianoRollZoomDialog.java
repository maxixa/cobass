package com.maxica.cobass.ui;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import androidx.annotation.NonNull;

public class PianoRollZoomDialog extends Dialog {

    public interface OnZoomChangeListener {
        void onZoomChanged(float timeScale, float pitchScale);
    }

    private final float currentTimeScale;
    private final float currentPitchScale;
    private final OnZoomChangeListener listener;

    public PianoRollZoomDialog(@NonNull Context context, float currentTimeScale, float currentPitchScale, OnZoomChangeListener listener) {
        super(context);
        this.currentTimeScale = currentTimeScale;
        this.currentPitchScale = currentPitchScale;
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        float density = getContext().getResources().getDisplayMetrics().density;

        LinearLayout content = new LinearLayout(getContext());
        content.setOrientation(LinearLayout.VERTICAL);

        int timeProgress = (int) (((currentTimeScale - 0.15f) / (1.5f - 0.15f)) * 100f);
        int pitchProgress = (int) (((currentPitchScale - 24f) / (64f - 24f)) * 100f);

        CobassSlider.SliderRow timeRow = CobassSlider.create(
            getContext(),
            "Time Zoom (Horizontal)",
            String.format("%.2fx", currentTimeScale),
            100,
            Math.max(0, Math.min(100, timeProgress)),
            null
        );

        CobassSlider.SliderRow pitchRow = CobassSlider.create(
            getContext(),
            "Key Row Height (Vertical)",
            String.format("%.0f dp", currentPitchScale),
            100,
            Math.max(0, Math.min(100, pitchProgress)),
            null
        );

        SeekBar.OnSeekBarChangeListener zoomListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float timeScale = 0.15f + (timeRow.seekBar.getProgress() / 100.0f) * 1.35f;
                float pitchScale = 24.0f + (pitchRow.seekBar.getProgress() / 100.0f) * 40.0f;
                timeRow.readoutView.setText(String.format("%.2fx", timeScale));
                pitchRow.readoutView.setText(String.format("%.0f dp", pitchScale));
                if (listener != null) listener.onZoomChanged(timeScale, pitchScale);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        };

        timeRow.seekBar.setOnSeekBarChangeListener(zoomListener);
        pitchRow.seekBar.setOnSeekBarChangeListener(zoomListener);

        content.addView(timeRow.container);
        content.addView(pitchRow.container);

        Button btnReset = new Button(getContext());
        btnReset.setText("Reset to Default (1.0x / 42dp)");
        CobassButton.apply(btnReset, CobassButton.Variant.SECONDARY, CobassButton.Size.STANDARD);
        LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rLp.topMargin = Math.round(CobassSpacing.SPACE_SM * density);
        btnReset.setLayoutParams(rLp);
        btnReset.setOnClickListener(v -> {
            timeRow.seekBar.setProgress(22);
            pitchRow.seekBar.setProgress(45);
            timeRow.readoutView.setText("0.45x");
            pitchRow.readoutView.setText("42 dp");
            if (listener != null) listener.onZoomChanged(0.45f, 42f);
        });
        content.addView(btnReset);

        LinearLayout root = CobassDialogShell.buildRootContainer(
            getContext(),
            "🔍 Viewport Zoom & Scaling",
            "Adjust horizontal timeline magnification and vertical keybed height",
            content,
            v -> dismiss()
        );

        setContentView(root);
        CobassDialogShell.configureWindow(this);
    }
}
