package com.maxica.cobass.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.Button;

public final class CobassButton {

    private CobassButton() {}

    public enum Variant {
        PRIMARY,
        SECONDARY,
        GHOST,
        SUCCESS,
        DANGER,
        WARNING
    }

    public enum Size {
        COMPACT(CobassSpacing.BTN_HEIGHT_COMPACT, CobassTypography.TYPE_CAPTION, 8),
        STANDARD(CobassSpacing.BTN_HEIGHT_STANDARD, CobassTypography.TYPE_LABEL, 12),
        LARGE(CobassSpacing.BTN_HEIGHT_LARGE, CobassTypography.TYPE_HEADING, 16);

        public final int heightDp;
        public final float textSizeSp;
        public final int horizontalPaddingDp;

        Size(int heightDp, float textSizeSp, int horizontalPaddingDp) {
            this.heightDp = heightDp;
            this.textSizeSp = textSizeSp;
            this.horizontalPaddingDp = horizontalPaddingDp;
        }
    }

    public static GradientDrawable createDrawable(int bgColor, int strokeColor, int strokeWidthPx, float cornerRadiusDp, float density) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setColor(bgColor);
        if (strokeWidthPx > 0) {
            gd.setStroke(strokeWidthPx, strokeColor);
        }
        gd.setCornerRadius(cornerRadiusDp * density);
        return gd;
    }

    public static int adjustBrightness(int color, float factor) {
        int a = Color.alpha(color);
        int r = Math.round(Color.red(color) * factor);
        int g = Math.round(Color.green(color) * factor);
        int b = Math.round(Color.blue(color) * factor);
        return Color.argb(a, Math.min(255, r), Math.min(255, g), Math.min(255, b));
    }

    public static void apply(Button btn, Variant variant, Size size) {
        if (btn == null) return;
        Context ctx = btn.getContext();
        float density = ctx.getResources().getDisplayMetrics().density;

        int normalBg;
        int textColor;
        int strokeColor = 0;
        int strokeWidth = 0;

        switch (variant) {
            case PRIMARY:
                normalBg = CobassTheme.ACCENT_PRIMARY;
                textColor = Color.WHITE;
                break;
            case SECONDARY:
                normalBg = CobassTheme.SURFACE_2;
                textColor = CobassTheme.TEXT_PRIMARY;
                strokeColor = CobassTheme.SURFACE_3;
                strokeWidth = Math.round(1f * density);
                break;
            case GHOST:
                normalBg = Color.TRANSPARENT;
                textColor = CobassTheme.TEXT_SECONDARY;
                strokeColor = CobassTheme.SURFACE_3;
                strokeWidth = Math.round(1f * density);
                break;
            case SUCCESS:
                normalBg = CobassTheme.ACCENT_SUCCESS;
                textColor = Color.WHITE;
                break;
            case DANGER:
                normalBg = CobassTheme.ACCENT_DANGER;
                textColor = Color.WHITE;
                break;
            case WARNING:
                normalBg = CobassTheme.ACCENT_WARNING;
                textColor = Color.BLACK;
                break;
            default:
                normalBg = CobassTheme.SURFACE_2;
                textColor = CobassTheme.TEXT_PRIMARY;
                break;
        }

        int pressedBg = (normalBg == Color.TRANSPARENT) ? 0x22FFFFFF : adjustBrightness(normalBg, 0.85f);

        StateListDrawable sld = new StateListDrawable();
        sld.addState(
            new int[]{android.R.attr.state_pressed},
            createDrawable(pressedBg, strokeColor, strokeWidth, CobassTheme.RADIUS_MD, density)
        );
        sld.addState(
            new int[]{android.R.attr.state_selected},
            createDrawable(CobassTheme.ACCENT_PRIMARY, strokeColor, strokeWidth, CobassTheme.RADIUS_MD, density)
        );
        sld.addState(
            new int[]{},
            createDrawable(normalBg, strokeColor, strokeWidth, CobassTheme.RADIUS_MD, density)
        );

        btn.setBackground(sld);
        btn.setTextColor(textColor);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, size.textSizeSp);

        int padX = Math.round(size.horizontalPaddingDp * density);
        btn.setPadding(padX, 0, padX, 0);

        ViewGroup.LayoutParams lp = btn.getLayoutParams();
        if (lp != null && lp.height > 0) {
            lp.height = Math.round(size.heightDp * density);
            btn.setLayoutParams(lp);
        } else {
            btn.setMinHeight(Math.round(size.heightDp * density));
        }
    }

    public static void applyToggle(Button btn, boolean active, String activeText, String inactiveText) {
        if (btn == null) return;
        btn.setText(active ? activeText : inactiveText);
        apply(btn, active ? Variant.SUCCESS : Variant.SECONDARY, Size.COMPACT);
    }
}
