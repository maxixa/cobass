package com.maxica.cobass.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import com.maxica.cobass.model.StepPatternItem;
import com.maxica.cobass.sequencer.EuclideanGenerator;
import com.maxica.cobass.sequencer.StepPatternTransformEngine;

public class StepLaneInspectorDialog extends Dialog {

    public interface OnLaneInspectorActionListener {
        void onLaneUpdated(StepPatternItem.Lane lane);
    }

    private final StepPatternItem.Lane lane;
    private final OnLaneInspectorActionListener listener;

    public StepLaneInspectorDialog(@NonNull Context context, StepPatternItem.Lane lane, OnLaneInspectorActionListener listener) {
        super(context);
        this.lane = lane;
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
        title.setText("⚙ Drum Lane Inspector");
        title.setTextColor(Color.parseColor("#0A84FF"));
        title.setTextSize(16f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(title);

        EditText editName = new EditText(getContext());
        editName.setText(lane.name);
        editName.setTextColor(Color.WHITE);
        editName.setSingleLine(true);
        layout.addView(editName);

        // Polymeter Step Length Slider (1..64)
        TextView txtSteps = new TextView(getContext());
        txtSteps.setText("Polymeter Step Length: " + lane.stepCount + " steps");
        txtSteps.setTextColor(Color.WHITE);
        txtSteps.setTextSize(12f);
        txtSteps.setPadding(0, 10, 0, 4);
        layout.addView(txtSteps);

        SeekBar seekSteps = new SeekBar(getContext());
        seekSteps.setMax(63);
        seekSteps.setProgress(lane.stepCount - 1);
        seekSteps.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                lane.stepCount = p + 1;
                txtSteps.setText("Polymeter Step Length: " + lane.stepCount + " steps");
                if (listener != null) listener.onLaneUpdated(lane);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        layout.addView(seekSteps);

        // Volume & Pan Sliders
        TextView txtVol = new TextView(getContext());
        txtVol.setText("Lane Volume: " + (int)(lane.volume * 100) + "%");
        txtVol.setTextColor(Color.parseColor("#8E8E93"));
        txtVol.setTextSize(11f);
        layout.addView(txtVol);

        SeekBar seekVol = new SeekBar(getContext());
        seekVol.setMax(100);
        seekVol.setProgress((int)(lane.volume * 100));
        seekVol.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                lane.volume = p / 100.0f;
                txtVol.setText("Lane Volume: " + p + "%");
                if (listener != null) listener.onLaneUpdated(lane);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        layout.addView(seekVol);

        // Algorithmic Tools Row
        TextView txtTools = new TextView(getContext());
        txtTools.setText("ALGORITHMIC GROOVE TOOLS");
        txtTools.setTextColor(Color.parseColor("#8E8E93"));
        txtTools.setTextSize(11f);
        txtTools.setPadding(0, 12, 0, 6);
        txtTools.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(txtTools);

        Button btnEuclid = new Button(getContext());
        btnEuclid.setText("🎲 Euclidean Generator...");
        btnEuclid.setBackgroundColor(Color.parseColor("#3D3216"));
        btnEuclid.setTextColor(Color.parseColor("#FFD60A"));
        btnEuclid.setOnClickListener(v -> {
            new EuclideanStudioDialog(getContext(), lane, (p, st, rot, vel) -> {
                EuclideanGenerator.applyEuclideanToLane(lane, p, st, rot, vel);
                if (listener != null) listener.onLaneUpdated(lane);
                dismiss();
            }).show();
        });
        layout.addView(btnEuclid);

        LinearLayout rowShift = new LinearLayout(getContext());
        rowShift.setOrientation(LinearLayout.HORIZONTAL);
        rowShift.setPadding(0, 6, 0, 6);

        Button btnShiftL = new Button(getContext());
        btnShiftL.setText("◀ Shift Left");
        btnShiftL.setBackgroundColor(Color.parseColor("#242734"));
        btnShiftL.setTextColor(Color.WHITE);
        btnShiftL.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        btnShiftL.setOnClickListener(v -> {
            StepPatternTransformEngine.rotateLane(lane, -1);
            if (listener != null) listener.onLaneUpdated(lane);
        });
        rowShift.addView(btnShiftL);

        Button btnShiftR = new Button(getContext());
        btnShiftR.setText("Shift Right ▶");
        btnShiftR.setBackgroundColor(Color.parseColor("#242734"));
        btnShiftR.setTextColor(Color.WHITE);
        btnShiftR.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        btnShiftR.setOnClickListener(v -> {
            StepPatternTransformEngine.rotateLane(lane, 1);
            if (listener != null) listener.onLaneUpdated(lane);
        });
        rowShift.addView(btnShiftR);
        layout.addView(rowShift);

        Button btnClear = new Button(getContext());
        btnClear.setText("Clear Lane Steps");
        btnClear.setBackgroundColor(Color.parseColor("#3D1C22"));
        btnClear.setTextColor(Color.parseColor("#FF453A"));
        btnClear.setOnClickListener(v -> {
            StepPatternTransformEngine.clearLane(lane);
            if (listener != null) listener.onLaneUpdated(lane);
            Toast.makeText(getContext(), "Cleared " + lane.name, Toast.LENGTH_SHORT).show();
            dismiss();
        });
        layout.addView(btnClear);

        Button btnClose = new Button(getContext());
        btnClose.setText("Done");
        btnClose.setBackgroundColor(Color.parseColor("#2C2F3C"));
        btnClose.setTextColor(Color.WHITE);
        btnClose.setOnClickListener(v -> {
            lane.name = editName.getText().toString().trim();
            if (listener != null) listener.onLaneUpdated(lane);
            dismiss();
        });
        layout.addView(btnClose);

        setContentView(scroll);
        if (getWindow() != null) {
            getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }
}
