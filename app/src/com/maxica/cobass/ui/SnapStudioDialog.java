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
import android.widget.TextView;
import androidx.annotation.NonNull;
import com.maxica.cobass.model.SnapGrid;

public class SnapStudioDialog extends Dialog {

    public interface OnSnapSelectedListener {
        void onSnapSelected(SnapGrid grid);
    }

    private final SnapGrid activeGrid;
    private final OnSnapSelectedListener listener;

    public SnapStudioDialog(@NonNull Context context, SnapGrid activeGrid, OnSnapSelectedListener listener) {
        super(context);
        this.activeGrid = activeGrid;
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        ScrollView scroll = new ScrollView(getContext());
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.parseColor("#1C1E24"));
        layout.setPadding(28, 20, 28, 20);
        scroll.addView(layout);

        TextView title = new TextView(getContext());
        title.setText("🧲 Snap & Quantize Studio");
        title.setTextColor(Color.parseColor("#0A84FF"));
        title.setTextSize(16f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(title);

        TextView subTitle = new TextView(getContext());
        subTitle.setText("Active Grid: " + (activeGrid != null ? activeGrid.getLabel() : "1/16 Beat"));
        subTitle.setTextColor(Color.parseColor("#8E8E93"));
        subTitle.setTextSize(11f);
        subTitle.setPadding(0, 4, 0, 12);
        layout.addView(subTitle);

        TextView sec1 = new TextView(getContext());
        sec1.setText("1. STRAIGHT MUSICAL GRID");
        sec1.setTextColor(Color.WHITE);
        sec1.setTextSize(11f);
        sec1.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(sec1);

        LinearLayout rowStraight = new LinearLayout(getContext());
        rowStraight.setOrientation(LinearLayout.HORIZONTAL);
        rowStraight.setPadding(0, 6, 0, 10);

        SnapGrid[] straights = {SnapGrid.BAR_1, SnapGrid.BEAT_1, SnapGrid.BEAT_1_8TH, SnapGrid.BEAT_1_16TH, SnapGrid.BEAT_1_32ND};
        for (SnapGrid sg : straights) {
            Button btn = new Button(getContext());
            btn.setText(sg.getLabel().replace(" Beat", "").replace(" (Beat)", ""));
            btn.setTextSize(10f);
            boolean isSel = (activeGrid == sg);
            btn.setBackgroundColor(isSel ? Color.parseColor("#0A84FF") : Color.parseColor("#2C2C2E"));
            btn.setTextColor(Color.WHITE);
            btn.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            btn.setOnClickListener(v -> {
                if (listener != null) listener.onSnapSelected(sg);
                dismiss();
            });
            rowStraight.addView(btn);
        }
        layout.addView(rowStraight);

        TextView sec2 = new TextView(getContext());
        sec2.setText("2. TRIPLET GROOVE GRID");
        sec2.setTextColor(Color.WHITE);
        sec2.setTextSize(11f);
        sec2.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(sec2);

        LinearLayout rowTriplets = new LinearLayout(getContext());
        rowTriplets.setOrientation(LinearLayout.HORIZONTAL);
        rowTriplets.setPadding(0, 6, 0, 10);

        SnapGrid[] triplets = {SnapGrid.TRIPLET_1_4, SnapGrid.TRIPLET_1_8, SnapGrid.TRIPLET_1_16, SnapGrid.TRIPLET_1_32};
        for (SnapGrid sg : triplets) {
            Button btn = new Button(getContext());
            btn.setText(sg.getLabel().replace(" Triplet", "T"));
            btn.setTextSize(10f);
            boolean isSel = (activeGrid == sg);
            btn.setBackgroundColor(isSel ? Color.parseColor("#0A84FF") : Color.parseColor("#2C2C2E"));
            btn.setTextColor(Color.WHITE);
            btn.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            btn.setOnClickListener(v -> {
                if (listener != null) listener.onSnapSelected(sg);
                dismiss();
            });
            rowTriplets.addView(btn);
        }
        layout.addView(rowTriplets);

        TextView sec3 = new TextView(getContext());
        sec3.setText("3. DOTTED SUBDIVISIONS & FREE");
        sec3.setTextColor(Color.WHITE);
        sec3.setTextSize(11f);
        sec3.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(sec3);

        LinearLayout rowDotted = new LinearLayout(getContext());
        rowDotted.setOrientation(LinearLayout.HORIZONTAL);
        rowDotted.setPadding(0, 6, 0, 10);

        SnapGrid[] dotted = {SnapGrid.DOTTED_1_4, SnapGrid.DOTTED_1_8, SnapGrid.DOTTED_1_16, SnapGrid.OFF};
        for (SnapGrid sg : dotted) {
            Button btn = new Button(getContext());
            btn.setText(sg == SnapGrid.OFF ? "FREE (OFF)" : sg.getLabel().replace(" Dotted", "D"));
            btn.setTextSize(10f);
            boolean isSel = (activeGrid == sg);
            btn.setBackgroundColor(isSel ? Color.parseColor("#0A84FF") : Color.parseColor("#2C2C2E"));
            btn.setTextColor(Color.WHITE);
            btn.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            btn.setOnClickListener(v -> {
                if (listener != null) listener.onSnapSelected(sg);
                dismiss();
            });
            rowDotted.addView(btn);
        }
        layout.addView(rowDotted);

        Button btnDone = new Button(getContext());
        btnDone.setText("Done");
        btnDone.setBackgroundColor(Color.parseColor("#3A3A3C"));
        btnDone.setTextColor(Color.WHITE);
        btnDone.setOnClickListener(v -> dismiss());
        layout.addView(btnDone);

        setContentView(scroll);
        if (getWindow() != null) {
            getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }
}
