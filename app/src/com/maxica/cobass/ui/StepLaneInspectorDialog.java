package com.maxica.cobass.ui;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
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

        float density = getContext().getResources().getDisplayMetrics().density;

        LinearLayout content = new LinearLayout(getContext());
        content.setOrientation(LinearLayout.VERTICAL);

        EditText editName = new EditText(getContext());
        editName.setText(lane.name);
        editName.setTextColor(CobassTheme.TEXT_PRIMARY);
        editName.setBackgroundColor(CobassTheme.SURFACE_0);
        int padName = Math.round(8 * density);
        editName.setPadding(padName, padName, padName, padName);
        editName.setSingleLine(true);
        content.addView(editName);

        CobassSlider.SliderRow stepsRow = CobassSlider.create(
            getContext(), "Polymeter Step Length", lane.stepCount + " steps", 63, lane.stepCount - 1, null
        );
        stepsRow.seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                lane.stepCount = p + 1;
                stepsRow.readoutView.setText(lane.stepCount + " steps");
                if (listener != null) listener.onLaneUpdated(lane);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        content.addView(stepsRow.container);

        CobassSlider.SliderRow volRow = CobassSlider.create(
            getContext(), "Lane Volume", (int)(lane.volume * 100) + "%", 100, (int)(lane.volume * 100), null
        );
        volRow.seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                lane.volume = p / 100.0f;
                volRow.readoutView.setText(p + "%");
                if (listener != null) listener.onLaneUpdated(lane);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        content.addView(volRow.container);

        TextView txtTools = new TextView(getContext());
        txtTools.setText("ALGORITHMIC GROOVE TOOLS");
        CobassTypography.applyCaption(txtTools);
        txtTools.setTextColor(CobassTheme.TEXT_PRIMARY);
        txtTools.setPadding(0, Math.round(CobassSpacing.SPACE_MD * density), 0, Math.round(CobassSpacing.SPACE_XS * density));
        content.addView(txtTools);

        Button btnEuclid = new Button(getContext());
        btnEuclid.setText("🎲 Euclidean Generator...");
        CobassButton.apply(btnEuclid, CobassButton.Variant.PRIMARY, CobassButton.Size.STANDARD);
        LinearLayout.LayoutParams euclidLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        euclidLp.setMargins(0, Math.round(2 * density), 0, Math.round(4 * density));
        btnEuclid.setLayoutParams(euclidLp);
        btnEuclid.setOnClickListener(v -> {
            new EuclideanStudioDialog(getContext(), lane, (p, st, rot, vel) -> {
                EuclideanGenerator.applyEuclideanToLane(lane, p, st, rot, vel);
                if (listener != null) listener.onLaneUpdated(lane);
                dismiss();
            }).show();
        });
        content.addView(btnEuclid);

        LinearLayout rowShift = new LinearLayout(getContext());
        rowShift.setOrientation(LinearLayout.HORIZONTAL);
        rowShift.setPadding(0, Math.round(2 * density), 0, Math.round(4 * density));

        Button btnShiftL = new Button(getContext());
        btnShiftL.setText("◀ Shift Left");
        CobassButton.apply(btnShiftL, CobassButton.Variant.SECONDARY, CobassButton.Size.STANDARD);
        LinearLayout.LayoutParams slLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        slLp.rightMargin = Math.round(2 * density);
        btnShiftL.setLayoutParams(slLp);
        btnShiftL.setOnClickListener(v -> {
            StepPatternTransformEngine.rotateLane(lane, -1);
            if (listener != null) listener.onLaneUpdated(lane);
        });
        rowShift.addView(btnShiftL);

        Button btnShiftR = new Button(getContext());
        btnShiftR.setText("Shift Right ▶");
        CobassButton.apply(btnShiftR, CobassButton.Variant.SECONDARY, CobassButton.Size.STANDARD);
        LinearLayout.LayoutParams srLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        srLp.leftMargin = Math.round(2 * density);
        btnShiftR.setLayoutParams(srLp);
        btnShiftR.setOnClickListener(v -> {
            StepPatternTransformEngine.rotateLane(lane, 1);
            if (listener != null) listener.onLaneUpdated(lane);
        });
        rowShift.addView(btnShiftR);
        content.addView(rowShift);

        Button btnClear = new Button(getContext());
        btnClear.setText("Clear Lane Steps");
        CobassButton.apply(btnClear, CobassButton.Variant.DANGER, CobassButton.Size.STANDARD);
        LinearLayout.LayoutParams clrLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clrLp.setMargins(0, Math.round(4 * density), 0, 0);
        btnClear.setLayoutParams(clrLp);
        btnClear.setOnClickListener(v -> {
            StepPatternTransformEngine.clearLane(lane);
            if (listener != null) listener.onLaneUpdated(lane);
            Toast.makeText(getContext(), "Cleared " + lane.name, Toast.LENGTH_SHORT).show();
            dismiss();
        });
        content.addView(btnClear);

        LinearLayout root = CobassDialogShell.buildRootContainer(
            getContext(),
            "⚙ Drum Lane Inspector",
            "Configure polymeter length, volume & algorithmic groove",
            content,
            v -> {
                lane.name = editName.getText().toString().trim();
                if (listener != null) listener.onLaneUpdated(lane);
                dismiss();
            }
        );

        setContentView(root);
        CobassDialogShell.configureWindow(this);
    }
}
