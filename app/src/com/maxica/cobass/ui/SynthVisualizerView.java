package com.maxica.cobass.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;
import com.maxica.cobass.audio.AudioEngineNative;

public class SynthVisualizerView extends View {

    public enum DisplayMode {
        COMBINED_HUD,
        OSCILLOSCOPE,
        FILTER_CURVE,
        ADSR_ENVELOPE
    }

    private DisplayMode currentMode = DisplayMode.COMBINED_HUD;
    private final Handler animHandler = new Handler(Looper.getMainLooper());
    private boolean isAnimating = false;
    private float animPhase = 0.0f;

    // Filter Parameters
    private float cutoffHz = 3500.0f;
    private float resonanceQ = 1.5f;

    // ADSR Envelope Parameters
    private float attackMs = 15.0f;
    private float decayMs = 120.0f;
    private float sustainPct = 0.70f;
    private float releaseMs = 250.0f;

    // Live Audio Telemetry
    private float peakEnergy = 0.0f;

    // Drawing Primitives
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint oscPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint filterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint filterFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint envPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint envFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Path fillPath = new Path();
    private final RectF rectF = new RectF();

    public SynthVisualizerView(Context context) {
        super(context);
        init();
    }

    public SynthVisualizerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        bgPaint.setStyle(Paint.Style.FILL);
        bgPaint.setColor(Color.parseColor("#12141C"));

        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1.0f);
        gridPaint.setColor(Color.parseColor("#222634"));

        textPaint.setColor(Color.parseColor("#8E8E93"));
        textPaint.setTextSize(20.0f);
        textPaint.setFakeBoldText(true);

        oscPaint.setStyle(Paint.Style.STROKE);
        oscPaint.setStrokeWidth(2.5f);
        oscPaint.setColor(Color.parseColor("#30D158"));
        oscPaint.setStrokeCap(Paint.Cap.ROUND);

        filterPaint.setStyle(Paint.Style.STROKE);
        filterPaint.setStrokeWidth(3.0f);
        filterPaint.setColor(Color.parseColor("#0A84FF"));
        filterPaint.setStrokeCap(Paint.Cap.ROUND);

        filterFillPaint.setStyle(Paint.Style.FILL);
        filterFillPaint.setColor(Color.parseColor("#220A84FF"));

        envPaint.setStyle(Paint.Style.STROKE);
        envPaint.setStrokeWidth(3.0f);
        envPaint.setColor(Color.parseColor("#FF9F0A"));
        envPaint.setStrokeCap(Paint.Cap.ROUND);

        envFillPaint.setStyle(Paint.Style.FILL);
        envFillPaint.setColor(Color.parseColor("#22FF9F0A"));
    }

    public void setFilterParams(float cutoff, float resonance) {
        this.cutoffHz = Math.max(20.0f, Math.min(20000.0f, cutoff));
        this.resonanceQ = Math.max(0.1f, Math.min(16.0f, resonance));
        invalidate();
    }

    public void setEnvelopeParams(float attack, float decay, float sustain, float release) {
        this.attackMs = Math.max(1.0f, attack);
        this.decayMs = Math.max(5.0f, decay);
        this.sustainPct = Math.max(0.0f, Math.min(1.0f, sustain));
        this.releaseMs = Math.max(5.0f, release);
        invalidate();
    }

    public void setDisplayMode(DisplayMode mode) {
        this.currentMode = mode;
        invalidate();
    }

    public DisplayMode getDisplayMode() { return currentMode; }

    public void startAnimation() {
        if (isAnimating) return;
        isAnimating = true;
        animHandler.post(new Runnable() {
            @Override
            public void run() {
                if (!isAnimating) return;
                animPhase += 0.08f;
                if (animPhase > 6.2831853f) animPhase -= 6.2831853f;

                if (AudioEngineNative.isLoaded()) {
                    peakEnergy = AudioEngineNative.nativeGetMasterPeakL();
                }
                invalidate();
                animHandler.postDelayed(this, 16); // 60 FPS
            }
        });
    }

    public void stopAnimation() {
        isAnimating = false;
        animHandler.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        final float width = getWidth();
        final float height = getHeight();

        // Background
        canvas.drawRect(0, 0, width, height, bgPaint);

        // Subtle Border & Axis
        rectF.set(1f, 1f, width - 1f, height - 1f);
        gridPaint.setColor(Color.parseColor("#262938"));
        canvas.drawRoundRect(rectF, 8f, 8f, gridPaint);

        switch (currentMode) {
            case COMBINED_HUD:
                drawCombinedHud(canvas, width, height);
                break;
            case OSCILLOSCOPE:
                drawOscilloscope(canvas, 0, 0, width, height, "LIVE OSCILLOSCOPE");
                break;
            case FILTER_CURVE:
                drawFilterCurve(canvas, 0, 0, width, height, "24dB ZDF LADDER FREQUENCY RESPONSE");
                break;
            case ADSR_ENVELOPE:
                drawAdsrEnvelope(canvas, 0, 0, width, height, "EXPONENTIAL ADSR ENVELOPE");
                break;
        }
    }

    private void drawCombinedHud(Canvas canvas, float width, float height) {
        final float colWidth = width / 3.0f;

        // Column Dividers
        gridPaint.setColor(Color.parseColor("#222634"));
        canvas.drawLine(colWidth, 0, colWidth, height, gridPaint);
        canvas.drawLine(colWidth * 2.0f, 0, colWidth * 2.0f, height, gridPaint);

        // Left Pane: Oscilloscope
        drawOscilloscope(canvas, 0, 0, colWidth, height, "OSCILLOSCOPE");

        // Center Pane: Filter Curve
        drawFilterCurve(canvas, colWidth, 0, colWidth, height, String.format("FILTER: %.0fHz", cutoffHz));

        // Right Pane: ADSR
        drawAdsrEnvelope(canvas, colWidth * 2.0f, 0, colWidth, height, "ADSR ENVELOPE");
    }

    private void drawOscilloscope(Canvas canvas, float left, float top, float width, float height, String label) {
        textPaint.setColor(Color.parseColor("#30D158"));
        canvas.drawText(label, left + 14f, top + 22f, textPaint);

        final float midY = top + (height * 0.55f);
        final float maxAmp = (height * 0.35f) * Math.max(0.3f, peakEnergy * 1.5f);

        path.reset();
        path.moveTo(left + 8f, midY);

        final int steps = 48;
        for (int i = 0; i <= steps; i++) {
            float frac = (float) i / steps;
            float px = left + 8f + frac * (width - 16f);
            float wave1 = (float) Math.sin(frac * 12.566f + animPhase);
            float wave2 = (float) (2.0 * (Math.sin(frac * 25.132f + animPhase * 1.5f) * 0.35f));
            float py = midY - (wave1 + wave2) * maxAmp;
            path.lineTo(px, py);
        }

        canvas.drawPath(path, oscPaint);
    }

    private void drawFilterCurve(Canvas canvas, float left, float top, float width, float height, String label) {
        textPaint.setColor(Color.parseColor("#0A84FF"));
        canvas.drawText(label, left + 14f, top + 22f, textPaint);

        final float plotBottom = top + height - 8f;
        final float plotTop = top + 28f;
        final float plotHeight = plotBottom - plotTop;

        path.reset();
        fillPath.reset();
        fillPath.moveTo(left + 8f, plotBottom);

        final int steps = 40;
        final float cutoffNormalized = (float) (Math.log10(cutoffHz / 20.0f) / Math.log10(20000.0f / 20.0f));
        final float peakX = left + 8f + cutoffNormalized * (width - 16f);

        for (int i = 0; i <= steps; i++) {
            float frac = (float) i / steps;
            float px = left + 8f + frac * (width - 16f);

            // 4-Pole 24dB Moog Ladder filter magnitude approximation
            float freqHz = (float) (20.0f * Math.pow(1000.0f, frac));
            float fRatio = freqHz / cutoffHz;
            float mag = 1.0f / (float) Math.sqrt(1.0 + Math.pow(fRatio, 8.0));

            // Resonant bump near cutoff
            float resonanceBump = (float) Math.exp(-Math.pow((frac - cutoffNormalized) * 6.0f, 2.0)) * (resonanceQ * 0.35f);
            mag = Math.min(1.6f, mag + resonanceBump);

            float py = plotBottom - (mag * plotHeight * 0.65f);
            if (i == 0) {
                path.moveTo(px, py);
                fillPath.lineTo(px, py);
            } else {
                path.lineTo(px, py);
                fillPath.lineTo(px, py);
            }
        }

        fillPath.lineTo(left + width - 8f, plotBottom);
        fillPath.close();

        canvas.drawPath(fillPath, filterFillPaint);
        canvas.drawPath(path, filterPaint);

        // Peak anchor dot
        filterPaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(peakX, plotTop + 14f, 4f, filterPaint);
        filterPaint.setStyle(Paint.Style.STROKE);
    }

    private void drawAdsrEnvelope(Canvas canvas, float left, float top, float width, float height, String label) {
        textPaint.setColor(Color.parseColor("#FF9F0A"));
        canvas.drawText(label, left + 14f, top + 22f, textPaint);

        final float plotBottom = top + height - 8f;
        final float plotTop = top + 28f;
        final float plotHeight = plotBottom - plotTop;
        final float plotWidth = width - 16f;

        float totalTime = attackMs + decayMs + 200.0f + releaseMs;
        float x0 = left + 8f;
        float x1 = x0 + (attackMs / totalTime) * plotWidth;
        float x2 = x1 + (decayMs / totalTime) * plotWidth;
        float x3 = x2 + (200.0f / totalTime) * plotWidth;
        float x4 = x0 + plotWidth;

        float yPeak = plotTop + 4f;
        float ySustain = plotBottom - (sustainPct * plotHeight);

        path.reset();
        fillPath.reset();

        path.moveTo(x0, plotBottom);
        fillPath.moveTo(x0, plotBottom);

        // Attack (Exponential curve)
        path.quadTo((x0 + x1) * 0.5f, yPeak + (plotHeight * 0.2f), x1, yPeak);
        fillPath.quadTo((x0 + x1) * 0.5f, yPeak + (plotHeight * 0.2f), x1, yPeak);

        // Decay (Soft exponential drop)
        path.quadTo((x1 + x2) * 0.5f, yPeak + (plotBottom - ySustain) * 0.4f, x2, ySustain);
        fillPath.quadTo((x1 + x2) * 0.5f, yPeak + (plotBottom - ySustain) * 0.4f, x2, ySustain);

        // Sustain stage
        path.lineTo(x3, ySustain);
        fillPath.lineTo(x3, ySustain);

        // Release stage
        path.quadTo((x3 + x4) * 0.5f, ySustain + (plotBottom - ySustain) * 0.7f, x4, plotBottom);
        fillPath.quadTo((x3 + x4) * 0.5f, ySustain + (plotBottom - ySustain) * 0.7f, x4, plotBottom);

        fillPath.close();

        canvas.drawPath(fillPath, envFillPaint);
        canvas.drawPath(path, envPaint);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            // Cycle Display Modes on touch
            switch (currentMode) {
                case COMBINED_HUD:   currentMode = DisplayMode.OSCILLOSCOPE; break;
                case OSCILLOSCOPE:   currentMode = DisplayMode.FILTER_CURVE; break;
                case FILTER_CURVE:   currentMode = DisplayMode.ADSR_ENVELOPE; break;
                case ADSR_ENVELOPE:  currentMode = DisplayMode.COMBINED_HUD; break;
            }
            invalidate();
            return true;
        }
        return true;
    }
}
