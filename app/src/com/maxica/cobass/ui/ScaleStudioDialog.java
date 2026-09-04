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
import com.maxica.cobass.model.MusicalScale;

public class ScaleStudioDialog extends Dialog {

    public interface OnScaleChangeListener {
        void onScaleChanged(MusicalScale scale, int rootKey);
    }

    private static final String[] ROOT_NAMES = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};

    private int selectedRootKey;
    private MusicalScale selectedScale;
    private final OnScaleChangeListener listener;

    public ScaleStudioDialog(@NonNull Context context, MusicalScale currentScale, int currentRootKey, OnScaleChangeListener listener) {
        super(context);
        this.selectedScale = currentScale != null ? currentScale : MusicalScale.CHROMATIC;
        this.selectedRootKey = Math.max(0, Math.min(11, currentRootKey));
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        float density = getContext().getResources().getDisplayMetrics().density;

        LinearLayout content = new LinearLayout(getContext());
        content.setOrientation(LinearLayout.VERTICAL);

        addSectionHeader(content, "1. SELECT ROOT KEY");
        LinearLayout row1 = new LinearLayout(getContext());
        row1.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 6; i++) {
            final int rIdx = i;
            Button btn = new Button(getContext());
            btn.setText(ROOT_NAMES[i]);
            boolean isSel = (selectedRootKey == i);
            CobassButton.apply(btn, isSel ? CobassButton.Variant.PRIMARY : CobassButton.Variant.SECONDARY, CobassButton.Size.COMPACT);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(Math.round(2 * density), 0, Math.round(2 * density), 0);
            btn.setLayoutParams(lp);
            btn.setOnClickListener(v -> {
                selectedRootKey = rIdx;
                if (listener != null) listener.onScaleChanged(selectedScale, selectedRootKey);
                dismiss();
            });
            row1.addView(btn);
        }
        content.addView(row1);

        LinearLayout row2 = new LinearLayout(getContext());
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setPadding(0, Math.round(4 * density), 0, Math.round(CobassSpacing.SPACE_SM * density));
        for (int i = 6; i < 12; i++) {
            final int rIdx = i;
            Button btn = new Button(getContext());
            btn.setText(ROOT_NAMES[i]);
            boolean isSel = (selectedRootKey == i);
            CobassButton.apply(btn, isSel ? CobassButton.Variant.PRIMARY : CobassButton.Variant.SECONDARY, CobassButton.Size.COMPACT);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(Math.round(2 * density), 0, Math.round(2 * density), 0);
            btn.setLayoutParams(lp);
            btn.setOnClickListener(v -> {
                selectedRootKey = rIdx;
                if (listener != null) listener.onScaleChanged(selectedScale, selectedRootKey);
                dismiss();
            });
            row2.addView(btn);
        }
        content.addView(row2);

        addSectionHeader(content, "2. SELECT MUSICAL SCALE");
        for (MusicalScale ms : MusicalScale.values()) {
            Button btn = new Button(getContext());
            btn.setText(ms.getLabel());
            boolean isSel = (selectedScale == ms);
            CobassButton.apply(btn, isSel ? CobassButton.Variant.PRIMARY : CobassButton.Variant.SECONDARY, CobassButton.Size.STANDARD);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, Math.round(2 * density), 0, Math.round(2 * density));
            btn.setLayoutParams(lp);
            btn.setOnClickListener(v -> {
                selectedScale = ms;
                if (listener != null) listener.onScaleChanged(selectedScale, selectedRootKey);
                dismiss();
            });
            content.addView(btn);
        }

        LinearLayout root = CobassDialogShell.buildRootContainer(
            getContext(),
            "🎹 Musical Scale Intelligence",
            ROOT_NAMES[selectedRootKey] + " " + selectedScale.getLabel(),
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
