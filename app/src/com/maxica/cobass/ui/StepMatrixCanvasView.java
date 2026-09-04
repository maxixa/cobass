package com.maxica.cobass.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;
import com.maxica.cobass.model.StepPatternItem;

public class StepMatrixCanvasView extends View {

    public interface OnStepMatrixEventListener {
        void onStepToggled(int laneIndex, int stepIndex, boolean active);
        void onStepVelocityChanged(int laneIndex, int stepIndex, float velocity);
        void onStepSelected(int laneIndex, int stepIndex);
        void onLaneAudition(int laneIndex);
        void onLaneMuteToggled(int laneIndex, boolean muted);
        void onLaneSoloToggled(int laneIndex, boolean solo);
        void onLaneInspectorRequested(int laneIndex);
        void onPatternModified();
    }

    private StepPatternItem pattern;
    private OnStepMatrixEventListener listener;

    // Viewport & Scale
    private float uiScale = 1.0f;
    private float scrollX = 0f;
    private float scrollY = 0f;

    private static final float BASE_HEADER_WIDTH = 190f;
    private static final float BASE_RULER_HEIGHT = 38f;
    private static final float BASE_STEP_WIDTH = 48f;
    private static final float BASE_STEP_HEIGHT = 52f;
    private static final float BASE_STEP_GAP = 4f;

    private float headerWidth = BASE_HEADER_WIDTH;
    private float rulerHeight = BASE_RULER_HEIGHT;
    private float stepWidth = BASE_STEP_WIDTH;
    private float stepHeight = BASE_STEP_HEIGHT;
    private float stepGap = BASE_STEP_GAP;

    // Playhead & Real-Time Sync
    private long currentPlayheadTick = 0;
    private boolean isPlaying = false;

    // Selection & Editing State
    private int selectedLaneIndex = -1;
    private int selectedStepIndex = -1;

    // Touch Drag & Velocity Scrub State
    private boolean isDraggingVelocity = false;
    private int dragLaneIndex = -1;
    private int dragStepIndex = -1;
    private float dragStartRawY = 0f;
    private float dragInitialVelocity = 0.85f;

    // Two-Finger Pan Navigation
    private boolean isTwoFingerPanning = false;
    private float lastPanMidX = 0f;
    private float lastPanMidY = 0f;
    private float lastTouchX = 0f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rectF = new RectF();
    private final RectF reusableBadgeRect = new RectF();

    private static final int COLOR_BADGE_BG = 0xDD000000;
    private static final int[] LANE_PALETTE = {
        0xFF0A84FF, // Blue (Kick)
        0xFFFF9F0A, // Orange (Snare)
        0xFF30D158, // Green (Cl. Hat)
        0xFFBF5AF2, // Purple (Op. Hat)
        0xFFFF453A, // Red (Tom/Perc)
        0xFF64D2FF, // Cyan (Clap)
        0xFFFFD60A, // Yellow (Ride)
        0xFFAC8E68  // Tan (Shaker)
    };
    private GestureDetector gestureDetector;

    public StepMatrixCanvasView(Context context) {
        super(context);
        init(context);
    }

    public StepMatrixCanvasView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        updateDimensions();

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);

        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeCap(Paint.Cap.ROUND);

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public void onLongPress(MotionEvent e) {
                float x = e.getX();
                float y = e.getY();
                if (x > headerWidth && y > rulerHeight && pattern != null) {
                    int laneIdx = (int) ((y - rulerHeight + scrollY) / (stepHeight + stepGap));
                    int stepIdx = (int) ((x - headerWidth + scrollX) / (stepWidth + stepGap));

                    if (laneIdx >= 0 && laneIdx < pattern.getLanes().size()) {
                        StepPatternItem.Lane lane = pattern.getLanes().get(laneIdx);
                        if (stepIdx >= 0 && stepIdx < lane.stepCount) {
                            selectedLaneIndex = laneIdx;
                            selectedStepIndex = stepIdx;
                            if (listener != null) {
                                listener.onStepSelected(laneIdx, stepIdx);
                            }
                            invalidate();
                        }
                    }
                }
            }
        });
    }

    public void setUiScale(float scale) {
        this.uiScale = Math.max(0.75f, Math.min(1.5f, scale));
        updateDimensions();
        invalidate();
    }

    private void updateDimensions() {
        this.headerWidth = BASE_HEADER_WIDTH * uiScale;
        this.rulerHeight = BASE_RULER_HEIGHT * uiScale;
        this.stepWidth = BASE_STEP_WIDTH * uiScale;
        this.stepHeight = BASE_STEP_HEIGHT * uiScale;
        this.stepGap = BASE_STEP_GAP * uiScale;
    }

    public void setPattern(StepPatternItem pattern) {
        this.pattern = pattern;
        this.selectedLaneIndex = -1;
        this.selectedStepIndex = -1;
        invalidate();
    }

    public StepPatternItem getPattern() { return pattern; }

    public void setEventListener(OnStepMatrixEventListener listener) {
        this.listener = listener;
    }

    public void setPlayheadState(long tick, boolean isPlaying) {
        this.currentPlayheadTick = Math.max(0, tick);
        this.isPlaying = isPlaying;
        if (isPlaying) postInvalidateOnAnimation(); else invalidate();
    }

    public void setSelectedStep(int laneIndex, int stepIndex) {
        this.selectedLaneIndex = laneIndex;
        this.selectedStepIndex = stepIndex;
        invalidate();
    }

    public int getSelectedLaneIndex() { return selectedLaneIndex; }
    public int getSelectedStepIndex() { return selectedStepIndex; }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        final int width = getWidth();
        final int height = getHeight();

        // 1. Dark Studio Background
        canvas.drawRect(0, 0, width, height, CobassCanvasTheme.PAINT_CANVAS_BG);

        if (pattern == null || pattern.getLanes().isEmpty()) {
            paint.setColor(0xFF8E8E93);
            paint.setTextSize(14f * uiScale);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("No Step Sequencer Lanes Loaded", width / 2.0f, height / 2.0f, paint);
            return;
        }

        final int maxPatternSteps = pattern.getBaseLength();

        canvas.save();
        canvas.clipRect(headerWidth, rulerHeight, width, height);

        // 2. Step Column Backgrounds (Alternating 4-Beat Group Striping)
        for (int s = 0; s < 64; s++) {
            float sx = headerWidth + (s * (stepWidth + stepGap)) - scrollX;
            if (sx + stepWidth < headerWidth || sx > width) continue;

            boolean isEvenBeatGroup = ((s / 4) % 2 == 0);
            paint.setColor(isEvenBeatGroup ? 0xFF161820 : 0xFF1A1D26);
            canvas.drawRect(sx - (stepGap / 2f), rulerHeight, sx + stepWidth + (stepGap / 2f), height, paint);

            // Beat Divider Accent Lines (every 4 steps)
            if (s % 4 == 0) {
                CobassCanvasTheme.PAINT_GRID_BAR.setStrokeWidth(CobassTheme.BORDER_STANDARD * uiScale);
                canvas.drawLine(sx - (stepGap / 2f), rulerHeight, sx - (stepGap / 2f), height, CobassCanvasTheme.PAINT_GRID_BAR);
            }
        }

        // 3. Render Step Matrix Pads per Lane
        for (int l = 0; l < pattern.getLanes().size(); l++) {
            StepPatternItem.Lane lane = pattern.getLanes().get(l);
            float ly = rulerHeight + (l * (stepHeight + stepGap)) - scrollY;
            if (ly + stepHeight < rulerHeight || ly > height) continue;

            for (int s = 0; s < 64; s++) {
                float sx = headerWidth + (s * (stepWidth + stepGap)) - scrollX;
                if (sx + stepWidth < headerWidth || sx > width) continue;

                rectF.set(sx, ly, sx + stepWidth, ly + stepHeight);
                boolean isBeyondLaneLength = (s >= lane.stepCount);
                StepPatternItem.Step step = s < lane.steps.size() ? lane.steps.get(s) : null;
                boolean isActive = (step != null && step.active && !isBeyondLaneLength);

                if (isBeyondLaneLength) {
                    // Out-of-bounds polymeter steps (Dimmed Recessed Look)
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(0xFF0E1015);
                    canvas.drawRoundRect(rectF, 6f * uiScale, 6f * uiScale, paint);

                    paint.setStyle(Paint.Style.STROKE);
                    paint.setColor(0xFF181A22);
                    paint.setStrokeWidth(1f * uiScale);
                    canvas.drawRoundRect(rectF, 6f * uiScale, 6f * uiScale, paint);
                    continue;
                }

                // In-Bounds Step Pad
                paint.setStyle(Paint.Style.FILL);
                if (isActive) {
                    // Active Neon Glow Pad (Color weighted by velocity)
                    int padColor = getNeonPadColor(l, step.velocity);
                    paint.setColor(padColor);
                    canvas.drawRoundRect(rectF, 6f * uiScale, 6f * uiScale, paint);

                    // Top Highlight Bevel
                    paint.setColor(Color.argb(90, 255, 255, 255));
                    canvas.drawRect(sx + 4f, ly + 2f, sx + stepWidth - 4f, ly + (6f * uiScale), paint);
                } else {
                    // Inactive Step Button (Recessed pad)
                    boolean isDownbeat = (s % 4 == 0);
                    paint.setColor(isDownbeat ? 0xFF262936 : 0xFF1F212C);
                    canvas.drawRoundRect(rectF, 6f * uiScale, 6f * uiScale, paint);

                    paint.setStyle(Paint.Style.STROKE);
                    paint.setColor(0xFF323646);
                    paint.setStrokeWidth(1f * uiScale);
                    canvas.drawRoundRect(rectF, 6f * uiScale, 6f * uiScale, paint);
                }

                // Selection Ring
                if (l == selectedLaneIndex && s == selectedStepIndex) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setColor(CobassTheme.SELECTION_BORDER);
                    paint.setStrokeWidth(CobassTheme.BORDER_SELECTION * uiScale);
                    canvas.drawRoundRect(rectF, CobassTheme.RADIUS_MD * uiScale, CobassTheme.RADIUS_MD * uiScale, paint);
                }

                // Sub-Step Ratchet Badge (e.g., 2x, 3x, 4x, 8x rolls)
                if (isActive && step.ratchets > 1) {
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(COLOR_BADGE_BG);
                    reusableBadgeRect.set(sx + 3f, ly + stepHeight - (16f * uiScale), sx + (22f * uiScale), ly + stepHeight - 3f);
                    canvas.drawRoundRect(reusableBadgeRect, 3f * uiScale, 3f * uiScale, paint);

                    textPaint.setColor(0xFFFFD60A);
                    textPaint.setTextSize(10f * uiScale);
                    canvas.drawText(step.ratchets + "x", sx + (12f * uiScale), ly + stepHeight - (6f * uiScale), textPaint);
                }

                // Micro-Nudge Indicator (small dot / offset tick)
                if (isActive && Math.abs(step.nudge) > 0.05f) {
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(0xFF64D2FF);
                    float nudgeDotX = sx + (stepWidth / 2f) + (step.nudge * (stepWidth * 0.35f));
                    canvas.drawCircle(nudgeDotX, ly + (8f * uiScale), 3f * uiScale, paint);
                }

                // Probability Dot (if < 100%)
                if (isActive && step.probability < 0.99f) {
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(0xFFFF453A);
                    canvas.drawCircle(sx + stepWidth - (7f * uiScale), ly + (8f * uiScale), 3f * uiScale, paint);
                }
            }

            // Polymeter Lane Boundary Marker Bar
            float boundX = headerWidth + (lane.stepCount * (stepWidth + stepGap)) - scrollX - (stepGap / 2f);
            paint.setColor(0xFFFF9F0A);
            paint.setStrokeWidth(3f * uiScale);
            canvas.drawLine(boundX, ly, boundX, ly + stepHeight, paint);
        }

        // 4. Live Playhead Vertical Sweep Cursor over Steps
        if (isPlaying) {
            for (int l = 0; l < pattern.getLanes().size(); l++) {
                StepPatternItem.Lane lane = pattern.getLanes().get(l);
                if (lane.stepCount <= 0) continue;

                int laneTicks = lane.stepCount * lane.subdivision.getTicks();
                if (laneTicks <= 0) continue;

                long patternTick = currentPlayheadTick % laneTicks;
                int currentActiveStep = (int) ((patternTick / lane.subdivision.getTicks()) % lane.stepCount);

                float stepPx = headerWidth + (currentActiveStep * (stepWidth + stepGap)) - scrollX;
                float ly = rulerHeight + (l * (stepHeight + stepGap)) - scrollY;

                // High-visibility illuminated cursor border around current step
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(0xFFFFFFFF);
                paint.setStrokeWidth(2.5f * uiScale);
                rectF.set(stepPx - 1f, ly - 1f, stepPx + stepWidth + 1f, ly + stepHeight + 1f);
                canvas.drawRoundRect(rectF, 7f * uiScale, 7f * uiScale, paint);
            }
        }

        canvas.restore();

        // 5. Top Step Ruler Strip
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF151720);
        canvas.drawRect(headerWidth, 0, width, rulerHeight, paint);
        paint.setColor(0xFF262936);
        paint.setStrokeWidth(1.5f * uiScale);
        canvas.drawLine(headerWidth, rulerHeight, width, rulerHeight, paint);

        for (int s = 0; s < 64; s++) {
            float sx = headerWidth + (s * (stepWidth + stepGap)) - scrollX;
            if (sx + stepWidth < headerWidth || sx > width) continue;

            boolean isDownbeat = (s % 4 == 0);
            textPaint.setColor(isDownbeat ? 0xFFFFFFFF : 0xFF8E8E93);
            textPaint.setTextSize(isDownbeat ? (13f * uiScale) : (11f * uiScale));
            canvas.drawText(String.format("%02d", s + 1), sx + (stepWidth / 2.0f), rulerHeight * 0.65f, textPaint);
        }

        // 6. Left Lane Headers
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF181A22);
        canvas.drawRect(0, 0, headerWidth, height, paint);
        paint.setColor(0xFF282B38);
        paint.setStrokeWidth(1.5f * uiScale);
        canvas.drawLine(headerWidth, 0, headerWidth, height, paint);

        // Top-Left Header Badge
        paint.setColor(0xFF121318);
        canvas.drawRect(0, 0, headerWidth, rulerHeight, paint);
        textPaint.setColor(0xFF0A84FF);
        textPaint.setTextSize(14f * uiScale);
        canvas.drawText("DRUM MATRIX", headerWidth / 2.0f, rulerHeight * 0.65f, textPaint);

        for (int l = 0; l < pattern.getLanes().size(); l++) {
            StepPatternItem.Lane lane = pattern.getLanes().get(l);
            float ly = rulerHeight + (l * (stepHeight + stepGap)) - scrollY;
            if (ly + stepHeight < rulerHeight || ly > height) continue;

            // Header Body Fill
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(lane.isMuted ? 0xFF14151B : 0xFF1C1E28);
            canvas.drawRect(0, ly, headerWidth, ly + stepHeight, paint);

            // Left Color Accent Strip
            paint.setColor(getLaneAccentColor(l));
            canvas.drawRect(0, ly, 6f * uiScale, ly + stepHeight, paint);

            // Lane Name & Step Count Readout
            textPaint.setColor(lane.isMuted ? 0xFF636366 : Color.WHITE);
            textPaint.setTextSize(13f * uiScale);
            textPaint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(lane.name, 12f * uiScale, ly + (20f * uiScale), textPaint);

            paint.setColor(0xFF8E8E93);
            paint.setTextSize(10f * uiScale);
            paint.setFakeBoldText(false);
            canvas.drawText(lane.stepCount + " steps • " + (int)(lane.volume * 100) + "%", 12f * uiScale, ly + (38f * uiScale), paint);

            // Inline [M] Mute Button
            float btnY = ly + (10f * uiScale);
            float mX1 = headerWidth - (84f * uiScale);
            float mX2 = mX1 + (24f * uiScale);
            rectF.set(mX1, btnY, mX2, btnY + (28f * uiScale));
            paint.setColor(lane.isMuted ? 0xFFFF453A : 0xFF2C2F3C);
            canvas.drawRoundRect(rectF, 4f * uiScale, 4f * uiScale, paint);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(10f * uiScale);
            textPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("M", mX1 + (12f * uiScale), btnY + (18f * uiScale), textPaint);

            // Inline [S] Solo Button
            float sX1 = mX2 + (4f * uiScale);
            float sX2 = sX1 + (24f * uiScale);
            rectF.set(sX1, btnY, sX2, btnY + (28f * uiScale));
            paint.setColor(lane.isSolo ? 0xFFFFD60A : 0xFF2C2F3C);
            canvas.drawRoundRect(rectF, 4f * uiScale, 4f * uiScale, paint);
            textPaint.setColor(lane.isSolo ? Color.BLACK : Color.WHITE);
            canvas.drawText("S", sX1 + (12f * uiScale), btnY + (18f * uiScale), textPaint);

            // Inline [⚙] Settings Button
            float gX1 = sX2 + (4f * uiScale);
            float gX2 = gX1 + (24f * uiScale);
            rectF.set(gX1, btnY, gX2, btnY + (28f * uiScale));
            paint.setColor(0xFF2C2F3C);
            canvas.drawRoundRect(rectF, 4f * uiScale, 4f * uiScale, paint);
            textPaint.setColor(0xFFC7C7CC);
            canvas.drawText("⚙", gX1 + (12f * uiScale), btnY + (18f * uiScale), textPaint);

            // Row Bottom Divider
            paint.setColor(0xFF222530);
            paint.setStrokeWidth(1f * uiScale);
            canvas.drawLine(0, ly + stepHeight, headerWidth, ly + stepHeight, paint);
        }
    }

    private int getNeonPadColor(int laneIndex, float velocity) {
        int baseColor = getLaneAccentColor(laneIndex);
        float v = Math.max(0.2f, Math.min(1.0f, velocity));
        int alpha = (int) (v * 255);
        return Color.argb(alpha, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor));
    }

    private static int getLaneAccentColor(int laneIndex) {
        return LANE_PALETTE[laneIndex % LANE_PALETTE.length];
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (gestureDetector != null && gestureDetector.onTouchEvent(event)) {
            return true;
        }

        final int pointerCount = event.getPointerCount();

        // 1. Two-Finger Pan Navigation
        if (pointerCount >= 2) {
            float midX = (event.getX(0) + event.getX(1)) / 2f;
            float midY = (event.getY(0) + event.getY(1)) / 2f;

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_POINTER_DOWN:
                    isTwoFingerPanning = true;
                    lastPanMidX = midX;
                    lastPanMidY = midY;
                    isDraggingVelocity = false;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    if (isTwoFingerPanning && pattern != null) {
                        float dx = midX - lastPanMidX;
                        float dy = midY - lastPanMidY;

                        scrollX = Math.max(0, scrollX - dx);
                        float maxScrollY = Math.max(0, (pattern.getLanes().size() * (stepHeight + stepGap)) - (getHeight() - rulerHeight));
                        scrollY = Math.max(0, Math.min(maxScrollY, scrollY - dy));

                        lastPanMidX = midX;
                        lastPanMidY = midY;
                        invalidate();
                        return true;
                    }
                    break;

                case MotionEvent.ACTION_POINTER_UP:
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    isTwoFingerPanning = false;
                    break;
            }
            return true;
        }

        final float x = event.getX();
        final float y = event.getY();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                lastTouchX = x;
                isDraggingVelocity = false;

                // 2a. Left Header Interaction (Audition / Mute / Solo / Gear)
                if (x <= headerWidth && y > rulerHeight && pattern != null) {
                    int laneIdx = (int) ((y - rulerHeight + scrollY) / (stepHeight + stepGap));
                    if (laneIdx >= 0 && laneIdx < pattern.getLanes().size()) {
                        StepPatternItem.Lane lane = pattern.getLanes().get(laneIdx);
                        float btnY = rulerHeight + (laneIdx * (stepHeight + stepGap)) - scrollY + (10f * uiScale);

                        float mX1 = headerWidth - (84f * uiScale);
                        float mX2 = mX1 + (24f * uiScale);
                        float sX1 = mX2 + (4f * uiScale);
                        float sX2 = sX1 + (24f * uiScale);
                        float gX1 = sX2 + (4f * uiScale);
                        float gX2 = gX1 + (24f * uiScale);

                        if (y >= btnY - 4f && y <= btnY + (32f * uiScale)) {
                            if (x >= mX1 && x <= mX2) {
                                lane.isMuted = !lane.isMuted;
                                if (listener != null) listener.onLaneMuteToggled(laneIdx, lane.isMuted);
                                invalidate();
                                return true;
                            }
                            if (x >= sX1 && x <= sX2) {
                                lane.isSolo = !lane.isSolo;
                                if (listener != null) listener.onLaneSoloToggled(laneIdx, lane.isSolo);
                                invalidate();
                                return true;
                            }
                            if (x >= gX1 && x <= gX2) {
                                if (listener != null) listener.onLaneInspectorRequested(laneIdx);
                                return true;
                            }
                        }

                        // Tap lane header body to audition sample
                        if (listener != null) {
                            listener.onLaneAudition(laneIdx);
                        }
                        return true;
                    }
                }

                // 2b. Step Matrix Pad Interaction
                if (x > headerWidth && y > rulerHeight && pattern != null) {
                    int laneIdx = (int) ((y - rulerHeight + scrollY) / (stepHeight + stepGap));
                    int stepIdx = (int) ((x - headerWidth + scrollX) / (stepWidth + stepGap));

                    if (laneIdx >= 0 && laneIdx < pattern.getLanes().size()) {
                        StepPatternItem.Lane lane = pattern.getLanes().get(laneIdx);
                        if (stepIdx >= 0 && stepIdx < lane.stepCount) {
                            StepPatternItem.Step step = lane.steps.get(stepIdx);
                            step.active = !step.active;

                            if (step.active) {
                                isDraggingVelocity = true;
                                dragLaneIndex = laneIdx;
                                dragStepIndex = stepIdx;
                                dragStartRawY = y;
                                dragInitialVelocity = step.velocity;
                                if (listener != null) listener.onLaneAudition(laneIdx);
                            }

                            selectedLaneIndex = laneIdx;
                            selectedStepIndex = stepIdx;

                            if (listener != null) {
                                listener.onStepToggled(laneIdx, stepIdx, step.active);
                                listener.onPatternModified();
                            }
                            invalidate();
                            return true;
                        }
                    }
                }
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                if (isDraggingVelocity && dragLaneIndex >= 0 && dragStepIndex >= 0 && pattern != null) {
                    float deltaY = dragStartRawY - y; // dragging up increases velocity
                    float newVel = Math.max(0.1f, Math.min(1.0f, dragInitialVelocity + (deltaY / (120f * uiScale))));

                    StepPatternItem.Lane lane = pattern.getLanes().get(dragLaneIndex);
                    lane.steps.get(dragStepIndex).velocity = newVel;

                    if (listener != null) {
                        listener.onStepVelocityChanged(dragLaneIndex, dragStepIndex, newVel);
                        listener.onPatternModified();
                    }
                    invalidate();
                    return true;
                }

                // Canvas Background Pan
                if (!isDraggingVelocity && x > headerWidth) {
                    float dx = x - lastTouchX;
                    scrollX = Math.max(0, scrollX - dx);
                    lastTouchX = x;
                    invalidate();
                    return true;
                }
                break;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isDraggingVelocity = false;
                dragLaneIndex = -1;
                dragStepIndex = -1;
                break;
        }
        return true;
    }
}
