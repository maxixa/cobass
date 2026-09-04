package com.maxica.cobass.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class CobassDialogShell {

    private CobassDialogShell() {}

    public static LinearLayout buildRootContainer(
        @NonNull Context context,
        @NonNull String title,
        @Nullable String subtitle,
        @NonNull View contentView,
        @Nullable View.OnClickListener onCloseListener
    ) {
        float density = context.getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setColor(CobassTheme.SURFACE_1);
        bg.setCornerRadius(CobassTheme.RADIUS_LG * density);
        root.setBackground(bg);

        int padPx = Math.round(CobassSpacing.DIALOG_PADDING * density);
        root.setPadding(padPx, padPx, padPx, padPx);

        // Header Row
        LinearLayout headerRow = new LinearLayout(context);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setPadding(0, 0, 0, Math.round(CobassSpacing.SPACE_MD * density));

        LinearLayout titleCol = new LinearLayout(context);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        TextView tvTitle = new TextView(context);
        tvTitle.setText(title);
        CobassTypography.applyHeading(tvTitle);
        tvTitle.setTextColor(CobassTheme.ACCENT_PRIMARY);
        titleCol.addView(tvTitle);

        if (subtitle != null && !subtitle.isEmpty()) {
            TextView tvSub = new TextView(context);
            tvSub.setText(subtitle);
            CobassTypography.applyCaption(tvSub);
            tvSub.setPadding(0, Math.round(2 * density), 0, 0);
            titleCol.addView(tvSub);
        }
        headerRow.addView(titleCol);

        // Close Button
        if (onCloseListener != null) {
            Button btnClose = new Button(context);
            btnClose.setText("✕");
            int btnSizePx = Math.round(CobassSpacing.BTN_HEIGHT_COMPACT * density);
            LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(btnSizePx, btnSizePx);
            closeLp.setMargins(Math.round(CobassSpacing.SPACE_SM * density), 0, 0, 0);
            btnClose.setLayoutParams(closeLp);
            CobassButton.apply(btnClose, CobassButton.Variant.GHOST, CobassButton.Size.COMPACT);
            btnClose.setOnClickListener(onCloseListener);
            headerRow.addView(btnClose);
        }

        root.addView(headerRow);

        // Divider
        View divider = new View(context);
        divider.setBackgroundColor(CobassTheme.SURFACE_3);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Math.round(1f * density)
        );
        divLp.setMargins(0, 0, 0, Math.round(CobassSpacing.SPACE_MD * density));
        root.addView(divider, divLp);

        // Content Area
        if (contentView instanceof ScrollView) {
            root.addView(contentView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ));
        } else {
            ScrollView scroll = new ScrollView(context);
            scroll.setFillViewport(true);
            scroll.addView(contentView);
            root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ));
        }

        return root;
    }

    public static void configureWindow(Dialog dialog) {
        if (dialog != null && dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }
}
