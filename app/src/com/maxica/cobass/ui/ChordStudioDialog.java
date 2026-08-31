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

public class ChordStudioDialog extends Dialog {

    public interface OnChordSelectedListener {
        void onChordSelected(String label, int[] intervals);
    }

    private final OnChordSelectedListener listener;

    public ChordStudioDialog(@NonNull Context context, OnChordSelectedListener listener) {
        super(context);
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
        title.setText("🎸 Chord Stamper Presets");
        title.setTextColor(Color.parseColor("#FFD60A"));
        title.setTextSize(16f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(title);

        TextView subTitle = new TextView(getContext());
        subTitle.setText("Tap a preset then tap on the canvas to stamp full chords");
        subTitle.setTextColor(Color.parseColor("#8E8E93"));
        subTitle.setTextSize(11f);
        subTitle.setPadding(0, 4, 0, 12);
        layout.addView(subTitle);

        Button btnSingle = new Button(getContext());
        btnSingle.setText("Single Note (Off / Default)");
        btnSingle.setBackgroundColor(Color.parseColor("#2C2C2E"));
        btnSingle.setTextColor(Color.WHITE);
        btnSingle.setOnClickListener(v -> {
            if (listener != null) listener.onChordSelected(null, null);
            dismiss();
        });
        layout.addView(btnSingle);

        addChordOption(layout, "Major Triad (1 - 3 - 5)", new int[]{0, 4, 7});
        addChordOption(layout, "Minor Triad (1 - b3 - 5)", new int[]{0, 3, 7});
        addChordOption(layout, "Dominant 7th (1 - 3 - 5 - b7)", new int[]{0, 4, 7, 10});
        addChordOption(layout, "Major 7th (1 - 3 - 5 - 7)", new int[]{0, 4, 7, 11});
        addChordOption(layout, "Minor 7th (1 - b3 - 5 - b7)", new int[]{0, 3, 7, 10});
        addChordOption(layout, "Suspended 4th (1 - 4 - 5)", new int[]{0, 5, 7});
        addChordOption(layout, "Diminished (1 - b3 - b5)", new int[]{0, 3, 6});
        addChordOption(layout, "Augmented (1 - 3 - #5)", new int[]{0, 4, 8});
        addChordOption(layout, "Add 9 (1 - 3 - 5 - 9)", new int[]{0, 4, 7, 14});

        Button btnCancel = new Button(getContext());
        btnCancel.setText("Cancel");
        btnCancel.setBackgroundColor(Color.parseColor("#3A3A3C"));
        btnCancel.setTextColor(Color.WHITE);
        btnCancel.setOnClickListener(v -> dismiss());
        layout.addView(btnCancel);

        setContentView(scroll);
        if (getWindow() != null) {
            getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    private void addChordOption(LinearLayout layout, String label, int[] intervals) {
        Button btn = new Button(getContext());
        btn.setText(label);
        btn.setTextSize(11f);
        btn.setBackgroundColor(Color.parseColor("#242734"));
        btn.setTextColor(Color.WHITE);
        btn.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        btn.setOnClickListener(v -> {
            if (listener != null) listener.onChordSelected(label, intervals);
            dismiss();
        });
        layout.addView(btn);
    }
}
