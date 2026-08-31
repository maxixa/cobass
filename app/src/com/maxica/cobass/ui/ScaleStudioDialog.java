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

        ScrollView scroll = new ScrollView(getContext());
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.parseColor("#1C1E24"));
        layout.setPadding(28, 20, 28, 20);
        scroll.addView(layout);

        TextView title = new TextView(getContext());
        title.setText("🎹 Musical Scale & Keybed Intelligence");
        title.setTextColor(Color.parseColor("#0A84FF"));
        title.setTextSize(16f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(title);

        TextView secRoot = new TextView(getContext());
        secRoot.setText("1. SELECT ROOT KEY");
        secRoot.setTextColor(Color.WHITE);
        secRoot.setTextSize(11f);
        secRoot.setPadding(0, 10, 0, 6);
        secRoot.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(secRoot);

        LinearLayout rowRoots1 = new LinearLayout(getContext());
        rowRoots1.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 6; i++) {
            final int rIdx = i;
            Button btn = new Button(getContext());
            btn.setText(ROOT_NAMES[i]);
            btn.setTextSize(10f);
            boolean isSel = (selectedRootKey == i);
            btn.setBackgroundColor(isSel ? Color.parseColor("#0A84FF") : Color.parseColor("#2C2C2E"));
            btn.setTextColor(Color.WHITE);
            btn.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            btn.setOnClickListener(v -> {
                selectedRootKey = rIdx;
                if (listener != null) listener.onScaleChanged(selectedScale, selectedRootKey);
                dismiss();
            });
            rowRoots1.addView(btn);
        }
        layout.addView(rowRoots1);

        LinearLayout rowRoots2 = new LinearLayout(getContext());
        rowRoots2.setOrientation(LinearLayout.HORIZONTAL);
        rowRoots2.setPadding(0, 4, 0, 10);
        for (int i = 6; i < 12; i++) {
            final int rIdx = i;
            Button btn = new Button(getContext());
            btn.setText(ROOT_NAMES[i]);
            btn.setTextSize(10f);
            boolean isSel = (selectedRootKey == i);
            btn.setBackgroundColor(isSel ? Color.parseColor("#0A84FF") : Color.parseColor("#2C2C2E"));
            btn.setTextColor(Color.WHITE);
            btn.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            btn.setOnClickListener(v -> {
                selectedRootKey = rIdx;
                if (listener != null) listener.onScaleChanged(selectedScale, selectedRootKey);
                dismiss();
            });
            rowRoots2.addView(btn);
        }
        layout.addView(rowRoots2);

        TextView secScale = new TextView(getContext());
        secScale.setText("2. SELECT MUSICAL SCALE");
        secScale.setTextColor(Color.WHITE);
        secScale.setTextSize(11f);
        secScale.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(secScale);

        for (MusicalScale ms : MusicalScale.values()) {
            Button btn = new Button(getContext());
            btn.setText(ms.getLabel());
            btn.setTextSize(11f);
            boolean isSel = (selectedScale == ms);
            btn.setBackgroundColor(isSel ? Color.parseColor("#1C385C") : Color.parseColor("#242734"));
            btn.setTextColor(isSel ? Color.parseColor("#0A84FF") : Color.WHITE);
            btn.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            btn.setOnClickListener(v -> {
                selectedScale = ms;
                if (listener != null) listener.onScaleChanged(selectedScale, selectedRootKey);
                dismiss();
            });
            layout.addView(btn);
        }

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
