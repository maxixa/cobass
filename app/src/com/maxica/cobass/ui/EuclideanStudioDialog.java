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

        float density = getContext().getResources().getDisplayMetrics().density;

        LinearLayout content = new LinearLayout(getContext());
        content.setOrientation(LinearLayout.VERTICAL);

        TextView txtPreview = new TextView(getContext());
        CobassTypography.applyBody(txtPreview);
        txtPreview.setTextColor(CobassTheme.ACCENT_SUCCESS);
        txtPreview.setBackgroundColor(CobassTheme.SURFACE_0);
        int padPreview = Math.round(10 * density);
        txtPreview.setPadding(padPreview, padPreview, padPreview, padPreview);
        txtPreview.setTypeface(android.graphics.Typeface.MONOSPACE);
        content.addView(txtPreview);

        CobassSlider.SliderRow pulsesRow = CobassSlider.create(
            getContext(), "Hits / Pulses (K)", String.valueOf(pulses), steps, pulses, null
        );
        CobassSlider.SliderRow stepsRow = CobassSlider.create(
            getContext(), "Sequence Length (N)", String.valueOf(steps), 32, steps, null
        );
        CobassSlider.SliderRow rotRow = CobassSlider.create(
            getContext(), "Rotation Offset (S)", String.format("%+d steps", rotation), 16, 8, null
        );

        content.addView(pulsesRow.container);
        content.addView(stepsRow.container);
        content.addView(rotRow.container);

        Runnable updatePreview = () -> {
            boolean[] pattern = EuclideanGenerator.generateEuclideanPattern(pulses, steps, rotation);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < pattern.length; i++) {
                if (i > 0 && i % 4 == 0) sb.append(" ");
                sb.append(pattern[i] ? "■" : "·");
            }
            txtPreview.setText(sb.toString());
            pulsesRow.readoutView.setText(String.valueOf(pulses));
            stepsRow.readoutView.setText(String.valueOf(steps));
            rotRow.readoutView.setText(String.format("%+d steps", rotation));
        };
        updatePreview.run();

        SeekBar.OnSeekBarChangeListener listenerChange = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                steps = Math.max(1, stepsRow.seekBar.getProgress());
                pulsesRow.seekBar.setMax(steps);
                pulses = Math.max(0, Math.min(steps, pulsesRow.seekBar.getProgress()));
                rotation = rotRow.seekBar.getProgress() - 8;
                updatePreview.run();
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        };

        pulsesRow.seekBar.setOnSeekBarChangeListener(listenerChange);
        stepsRow.seekBar.setOnSeekBarChangeListener(listenerChange);
        rotRow.seekBar.setOnSeekBarChangeListener(listenerChange);

        Button btnApply = new Button(getContext());
        btnApply.setText("Apply Euclidean Groove");
        CobassButton.apply(btnApply, CobassButton.Variant.PRIMARY, CobassButton.Size.STANDARD);
        LinearLayout.LayoutParams apLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        apLp.topMargin = Math.round(CobassSpacing.SPACE_MD * density);
        btnApply.setLayoutParams(apLp);
        btnApply.setOnClickListener(v -> {
            if (listener != null) {
                listener.onApplied(pulses, steps, rotation, velocity);
            }
            dismiss();
        });
        content.addView(btnApply);

        LinearLayout root = CobassDialogShell.buildRootContainer(
            getContext(),
            "🎲 Euclidean Generator",
            "Lane: " + (lane != null ? lane.name : "Active Lane"),
            content,
            v -> dismiss()
        );

        setContentView(root);
        CobassDialogShell.configureWindow(this);
    }
}
