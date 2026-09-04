package com.maxica.cobass.ui;

import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.widget.TextView;

public final class CobassTypography {

    private CobassTypography() {}

    // 5-Level Standard Type Scale (sp)
    public static final float TYPE_CAPTION = 10.0f; // Step numbers, micro-badges, parameter tags
    public static final float TYPE_BODY    = 12.0f; // Descriptions, readouts, slider labels
    public static final float TYPE_LABEL   = 13.0f; // Standard button text, list items
    public static final float TYPE_HEADING = 15.0f; // Dialog headers, panel section titles
    public static final float TYPE_DISPLAY = 17.0f; // Time counter, primary transport banner

    public static void applyCaption(TextView tv) {
        if (tv == null) return;
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, TYPE_CAPTION);
        tv.setTextColor(CobassTheme.TEXT_SECONDARY);
    }

    public static void applyBody(TextView tv) {
        if (tv == null) return;
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, TYPE_BODY);
        tv.setTextColor(CobassTheme.TEXT_SECONDARY);
    }

    public static void applyLabel(TextView tv) {
        if (tv == null) return;
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, TYPE_LABEL);
        tv.setTextColor(CobassTheme.TEXT_PRIMARY);
    }

    public static void applyHeading(TextView tv) {
        if (tv == null) return;
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, TYPE_HEADING);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setTextColor(CobassTheme.TEXT_PRIMARY);
    }

    public static void applyDisplay(TextView tv) {
        if (tv == null) return;
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, TYPE_DISPLAY);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setTextColor(CobassTheme.TEXT_PRIMARY);
    }

    public static void configurePaint(Paint paint, float sizePx, boolean bold, int color) {
        if (paint == null) return;
        paint.setTextSize(sizePx);
        paint.setFakeBoldText(bold);
        paint.setColor(color);
    }
}
