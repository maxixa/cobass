package com.maxica.cobass.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

public final class CobassCanvasTheme {

    private CobassCanvasTheme() {}

    // Pre-allocated static Paint singletons (Zero allocations at 60 FPS)
    public static final Paint PAINT_CANVAS_BG = new Paint(Paint.ANTI_ALIAS_FLAG);
    public static final Paint PAINT_GRID_BAR = new Paint(Paint.ANTI_ALIAS_FLAG);
    public static final Paint PAINT_GRID_BEAT = new Paint(Paint.ANTI_ALIAS_FLAG);
    public static final Paint PAINT_GRID_SUB = new Paint(Paint.ANTI_ALIAS_FLAG);

    public static final Paint PAINT_RULER_BG = new Paint(Paint.ANTI_ALIAS_FLAG);
    public static final Paint PAINT_RULER_TEXT = new Paint(Paint.ANTI_ALIAS_FLAG);
    public static final Paint PAINT_RULER_TICK = new Paint(Paint.ANTI_ALIAS_FLAG);

    public static final Paint PAINT_PLAYHEAD_NEEDLE = new Paint(Paint.ANTI_ALIAS_FLAG);
    public static final Paint PAINT_PLAYHEAD_FLAG = new Paint(Paint.ANTI_ALIAS_FLAG);

    public static final Paint PAINT_LOOP_OVERLAY = new Paint(Paint.ANTI_ALIAS_FLAG);
    public static final Paint PAINT_LOOP_BORDER = new Paint(Paint.ANTI_ALIAS_FLAG);
    public static final Paint PAINT_LOOP_HANDLE = new Paint(Paint.ANTI_ALIAS_FLAG);

    public static final Paint PAINT_MARQUEE_FILL = new Paint(Paint.ANTI_ALIAS_FLAG);
    public static final Paint PAINT_MARQUEE_BORDER = new Paint(Paint.ANTI_ALIAS_FLAG);

    public static final Paint PAINT_SELECTION_BORDER = new Paint(Paint.ANTI_ALIAS_FLAG);

    private static final Path SHARED_FLAG_PATH = new Path();

    static {
        PAINT_CANVAS_BG.setStyle(Paint.Style.FILL);
        PAINT_CANVAS_BG.setColor(CobassTheme.SURFACE_0);

        PAINT_GRID_BAR.setStyle(Paint.Style.STROKE);
        PAINT_GRID_BAR.setColor(CobassTheme.GRID_BAR);
        PAINT_GRID_BAR.setStrokeWidth(CobassTheme.BORDER_ACTIVE);

        PAINT_GRID_BEAT.setStyle(Paint.Style.STROKE);
        PAINT_GRID_BEAT.setColor(CobassTheme.GRID_BEAT);
        PAINT_GRID_BEAT.setStrokeWidth(CobassTheme.BORDER_THIN);

        PAINT_GRID_SUB.setStyle(Paint.Style.STROKE);
        PAINT_GRID_SUB.setColor(CobassTheme.GRID_SUBDIVISION);
        PAINT_GRID_SUB.setStrokeWidth(CobassTheme.BORDER_THIN);

        PAINT_RULER_BG.setStyle(Paint.Style.FILL);
        PAINT_RULER_BG.setColor(CobassTheme.SURFACE_1);

        PAINT_RULER_TEXT.setColor(CobassTheme.TEXT_SECONDARY);
        PAINT_RULER_TEXT.setFakeBoldText(true);

        PAINT_RULER_TICK.setStyle(Paint.Style.STROKE);
        PAINT_RULER_TICK.setColor(CobassTheme.SURFACE_3);
        PAINT_RULER_TICK.setStrokeWidth(CobassTheme.BORDER_THIN);

        PAINT_PLAYHEAD_NEEDLE.setStyle(Paint.Style.STROKE);
        PAINT_PLAYHEAD_NEEDLE.setColor(CobassTheme.PLAYHEAD_NEEDLE);
        PAINT_PLAYHEAD_NEEDLE.setStrokeWidth(CobassTheme.BORDER_ACTIVE);

        PAINT_PLAYHEAD_FLAG.setStyle(Paint.Style.FILL);
        PAINT_PLAYHEAD_FLAG.setColor(CobassTheme.PLAYHEAD_NEEDLE);

        PAINT_LOOP_OVERLAY.setStyle(Paint.Style.FILL);
        PAINT_LOOP_OVERLAY.setColor(CobassTheme.LOOP_OVERLAY);

        PAINT_LOOP_BORDER.setStyle(Paint.Style.STROKE);
        PAINT_LOOP_BORDER.setColor(CobassTheme.LOOP_BORDER);
        PAINT_LOOP_BORDER.setStrokeWidth(CobassTheme.BORDER_STANDARD);

        PAINT_LOOP_HANDLE.setStyle(Paint.Style.FILL);
        PAINT_LOOP_HANDLE.setColor(CobassTheme.ACCENT_PRIMARY);

        PAINT_MARQUEE_FILL.setStyle(Paint.Style.FILL);
        PAINT_MARQUEE_FILL.setColor(CobassTheme.MARQUEE_FILL);

        PAINT_MARQUEE_BORDER.setStyle(Paint.Style.STROKE);
        PAINT_MARQUEE_BORDER.setColor(CobassTheme.MARQUEE_BORDER);
        PAINT_MARQUEE_BORDER.setStrokeWidth(CobassTheme.BORDER_STANDARD);

        PAINT_SELECTION_BORDER.setStyle(Paint.Style.STROKE);
        PAINT_SELECTION_BORDER.setColor(CobassTheme.SELECTION_BORDER);
        PAINT_SELECTION_BORDER.setStrokeWidth(CobassTheme.BORDER_SELECTION);
    }

    public static void drawPlayheadNeedle(Canvas canvas, float x, float top, float bottom, float uiScale) {
        PAINT_PLAYHEAD_NEEDLE.setStrokeWidth(CobassTheme.BORDER_ACTIVE * uiScale);
        canvas.drawLine(x, top, x, bottom, PAINT_PLAYHEAD_NEEDLE);

        SHARED_FLAG_PATH.reset();
        SHARED_FLAG_PATH.moveTo(x - (8f * uiScale), top);
        SHARED_FLAG_PATH.lineTo(x + (8f * uiScale), top);
        SHARED_FLAG_PATH.lineTo(x, top + (12f * uiScale));
        SHARED_FLAG_PATH.close();
        canvas.drawPath(SHARED_FLAG_PATH, PAINT_PLAYHEAD_FLAG);
    }

    public static void drawLoopOverlay(Canvas canvas, float left, float right, float top, float bottom) {
        canvas.drawRect(left, top, right, bottom, PAINT_LOOP_OVERLAY);
        canvas.drawLine(left, top, left, bottom, PAINT_LOOP_BORDER);
        canvas.drawLine(right, top, right, bottom, PAINT_LOOP_BORDER);
    }

    public static void drawMarquee(Canvas canvas, RectF rect) {
        canvas.drawRect(rect, PAINT_MARQUEE_FILL);
        canvas.drawRect(rect, PAINT_MARQUEE_BORDER);
    }

    public static void drawMarquee(Canvas canvas, float left, float top, float right, float bottom) {
        canvas.drawRect(left, top, right, bottom, PAINT_MARQUEE_FILL);
        canvas.drawRect(left, top, right, bottom, PAINT_MARQUEE_BORDER);
    }

    public static void drawGridLine(Canvas canvas, float x, float top, float bottom, boolean isBar, boolean isBeat, float uiScale) {
        if (isBar) {
            PAINT_GRID_BAR.setStrokeWidth(CobassTheme.BORDER_ACTIVE * uiScale);
            canvas.drawLine(x, top, x, bottom, PAINT_GRID_BAR);
        } else if (isBeat) {
            PAINT_GRID_BEAT.setStrokeWidth(CobassTheme.BORDER_THIN * uiScale);
            canvas.drawLine(x, top, x, bottom, PAINT_GRID_BEAT);
        } else {
            PAINT_GRID_SUB.setStrokeWidth(CobassTheme.BORDER_THIN * uiScale);
            canvas.drawLine(x, top, x, bottom, PAINT_GRID_SUB);
        }
    }
}
