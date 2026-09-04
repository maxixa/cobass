package com.maxica.cobass.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
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
        ADSR_ENVELOPE,
        DRUM_MATRIX_HUD
    }

    private DisplayMode currentMode = DisplayMode.COMBINED_HUD;
    private final Handler animHandler = new Handler(Looper.getMainLooper());
    private boolean isAnimating = false;
    private float animPhase = 0.0f;

    // Filter Parameters
    private float cutoffHz = 3500.0f;
    private float resonanceQ = 1.8f;
    private int filterMode = 0; // 0=Ladder24, 1=Diode18, 6=Formant, 7=Comb

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
    private final Paint drumPadPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
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

        drumPadPaint.setStyle(Paint.Style.FILL);
    }

    public void setFilterParams(float cutoff, float resonance) {
        this.cutoffHz = Math.max(20.0f, Math.min(20000.0f, cutoff));
        this.resonanceQ = Math.max(0.1f, Math.min(16.0f, resonance));
        invalidate();
    }

    public void setFilterMode(int mode) {
        this.filterMode = mode;
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
                animHandler.postDelayed(this, 16);
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

        canvas.drawRect(0, 0, width, height, bgPaint);

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
                drawFilterCurve(canvas, 0, 0, width, height, "DANCE FILTER FREQUENCY RESPONSE");
                break;
            case ADSR_ENVELOPE:
                drawAdsrEnvelope(canvas, 0, 0, width, height, "EXPONENTIAL ADSR ENVELOPE");
                break;
            case DRUM_MATRIX_HUD:
                drawDrumMatrixHud(canvas, width, height);
                break;
        }
    }

    private void drawCombinedHud(Canvas canvas, float width, float height) {
        final float colWidth = width / 3.0f;

        gridPaint.setColor(Color.parseColor("#222634"));
        canvas.drawLine(colWidth, 0, colWidth, height, gridPaint);
        canvas.drawLine(colWidth * 2.0f, 0, colWidth * 2.0f, height, gridPaint);

        drawOscilloscope(canvas, 0, 0, colWidth, height, "OSCILLOSCOPE");
        drawFilterCurve(canvas, colWidth, 0, colWidth, height, String.format("FILTER: %.0fHz", cutoffHz));
        drawAdsrEnvelope(canvas, colWidth * 2.0f, 0, colWidth, height, "ADSR ENVELOPE");
    }

    private void drawDrumMatrixHud(Canvas canvas, float width, float height) {
        final float padW = (width - 48f) / 8f;
        final float padH = height - 38f;
        final String[] drumNames = {"BD", "SD", "CL", "CH", "OH", "TM", "RM", "CB"};
        final String[] subLabels = {"808/909", "Modal", "Flam", "Schmitt", "FM Cym", "Slap", "Clave", "Agogo"};
        final int[] colors = {
            Color.parseColor("#0A84FF"), Color.parseColor("#FF9F0A"),
            Color.parseColor("#30D158"), Color.parseColor("#BF5AF2"),
            Color.parseColor("#FF453A"), Color.parseColor("#64D2FF"),
            Color.parseColor("#FFD60A"), Color.parseColor("#AC8E68")
        };

        for (int i = 0; i < 8; i++) {
            float px = 8f + (i * (padW + 4f));
            float py = 26f;

            float vEnergy = Math.max(0.20f, Math.min(1.0f, peakEnergy * (1.1f + (i % 4) * 0.25f)));
            int alpha = (int) (vEnergy * 255);

            drumPadPaint.setColor(Color.argb(alpha, Color.red(colors[i]), Color.green(colors[i]), Color.blue(colors[i])));
            rectF.set(px, py, px + padW, py + padH);
            canvas.drawRoundRect(rectF, 6f, 6f, drumPadPaint);

            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(11f);
            canvas.drawText(drumNames[i], px + (padW * 0.25f), py + (padH * 0.50f), textPaint);

            textPaint.setColor(Color.parseColor("#D0D0D0"));
            textPaint.setTextSize(8f);
            canvas.drawText(subLabels[i], px + (padW * 0.12f), py + (padH * 0.82f), textPaint);
        }

        textPaint.setColor(Color.parseColor("#30D158"));
        textPaint.setTextSize(12f);
        canvas.drawText("⚡ COBALT HYBRID DRUM MATRIX v2 (52-PARAM HYBRID DSP)", 14f, 18f, textPaint);
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

            float freqHz = (float) (20.0f * Math.pow(1000.0f, frac));
            float fRatio = freqHz / cutoffHz;
            float mag = 1.0f;

            if (filterMode == 1) {
                // Diode 18dB Acid Ladder
                mag = 1.0f / (float) Math.sqrt(1.0 + Math.pow(fRatio, 6.0));
                float acidRes = (float) Math.exp(-Math.pow((frac - cutoffNormalized) * 7.0f, 2.0)) * (resonanceQ * 0.45f);
                mag = Math.min(1.8f, mag + acidRes);
            } else if (filterMode == 6) {
                // 3-Peak Formant Vowel Filter
                float p1 = (float) Math.exp(-Math.pow(frac - 0.35f, 2.0) * 45.0f);
                float p2 = (float) Math.exp(-Math.pow(frac - 0.60f, 2.0) * 55.0f) * 0.7f;
                float p3 = (float) Math.exp(-Math.pow(frac - 0.85f, 2.0) * 65.0f) * 0.4f;
                mag = Math.min(1.5f, 0.2f + p1 + p2 + p3);
            } else {
                // 24dB Moog Ladder
                mag = 1.0f / (float) Math.sqrt(1.0 + Math.pow(fRatio, 8.0));
                float resBump = (float) Math.exp(-Math.pow((frac - cutoffNormalized) * 6.0f, 2.0)) * (resonanceQ * 0.35f);
                mag = Math.min(1.6f, mag + resBump);
            }

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

        path.quadTo((x0 + x1) * 0.5f, yPeak + (plotHeight * 0.2f), x1, yPeak);
        fillPath.quadTo((x0 + x1) * 0.5f, yPeak + (plotHeight * 0.2f), x1, yPeak);

        path.quadTo((x1 + x2) * 0.5f, yPeak + (plotBottom - ySustain) * 0.4f, x2, ySustain);
        fillPath.quadTo((x1 + x2) * 0.5f, yPeak + (plotBottom - ySustain) * 0.4f, x2, ySustain);

        path.lineTo(x3, ySustain);
        fillPath.lineTo(x3, ySustain);

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
            switch (currentMode) {
                case COMBINED_HUD:    currentMode = DisplayMode.DRUM_MATRIX_HUD; break;
                case DRUM_MATRIX_HUD: currentMode = DisplayMode.OSCILLOSCOPE; break;
                case OSCILLOSCOPE:    currentMode = DisplayMode.FILTER_CURVE; break;
                case FILTER_CURVE:    currentMode = DisplayMode.ADSR_ENVELOPE; break;
                case ADSR_ENVELOPE:   currentMode = DisplayMode.COMBINED_HUD; break;
            }
            invalidate();
            return true;
        }
        return true;
    }
}
