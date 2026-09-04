package com.maxica.cobass.ui;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
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

        float density = getContext().getResources().getDisplayMetrics().density;

        LinearLayout content = new LinearLayout(getContext());
        content.setOrientation(LinearLayout.VERTICAL);

        addSectionHeader(content, "1. STRAIGHT MUSICAL GRID");
        LinearLayout rowStraight = new LinearLayout(getContext());
        rowStraight.setOrientation(LinearLayout.HORIZONTAL);
        rowStraight.setPadding(0, 0, 0, Math.round(CobassSpacing.SPACE_SM * density));

        SnapGrid[] straights = {SnapGrid.BAR_1, SnapGrid.BEAT_1, SnapGrid.BEAT_1_8TH, SnapGrid.BEAT_1_16TH, SnapGrid.BEAT_1_32ND};
        for (SnapGrid sg : straights) {
            Button btn = new Button(getContext());
            btn.setText(sg.getLabel().replace(" Beat", "").replace(" (Beat)", ""));
            boolean isSel = (activeGrid == sg);
            CobassButton.apply(btn, isSel ? CobassButton.Variant.PRIMARY : CobassButton.Variant.SECONDARY, CobassButton.Size.COMPACT);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(Math.round(2 * density), 0, Math.round(2 * density), 0);
            btn.setLayoutParams(lp);
            btn.setOnClickListener(v -> {
                if (listener != null) listener.onSnapSelected(sg);
                dismiss();
            });
            rowStraight.addView(btn);
        }
        content.addView(rowStraight);

        addSectionHeader(content, "2. TRIPLET GROOVE GRID");
        LinearLayout rowTriplets = new LinearLayout(getContext());
        rowTriplets.setOrientation(LinearLayout.HORIZONTAL);
        rowTriplets.setPadding(0, 0, 0, Math.round(CobassSpacing.SPACE_SM * density));

        SnapGrid[] triplets = {SnapGrid.TRIPLET_1_4, SnapGrid.TRIPLET_1_8, SnapGrid.TRIPLET_1_16, SnapGrid.TRIPLET_1_32};
        for (SnapGrid sg : triplets) {
            Button btn = new Button(getContext());
            btn.setText(sg.getLabel().replace(" Triplet", "T"));
            boolean isSel = (activeGrid == sg);
            CobassButton.apply(btn, isSel ? CobassButton.Variant.PRIMARY : CobassButton.Variant.SECONDARY, CobassButton.Size.COMPACT);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(Math.round(2 * density), 0, Math.round(2 * density), 0);
            btn.setLayoutParams(lp);
            btn.setOnClickListener(v -> {
                if (listener != null) listener.onSnapSelected(sg);
                dismiss();
            });
            rowTriplets.addView(btn);
        }
        content.addView(rowTriplets);

        addSectionHeader(content, "3. DOTTED & FREE (UNQUANTIZED)");
        LinearLayout rowDotted = new LinearLayout(getContext());
        rowDotted.setOrientation(LinearLayout.HORIZONTAL);
        rowDotted.setPadding(0, 0, 0, Math.round(CobassSpacing.SPACE_MD * density));

        SnapGrid[] dotted = {SnapGrid.DOTTED_1_4, SnapGrid.DOTTED_1_8, SnapGrid.DOTTED_1_16, SnapGrid.OFF};
        for (SnapGrid sg : dotted) {
            Button btn = new Button(getContext());
            btn.setText(sg == SnapGrid.OFF ? "FREE" : sg.getLabel().replace(" Dotted", "D"));
            boolean isSel = (activeGrid == sg);
            CobassButton.apply(btn, isSel ? CobassButton.Variant.PRIMARY : CobassButton.Variant.SECONDARY, CobassButton.Size.COMPACT);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(Math.round(2 * density), 0, Math.round(2 * density), 0);
            btn.setLayoutParams(lp);
            btn.setOnClickListener(v -> {
                if (listener != null) listener.onSnapSelected(sg);
                dismiss();
            });
            rowDotted.addView(btn);
        }
        content.addView(rowDotted);

        LinearLayout root = CobassDialogShell.buildRootContainer(
            getContext(),
            "🧲 Snap & Quantize Studio",
            "Active Grid: " + (activeGrid != null ? activeGrid.getLabel() : "1/16 Beat"),
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
