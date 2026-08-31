package com.maxica.cobass.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;
import com.maxica.cobass.model.ClipItem;
import com.maxica.cobass.model.SnapGrid;
import com.maxica.cobass.model.ToolMode;
import com.maxica.cobass.model.TrackItem;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

public class ArrangerTimelineView extends View {

    public interface OnArrangerListener {
        void onClipSelected(@Nullable ClipItem clip);
        void onClipDoubleTap(ClipItem clip);
        void onClipCreated(ClipItem clip);
        void onClipModified(ClipItem clip);
        void onClipDeleted(ClipItem clip);
        void onClipsBatchChanged();
        void onPlayheadScrubbed(long tick);
        void onLoopRangeChanged(long startTick, long endTick, boolean enabled);
        void onTrackMuteToggled(TrackItem track);
        void onTrackSoloToggled(TrackItem track);
        void onTrackFxRequested(TrackItem track);
        void onTrackInspectorRequested(TrackItem track);
    }

    private static class ClipAnchor {
        ClipItem clip;
        long initialStartTick;
        long initialLengthTicks;
        int initialTrackIndex;
        int initialTrackId;
        TrackItem.Type clipType;
        List<ClipItem.Note> initialNotesSnapshot = new ArrayList<>();

        int currentPreviewTrackIndex;
        int currentPreviewTrackId;
        long currentPreviewStartTick;

        ClipAnchor(ClipItem clip, int trackIndex) {
            this.clip = clip;
            this.initialStartTick = clip.getStartTick();
            this.initialLengthTicks = clip.getLengthTicks();
            this.initialTrackIndex = trackIndex;
            this.initialTrackId = clip.getTrackId();
            this.clipType = clip.getType();
            this.currentPreviewTrackIndex = trackIndex;
            this.currentPreviewTrackId = clip.getTrackId();
            this.currentPreviewStartTick = clip.getStartTick();
            for (ClipItem.Note n : clip.getNotes()) {
                this.initialNotesSnapshot.add(n.copy());
            }
        }
    }

    private static final int PPQ = 480;
    private static final int TICKS_PER_BAR = PPQ * 4;

    private final List<TrackItem> tracks = new ArrayList<>();
    private final List<ClipItem> clips = new ArrayList<>();

    private ToolMode toolMode = ToolMode.SELECT;
    private SnapGrid snapGrid = SnapGrid.BEAT_1;
    private OnArrangerListener listener;

    // Viewport & Scale
    private float uiScale = 1.0f;
    private float scrollX = 0f;
    private float scrollY = 0f;
    private float pixelsPerTick = 0.25f;

    private static final float BASE_HEADER_WIDTH = 200f;
    private static final float BASE_RULER_HEIGHT = 65f;
    private static final float BASE_TRACK_HEIGHT = 135f;

    private float headerWidth = BASE_HEADER_WIDTH;
    private float rulerHeight = BASE_RULER_HEIGHT;
    private float trackHeight = BASE_TRACK_HEIGHT;

    // Playhead & Loop State
    private long currentPlayheadTick = 0;
    private long loopStartTick = 0;
    private long loopEndTick = TICKS_PER_BAR * 4;
    private boolean isLoopEnabled = true;

    private enum RulerTouchMode {
        NONE,
        SCRUBBING_PLAYHEAD,
        DRAGGING_LOOP_START,
        DRAGGING_LOOP_END,
        DRAGGING_ENTIRE_LOOP_BODY
    }

    private RulerTouchMode rulerTouchMode = RulerTouchMode.NONE;
    private float loopDragStartRawX = 0f;
    private long loopDragInitialStartTick = 0;
    private long loopDragInitialEndTick = 0;

    // Two-Finger Pan Navigation
    private boolean isTwoFingerPanning = false;
    private float lastPanMidX = 0f;
    private float lastPanMidY = 0f;

    // Multi-Selection Marquee State
    private boolean isMarqueeActive = false;
    private float marqueeStartX = 0f;
    private float marqueeStartY = 0f;
    private float marqueeCurrentX = 0f;
    private float marqueeCurrentY = 0f;
    private final RectF marqueeRect = new RectF();

    // Multi-Axis 2D Dragging, Trimming, and Slip State
    private final List<ClipAnchor> dragAnchors = new ArrayList<>();
    private ClipItem primaryHitClip = null;
    private boolean isResizingRight = false;
    private boolean isResizingLeft = false;
    private boolean isSlippingContent = false;
    private long currentSlipOffsetTicks = 0;
    private float dragStartRawX = 0f;
    private float dragStartRawY = 0f;
    private boolean isDraggingClipsActive = false;
    private boolean isMagneticSnapEnabled = true;
    private long activeMagneticGuideTick = -1;
    private float magneticSensitivityPx = 18f;
    private boolean hasModifiedClipsInGesture = false;

    // Undo / Redo Transaction Stack
    private static final int MAX_UNDO_STACK = 50;
    private final Deque<List<ClipItem>> undoStack = new ArrayDeque<>();
    private final Deque<List<ClipItem>> redoStack = new ArrayDeque<>();

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint marqueePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ghostPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rectF = new RectF();
    private GestureDetector gestureDetector;

    public ArrangerTimelineView(Context context) {
        super(context);
        init(context);
    }

    public ArrangerTimelineView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        updateDimensions();

        marqueePaint.setStyle(Paint.Style.FILL);
        marqueePaint.setColor(Color.parseColor("#330A84FF"));

        ghostPaint.setStyle(Paint.Style.FILL);
        ghostPaint.setColor(Color.argb(170, 10, 132, 255));

        handlePaint.setStyle(Paint.Style.FILL);
        handlePaint.setColor(Color.parseColor("#FFD60A"));

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (e.getX() > headerWidth && e.getY() > rulerHeight) {
                    ClipItem hit = findClipAt(e.getX(), e.getY());
                    if (hit != null && listener != null) {
                        listener.onClipDoubleTap(hit);
                        return true;
                    }
                } else if (e.getX() > headerWidth && e.getY() <= rulerHeight) {
                    long tappedTick = (long) ((e.getX() - headerWidth + scrollX) / pixelsPerTick);
                    long barStart = (tappedTick / TICKS_PER_BAR) * TICKS_PER_BAR;
                    setLoopRange(barStart, barStart + (TICKS_PER_BAR * 2), true);
                    if (listener != null) {
                        listener.onLoopRangeChanged(loopStartTick, loopEndTick, isLoopEnabled);
                    }
                    return true;
                }
                return false;
            }
        });
    }

    public void setUiScale(float scale) {
        this.uiScale = Math.max(0.7f, Math.min(1.5f, scale));
        updateDimensions();
        invalidate();
    }

    private void updateDimensions() {
        this.headerWidth = BASE_HEADER_WIDTH * uiScale;
        this.rulerHeight = BASE_RULER_HEIGHT * uiScale;
        this.trackHeight = BASE_TRACK_HEIGHT * uiScale;
    }

    public void setArrangerListener(OnArrangerListener listener) { this.listener = listener; }
    public void setToolMode(ToolMode mode) {
        this.toolMode = mode;
        this.isMarqueeActive = false;
        this.dragAnchors.clear();
        this.primaryHitClip = null;
        this.isDraggingClipsActive = false;
        this.isSlippingContent = false;
        invalidate();
    }

    public void setSnapGrid(SnapGrid grid) { this.snapGrid = grid; invalidate(); }
    public void setMagneticSnapEnabled(boolean enabled) {
        this.isMagneticSnapEnabled = enabled;
        invalidate();
    }

    public boolean isMagneticSnapEnabled() { return isMagneticSnapEnabled; }

    public void setMagneticSensitivity(float sensitivityPx) {
        this.magneticSensitivityPx = Math.max(6f, Math.min(40f, sensitivityPx));
    }

    public float getMagneticSensitivity() { return magneticSensitivityPx; }

    public SnapGrid getSnapGrid() { return snapGrid; }

    public long computeMagneticSnap(long targetStartTick, long clipLength, @Nullable ClipItem activeClip) {
        long baseSnapped = snapGrid.snap(targetStartTick);
        if (!isMagneticSnapEnabled || pixelsPerTick <= 0.0f) {
            activeMagneticGuideTick = -1;
            return baseSnapped;
        }

        long thresholdTicks = (long) (magneticSensitivityPx / pixelsPerTick);
        long bestSnapStart = baseSnapped;
        long bestDelta = thresholdTicks + 1;
        long magneticGuide = -1;

        List<Long> snapTargets = new ArrayList<>();
        snapTargets.add(loopStartTick);
        snapTargets.add(loopEndTick);
        snapTargets.add(currentPlayheadTick);

        for (ClipItem c : clips) {
            if (c == activeClip || (c.isSelected() && activeClip != null && activeClip.isSelected())) continue;
            snapTargets.add(c.getStartTick());
            snapTargets.add(c.getEndTick());
        }

        for (long target : snapTargets) {
            // Test Start Edge snapping to Target
            long deltaStart = Math.abs(targetStartTick - target);
            if (deltaStart < bestDelta) {
                bestDelta = deltaStart;
                bestSnapStart = target;
                magneticGuide = target;
            }

            // Test End Edge snapping to Target
            long candidateStartForEndSnap = target - clipLength;
            long deltaEnd = Math.abs(targetStartTick - candidateStartForEndSnap);
            if (deltaEnd < bestDelta) {
                bestDelta = deltaEnd;
                bestSnapStart = candidateStartForEndSnap;
                magneticGuide = target;
            }
        }

        if (bestDelta <= thresholdTicks) {
            activeMagneticGuideTick = magneticGuide;
            return Math.max(0, bestSnapStart);
        } else {
            activeMagneticGuideTick = -1;
            return baseSnapped;
        }
    }

    public void setPlayheadTick(long tick) { this.currentPlayheadTick = tick; invalidate(); }

    public void setLoopRange(long startTick, long endTick, boolean enabled) {
        this.loopStartTick = Math.max(0, startTick);
        this.loopEndTick = Math.max(this.loopStartTick + (PPQ / 2), endTick);
        this.isLoopEnabled = enabled;
        invalidate();
    }

    public void doubleLoopLength() {
        long len = loopEndTick - loopStartTick;
        setLoopRange(loopStartTick, loopStartTick + (len * 2), isLoopEnabled);
        if (listener != null) listener.onLoopRangeChanged(loopStartTick, loopEndTick, isLoopEnabled);
    }

    public void halveLoopLength() {
        long len = loopEndTick - loopStartTick;
        long halfLen = Math.max(PPQ, len / 2);
        setLoopRange(loopStartTick, loopStartTick + halfLen, isLoopEnabled);
        if (listener != null) listener.onLoopRangeChanged(loopStartTick, loopEndTick, isLoopEnabled);
    }

    public boolean loopToSelectedClips() {
        List<ClipItem> selected = getSelectedClips();
        if (selected.isEmpty() && primaryHitClip != null) selected.add(primaryHitClip);

        if (!selected.isEmpty()) {
            long minStart = Long.MAX_VALUE;
            long maxEnd = Long.MIN_VALUE;
            for (ClipItem c : selected) {
                minStart = Math.min(minStart, c.getStartTick());
                maxEnd = Math.max(maxEnd, c.getEndTick());
            }
            setLoopRange(minStart, maxEnd, true);
            if (listener != null) listener.onLoopRangeChanged(loopStartTick, loopEndTick, isLoopEnabled);
            return true;
        }
        return false;
    }

    public long getLoopStart() { return loopStartTick; }
    public long getLoopEnd() { return loopEndTick; }
    public boolean isLoopEnabled() { return isLoopEnabled; }

    public List<TrackItem> getTracks() { return tracks; }
    public List<ClipItem> getClips() { return clips; }
    public @Nullable ClipItem getSelectedClip() { return primaryHitClip; }

    public void setTracksAndClips(List<TrackItem> trackList, List<ClipItem> clipList) {
        this.tracks.clear();
        this.tracks.addAll(trackList);
        this.clips.clear();
        this.clips.addAll(clipList);
        this.primaryHitClip = null;
        this.dragAnchors.clear();
        this.undoStack.clear();
        this.redoStack.clear();
        this.isDraggingClipsActive = false;
        this.isSlippingContent = false;
        invalidate();
    }

    // --- CLIPBOARD & BATCH ACTIONS ---
    public List<ClipItem> getSelectedClips() {
        List<ClipItem> selected = new ArrayList<>();
        for (ClipItem c : clips) {
            if (c.isSelected()) selected.add(c);
        }
        return selected;
    }

    public void selectAllClips(boolean select) {
        for (ClipItem c : clips) c.setSelected(select);
        if (!select) primaryHitClip = null;
        invalidate();
    }

    public void deleteSelectedClips() {
        List<ClipItem> selected = getSelectedClips();
        if (selected.isEmpty()) return;

        captureUndoPoint();
        for (ClipItem c : selected) {
            clips.remove(c);
            if (listener != null) listener.onClipDeleted(c);
        }
        primaryHitClip = null;
        if (listener != null) listener.onClipsBatchChanged();
        invalidate();
    }

    public void toggleMuteSelectedClips() {
        List<ClipItem> selected = getSelectedClips();
        if (selected.isEmpty() && primaryHitClip != null) selected.add(primaryHitClip);
        if (selected.isEmpty()) return;

        captureUndoPoint();
        boolean anyUnmuted = false;
        for (ClipItem c : selected) {
            if (!c.isMuted()) {
                anyUnmuted = true;
                break;
            }
        }
        for (ClipItem c : selected) {
            c.setMuted(anyUnmuted);
            if (listener != null) listener.onClipModified(c);
        }
        if (listener != null) listener.onClipsBatchChanged();
        invalidate();
    }

    public List<ClipItem> duplicateSelectedClips(long snapTicks) {
        List<ClipItem> selected = getSelectedClips();
        if (selected.isEmpty() && primaryHitClip != null) selected.add(primaryHitClip);
        if (selected.isEmpty()) return new ArrayList<>();

        captureUndoPoint();
        long minStart = Long.MAX_VALUE;
        long maxEnd = Long.MIN_VALUE;
        for (ClipItem c : selected) {
            minStart = Math.min(minStart, c.getStartTick());
            maxEnd = Math.max(maxEnd, c.getEndTick());
        }

        long selectionDuration = maxEnd - minStart;
        long shiftTicks = snapTicks > 0 ? (((selectionDuration + snapTicks - 1) / snapTicks) * snapTicks) : selectionDuration;
        if (shiftTicks <= 0) shiftTicks = TICKS_PER_BAR;

        selectAllClips(false);
        List<ClipItem> newDuplicates = new ArrayList<>();
        for (ClipItem c : selected) {
            int tempId = (int) ((System.currentTimeMillis() + newDuplicates.size() + 100) & 0xFFFF);
            ClipItem dup = c.copy();
            dup.setId(tempId);
            dup.setStartTick(c.getStartTick() + shiftTicks);
            dup.setName(c.getName());
            dup.setSelected(true);

            clips.add(dup);
            newDuplicates.add(dup);
            if (listener != null) listener.onClipCreated(dup);
        }

        if (listener != null) listener.onClipsBatchChanged();
        invalidate();
        return newDuplicates;
    }

    // --- PHASE 4: GLUE / MERGE TOOL IMPLEMENTATION ---
    private boolean performGlueAtClip(ClipItem clip1) {
        if (clip1 == null) return false;

        // Find the adjacent or overlapping next clip on the same track
        ClipItem clip2 = null;
        long minDelta = Long.MAX_VALUE;

        for (ClipItem other : clips) {
            if (other == clip1 || other.getTrackId() != clip1.getTrackId()) continue;
            if (other.getStartTick() >= clip1.getStartTick()) {
                long delta = other.getStartTick() - clip1.getEndTick();
                if (delta >= -PPQ && delta < minDelta) { // within proximity or overlap
                    minDelta = delta;
                    clip2 = other;
                }
            }
        }

        if (clip2 == null) return false;

        captureUndoPoint();

        long mergedStart = Math.min(clip1.getStartTick(), clip2.getStartTick());
        long mergedEnd = Math.max(clip1.getEndTick(), clip2.getEndTick());
        long mergedLength = mergedEnd - mergedStart;

        // Merge notes
        long offsetShiftClip1 = clip1.getStartTick() - mergedStart;
        long offsetShiftClip2 = clip2.getStartTick() - mergedStart;

        List<ClipItem.Note> combinedNotes = new ArrayList<>();
        for (ClipItem.Note n : clip1.getNotes()) {
            ClipItem.Note copy = n.copy();
            copy.startOffsetTicks += offsetShiftClip1;
            combinedNotes.add(copy);
        }
        for (ClipItem.Note n : clip2.getNotes()) {
            ClipItem.Note copy = n.copy();
            copy.startOffsetTicks += offsetShiftClip2;
            combinedNotes.add(copy);
        }

        clip1.setStartTick(mergedStart);
        clip1.setLengthTicks(mergedLength);
        clip1.setName(clip1.getName() + " + " + clip2.getName());
        clip1.getNotes().clear();
        clip1.getNotes().addAll(combinedNotes);

        clips.remove(clip2);

        if (listener != null) {
            listener.onClipModified(clip1);
            listener.onClipDeleted(clip2);
            listener.onClipsBatchChanged();
        }

        invalidate();
        return true;
    }

    // --- UNDO / REDO STACK ---
    public void captureUndoPoint() {
        pushUndoState(cloneClipsList(clips));
    }

    private void pushUndoState(List<ClipItem> snapshot) {
        if (snapshot == null) return;
        if (undoStack.size() >= MAX_UNDO_STACK) {
            undoStack.removeLast();
        }
        undoStack.push(snapshot);
        redoStack.clear();
    }

    public boolean canUndo() { return !undoStack.isEmpty(); }
    public boolean canRedo() { return !redoStack.isEmpty(); }

    public void performUndo() {
        if (undoStack.isEmpty()) return;

        redoStack.push(cloneClipsList(clips));
        List<ClipItem> previous = undoStack.pop();
        restoreClipsList(previous);

        if (listener != null) listener.onClipsBatchChanged();
        invalidate();
    }

    public void performRedo() {
        if (redoStack.isEmpty()) return;

        undoStack.push(cloneClipsList(clips));
        List<ClipItem> next = redoStack.pop();
        restoreClipsList(next);

        if (listener != null) listener.onClipsBatchChanged();
        invalidate();
    }

    private List<ClipItem> cloneClipsList(List<ClipItem> source) {
        List<ClipItem> copy = new ArrayList<>(source.size());
        for (ClipItem c : source) copy.add(c.copy());
        return copy;
    }

    private void restoreClipsList(List<ClipItem> snapshot) {
        clips.clear();
        primaryHitClip = null;
        if (snapshot != null) {
            for (ClipItem c : snapshot) clips.add(c.copy());
        }
    }

    private void updateMarqueeSelection() {
        for (ClipItem clip : clips) {
            int trackIndex = getTrackIndex(clip.getTrackId());
            if (trackIndex < 0) continue;

            float cx = headerWidth + (clip.getStartTick() * pixelsPerTick) - scrollX;
            float cw = clip.getLengthTicks() * pixelsPerTick;
            float cy = rulerHeight + (trackIndex * trackHeight) + (8f * uiScale) - scrollY;
            float ch = trackHeight - (16f * uiScale);

            rectF.set(cx, cy, cx + cw, cy + ch);
            clip.setSelected(RectF.intersects(marqueeRect, rectF));
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        final int width = getWidth();
        final int height = getHeight();

        // 1. Dark Main Canvas Background
        paint.setColor(Color.parseColor("#121316"));
        canvas.drawRect(0, 0, width, height, paint);

        canvas.save();
        canvas.clipRect(headerWidth, rulerHeight, width, height);

        // 2. Timeline Grid Bars & Beats
        final long visibleStartTick = Math.max(0, (long) (scrollX / pixelsPerTick));
        final long visibleEndTick = (long) ((scrollX + width - headerWidth) / pixelsPerTick) + TICKS_PER_BAR;
        final long startBar = visibleStartTick / TICKS_PER_BAR;
        final long endBar = (visibleEndTick / TICKS_PER_BAR) + 1;

        for (long b = startBar; b <= endBar; b++) {
            float x = headerWidth + (b * TICKS_PER_BAR * pixelsPerTick) - scrollX;
            if (b % 2 == 1) {
                paint.setColor(Color.parseColor("#171920"));
                canvas.drawRect(x, rulerHeight, x + (TICKS_PER_BAR * pixelsPerTick), height, paint);
            }
            paint.setColor(Color.parseColor("#262934"));
            paint.setStrokeWidth(2f * uiScale);
            canvas.drawLine(x, rulerHeight, x, height, paint);

            for (int beat = 1; beat < 4; beat++) {
                float bx = x + (beat * PPQ * pixelsPerTick);
                paint.setColor(Color.parseColor("#1E202A"));
                paint.setStrokeWidth(1f * uiScale);
                canvas.drawLine(bx, rulerHeight, bx, height, paint);
            }
        }

        // 3. Track Rows & Destination Highlight Guides
        for (int i = 0; i < tracks.size(); i++) {
            float y = rulerHeight + (i * trackHeight) - scrollY;

            boolean isDropTarget = false;
            if (isDraggingClipsActive) {
                for (ClipAnchor anchor : dragAnchors) {
                    if (anchor.currentPreviewTrackIndex == i) {
                        isDropTarget = true;
                        break;
                    }
                }
            }

            if (isDropTarget) {
                paint.setColor(Color.parseColor("#1C2A40"));
                canvas.drawRect(headerWidth, y, width, y + trackHeight, paint);
            }

            paint.setColor(Color.parseColor("#222530"));
            paint.setStrokeWidth(1.5f * uiScale);
            canvas.drawLine(headerWidth, y + trackHeight, width, y + trackHeight, paint);
        }

        // 4. Highlighted Loop Region across Grid Background
        if (isLoopEnabled) {
            float loopX1 = headerWidth + (loopStartTick * pixelsPerTick) - scrollX;
            float loopX2 = headerWidth + (loopEndTick * pixelsPerTick) - scrollX;
            paint.setColor(Color.parseColor("#150A84FF"));
            canvas.drawRect(Math.max(headerWidth, loopX1), rulerHeight, Math.min(width, loopX2), height, paint);

            paint.setColor(Color.parseColor("#440A84FF"));
            paint.setStrokeWidth(1.5f * uiScale);
            canvas.drawLine(loopX1, rulerHeight, loopX1, height, paint);
            canvas.drawLine(loopX2, rulerHeight, loopX2, height, paint);
        }

        // 5. Render Stationary & Dimmed Clips
        for (ClipItem clip : clips) {
            int trackIndex = getTrackIndex(clip.getTrackId());
            if (trackIndex < 0) continue;

            float clipX = headerWidth + (clip.getStartTick() * pixelsPerTick) - scrollX;
            float clipW = clip.getLengthTicks() * pixelsPerTick;
            float clipY = rulerHeight + (trackIndex * trackHeight) + (8f * uiScale) - scrollY;
            float clipH = trackHeight - (16f * uiScale);

            if (clipX + clipW < headerWidth || clipX > width) continue;

            rectF.set(clipX, clipY, clipX + clipW, clipY + clipH);

            boolean isBeingDragged = isDraggingClipsActive && clip.isSelected();

            // Clip Body Fill
            paint.setStyle(Paint.Style.FILL);
            if (isBeingDragged) {
                paint.setColor(Color.argb(45, 100, 100, 110));
            } else if (clip.isMuted()) {
                paint.setColor(Color.argb(90, 75, 80, 95));
            } else {
                paint.setColor(clip.getColor());
            }
            canvas.drawRoundRect(rectF, 8f * uiScale, 8f * uiScale, paint);

            // Selection Glow Border
            paint.setStyle(Paint.Style.STROKE);
            if (clip.isSelected() && !isBeingDragged) {
                paint.setStrokeWidth(3.5f * uiScale);
                paint.setColor(Color.parseColor("#FFD60A"));
            } else {
                paint.setStrokeWidth(1.2f * uiScale);
                paint.setColor(isBeingDragged ? Color.parseColor("#22FFFFFF") : (clip.isMuted() ? Color.parseColor("#33FFFFFF") : Color.parseColor("#66FFFFFF")));
            }
            canvas.drawRoundRect(rectF, 8f * uiScale, 8f * uiScale, paint);

            // Mute Strikethrough
            if (clip.isMuted() && !isBeingDragged) {
                paint.setColor(Color.parseColor("#88FF453A"));
                paint.setStrokeWidth(2.5f * uiScale);
                canvas.drawLine(clipX + 4f, clipY + 4f, clipX + clipW - 4f, clipY + clipH - 4f, paint);
            }

            // PHASE 4: Dual-Edge Trim Handles [ < ] & [ > ] on Selected Clips
            if (clip.isSelected() && !isBeingDragged && clipW > (40f * uiScale)) {
                // Left Handle [ < ]
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.parseColor("#FFD60A"));
                canvas.drawRect(clipX + 2f, clipY + 4f, clipX + (8f * uiScale), clipY + clipH - 4f, paint);

                // Right Handle [ > ]
                canvas.drawRect(clipX + clipW - (8f * uiScale), clipY + 4f, clipX + clipW - 2f, clipY + clipH - 4f, paint);
            }

            // Clip Title
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(isBeingDragged ? Color.parseColor("#55FFFFFF") : (clip.isMuted() ? Color.parseColor("#8E8E93") : Color.WHITE));
            paint.setTextSize(18f * uiScale);
            paint.setFakeBoldText(true);
            String title = clip.isMuted() ? ("🔇 " + clip.getName()) : clip.getName();
            canvas.drawText(title, clipX + (14f * uiScale), clipY + (24f * uiScale), paint);

            // Mini Waveform / Notes
            paint.setColor(isBeingDragged ? Color.parseColor("#22FFFFFF") : (clip.isMuted() ? Color.parseColor("#44FFFFFF") : Color.parseColor("#88FFFFFF")));
            paint.setStrokeWidth(2.2f * uiScale);
            if (clip.getType() == TrackItem.Type.SYNTH) {
                float noteBaseY = clipY + clipH - (14f * uiScale);
                for (ClipItem.Note note : clip.getNotes()) {
                    if (note.startOffsetTicks < 0 || note.startOffsetTicks > clip.getLengthTicks()) continue;
                    float nx = clipX + (note.startOffsetTicks * pixelsPerTick);
                    float nw = Math.max(5f * uiScale, note.lengthTicks * pixelsPerTick);
                    float ny = noteBaseY - ((note.note % 24) * (2.2f * uiScale));
                    canvas.drawLine(nx, ny, nx + nw, ny, paint);
                }
            } else {
                float midY = clipY + (clipH / 2f);
                for (float px = clipX + 8; px < clipX + clipW - 8; px += (12f * uiScale)) {
                    float waveH = (float) Math.sin((px - clipX) * 0.1f) * (clipH * 0.25f);
                    canvas.drawLine(px, midY - waveH, px, midY + waveH, paint);
                }
            }
        }

        // 6. Real-Time Ghost Previews for 2D Dragging & Slip Indicator
        if (isDraggingClipsActive && !dragAnchors.isEmpty()) {
            for (ClipAnchor anchor : dragAnchors) {
                int targetTrackIdx = anchor.currentPreviewTrackIndex;
                if (targetTrackIdx < 0 || targetTrackIdx >= tracks.size()) continue;

                float gx = headerWidth + (anchor.currentPreviewStartTick * pixelsPerTick) - scrollX;
                float gw = anchor.initialLengthTicks * pixelsPerTick;
                float gy = rulerHeight + (targetTrackIdx * trackHeight) + (8f * uiScale) - scrollY;
                float gh = trackHeight - (16f * uiScale);

                rectF.set(gx, gy, gx + gw, gy + gh);

                ghostPaint.setColor(anchor.clipType == TrackItem.Type.SYNTH ? Color.argb(190, 28, 109, 208) : Color.argb(190, 217, 119, 6));
                canvas.drawRoundRect(rectF, 8f * uiScale, 8f * uiScale, ghostPaint);

                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(3.5f * uiScale);
                paint.setColor(Color.parseColor("#FFD60A"));
                canvas.drawRoundRect(rectF, 8f * uiScale, 8f * uiScale, paint);

                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.WHITE);
                paint.setTextSize(18f * uiScale);
                paint.setFakeBoldText(true);

                TrackItem targetTrack = tracks.get(targetTrackIdx);
                String previewLabel = anchor.clip.getName() + "  ➔  " + targetTrack.getName();
                canvas.drawText(previewLabel, gx + (12f * uiScale), gy + (24f * uiScale), paint);
            }
        } else if (isSlippingContent && primaryHitClip != null) {
            // Slip Content Badge Overlay
            int trackIndex = getTrackIndex(primaryHitClip.getTrackId());
            if (trackIndex >= 0) {
                float clipX = headerWidth + (primaryHitClip.getStartTick() * pixelsPerTick) - scrollX;
                float clipY = rulerHeight + (trackIndex * trackHeight) + (8f * uiScale) - scrollY;

                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.parseColor("#DD000000"));
                rectF.set(clipX + 10f, clipY + 30f, clipX + 220f, clipY + 65f);
                canvas.drawRoundRect(rectF, 6f, 6f, paint);

                paint.setColor(Color.parseColor("#FFD60A"));
                paint.setTextSize(16f * uiScale);
                paint.setFakeBoldText(true);
                String slipText = String.format("⇄ Slip: %+d ticks", currentSlipOffsetTicks);
                canvas.drawText(slipText, clipX + 20f, clipY + 54f, paint);
            }
        }

        // 7. Marquee Selection Rectangle
        if (isMarqueeActive) {
            marqueePaint.setStyle(Paint.Style.FILL);
            marqueePaint.setColor(Color.parseColor("#250A84FF"));
            canvas.drawRect(marqueeRect, marqueePaint);

            marqueePaint.setStyle(Paint.Style.STROKE);
            marqueePaint.setStrokeWidth(2f * uiScale);
            marqueePaint.setColor(Color.parseColor("#0A84FF"));
            canvas.drawRect(marqueeRect, marqueePaint);
        }

        // 8. Playhead Vertical Timeline Needle
        float playheadX = headerWidth + (currentPlayheadTick * pixelsPerTick) - scrollX;
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.parseColor("#FF453A"));
        paint.setStrokeWidth(2.5f * uiScale);
        canvas.drawLine(playheadX, rulerHeight, playheadX, height, paint);

                // PHASE 6: Real-Time Magnetic Snap Guide Line
        if (activeMagneticGuideTick >= 0) {
            float guideX = headerWidth + (activeMagneticGuideTick * pixelsPerTick) - scrollX;
            if (guideX >= headerWidth && guideX <= width) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(Color.parseColor("#64D2FF")); // Glowing Cyan
                paint.setStrokeWidth(2.5f * uiScale);
                canvas.drawLine(guideX, rulerHeight, guideX, height, paint);

                paint.setStyle(Paint.Style.FILL);
                Path diamond = new Path();
                diamond.moveTo(guideX, rulerHeight);
                diamond.lineTo(guideX + (7f * uiScale), rulerHeight + (10f * uiScale));
                diamond.lineTo(guideX, rulerHeight + (20f * uiScale));
                diamond.lineTo(guideX - (7f * uiScale), rulerHeight + (10f * uiScale));
                diamond.close();
                canvas.drawPath(diamond, paint);
            }
        }

        canvas.restore();

        // 9. Top Time Ruler Bar
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#171922"));
        canvas.drawRect(headerWidth, 0, width, rulerHeight, paint);

        if (isLoopEnabled) {
            float loopX1 = headerWidth + (loopStartTick * pixelsPerTick) - scrollX;
            float loopX2 = headerWidth + (loopEndTick * pixelsPerTick) - scrollX;

            paint.setColor(Color.parseColor("#440A84FF"));
            canvas.drawRect(Math.max(headerWidth, loopX1), 0, Math.min(width, loopX2), rulerHeight, paint);

            paint.setColor(Color.parseColor("#0A84FF"));
            paint.setStrokeWidth(3f * uiScale);
            canvas.drawLine(Math.max(headerWidth, loopX1), rulerHeight - 2f, Math.min(width, loopX2), rulerHeight - 2f, paint);
        }

        for (long b = startBar; b <= endBar; b++) {
            float x = headerWidth + (b * TICKS_PER_BAR * pixelsPerTick) - scrollX;
            if (x < headerWidth - 40 || x > width) continue;

            paint.setColor(Color.parseColor("#8E8E93"));
            paint.setTextSize(16f * uiScale);
            paint.setFakeBoldText(true);
            canvas.drawText(String.valueOf(b + 1), x + (6f * uiScale), 22f * uiScale, paint);

            paint.setColor(Color.parseColor("#343848"));
            paint.setStrokeWidth(1.2f * uiScale);
            canvas.drawLine(x, 26f * uiScale, x, rulerHeight, paint);

            for (int beat = 1; beat < 4; beat++) {
                float bx = x + (beat * PPQ * pixelsPerTick);
                if (bx >= headerWidth && bx <= width) {
                    canvas.drawLine(bx, rulerHeight - (10f * uiScale), bx, rulerHeight, paint);
                }
            }
        }

        // 10. Draggable Loop Marker Handles [L ▾] & [R ▾] on Ruler
        if (isLoopEnabled) {
            float loopX1 = headerWidth + (loopStartTick * pixelsPerTick) - scrollX;
            float loopX2 = headerWidth + (loopEndTick * pixelsPerTick) - scrollX;

            if (loopX1 >= headerWidth - 25 && loopX1 <= width + 25) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(rulerTouchMode == RulerTouchMode.DRAGGING_LOOP_START ? Color.parseColor("#FFD60A") : Color.parseColor("#0A84FF"));
                Path lMarker = new Path();
                lMarker.moveTo(loopX1, 0);
                lMarker.lineTo(loopX1 + (22f * uiScale), 0);
                lMarker.lineTo(loopX1 + (22f * uiScale), 24f * uiScale);
                lMarker.lineTo(loopX1, 34f * uiScale);
                lMarker.close();
                canvas.drawPath(lMarker, paint);

                paint.setColor(rulerTouchMode == RulerTouchMode.DRAGGING_LOOP_START ? Color.BLACK : Color.WHITE);
                paint.setTextSize(13f * uiScale);
                paint.setFakeBoldText(true);
                canvas.drawText("L", loopX1 + (5f * uiScale), 18f * uiScale, paint);
            }

            if (loopX2 >= headerWidth - 25 && loopX2 <= width + 25) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(rulerTouchMode == RulerTouchMode.DRAGGING_LOOP_END ? Color.parseColor("#FFD60A") : Color.parseColor("#0A84FF"));
                Path rMarker = new Path();
                rMarker.moveTo(loopX2 - (22f * uiScale), 0);
                rMarker.lineTo(loopX2, 0);
                rMarker.lineTo(loopX2, 34f * uiScale);
                rMarker.lineTo(loopX2 - (22f * uiScale), 24f * uiScale);
                rMarker.close();
                canvas.drawPath(rMarker, paint);

                paint.setColor(rulerTouchMode == RulerTouchMode.DRAGGING_LOOP_END ? Color.BLACK : Color.WHITE);
                paint.setTextSize(13f * uiScale);
                paint.setFakeBoldText(true);
                canvas.drawText("R", loopX2 - (17f * uiScale), 18f * uiScale, paint);
            }
        }

        // 11. Playhead Ruler Scrubber Needle & Flag
        if (playheadX >= headerWidth - 20 && playheadX <= width + 20) {
            paint.setColor(Color.parseColor("#FF453A"));
            paint.setStyle(Paint.Style.FILL);
            Path scrubTriangle = new Path();
            scrubTriangle.moveTo(playheadX - (12f * uiScale), 0);
            scrubTriangle.lineTo(playheadX + (12f * uiScale), 0);
            scrubTriangle.lineTo(playheadX, 22f * uiScale);
            scrubTriangle.close();
            canvas.drawPath(scrubTriangle, paint);
        }

        // 12. Left Track Headers
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#181A22"));
        canvas.drawRect(0, 0, headerWidth, height, paint);
        paint.setColor(Color.parseColor("#282B38"));
        canvas.drawLine(headerWidth, 0, headerWidth, height, paint);

        paint.setColor(Color.parseColor("#121318"));
        canvas.drawRect(0, 0, headerWidth, rulerHeight, paint);
        paint.setColor(Color.parseColor("#0A84FF"));
        paint.setTextSize(17f * uiScale);
        paint.setFakeBoldText(true);
        canvas.drawText("TRACKS", 18f * uiScale, rulerHeight * 0.6f, paint);

        for (int i = 0; i < tracks.size(); i++) {
            TrackItem track = tracks.get(i);
            float y = rulerHeight + (i * trackHeight) - scrollY;

            // Track Header Background & Color Strip
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.parseColor("#181A22"));
            canvas.drawRect(0, y, headerWidth, y + trackHeight, paint);

            paint.setColor(track.getColor());
            canvas.drawRect(0, y, 6f * uiScale, y + trackHeight, paint);

            // Track Title
            paint.setColor(Color.WHITE);
            paint.setTextSize(16f * uiScale);
            paint.setFakeBoldText(true);
            canvas.drawText(track.getName(), 14f * uiScale, y + (26f * uiScale), paint);

            // Track Type & Vol Readout
            paint.setColor(Color.parseColor("#8E8E93"));
            paint.setTextSize(11f * uiScale);
            paint.setFakeBoldText(false);
            String subInfo = (track.getType() == TrackItem.Type.SYNTH ? "Synth" : "Audio") + " • " + (int)(track.getVolume() * 100) + "%";
            canvas.drawText(subInfo, 14f * uiScale, y + (44f * uiScale), paint);

            // [⚙] Gear Inspector Button (Top-Right)
            float gearX = headerWidth - (34f * uiScale);
            float gearY = y + (8f * uiScale);
            paint.setColor(Color.parseColor("#2C2F3C"));
            rectF.set(gearX, gearY, gearX + (26f * uiScale), gearY + (24f * uiScale));
            canvas.drawRoundRect(rectF, 4f * uiScale, 4f * uiScale, paint);
            paint.setColor(Color.parseColor("#C7C7CC"));
            paint.setTextSize(13f * uiScale);
            paint.setFakeBoldText(true);
            canvas.drawText("⚙", gearX + (6f * uiScale), gearY + (17f * uiScale), paint);

            // Bottom Buttons Row: [ M ]  [ S ]  [ FX ]
            float btnY1 = y + (54f * uiScale);
            float btnY2 = btnY1 + (26f * uiScale);

            // [ M ] MUTE Button
            float mX1 = 14f * uiScale;
            float mX2 = mX1 + (32f * uiScale);
            rectF.set(mX1, btnY1, mX2, btnY2);
            paint.setColor(track.isMuted() ? Color.parseColor("#FF453A") : Color.parseColor("#282B38"));
            canvas.drawRoundRect(rectF, 4f * uiScale, 4f * uiScale, paint);
            paint.setColor(Color.WHITE);
            paint.setTextSize(11f * uiScale);
            paint.setFakeBoldText(true);
            canvas.drawText("M", mX1 + (11f * uiScale), btnY1 + (18f * uiScale), paint);

            // [ S ] SOLO Button
            float sX1 = mX2 + (6f * uiScale);
            float sX2 = sX1 + (32f * uiScale);
            rectF.set(sX1, btnY1, sX2, btnY2);
            paint.setColor(track.isSolo() ? Color.parseColor("#FFD60A") : Color.parseColor("#282B38"));
            canvas.drawRoundRect(rectF, 4f * uiScale, 4f * uiScale, paint);
            paint.setColor(track.isSolo() ? Color.BLACK : Color.WHITE);
            paint.setTextSize(11f * uiScale);
            paint.setFakeBoldText(true);
            canvas.drawText("S", sX1 + (11f * uiScale), btnY1 + (18f * uiScale), paint);

            // [ FX ] Button
            float fxX1 = sX2 + (6f * uiScale);
            float fxX2 = fxX1 + (40f * uiScale);
            rectF.set(fxX1, btnY1, fxX2, btnY2);
            paint.setColor(Color.parseColor("#0A84FF"));
            canvas.drawRoundRect(rectF, 4f * uiScale, 4f * uiScale, paint);
            paint.setColor(Color.WHITE);
            paint.setTextSize(10f * uiScale);
            paint.setFakeBoldText(true);
            canvas.drawText("FX", fxX1 + (12f * uiScale), btnY1 + (17f * uiScale), paint);

            paint.setColor(Color.parseColor("#262936"));
            paint.setStrokeWidth(1.5f * uiScale);
            canvas.drawLine(0, y + trackHeight, headerWidth, y + trackHeight, paint);
        }
    }

    private int getTrackIndex(int trackId) {
        for (int i = 0; i < tracks.size(); i++) {
            if (tracks.get(i).getId() == trackId) return i;
        }
        return -1;
    }

    private ClipItem findClipAt(float x, float y) {
        for (ClipItem clip : clips) {
            int trackIndex = getTrackIndex(clip.getTrackId());
            if (trackIndex < 0) continue;

            float cx = headerWidth + (clip.getStartTick() * pixelsPerTick) - scrollX;
            float cw = clip.getLengthTicks() * pixelsPerTick;
            float cy = rulerHeight + (trackIndex * trackHeight) - scrollY;

            if (x >= cx && x <= cx + cw && y >= cy && y <= cy + trackHeight) {
                return clip;
            }
        }
        return null;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (gestureDetector != null) gestureDetector.onTouchEvent(event);

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
                    dragAnchors.clear();
                    primaryHitClip = null;
                    isDraggingClipsActive = false;
                    isSlippingContent = false;
                    isMarqueeActive = false;
                    rulerTouchMode = RulerTouchMode.NONE;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    if (isTwoFingerPanning) {
                        float dx = midX - lastPanMidX;
                        float dy = midY - lastPanMidY;

                        scrollX = Math.max(0, scrollX - dx);
                        float maxScrollY = Math.max(0, (tracks.size() * trackHeight) - (getHeight() - rulerHeight));
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

        // 2. Single-Touch Event Processing
        final float x = event.getX();
        final float y = event.getY();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                isTwoFingerPanning = false;
                dragStartRawX = x;
                dragStartRawY = y;
                hasModifiedClipsInGesture = false;
                isDraggingClipsActive = false;
                isSlippingContent = false;
                currentSlipOffsetTicks = 0;

                // 2a. Time Ruler Interaction
                if (y <= rulerHeight && x > headerWidth) {
                    float loopX1 = headerWidth + (loopStartTick * pixelsPerTick) - scrollX;
                    float loopX2 = headerWidth + (loopEndTick * pixelsPerTick) - scrollX;
                    float handleTolerance = 30f * uiScale;

                    if (isLoopEnabled && Math.abs(x - loopX1) <= handleTolerance) {
                        rulerTouchMode = RulerTouchMode.DRAGGING_LOOP_START;
                        invalidate();
                        return true;
                    } else if (isLoopEnabled && Math.abs(x - loopX2) <= handleTolerance) {
                        rulerTouchMode = RulerTouchMode.DRAGGING_LOOP_END;
                        invalidate();
                        return true;
                    } else if (isLoopEnabled && x > loopX1 + handleTolerance && x < loopX2 - handleTolerance && y <= (rulerHeight * 0.55f)) {
                        rulerTouchMode = RulerTouchMode.DRAGGING_ENTIRE_LOOP_BODY;
                        loopDragStartRawX = x;
                        loopDragInitialStartTick = loopStartTick;
                        loopDragInitialEndTick = loopEndTick;
                        invalidate();
                        return true;
                    } else {
                        rulerTouchMode = RulerTouchMode.SCRUBBING_PLAYHEAD;
                        long targetTick = snapGrid.snap((long) ((x - headerWidth + scrollX) / pixelsPerTick));
                        currentPlayheadTick = Math.max(0, targetTick);
                        if (listener != null) listener.onPlayheadScrubbed(targetTick);
                        invalidate();
                        return true;
                    }
                }

                                // 2c. Left Track Header Interactive Button Clicks [M, S, FX, ⚙]
                if (x <= headerWidth && y > rulerHeight) {
                    int trackIndex = (int) ((y - rulerHeight + scrollY) / trackHeight);
                    if (trackIndex >= 0 && trackIndex < tracks.size()) {
                        TrackItem track = tracks.get(trackIndex);
                        float rowY = rulerHeight + (trackIndex * trackHeight) - scrollY;

                        // [⚙] Inspector button hit
                        float gearX = headerWidth - (34f * uiScale);
                        float gearY = rowY + (8f * uiScale);
                        if (x >= gearX && x <= gearX + (30f * uiScale) && y >= gearY && y <= gearY + (28f * uiScale)) {
                            if (listener != null) listener.onTrackInspectorRequested(track);
                            return true;
                        }

                        float btnY1 = rowY + (54f * uiScale);
                        float btnY2 = btnY1 + (26f * uiScale);

                        if (y >= btnY1 - 6f && y <= btnY2 + 6f) {
                            float mX1 = 14f * uiScale;
                            float mX2 = mX1 + (32f * uiScale);
                            float sX1 = mX2 + (6f * uiScale);
                            float sX2 = sX1 + (32f * uiScale);
                            float fxX1 = sX2 + (6f * uiScale);
                            float fxX2 = fxX1 + (40f * uiScale);

                            // [M] Mute hit
                            if (x >= mX1 && x <= mX2) {
                                track.setMuted(!track.isMuted());
                                if (listener != null) listener.onTrackMuteToggled(track);
                                invalidate();
                                return true;
                            }

                            // [S] Solo hit
                            if (x >= sX1 && x <= sX2) {
                                track.setSolo(!track.isSolo());
                                if (listener != null) listener.onTrackSoloToggled(track);
                                invalidate();
                                return true;
                            }

                            // [FX] hit
                            if (x >= fxX1 && x <= fxX2) {
                                if (listener != null) listener.onTrackFxRequested(track);
                                return true;
                            }
                        }

                        // Clicking track header body opens inspector
                        if (listener != null) listener.onTrackInspectorRequested(track);
                        return true;
                    }
                }

                // 2b. Arranger Canvas Interaction
                if (x > headerWidth && y > rulerHeight) {
                    ClipItem hitClip = findClipAt(x, y);
                    int trackIndex = (int) ((y - rulerHeight + scrollY) / trackHeight);

                    if (trackIndex >= 0 && trackIndex < tracks.size()) {
                        TrackItem track = tracks.get(trackIndex);
                        long clickTick = snapGrid.snap((long) ((x - headerWidth + scrollX) / pixelsPerTick));

                        if (toolMode == ToolMode.PENCIL) {
                            if (hitClip == null) {
                                captureUndoPoint();
                                long length = snapGrid == SnapGrid.BAR_1 ? TICKS_PER_BAR : snapGrid.getTicks() * 4;
                                int tempId = (int) (System.currentTimeMillis() & 0xFFFF);
                                int color = track.getType() == TrackItem.Type.SYNTH ? Color.parseColor("#1C6DD0") : Color.parseColor("#D97706");
                                ClipItem newClip = new ClipItem(tempId, track.getId(), clickTick, length, "Clip " + (clips.size() + 1), color, track.getType());
                                newClip.setSelected(true);

                                if (track.getType() == TrackItem.Type.SYNTH) {
                                    newClip.addNote(60, 0.9f, 0, PPQ);
                                    newClip.addNote(64, 0.9f, PPQ, PPQ);
                                    newClip.addNote(67, 0.9f, PPQ * 2, PPQ * 2);
                                }

                                selectAllClips(false);
                                clips.add(newClip);
                                primaryHitClip = newClip;
                                if (listener != null) listener.onClipCreated(newClip);
                                invalidate();
                                return true;
                            }
                        } else if (toolMode == ToolMode.ERASER) {
                            if (hitClip != null) {
                                captureUndoPoint();
                                if (hitClip.isSelected()) {
                                    deleteSelectedClips();
                                } else {
                                    clips.remove(hitClip);
                                    if (listener != null) listener.onClipDeleted(hitClip);
                                    invalidate();
                                }
                                return true;
                            }
                        } else if (toolMode == ToolMode.SPLIT) {
                            if (hitClip != null && clickTick > hitClip.getStartTick() && clickTick < hitClip.getEndTick()) {
                                captureUndoPoint();
                                long firstPartLen = clickTick - hitClip.getStartTick();
                                long secondPartLen = hitClip.getLengthTicks() - firstPartLen;
                                hitClip.setLengthTicks(firstPartLen);

                                ClipItem secondPart = new ClipItem(0, hitClip.getTrackId(), clickTick, secondPartLen, hitClip.getName() + " (B)", hitClip.getColor(), hitClip.getType());
                                for (ClipItem.Note n : hitClip.getNotes()) {
                                    if (n.startOffsetTicks >= firstPartLen) {
                                        secondPart.addNote(n.note, n.velocity, n.startOffsetTicks - firstPartLen, n.lengthTicks);
                                    }
                                }
                                hitClip.getNotes().removeIf(n -> n.startOffsetTicks >= firstPartLen);

                                if (listener != null) {
                                    listener.onClipModified(hitClip);
                                    listener.onClipCreated(secondPart);
                                }
                                clips.add(secondPart);
                                invalidate();
                                return true;
                            }
                        } else if (toolMode == ToolMode.GLUE) {
                            // PHASE 4: Glue adjacent clip
                            if (hitClip != null) {
                                performGlueAtClip(hitClip);
                                return true;
                            }
                        } else if (toolMode == ToolMode.SLIP) {
                            // PHASE 4: Begin Slip Editing
                            if (hitClip != null) {
                                primaryHitClip = hitClip;
                                isSlippingContent = true;
                                dragAnchors.clear();
                                dragAnchors.add(new ClipAnchor(hitClip, trackIndex));
                                invalidate();
                                return true;
                            }
                        } else if (toolMode == ToolMode.SELECT) {
                            if (hitClip != null) {
                                float clipScreenLeft = headerWidth + (hitClip.getStartTick() * pixelsPerTick) - scrollX;
                                float clipScreenRight = headerWidth + (hitClip.getEndTick() * pixelsPerTick) - scrollX;
                                float handleMargin = 30f * uiScale;

                                isResizingRight = Math.abs(x - clipScreenRight) <= handleMargin;
                                isResizingLeft = Math.abs(x - clipScreenLeft) <= handleMargin;

                                if (!hitClip.isSelected() && !isResizingRight && !isResizingLeft) {
                                    selectAllClips(false);
                                    hitClip.setSelected(true);
                                }
                                primaryHitClip = hitClip;
                                if (listener != null) listener.onClipSelected(primaryHitClip);

                                dragAnchors.clear();
                                for (ClipItem c : getSelectedClips()) {
                                    int tIdx = getTrackIndex(c.getTrackId());
                                    dragAnchors.add(new ClipAnchor(c, tIdx));
                                }

                                invalidate();
                                return true;
                            } else {
                                isMarqueeActive = true;
                                marqueeStartX = x;
                                marqueeStartY = y;
                                marqueeCurrentX = x;
                                marqueeCurrentY = y;
                                marqueeRect.set(x, y, x, y);
                                selectAllClips(false);
                                invalidate();
                                return true;
                            }
                        }
                    }
                }
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                float deltaX = x - dragStartRawX;
                float deltaY = y - dragStartRawY;

                if (rulerTouchMode == RulerTouchMode.DRAGGING_LOOP_START) {
                    long snapped = snapGrid.snap((long) ((x - headerWidth + scrollX) / pixelsPerTick));
                    loopStartTick = Math.max(0, Math.min(loopEndTick - (PPQ / 2), snapped));
                    if (listener != null) listener.onLoopRangeChanged(loopStartTick, loopEndTick, isLoopEnabled);
                    invalidate();
                    return true;
                }

                if (rulerTouchMode == RulerTouchMode.DRAGGING_LOOP_END) {
                    long snapped = snapGrid.snap((long) ((x - headerWidth + scrollX) / pixelsPerTick));
                    loopEndTick = Math.max(loopStartTick + (PPQ / 2), snapped);
                    if (listener != null) listener.onLoopRangeChanged(loopStartTick, loopEndTick, isLoopEnabled);
                    invalidate();
                    return true;
                }

                if (rulerTouchMode == RulerTouchMode.DRAGGING_ENTIRE_LOOP_BODY) {
                    long deltaTicks = snapGrid.snap((long) ((x - loopDragStartRawX) / pixelsPerTick));
                    long duration = loopDragInitialEndTick - loopDragInitialStartTick;
                    long newStart = Math.max(0, loopDragInitialStartTick + deltaTicks);
                    loopStartTick = newStart;
                    loopEndTick = newStart + duration;
                    if (listener != null) listener.onLoopRangeChanged(loopStartTick, loopEndTick, isLoopEnabled);
                    invalidate();
                    return true;
                }

                if (rulerTouchMode == RulerTouchMode.SCRUBBING_PLAYHEAD) {
                    long targetTick = snapGrid.snap((long) ((x - headerWidth + scrollX) / pixelsPerTick));
                    currentPlayheadTick = Math.max(0, targetTick);
                    if (listener != null) listener.onPlayheadScrubbed(currentPlayheadTick);
                    invalidate();
                    return true;
                }

                if (isMarqueeActive) {
                    marqueeCurrentX = x;
                    marqueeCurrentY = y;
                    marqueeRect.set(
                        Math.min(marqueeStartX, marqueeCurrentX),
                        Math.min(marqueeStartY, marqueeCurrentY),
                        Math.max(marqueeStartX, marqueeCurrentX),
                        Math.max(marqueeStartY, marqueeCurrentY)
                    );
                    updateMarqueeSelection();
                    invalidate();
                    return true;
                }

                // PHASE 4: Slip Editing Moves
                if (isSlippingContent && primaryHitClip != null && !dragAnchors.isEmpty()) {
                    long slipDeltaTicks = snapGrid.snap((long) (deltaX / pixelsPerTick));
                    currentSlipOffsetTicks = slipDeltaTicks;
                    ClipAnchor anchor = dragAnchors.get(0);

                    // Recompute shifted note offsets inside clip
                    primaryHitClip.getNotes().clear();
                    for (ClipItem.Note orig : anchor.initialNotesSnapshot) {
                        ClipItem.Note slipped = orig.copy();
                        slipped.startOffsetTicks += slipDeltaTicks;
                        primaryHitClip.addNote(slipped.note, slipped.velocity, slipped.startOffsetTicks, slipped.lengthTicks, slipped.isMuted);
                    }
                    hasModifiedClipsInGesture = true;
                    invalidate();
                    return true;
                }

                // PHASE 4: Dual-Edge Trimming & 2D Multi-Axis Dragging
                if (!dragAnchors.isEmpty() && toolMode == ToolMode.SELECT) {
                    long deltaTicks = (long) (deltaX / pixelsPerTick);

                    if (isResizingRight && primaryHitClip != null) {
                        for (ClipAnchor anchor : dragAnchors) {
                            if (anchor.clip == primaryHitClip) {
                                long newLen = snapGrid.snap(anchor.initialLengthTicks + deltaTicks);
                                anchor.clip.setLengthTicks(Math.max(PPQ / 4, newLen));
                            }
                        }
                        hasModifiedClipsInGesture = true;
                    } else if (isResizingLeft && primaryHitClip != null) {
                        // PHASE 4: Left Trim with Child Note Re-Anchoring
                        for (ClipAnchor anchor : dragAnchors) {
                            if (anchor.clip == primaryHitClip) {
                                long shiftTicks = snapGrid.snap(deltaTicks);
                                long newStart = Math.max(0, anchor.initialStartTick + shiftTicks);
                                long actualShift = newStart - anchor.initialStartTick;
                                long newLen = anchor.initialLengthTicks - actualShift;

                                if (newLen >= PPQ / 4) {
                                    anchor.clip.setStartTick(newStart);
                                    anchor.clip.setLengthTicks(newLen);

                                    // Shift notes internally so they stay stationary on timeline
                                    anchor.clip.getNotes().clear();
                                    for (ClipItem.Note orig : anchor.initialNotesSnapshot) {
                                        long newOffset = orig.startOffsetTicks - actualShift;
                                        if (newOffset + orig.lengthTicks > 0) { // Keep if visible in trimmed region
                                            anchor.clip.addNote(orig.note, orig.velocity, newOffset, orig.lengthTicks, orig.isMuted);
                                        }
                                    }
                                }
                            }
                        }
                        hasModifiedClipsInGesture = true;
                    } else {
                        // 2D Movement: Calculate Time Offset & Track Row Shift
                        isDraggingClipsActive = true;
                        long primaryTarget = computeMagneticSnap(dragAnchors.get(0).initialStartTick + deltaTicks, dragAnchors.get(0).initialLengthTicks, primaryHitClip);
                        long totalDeltaTicks = primaryTarget - dragAnchors.get(0).initialStartTick;
                        int rowShift = Math.round(deltaY / trackHeight);

                        long minStart = Long.MAX_VALUE;
                        for (ClipAnchor anchor : dragAnchors) {
                            minStart = Math.min(minStart, anchor.initialStartTick + totalDeltaTicks);
                        }

                        long allowedTimeShift = totalDeltaTicks;
                        if (minStart < 0) {
                            allowedTimeShift -= minStart;
                        }

                        int allowedRowShift = 0;
                        boolean canShiftRows = true;

                        for (ClipAnchor anchor : dragAnchors) {
                            int targetIdx = anchor.initialTrackIndex + rowShift;
                            if (targetIdx < 0 || targetIdx >= tracks.size()) {
                                canShiftRows = false;
                                break;
                            }
                            if (tracks.get(targetIdx).getType() != anchor.clipType) {
                                canShiftRows = false;
                                break;
                            }
                        }

                        if (canShiftRows) {
                            allowedRowShift = rowShift;
                        }

                        for (ClipAnchor anchor : dragAnchors) {
                            int prospectiveTrackIdx = anchor.initialTrackIndex + allowedRowShift;
                            anchor.currentPreviewTrackIndex = prospectiveTrackIdx;
                            anchor.currentPreviewTrackId = tracks.get(prospectiveTrackIdx).getId();
                            anchor.currentPreviewStartTick = Math.max(0, anchor.initialStartTick + allowedTimeShift);
                        }

                        hasModifiedClipsInGesture = true;
                    }
                    invalidate();
                    return true;
                }

                // Arranger Canvas Background Pan
                if (dragAnchors.isEmpty() && !isMarqueeActive && rulerTouchMode == RulerTouchMode.NONE && event.getPointerCount() == 1 && x > headerWidth && y > rulerHeight) {
                    scrollX = Math.max(0, scrollX - deltaX * 0.55f);
                    dragStartRawX = x;
                    invalidate();
                    return true;
                }
                break;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (rulerTouchMode != RulerTouchMode.NONE) {
                    if (listener != null && (rulerTouchMode == RulerTouchMode.DRAGGING_LOOP_START || rulerTouchMode == RulerTouchMode.DRAGGING_LOOP_END || rulerTouchMode == RulerTouchMode.DRAGGING_ENTIRE_LOOP_BODY)) {
                        listener.onLoopRangeChanged(loopStartTick, loopEndTick, isLoopEnabled);
                    }
                    rulerTouchMode = RulerTouchMode.NONE;
                    invalidate();
                }

                if (isMarqueeActive) {
                    isMarqueeActive = false;
                    invalidate();
                }

                // Commit Trims, Slips, & 2D translations
                if (hasModifiedClipsInGesture && !dragAnchors.isEmpty()) {
                    captureUndoPoint();
                    for (ClipAnchor anchor : dragAnchors) {
                        if (isDraggingClipsActive) {
                            anchor.clip.setTrackId(anchor.currentPreviewTrackId);
                            anchor.clip.setStartTick(anchor.currentPreviewStartTick);
                        }
                        if (listener != null) listener.onClipModified(anchor.clip);
                    }
                    if (listener != null) listener.onClipsBatchChanged();
                }

                dragAnchors.clear();
                isResizingRight = false;
                isResizingLeft = false;
                isDraggingClipsActive = false;
                isSlippingContent = false;
                hasModifiedClipsInGesture = false;
                currentSlipOffsetTicks = 0;
                activeMagneticGuideTick = -1;
                invalidate();
                break;
            }
        }
        return true;
    }
}
