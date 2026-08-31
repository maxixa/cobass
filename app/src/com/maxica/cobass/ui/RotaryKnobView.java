package com.maxica.cobass.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;
import com.maxica.cobass.model.PluginParamItem;

public class RotaryKnobView extends View {

    public interface OnKnobChangeListener {
        void onValueChanged(RotaryKnobView knob, float value, boolean fromUser);
    }

    private PluginParamItem paramItem;
    private float normalizedValue = 0.5f; // 0.0f to 1.0f
    private float currentValue = 0.0f;     // Scaled value in [minValue, maxValue]
    private float defaultValue = 0.0f;
    private float minValue = 0.0f;
    private float maxValue = 1.0f;
    private boolean isLogarithmic = false;
    private String paramName = "Param";
    private String unitLabel = "";

    private boolean isFineMode = false;
    private float lastTouchY = 0f;
    private OnKnobChangeListener listener;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobBodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcRect = new RectF();

    private GestureDetector gestureDetector;

    public RotaryKnobView(Context context) {
        super(context);
        init(context);
    }

    public RotaryKnobView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(6f);
        trackPaint.setColor(Color.parseColor("#262936"));
        trackPaint.setStrokeCap(Paint.Cap.ROUND);

        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(6f);
        arcPaint.setColor(Color.parseColor("#0A84FF"));
        arcPaint.setStrokeCap(Paint.Cap.ROUND);

        knobBodyPaint.setStyle(Paint.Style.FILL);
        knobBodyPaint.setColor(Color.parseColor("#1C1E26"));

        pointerPaint.setStyle(Paint.Style.STROKE);
        pointerPaint.setStrokeWidth(4f);
        pointerPaint.setColor(Color.parseColor("#FFD60A"));
        pointerPaint.setStrokeCap(Paint.Cap.ROUND);

        textPaint.setColor(Color.parseColor("#8E8E93"));
        textPaint.setTextSize(22f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);

        valTextPaint.setColor(Color.WHITE);
        valTextPaint.setTextSize(24f);
        valTextPaint.setTextAlign(Paint.Align.CENTER);
        valTextPaint.setFakeBoldText(true);

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                setValue(defaultValue, true);
                return true;
            }
        });
    }

    public void setParamItem(PluginParamItem item) {
        this.paramItem = item;
        this.paramName = item.getName();
        this.unitLabel = item.getLabel();
        this.minValue = item.getMinValue();
        this.maxValue = item.getMaxValue();
        this.defaultValue = item.getDefaultValue();
        this.isLogarithmic = item.isLogarithmic();
        setValue(item.getDefaultValue(), false);
    }

    public PluginParamItem getParamItem() { return paramItem; }

    public void setOnKnobChangeListener(OnKnobChangeListener listener) {
        this.listener = listener;
    }

    public void setValue(float value, boolean notify) {
        this.currentValue = Math.max(minValue, Math.min(maxValue, value));
        if (isLogarithmic && minValue > 0.0f && maxValue > minValue) {
            double logMin = Math.log(minValue);
            double logMax = Math.log(maxValue);
            normalizedValue = (float) ((Math.log(this.currentValue) - logMin) / (logMax - logMin));
        } else {
            normalizedValue = (maxValue > minValue) ? (this.currentValue - minValue) / (maxValue - minValue) : 0.0f;
        }
        normalizedValue = Math.max(0.0f, Math.min(1.0f, normalizedValue));
        invalidate();
        if (notify && listener != null) {
            listener.onValueChanged(this, this.currentValue, true);
        }
    }

    public float getValue() { return currentValue; }

    private void updateValueFromNormalized(float norm, boolean notify) {
        this.normalizedValue = Math.max(0.0f, Math.min(1.0f, norm));
        if (isLogarithmic && minValue > 0.0f && maxValue > minValue) {
            double logMin = Math.log(minValue);
            double logMax = Math.log(maxValue);
            this.currentValue = (float) Math.exp(logMin + normalizedValue * (logMax - logMin));
        } else {
            this.currentValue = minValue + normalizedValue * (maxValue - minValue);
        }

        invalidate();
        if (notify && listener != null) {
            listener.onValueChanged(this, this.currentValue, true);
        }
    }

    public String getFormattedValue() {
        if (unitLabel.equalsIgnoreCase("Hz") || unitLabel.equalsIgnoreCase("kHz")) {
            if (currentValue >= 1000.0f) {
                return String.format("%.1f kHz", currentValue / 1000.0f);
            } else {
                return String.format("%.0f Hz", currentValue);
            }
        } else if (unitLabel.equalsIgnoreCase("dB")) {
            return String.format("%+.1f dB", currentValue);
        } else if (unitLabel.equalsIgnoreCase("%")) {
            return String.format("%.0f %%", currentValue * (maxValue <= 1.0f ? 100.0f : 1.0f));
        } else if (unitLabel.equalsIgnoreCase("ms") || unitLabel.equalsIgnoreCase("s")) {
            if (currentValue < 1.0f && unitLabel.equalsIgnoreCase("s")) {
                return String.format("%.0f ms", currentValue * 1000.0f);
            } else {
                return String.format("%.1f %s", currentValue, unitLabel);
            }
        } else if (maxValue - minValue <= 10.0f && (maxValue - minValue) > 0.0f) {
            return String.format("%.2f %s", currentValue, unitLabel).trim();
        } else {
            return String.format("%.1f %s", currentValue, unitLabel).trim();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredWidth = 180;
        int desiredHeight = 220;

        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int width = (widthMode == MeasureSpec.EXACTLY) ? widthSize : desiredWidth;
        int height = (heightMode == MeasureSpec.EXACTLY) ? heightSize : desiredHeight;

        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        final float w = getWidth();
        final float h = getHeight();
        final float cx = w / 2.0f;
        final float cy = h * 0.44f;
        final float radius = Math.min(w * 0.36f, h * 0.32f);

        // 1. Parameter Name Label (Top)
        canvas.drawText(paramName, cx, 24f, textPaint);

        // 2. Knob Body Circle
        canvas.drawCircle(cx, cy, radius, knobBodyPaint);

        // 3. Track Arc & Active Sweep (from 135 deg to 405 deg -> 270 deg span)
        final float startAngle = 135.0f;
        final float sweepAngle = 270.0f;
        arcRect.set(cx - radius - 4f, cy - radius - 4f, cx + radius + 4f, cy + radius + 4f);

        canvas.drawArc(arcRect, startAngle, sweepAngle, false, trackPaint);
        if (normalizedValue > 0.001f) {
            canvas.drawArc(arcRect, startAngle, normalizedValue * sweepAngle, false, arcPaint);
        }

        // 4. Pointer Line Tick
        final float pointerAngle = (float) Math.toRadians(startAngle + normalizedValue * sweepAngle);
        final float innerX = cx + (radius * 0.35f) * (float) Math.cos(pointerAngle);
        final float innerY = cy + (radius * 0.35f) * (float) Math.sin(pointerAngle);
        final float outerX = cx + (radius * 0.85f) * (float) Math.cos(pointerAngle);
        final float outerY = cy + (radius * 0.85f) * (float) Math.sin(pointerAngle);
        canvas.drawLine(innerX, innerY, outerX, outerY, pointerPaint);

        // 5. Value Readout (Bottom)
        canvas.drawText(getFormattedValue(), cx, h - 16f, valTextPaint);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (gestureDetector != null && gestureDetector.onTouchEvent(event)) {
            return true;
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchY = event.getY();
                isFineMode = (event.getPointerCount() > 1);
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;

            case MotionEvent.ACTION_MOVE: {
                float deltaY = lastTouchY - event.getY();
                lastTouchY = event.getY();

                float sensitivity = isFineMode ? 0.0015f : 0.006f;
                float newNorm = normalizedValue + deltaY * sensitivity;
                updateValueFromNormalized(newNorm, true);
                return true;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
        }
        return super.onTouchEvent(event);
    }
}
