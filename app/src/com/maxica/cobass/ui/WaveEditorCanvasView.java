package com.maxica.cobass.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import androidx.annotation.Nullable;
import com.maxica.cobass.model.ClipItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WaveEditorCanvasView extends View {

    public enum Mode {
        RANGE_SELECT,
        TRIM_FADE,
        SLICE
    }

    public interface OnWaveEventListener {
        void onTrimAndFadeChanged(float startRatio, float endRatio, float fadeInRatio, float fadeOutRatio);
        void onSelectionChanged(float selStartRatio, float selEndRatio);
        void onSliceMarkerAudition(float sliceStartFrac, float sliceEndFrac);
        void onScrubRequested(float fraction);
        void onGestureActionCommitted();
    }

    public static class WaveformMipmap {
        public float[] minPeaks;
        public float[] maxPeaks;
        public float[] rmsPeaks;
        public int factor;

        public static WaveformMipmap build(float[] pcm, int factor) {
            int length = pcm.length;
            int outLen = (length + factor - 1) / factor;
            WaveformMipmap m = new WaveformMipmap();
            m.minPeaks = new float[outLen];
            m.maxPeaks = new float[outLen];
            m.rmsPeaks = new float[outLen];
            m.factor = factor;

            for (int i = 0; i < outLen; i++) {
                int start = i * factor;
                int end = Math.min(length, start + factor);
                float minVal = 1.0f, maxVal = -1.0f, sumSq = 0.0f;
                for (int s = start; s < end; s++) {
                    float v = pcm[s];
                    if (v < minVal) minVal = v;
                    if (v > maxVal) maxVal = v;
                    sumSq += v * v;
                }
                m.minPeaks[i] = minVal;
                m.maxPeaks[i] = maxVal;
                m.rmsPeaks[i] = (float) Math.sqrt(sumSq / Math.max(1, end - start));
            }
            return m;
        }
    }

    private ClipItem clip;
    private final List<WaveformMipmap> mipmaps = new ArrayList<>();

    private Mode currentMode = Mode.RANGE_SELECT;

    // Trim & Fade Bounds
    private float trimStartRatio = 0.0f;
    private float trimEndRatio = 1.0f;
    private float fadeInRatio = 0.05f;
    private float fadeOutRatio = 0.05f;
    private boolean snapZeroCrossing = true;

    // Region Selection Bounds
    private float selectionStartRatio = -1.0f;
    private float selectionEndRatio = -1.0f;

    // Transient Slices (0.0 to 1.0)
    private final List<Float> sliceMarkers = new ArrayList<>();
    private int draggingSliceIndex = -1;

    // Viewport & Zoom
    private float zoomLevel = 1.0f;
    private float scrollFraction = 0.0f;
    private final float rulerHeight = 36f;
    private boolean isFollowPlayhead = true;

    // Playhead State
    private float currentPlayheadFraction = 0.0f;
    private boolean isPlaying = false;

    // Drag States
    private boolean isDraggingStartHandle = false;
    private boolean isDraggingEndHandle = false;
    private boolean isDraggingFadeInHandle = false;
    private boolean isDraggingFadeOutHandle = false;
    private boolean isDraggingSelection = false;
    private boolean isScrubbingRuler = false;
    private float dragStartRawX = 0f;
    private float lastTouchX = 0f;

    private OnWaveEventListener listener;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rmsPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sliceLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path fadePath = new Path();
    private final Path sliceFlagPath = new Path();
    private final Path startFlagPath = new Path();
    private final Path endFlagPath = new Path();
    private final RectF rectF = new RectF();

    public WaveEditorCanvasView(Context context) {
        super(context);
        init(context);
    }

    public WaveEditorCanvasView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        wavePaint.setStyle(Paint.Style.STROKE);
        wavePaint.setColor(Color.parseColor("#FF9F0A"));
        wavePaint.setStrokeWidth(2f);

        rmsPaint.setStyle(Paint.Style.STROKE);
        rmsPaint.setColor(Color.parseColor("#FFD60A"));
        rmsPaint.setStrokeWidth(2f);

        fadePaint.setStyle(Paint.Style.FILL);
        fadePaint.setColor(Color.parseColor("#440A84FF"));

        selectionPaint.setStyle(Paint.Style.FILL);
        selectionPaint.setColor(Color.parseColor("#330A84FF"));

        sliceLinePaint.setStyle(Paint.Style.STROKE);
        sliceLinePaint.setColor(Color.parseColor("#FFD60A"));
        sliceLinePaint.setStrokeWidth(2.5f);
        sliceLinePaint.setPathEffect(new DashPathEffect(new float[]{10f, 10f}, 0));
    }

    public void setEventListener(OnWaveEventListener listener) {
        this.listener = listener;
    }

    public void setMode(Mode mode) {
        this.currentMode = mode;
        invalidate();
    }

    public Mode getMode() { return currentMode; }

    public void setClip(ClipItem clip) {
        this.clip = clip;
        rebuildMipmaps();
        this.trimStartRatio = 0.0f;
        this.trimEndRatio = 1.0f;
        this.fadeInRatio = 0.05f;
        this.fadeOutRatio = 0.05f;
        this.selectionStartRatio = -1.0f;
        this.selectionEndRatio = -1.0f;
        this.sliceMarkers.clear();
        if (clip != null && clip.getSliceFractions() != null) {
            this.sliceMarkers.addAll(clip.getSliceFractions());
        }
        this.scrollFraction = 0.0f;
        this.zoomLevel = 1.0f;
        invalidate();
    }

    public void rebuildMipmaps() {
        mipmaps.clear();
        if (clip == null || clip.getSampleData() == null || clip.getSampleData().length == 0) return;

        float[] pcm = clip.getSampleData();
        int[] factors = {1, 8, 64, 512, 4096};
        for (int factor : factors) {
            if (pcm.length >= factor * 2) {
                mipmaps.add(WaveformMipmap.build(pcm, factor));
            }
        }
    }

    public void setZoomLevel(float zoom) {
        this.zoomLevel = Math.max(1.0f, Math.min(60.0f, zoom));
        clampScroll();
        invalidate();
    }

    public void resetZoom() {
        this.zoomLevel = 1.0f;
        this.scrollFraction = 0.0f;
        invalidate();
    }

    public void setSnapZeroCrossing(boolean enable) {
        this.snapZeroCrossing = enable;
        invalidate();
    }

    public boolean isSnapZeroCrossing() { return snapZeroCrossing; }

    public float getZoomLevel() { return zoomLevel; }
    public float getTrimStartRatio() { return trimStartRatio; }
    public float getTrimEndRatio() { return trimEndRatio; }
    public float getFadeInRatio() { return fadeInRatio; }
    public float getFadeOutRatio() { return fadeOutRatio; }

    public boolean hasSelection() {
        return selectionStartRatio >= 0.0f && selectionEndRatio > selectionStartRatio;
    }

    public float getSelectionStartRatio() { return hasSelection() ? selectionStartRatio : trimStartRatio; }
    public float getSelectionEndRatio() { return hasSelection() ? selectionEndRatio : trimEndRatio; }

    public void clearSelection() {
        this.selectionStartRatio = -1.0f;
        this.selectionEndRatio = -1.0f;
        if (listener != null) listener.onSelectionChanged(-1.0f, -1.0f);
        invalidate();
    }

    public List<Float> getSliceMarkers() { return sliceMarkers; }

    public void setSliceMarkers(List<Float> slices) {
        this.sliceMarkers.clear();
        if (slices != null) {
            this.sliceMarkers.addAll(slices);
            Collections.sort(this.sliceMarkers);
        }
        if (clip != null) clip.setSliceFractions(this.sliceMarkers);
        invalidate();
    }

    public void clearSlices() {
        this.sliceMarkers.clear();
        if (clip != null) clip.clearSlices();
        invalidate();
    }

    // --- REAL-TIME TRANSIENT ENERGY FLUX DETECTOR ---
    public int detectTransients(float sensitivity, float minDistanceSec) {
        if (clip == null || clip.getSampleData() == null || clip.getSampleData().length == 0) return 0;
        float[] pcm = clip.getSampleData();
        int sampleRate = 48000;
        int totalSamples = pcm.length;

        int frameSize = 256;
        int hopSize = 128;
        int numFrames = totalSamples / hopSize;
        if (numFrames <= 2) return 0;

        float[] energy = new float[numFrames];
        for (int i = 0; i < numFrames; i++) {
            int start = i * hopSize;
            int end = Math.min(totalSamples, start + frameSize);
            float sum = 0.0f;
            for (int s = start; s < end; s++) sum += pcm[s] * pcm[s];
            energy[i] = (float) Math.sqrt(sum / (end - start));
        }

        float[] flux = new float[numFrames];
        float meanFlux = 0.0f;
        for (int i = 1; i < numFrames; i++) {
            flux[i] = Math.max(0.0f, energy[i] - energy[i - 1]);
            meanFlux += flux[i];
        }
        meanFlux /= numFrames;

        float variance = 0.0f;
        for (int i = 1; i < numFrames; i++) {
            float diff = flux[i] - meanFlux;
            variance += diff * diff;
        }
        float stdDev = (float) Math.sqrt(variance / numFrames);

        float threshold = meanFlux + (1.0f - sensitivity) * stdDev * 3.5f;
        int minHopDistance = Math.max(1, (int) ((minDistanceSec * sampleRate) / hopSize));

        List<Float> detected = new ArrayList<>();
        int lastOnsetFrame = -minHopDistance;

        for (int i = 2; i < numFrames - 2; i++) {
            if (flux[i] > threshold && flux[i] > flux[i - 1] && flux[i] > flux[i + 1] && (i - lastOnsetFrame) >= minHopDistance) {
                int peakSample = i * hopSize;
                if (snapZeroCrossing) peakSample = findNearestZeroCrossing(peakSample, 128);
                float frac = (float) peakSample / totalSamples;
                if (frac > 0.01f && frac < 0.99f) {
                    detected.add(frac);
                    lastOnsetFrame = i;
                }
            }
        }

        setSliceMarkers(detected);
        return detected.size();
    }

    public void setTrimAndFadeRatios(float start, float end, float fIn, float fOut) {
        this.trimStartRatio = Math.max(0.0f, Math.min(0.99f, start));
        this.trimEndRatio = Math.max(this.trimStartRatio + 0.01f, Math.min(1.0f, end));
        this.fadeInRatio = Math.max(0.0f, Math.min(0.5f, fIn));
        this.fadeOutRatio = Math.max(0.0f, Math.min(0.5f, fOut));
        invalidate();
    }

    public void setFollowPlayhead(boolean follow) {
        this.isFollowPlayhead = follow;
        invalidate();
    }

    public void setPlaybackState(float fraction, boolean playing) {
        this.currentPlayheadFraction = Math.max(0.0f, Math.min(1.0f, fraction));
        this.isPlaying = playing;

        if (isPlaying && isFollowPlayhead && zoomLevel > 1.05f) {
            float visibleSpan = 1.0f / zoomLevel;
            float viewStart = scrollFraction;
            float viewEnd = scrollFraction + visibleSpan;

            if (currentPlayheadFraction > viewEnd - (visibleSpan * 0.15f) || currentPlayheadFraction < viewStart) {
                scrollFraction = Math.max(0.0f, Math.min(1.0f - visibleSpan, currentPlayheadFraction - (visibleSpan * 0.2f)));
            }
        }
        if (isPlaying) postInvalidateOnAnimation(); else invalidate();
    }

    private void clampScroll() {
        float visibleSpan = 1.0f / zoomLevel;
        scrollFraction = Math.max(0.0f, Math.min(1.0f - visibleSpan, scrollFraction));
    }

    public int findNearestZeroCrossing(int sampleIndex, int searchWindow) {
        if (clip == null || clip.getSampleData() == null || clip.getSampleData().length == 0) return sampleIndex;
        float[] pcm = clip.getSampleData();
        int len = pcm.length;
        int clampedIndex = Math.max(0, Math.min(len - 1, sampleIndex));

        int start = Math.max(0, clampedIndex - searchWindow);
        int end = Math.min(len - 2, clampedIndex + searchWindow);

        int bestIndex = clampedIndex;
        float minAbsVal = Math.abs(pcm[clampedIndex]);

        for (int i = start; i <= end; i++) {
            if ((pcm[i] <= 0f && pcm[i + 1] > 0f) || (pcm[i] >= 0f && pcm[i + 1] < 0f)) {
                return (Math.abs(pcm[i]) < Math.abs(pcm[i + 1])) ? i : i + 1;
            }
            if (Math.abs(pcm[i]) < minAbsVal) {
                minAbsVal = Math.abs(pcm[i]);
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        final int width = getWidth();
        final int height = getHeight();
        final float waveTop = rulerHeight;
        final float waveHeight = height - waveTop;
        final float midY = waveTop + (waveHeight / 2.0f);
        final float maxAmp = waveHeight * 0.44f;

        // 1. Background
        canvas.drawRect(0, 0, width, height, CobassCanvasTheme.PAINT_CANVAS_BG);

        // 2. Timeline Ruler Header
        canvas.drawRect(0, 0, width, rulerHeight, CobassCanvasTheme.PAINT_RULER_BG);
        paint.setColor(0xFF262936);
        paint.setStrokeWidth(1.5f);
        canvas.drawLine(0, rulerHeight, width, rulerHeight, paint);

        float totalDurationSec = (clip != null && clip.getSampleData() != null) ? (clip.getSampleData().length / 48000.0f) : 2.0f;
        float visibleSpan = 1.0f / zoomLevel;
        float startSec = scrollFraction * totalDurationSec;
        float endSec = (scrollFraction + visibleSpan) * totalDurationSec;

        int numRulerTicks = 8;
        for (int i = 0; i <= numRulerTicks; i++) {
            float frac = (float) i / numRulerTicks;
            float rx = frac * width;
            float timeAtTick = startSec + frac * (endSec - startSec);

            paint.setColor(0xFF3B4052);
            canvas.drawLine(rx, rulerHeight - 12f, rx, rulerHeight, paint);

            paint.setColor(0xFF8E8E93);
            paint.setTextSize(11f);
            paint.setFakeBoldText(true);
            String timeLabel = String.format("%.2fs", timeAtTick);
            canvas.drawText(timeLabel, rx + 4f, rulerHeight - 14f, paint);
        }

        // Center Axis
        paint.setColor(0xFF222530);
        paint.setStrokeWidth(1.2f);
        canvas.drawLine(0, midY, width, midY, paint);

        if (clip == null || clip.getSampleData() == null || clip.getSampleData().length == 0) {
            paint.setColor(0xFF8E8E93);
            paint.setTextSize(15f);
            paint.setFakeBoldText(false);
            canvas.drawText("No Sample Loaded", width / 2.0f - 60f, midY, paint);
            return;
        }

        final float[] pcm = clip.getSampleData();
        final int totalSamples = pcm.length;

        // 3. Mipmap Selection
        int samplesPerPixel = Math.max(1, (int) ((totalSamples * visibleSpan) / width));
        WaveformMipmap selectedMipmap = null;
        for (int m = mipmaps.size() - 1; m >= 0; m--) {
            if (mipmaps.get(m).factor <= samplesPerPixel) {
                selectedMipmap = mipmaps.get(m);
                break;
            }
        }

        // 4. Waveform & RMS Power Rendering
        for (int x = 0; x < width; x++) {
            float sampleFrac = scrollFraction + ((float) x / width) * visibleSpan;
            int sampleIdx = (int) (sampleFrac * totalSamples);
            if (sampleIdx < 0 || sampleIdx >= totalSamples) continue;

            float minSample = 0.0f;
            float maxSample = 0.0f;
            float rms = 0.0f;

            if (selectedMipmap != null) {
                int mipmapIdx = Math.min(selectedMipmap.minPeaks.length - 1, sampleIdx / selectedMipmap.factor);
                minSample = selectedMipmap.minPeaks[mipmapIdx];
                maxSample = selectedMipmap.maxPeaks[mipmapIdx];
                rms = selectedMipmap.rmsPeaks[mipmapIdx];
            } else {
                minSample = maxSample = pcm[sampleIdx];
                rms = Math.abs(pcm[sampleIdx]);
            }

            float y1 = midY - (maxSample * maxAmp);
            float y2 = midY - (minSample * maxAmp);
            float rmsY1 = midY - (rms * maxAmp * 0.7f);
            float rmsY2 = midY + (rms * maxAmp * 0.7f);

            wavePaint.setColor(0xFFFF9F0A);
            canvas.drawLine(x, y1, x, y2, wavePaint);

            rmsPaint.setColor(0x44FFD60A);
            canvas.drawLine(x, rmsY1, x, rmsY2, rmsPaint);
        }

        // 5. Render Dimmed Untrimmed Regions
        float screenTrimStartX = ((trimStartRatio - scrollFraction) / visibleSpan) * width;
        float screenTrimEndX = ((trimEndRatio - scrollFraction) / visibleSpan) * width;
        float activeSpan = trimEndRatio - trimStartRatio;

        paint.setColor(0x99000000);
        if (screenTrimStartX > 0) canvas.drawRect(0, waveTop, Math.min(width, screenTrimStartX), height, paint);
        if (screenTrimEndX < width) canvas.drawRect(Math.max(0, screenTrimEndX), waveTop, width, height, paint);

        // 6. Equal-Power Fade Shading (Only in TRIM_FADE mode)
        if (currentMode == Mode.TRIM_FADE) {
            float screenFadeInEnd = screenTrimStartX + ((fadeInRatio * activeSpan) / visibleSpan) * width;
            float screenFadeOutStart = screenTrimEndX - ((fadeOutRatio * activeSpan) / visibleSpan) * width;

            if (fadeInRatio > 0.001f && screenFadeInEnd > screenTrimStartX) {
                fadePath.reset();
                fadePath.moveTo(screenTrimStartX, waveTop);
                int steps = 24;
                for (int s = 0; s <= steps; s++) {
                    float t = (float) s / steps;
                    float fx = screenTrimStartX + t * (screenFadeInEnd - screenTrimStartX);
                    float env = (float) Math.sin(t * 1.57079632679f);
                    float fy = waveTop + (1.0f - env) * (waveHeight * 0.5f);
                    fadePath.lineTo(fx, fy);
                }
                fadePath.lineTo(screenTrimStartX, waveTop + (waveHeight * 0.5f));
                fadePath.close();
                fadePaint.setColor(0x330A84FF);
                canvas.drawPath(fadePath, fadePaint);
            }

            if (fadeOutRatio > 0.001f && screenTrimEndX > screenFadeOutStart) {
                fadePath.reset();
                fadePath.moveTo(screenFadeOutStart, waveTop);
                int steps = 24;
                for (int s = 0; s <= steps; s++) {
                    float t = (float) s / steps;
                    float fx = screenFadeOutStart + t * (screenTrimEndX - screenFadeOutStart);
                    float env = (float) Math.sin((1.0f - t) * 1.57079632679f);
                    float fy = waveTop + (1.0f - env) * (waveHeight * 0.5f);
                    fadePath.lineTo(fx, fy);
                }
                fadePath.lineTo(screenTrimEndX, waveTop + (waveHeight * 0.5f));
                fadePath.close();
                fadePaint.setColor(0x33BF5AF2);
                canvas.drawPath(fadePath, fadePaint);
            }
        }

        // 7. Region Selection Overlay (RANGE_SELECT mode)
        if (hasSelection()) {
            float screenSelX1 = ((selectionStartRatio - scrollFraction) / visibleSpan) * width;
            float screenSelX2 = ((selectionEndRatio - scrollFraction) / visibleSpan) * width;

            rectF.set(screenSelX1, waveTop, screenSelX2, height);
            CobassCanvasTheme.drawMarquee(canvas, rectF);
        }

        // 8. Transient Slice Markers & Flags (Always visible or in SLICE mode)
        for (int i = 0; i < sliceMarkers.size(); i++) {
            float frac = sliceMarkers.get(i);
            float sx = ((frac - scrollFraction) / visibleSpan) * width;
            if (sx < -20 || sx > width + 20) continue;

            // Dashed Slice Boundary Line
            canvas.drawLine(sx, waveTop, sx, height, sliceLinePaint);

            // Slice Flag Header [S1], [S2]...
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFFD97706);

            sliceFlagPath.reset(); Path flag = sliceFlagPath;
            flag.moveTo(sx - 14f, waveTop);
            flag.lineTo(sx + 14f, waveTop);
            flag.lineTo(sx + 14f, waveTop + 18f);
            flag.lineTo(sx, waveTop + 26f);
            flag.lineTo(sx - 14f, waveTop + 18f);
            flag.close();
            canvas.drawPath(flag, paint);

            paint.setColor(Color.WHITE);
            paint.setTextSize(10f);
            paint.setFakeBoldText(true);
            String label = "S" + (i + 1);
            canvas.drawText(label, sx - 7f, waveTop + 13f, paint);
        }

        // 9. Handles depending on Current Mode
        if (currentMode == Mode.TRIM_FADE) {
            // Start & End Trim Flags
            if (screenTrimStartX >= -20 && screenTrimStartX <= width + 20) {
                paint.setColor(0xFF30D158);
                paint.setStrokeWidth(3.5f);
                canvas.drawLine(screenTrimStartX, waveTop, screenTrimStartX, height, paint);

                startFlagPath.reset(); Path startFlag = startFlagPath;
                startFlag.moveTo(screenTrimStartX, waveTop);
                startFlag.lineTo(screenTrimStartX + 20f, waveTop);
                startFlag.lineTo(screenTrimStartX + 20f, waveTop + 24f);
                startFlag.lineTo(screenTrimStartX, waveTop + 34f);
                startFlag.close();
                paint.setStyle(Paint.Style.FILL);
                canvas.drawPath(startFlag, paint);

                paint.setColor(Color.BLACK);
                paint.setTextSize(11f);
                paint.setFakeBoldText(true);
                canvas.drawText("S", screenTrimStartX + 5f, waveTop + 18f, paint);
            }

            if (screenTrimEndX >= -20 && screenTrimEndX <= width + 20) {
                paint.setColor(0xFFFF453A);
                paint.setStrokeWidth(3.5f);
                canvas.drawLine(screenTrimEndX, waveTop, screenTrimEndX, height, paint);

                endFlagPath.reset(); Path endFlag = endFlagPath;
                endFlag.moveTo(screenTrimEndX - 20f, waveTop);
                endFlag.lineTo(screenTrimEndX, waveTop);
                endFlag.lineTo(screenTrimEndX, waveTop + 34f);
                endFlag.lineTo(screenTrimEndX - 20f, waveTop + 24f);
                endFlag.close();
                paint.setStyle(Paint.Style.FILL);
                canvas.drawPath(endFlag, paint);

                paint.setColor(Color.WHITE);
                paint.setTextSize(11f);
                paint.setFakeBoldText(true);
                canvas.drawText("E", screenTrimEndX - 15f, waveTop + 18f, paint);
            }
        }

        // 10. Playhead Cursor
        float screenPlayheadX = ((currentPlayheadFraction - scrollFraction) / visibleSpan) * width;
        if (screenPlayheadX >= 0 && screenPlayheadX <= width) {
            CobassCanvasTheme.drawPlayheadNeedle(canvas, screenPlayheadX, 0, height, 1.0f);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final int pointerCount = event.getPointerCount();
        final float width = getWidth();
        final float visibleSpan = 1.0f / zoomLevel;

        if (pointerCount >= 2) {
            isDraggingStartHandle = false;
            isDraggingEndHandle = false;
            isDraggingFadeInHandle = false;
            isDraggingFadeOutHandle = false;
            isDraggingSelection = false;
            isScrubbingRuler = false;
            draggingSliceIndex = -1;
            return true;
        }

        final float x = event.getX();
        final float y = event.getY();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                lastTouchX = x;
                dragStartRawX = x;

                if (y <= rulerHeight) {
                    isScrubbingRuler = true;
                    float scrubFrac = scrollFraction + (x / width) * visibleSpan;
                    currentPlayheadFraction = Math.max(0.0f, Math.min(1.0f, scrubFrac));
                    if (listener != null) listener.onScrubRequested(currentPlayheadFraction);
                    invalidate();
                    return true;
                }

                if (currentMode == Mode.SLICE) {
                    // Check if clicked near an existing slice marker flag
                    for (int i = 0; i < sliceMarkers.size(); i++) {
                        float sx = ((sliceMarkers.get(i) - scrollFraction) / visibleSpan) * width;
                        if (Math.abs(x - sx) <= 28f) {
                            draggingSliceIndex = i;
                            // Audition slice
                            float sStart = sliceMarkers.get(i);
                            float sEnd = (i < sliceMarkers.size() - 1) ? sliceMarkers.get(i + 1) : trimEndRatio;
                            if (listener != null) listener.onSliceMarkerAudition(sStart, sEnd);
                            return true;
                        }
                    }
                    // Tap to add slice
                    float addFrac = scrollFraction + (x / width) * visibleSpan;
                    int totalSamples = (clip != null && clip.getSampleData() != null) ? clip.getSampleData().length : 1;
                    if (snapZeroCrossing) {
                        int targetSample = (int) (addFrac * totalSamples);
                        addFrac = (float) findNearestZeroCrossing(targetSample, 128) / totalSamples;
                    }
                    if (addFrac > 0.01f && addFrac < 0.99f) {
                        sliceMarkers.add(addFrac);
                        Collections.sort(sliceMarkers);
                        if (clip != null) clip.setSliceFractions(sliceMarkers);
                        if (listener != null) listener.onGestureActionCommitted();
                        invalidate();
                    }
                    return true;
                } else if (currentMode == Mode.TRIM_FADE) {
                    float screenTrimStartX = ((trimStartRatio - scrollFraction) / visibleSpan) * width;
                    float screenTrimEndX = ((trimEndRatio - scrollFraction) / visibleSpan) * width;
                    float activeSpan = trimEndRatio - trimStartRatio;

                    float screenFadeInEnd = screenTrimStartX + ((fadeInRatio * activeSpan) / visibleSpan) * width;
                    float screenFadeOutStart = screenTrimEndX - ((fadeOutRatio * activeSpan) / visibleSpan) * width;

                    if (y <= rulerHeight + 35f && Math.abs(x - screenFadeInEnd) <= 30f) {
                        isDraggingFadeInHandle = true;
                        return true;
                    } else if (y <= rulerHeight + 35f && Math.abs(x - screenFadeOutStart) <= 30f) {
                        isDraggingFadeOutHandle = true;
                        return true;
                    } else if (Math.abs(x - screenTrimStartX) <= 40f) {
                        isDraggingStartHandle = true;
                        return true;
                    } else if (Math.abs(x - screenTrimEndX) <= 40f) {
                        isDraggingEndHandle = true;
                        return true;
                    }
                } else if (currentMode == Mode.RANGE_SELECT) {
                    isDraggingSelection = true;
                    float clickFrac = scrollFraction + (x / width) * visibleSpan;
                    int totalSamples = (clip != null && clip.getSampleData() != null) ? clip.getSampleData().length : 1;
                    if (snapZeroCrossing) {
                        int targetSample = (int) (clickFrac * totalSamples);
                        clickFrac = (float) findNearestZeroCrossing(targetSample, 128) / totalSamples;
                    }
                    selectionStartRatio = clickFrac;
                    selectionEndRatio = clickFrac;
                    invalidate();
                    return true;
                }
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                float dx = x - lastTouchX;
                lastTouchX = x;
                int totalSamples = (clip != null && clip.getSampleData() != null) ? clip.getSampleData().length : 1;

                if (draggingSliceIndex >= 0 && draggingSliceIndex < sliceMarkers.size()) {
                    float newFrac = scrollFraction + (x / width) * visibleSpan;
                    if (snapZeroCrossing) {
                        int targetSample = (int) (newFrac * totalSamples);
                        newFrac = (float) findNearestZeroCrossing(targetSample, 128) / totalSamples;
                    }
                    sliceMarkers.set(draggingSliceIndex, Math.max(0.01f, Math.min(0.99f, newFrac)));
                    Collections.sort(sliceMarkers);
                    if (clip != null) clip.setSliceFractions(sliceMarkers);
                    invalidate();
                    return true;
                } else if (isDraggingStartHandle) {
                    float newRatio = scrollFraction + (x / width) * visibleSpan;
                    if (snapZeroCrossing) {
                        int targetSample = (int) (newRatio * totalSamples);
                        newRatio = (float) findNearestZeroCrossing(targetSample, 128) / totalSamples;
                    }
                    trimStartRatio = Math.max(0.0f, Math.min(trimEndRatio - 0.02f, newRatio));
                    if (listener != null) listener.onTrimAndFadeChanged(trimStartRatio, trimEndRatio, fadeInRatio, fadeOutRatio);
                    invalidate();
                    return true;
                } else if (isDraggingEndHandle) {
                    float newRatio = scrollFraction + (x / width) * visibleSpan;
                    if (snapZeroCrossing) {
                        int targetSample = (int) (newRatio * totalSamples);
                        newRatio = (float) findNearestZeroCrossing(targetSample, 128) / totalSamples;
                    }
                    trimEndRatio = Math.max(trimStartRatio + 0.02f, Math.min(1.0f, newRatio));
                    if (listener != null) listener.onTrimAndFadeChanged(trimStartRatio, trimEndRatio, fadeInRatio, fadeOutRatio);
                    invalidate();
                    return true;
                } else if (isDraggingFadeInHandle) {
                    float currentFrac = scrollFraction + (x / width) * visibleSpan;
                    float activeSpan = trimEndRatio - trimStartRatio;
                    fadeInRatio = Math.max(0.0f, Math.min(0.5f, (currentFrac - trimStartRatio) / activeSpan));
                    if (listener != null) listener.onTrimAndFadeChanged(trimStartRatio, trimEndRatio, fadeInRatio, fadeOutRatio);
                    invalidate();
                    return true;
                } else if (isDraggingFadeOutHandle) {
                    float currentFrac = scrollFraction + (x / width) * visibleSpan;
                    float activeSpan = trimEndRatio - trimStartRatio;
                    fadeOutRatio = Math.max(0.0f, Math.min(0.5f, (trimEndRatio - currentFrac) / activeSpan));
                    if (listener != null) listener.onTrimAndFadeChanged(trimStartRatio, trimEndRatio, fadeInRatio, fadeOutRatio);
                    invalidate();
                    return true;
                } else if (isDraggingSelection) {
                    float startFrac = scrollFraction + (dragStartRawX / width) * visibleSpan;
                    float currentFrac = scrollFraction + (x / width) * visibleSpan;

                    if (snapZeroCrossing) {
                        int sample1 = (int) (startFrac * totalSamples);
                        int sample2 = (int) (currentFrac * totalSamples);
                        startFrac = (float) findNearestZeroCrossing(sample1, 128) / totalSamples;
                        currentFrac = (float) findNearestZeroCrossing(sample2, 128) / totalSamples;
                    }

                    selectionStartRatio = Math.max(0.0f, Math.min(startFrac, currentFrac));
                    selectionEndRatio = Math.min(1.0f, Math.max(startFrac, currentFrac));
                    if (listener != null) listener.onSelectionChanged(selectionStartRatio, selectionEndRatio);
                    invalidate();
                    return true;
                } else if (isScrubbingRuler) {
                    float scrubFrac = scrollFraction + (x / width) * visibleSpan;
                    currentPlayheadFraction = Math.max(0.0f, Math.min(1.0f, scrubFrac));
                    if (listener != null) listener.onScrubRequested(currentPlayheadFraction);
                    invalidate();
                    return true;
                } else if (zoomLevel > 1.0f) {
                    float deltaFrac = (dx / width) * visibleSpan;
                    scrollFraction = Math.max(0.0f, Math.min(1.0f - visibleSpan, scrollFraction - deltaFrac));
                    invalidate();
                    return true;
                }
                break;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (isDraggingStartHandle || isDraggingEndHandle || isDraggingFadeInHandle || isDraggingFadeOutHandle || draggingSliceIndex >= 0) {
                    if (listener != null) {
                        listener.onTrimAndFadeChanged(trimStartRatio, trimEndRatio, fadeInRatio, fadeOutRatio);
                        listener.onGestureActionCommitted();
                    }
                }
                isDraggingStartHandle = false;
                isDraggingEndHandle = false;
                isDraggingFadeInHandle = false;
                isDraggingFadeOutHandle = false;
                isDraggingSelection = false;
                isScrubbingRuler = false;
                draggingSliceIndex = -1;
                break;
            }
        }
        return true;
    }
}
