package com.maxica.cobass.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

public final class CobassSlider {

    private CobassSlider() {}

    public static class SliderRow {
        public final LinearLayout container;
        public final TextView labelView;
        public final TextView readoutView;
        public final SeekBar seekBar;

        public SliderRow(LinearLayout container, TextView labelView, TextView readoutView, SeekBar seekBar) {
            this.container = container;
            this.labelView = labelView;
            this.readoutView = readoutView;
            this.seekBar = seekBar;
        }
    }

    public static SliderRow create(
        Context context,
        String labelText,
        String initialReadout,
        int max,
        int progress,
        SeekBar.OnSeekBarChangeListener listener
    ) {
        float density = context.getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, Math.round(4 * density), 0, Math.round(CobassSpacing.SPACE_SM * density));

        LinearLayout topRow = new LinearLayout(context);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvLabel = new TextView(context);
        tvLabel.setText(labelText);
        CobassTypography.applyBody(tvLabel);
        tvLabel.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        topRow.addView(tvLabel);

        TextView tvReadout = new TextView(context);
        tvReadout.setText(initialReadout);
        CobassTypography.applyBody(tvReadout);
        tvReadout.setTypeface(Typeface.MONOSPACE);
        tvReadout.setTextColor(CobassTheme.ACCENT_PRIMARY);
        topRow.addView(tvReadout);

        root.addView(topRow);

        SeekBar sb = new SeekBar(context);
        sb.setMax(max);
        sb.setProgress(progress);
        if (listener != null) {
            sb.setOnSeekBarChangeListener(listener);
        }
        LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        sbLp.topMargin = Math.round(4 * density);
        root.addView(sb, sbLp);

        return new SliderRow(root, tvLabel, tvReadout, sb);
    }
}
