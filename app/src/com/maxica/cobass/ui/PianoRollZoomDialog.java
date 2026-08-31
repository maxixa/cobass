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
import android.widget.SeekBar;
import android.widget.TextView;
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
        txtTime.setText(String.format("Time Zoom (Horizontal): %.2fx", currentTimeScale));
        txtTime.setTextColor(Color.WHITE);
        txtTime.setPadding(0, 14, 0, 6);
        layout.addView(txtTime);

        SeekBar seekTime = new SeekBar(getContext());
        seekTime.setMax(100);
        int timeProgress = (int) (((currentTimeScale - 0.15f) / (1.5f - 0.15f)) * 100f);
        seekTime.setProgress(Math.max(0, Math.min(100, timeProgress)));
        layout.addView(seekTime);

        TextView txtPitch = new TextView(getContext());
        txtPitch.setText(String.format("Key Height (Vertical): %.0f dp", currentPitchScale));
        txtPitch.setTextColor(Color.WHITE);
        txtPitch.setPadding(0, 14, 0, 6);
        layout.addView(txtPitch);

        SeekBar seekPitch = new SeekBar(getContext());
        seekPitch.setMax(100);
        int pitchProgress = (int) (((currentPitchScale - 24f) / (64f - 24f)) * 100f);
        seekPitch.setProgress(Math.max(0, Math.min(100, pitchProgress)));
        layout.addView(seekPitch);

        SeekBar.OnSeekBarChangeListener seekListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float timeScale = 0.15f + (seekTime.getProgress() / 100.0f) * 1.35f;
                float pitchScale = 24.0f + (seekPitch.getProgress() / 100.0f) * 40.0f;
                txtTime.setText(String.format("Time Zoom (Horizontal): %.2fx", timeScale));
                txtPitch.setText(String.format("Key Height (Vertical): %.0f dp", pitchScale));
                if (listener != null) listener.onZoomChanged(timeScale, pitchScale);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        };

        seekTime.setOnSeekBarChangeListener(seekListener);
        seekPitch.setOnSeekBarChangeListener(seekListener);

        Button btnReset = new Button(getContext());
        btnReset.setText("Reset to Default (1.0x / 42dp)");
        btnReset.setBackgroundColor(Color.parseColor("#242734"));
        btnReset.setTextColor(Color.WHITE);
        btnReset.setOnClickListener(v -> {
            seekTime.setProgress(22);
            seekPitch.setProgress(45);
            if (listener != null) listener.onZoomChanged(0.45f, 42f);
        });
        layout.addView(btnReset);

        Button btnDone = new Button(getContext());
        btnDone.setText("Done");
        btnDone.setBackgroundColor(Color.parseColor("#0A84FF"));
        btnDone.setTextColor(Color.WHITE);
        btnDone.setOnClickListener(v -> dismiss());
        layout.addView(btnDone);

        setContentView(layout);
        if (getWindow() != null) {
            getWindow().setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }
}
