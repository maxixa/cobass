package com.maxica.cobass.ui;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
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

        float density = getContext().getResources().getDisplayMetrics().density;

        LinearLayout content = new LinearLayout(getContext());
        content.setOrientation(LinearLayout.VERTICAL);

        Button btnSingle = new Button(getContext());
        btnSingle.setText("Single Note (Off / Default)");
        CobassButton.apply(btnSingle, CobassButton.Variant.GHOST, CobassButton.Size.STANDARD);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sLp.setMargins(0, 0, 0, Math.round(CobassSpacing.SPACE_SM * density));
        btnSingle.setLayoutParams(sLp);
        btnSingle.setOnClickListener(v -> {
            if (listener != null) listener.onChordSelected(null, null);
            dismiss();
        });
        content.addView(btnSingle);

        addChordOption(content, "Major Triad (1 - 3 - 5)", new int[]{0, 4, 7});
        addChordOption(content, "Minor Triad (1 - b3 - 5)", new int[]{0, 3, 7});
        addChordOption(content, "Dominant 7th (1 - 3 - 5 - b7)", new int[]{0, 4, 7, 10});
        addChordOption(content, "Major 7th (1 - 3 - 5 - 7)", new int[]{0, 4, 7, 11});
        addChordOption(content, "Minor 7th (1 - b3 - 5 - b7)", new int[]{0, 3, 7, 10});
        addChordOption(content, "Suspended 4th (1 - 4 - 5)", new int[]{0, 5, 7});
        addChordOption(content, "Diminished (1 - b3 - b5)", new int[]{0, 3, 6});
        addChordOption(content, "Augmented (1 - 3 - #5)", new int[]{0, 4, 8});
        addChordOption(content, "Add 9 (1 - 3 - 5 - 9)", new int[]{0, 4, 7, 14});

        LinearLayout root = CobassDialogShell.buildRootContainer(
            getContext(),
            "🎸 Chord Stamper Presets",
            "Tap a chord preset to stamp polyphonic harmonies on grid tap",
            content,
            v -> dismiss()
        );

        setContentView(root);
        CobassDialogShell.configureWindow(this);
    }

    private void addChordOption(LinearLayout layout, String label, int[] intervals) {
        float density = getContext().getResources().getDisplayMetrics().density;
        Button btn = new Button(getContext());
        btn.setText(label);
        CobassButton.apply(btn, CobassButton.Variant.SECONDARY, CobassButton.Size.STANDARD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, Math.round(2 * density), 0, Math.round(2 * density));
        btn.setLayoutParams(lp);
        btn.setOnClickListener(v -> {
            if (listener != null) listener.onChordSelected(label, intervals);
            dismiss();
        });
        layout.addView(btn);
    }
}
