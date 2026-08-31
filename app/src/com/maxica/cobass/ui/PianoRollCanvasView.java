package com.maxica.cobass.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;
import com.maxica.cobass.model.ClipItem;
import com.maxica.cobass.model.MusicalScale;
import com.maxica.cobass.model.SnapGrid;
import com.maxica.cobass.model.ToolMode;

import java.util.ArrayList;
import java.util.List;

public class PianoRollCanvasView extends View {

    public interface OnPianoRollEventListener {
        void onNoteAudition(int note, float velocity, boolean isNoteOn);
        void onNotesChanged();
        void onTransactionCommitted(List<ClipItem.Note> preSnapshot);
        void onLoopRangeChanged(long startTick, long endTick, boolean enabled);
    }

    private static class NoteAnchor {
        ClipItem.Note note;
        long initialOffset;
        long initialLength;
        int initialPitch;

        NoteAnchor(ClipItem.Note note) {
            this.note = note;
            this.initialOffset = note.startOffsetTicks;
            this.initialLength = note.lengthTicks;
            this.initialPitch = note.note;
        }
    }

    private static final int PPQ = 480;
    private static final int MIN_MIDI_NOTE = 24;  // C1
    private static final int MAX_MIDI_NOTE = 96;  // C7

    private ClipItem clip;
    private ToolMode toolMode = ToolMode.PENCIL;
    private SnapGrid snapGrid = SnapGrid.BEAT_1_4;
    private MusicalScale scale = MusicalScale.CHROMATIC;
    private int rootKey = 0;
    private boolean isScaleFolded = false;
    private boolean isScaleSnapLocked = false;
    private int[] activeChordIntervals = null;
    private OnPianoRollEventListener listener;

    // Note Length Memory State (Default: 1 Beat = 480 Ticks)
    private long lastDrawnNoteLengthTicks = PPQ;
    private ClipItem.Note pendingNewNote = null;

    // Viewport & Dimensions
    private float scrollX = 0f;
    private float scrollY = 0f;
    private float pixelsPerTick = 0.45f;
    private float noteRowHeight = 42f;
    private final float keyWidth = 115f;
    private final float velocityLaneHeight = 100f;
    private final float rulerHeaderHeight = 24f;

    // In-Dialog Transport & Loop Markers
    private long currentPlayheadTick = 0;
    private boolean isPlaying = false;
    private boolean isFollowPlayhead = true;
    private long loopStartTick = 0;
    private long loopEndTick = 1920 * 2;
    private boolean isLoopEnabled = true;

    private boolean isDraggingLoopStart = false;
    private boolean isDraggingLoopEnd = false;

    // Two-Finger Pan Navigation
    private boolean isTwoFingerPanning = false;
    private float lastPanMidX = 0f;
    private float lastPanMidY = 0f;

    // Undo Transaction Tracker
    private List<ClipItem.Note> gestureStartSnapshot = null;
    private boolean hasModifiedNotesInGesture = false;

    // Velocity Stalk & Ramp State
    private boolean isDrawingVelocityRamp = false;
    private ClipItem.Note activeVelocityDragNote = null;
    private float lastVelocityTouchX = 0f;
    private float lastVelocityTouchY = 0f;
    private float currentHoverVelocity = 0.8f;
    private final Path velocityRampPath = new Path();

    // Marquee State (Select Tool)
    private boolean isMarqueeActive = false;
    private float marqueeStartX = 0f;
    private float marqueeStartY = 0f;
    private float marqueeCurrentX = 0f;
    private float marqueeCurrentY = 0f;
    private final RectF marqueeRect = new RectF();

    // Note Interaction State
    private final List<NoteAnchor> dragAnchors = new ArrayList<>();
    private ClipItem.Note primaryDragNote = null;
    private boolean isCreatingNewNote = false;
    private boolean isResizingRight = false;
    private boolean isResizingLeft = false;
    private boolean isMovingNoteBody = false;
    private float dragStartRawX = 0f;
    private float dragStartRawY = 0f;

    // Brush Tool State
    private long lastBrushTick = -1;
    private int lastBrushPitch = -1;

    private int activeAuditionPitch = -1;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint marqueePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rampPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rectF = new RectF();
    private static final String[] NOTE_NAMES = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};

    public PianoRollCanvasView(Context context) {
        super(context);
        init();
    }

    public PianoRollCanvasView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        marqueePaint.setStyle(Paint.Style.FILL);
        marqueePaint.setColor(Color.parseColor("#330A84FF"));

        rampPaint.setStyle(Paint.Style.STROKE);
        rampPaint.setStrokeWidth(3f);
        rampPaint.setColor(Color.parseColor("#FFD60A"));
        rampPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setClip(ClipItem clip) {
        this.clip = clip;
        this.loopStartTick = 0;
        this.loopEndTick = clip.getLengthTicks();
        int noteIndex = getRowIndexForMidiNote(60);
        scrollY = Math.max(0, (noteIndex * noteRowHeight) - 280f);
        invalidate();
    }

    public void setEventListener(OnPianoRollEventListener listener) { this.listener = listener; }

    public void setToolMode(ToolMode mode) {
        stopAudition();
        this.toolMode = mode;
        this.isMarqueeActive = false;
        this.dragAnchors.clear();
        this.primaryDragNote = null;
        this.pendingNewNote = null;
        this.isCreatingNewNote = false;
        this.isMovingNoteBody = false;
        this.lastBrushTick = -1;
        this.lastBrushPitch = -1;
        invalidate();
    }

    public ToolMode getToolMode() { return toolMode; }

    public void setSnapGrid(SnapGrid grid) {
        this.snapGrid = grid;
        invalidate();
    }

    public SnapGrid getSnapGrid() { return snapGrid; }

    public void setCustomNoteLength(long lengthTicks) {
        this.lastDrawnNoteLengthTicks = Math.max(1, lengthTicks);
    }

    public long getLastDrawnNoteLength() {
        return lastDrawnNoteLengthTicks;
    }

    public void setMusicalScale(MusicalScale scale) {
        this.scale = scale;
        invalidate();
    }

    public MusicalScale getMusicalScale() { return scale; }

    public void setRootKey(int rootKey) {
        this.rootKey = Math.max(0, Math.min(11, rootKey));
        invalidate();
    }

    public int getRootKey() { return rootKey; }

    public void setScaleFolded(boolean folded) {
        this.isScaleFolded = folded;
        invalidate();
    }

    public boolean isScaleFolded() { return isScaleFolded; }

    public void setScaleSnapLocked(boolean locked) {
        this.isScaleSnapLocked = locked;
        invalidate();
    }

    public boolean isScaleSnapLocked() { return isScaleSnapLocked; }

    public void setActiveChordIntervals(@Nullable int[] intervals) {
        this.activeChordIntervals = intervals;
    }

    public @Nullable int[] getActiveChordIntervals() { return activeChordIntervals; }

    public void set2DZoom(float timeScale, float pitchScale) {
        this.pixelsPerTick = Math.max(0.12f, Math.min(2.5f, timeScale));
        this.noteRowHeight = Math.max(24f, Math.min(68f, pitchScale));
        invalidate();
    }

    public float getPixelsPerTick() { return pixelsPerTick; }
    public float getNoteRowHeight() { return noteRowHeight; }

    public void setFollowPlayhead(boolean follow) {
        this.isFollowPlayhead = follow;
        invalidate();
    }

    public void setLoopBounds(long startTick, long endTick, boolean enabled) {
        this.loopStartTick = Math.max(0, startTick);
        this.loopEndTick = Math.max(this.loopStartTick + PPQ / 2, endTick);
        this.isLoopEnabled = enabled;
        invalidate();
    }

    public long getLoopStart() { return loopStartTick; }
    public long getLoopEnd() { return loopEndTick; }
    public boolean isLoopEnabled() { return isLoopEnabled; }

    public void setPlayheadState(long globalTick, boolean playing) {
        this.currentPlayheadTick = globalTick;
        this.isPlaying = playing;

        if (clip != null && isPlaying && isFollowPlayhead) {
            long clipRelativeTick = globalTick - clip.getStartTick();
            if (clipRelativeTick >= 0 && clipRelativeTick <= clip.getLengthTicks()) {
                float playheadScreenX = keyWidth + (clipRelativeTick * pixelsPerTick) - scrollX;
                final float viewportW = getWidth();
                if (viewportW > keyWidth + 50) {
                    if (playheadScreenX > viewportW - 80f || playheadScreenX < keyWidth) {
                        scrollX = Math.max(0, (clipRelativeTick * pixelsPerTick) - 100f);
                    }
                }
            }
        }
        invalidate();
    }

    public void stopAudition() {
        if (activeAuditionPitch != -1 && listener != null) {
            listener.onNoteAudition(activeAuditionPitch, 0.0f, false);
            activeAuditionPitch = -1;
            invalidate();
        }
    }

    private void playAudition(int note, float vel) {
        if (activeAuditionPitch == note) return;
        stopAudition();
        activeAuditionPitch = note;
        if (listener != null) {
            listener.onNoteAudition(note, vel, true);
        }
        invalidate();
    }

    private List<Integer> getActiveMidiNotes() {
        List<Integer> list = new ArrayList<>();
        for (int note = MAX_MIDI_NOTE; note >= MIN_MIDI_NOTE; note--) {
            if (!isScaleFolded || scale.isNoteInScale(note, rootKey)) {
                list.add(note);
            }
        }
        return list;
    }

    private int getRowIndexForMidiNote(int midiNote) {
        if (!isScaleFolded) {
            return MAX_MIDI_NOTE - midiNote;
        }
        List<Integer> active = getActiveMidiNotes();
        int idx = active.indexOf(midiNote);
        if (idx >= 0) return idx;

        int closestIdx = 0;
        int minDelta = 999;
        for (int i = 0; i < active.size(); i++) {
            int d = Math.abs(active.get(i) - midiNote);
            if (d < minDelta) {
                minDelta = d;
                closestIdx = i;
            }
        }
        return closestIdx;
    }

    private int getMidiNoteFromRowIndex(int rowIndex) {
        List<Integer> active = getActiveMidiNotes();
        if (active.isEmpty()) return 60;
        int clampedRow = Math.max(0, Math.min(active.size() - 1, rowIndex));
        return active.get(clampedRow);
    }

    private int getMidiNoteFromY(float y) {
        int rowIndex = (int) ((y - rulerHeaderHeight + scrollY) / noteRowHeight);
        int note = getMidiNoteFromRowIndex(rowIndex);
        return snapPitchToScale(note);
    }

    private int snapPitchToScale(int midiNote) {
        if (scale == MusicalScale.CHROMATIC || !isScaleSnapLocked) return midiNote;
        if (scale.isNoteInScale(midiNote, rootKey)) return midiNote;

        for (int dist = 1; dist <= 6; dist++) {
            if (midiNote + dist <= MAX_MIDI_NOTE && scale.isNoteInScale(midiNote + dist, rootKey)) {
                return midiNote + dist;
            }
            if (midiNote - dist >= MIN_MIDI_NOTE && scale.isNoteInScale(midiNote - dist, rootKey)) {
                return midiNote - dist;
            }
        }
        return midiNote;
    }

    public static int getVelocityHeatmapColor(float vel, boolean isSelected, boolean isMuted) {
        if (isMuted) return Color.argb(90, 110, 115, 130);
        if (isSelected) return Color.parseColor("#FFD60A");

        float v = Math.max(0.0f, Math.min(1.0f, vel));
        if (v <= 0.5f) {
            float t = v / 0.5f;
            int r = (int) (50 + t * (10 - 50));
            int g = (int) (173 + t * (132 - 173));
            int b = (int) (230 + t * (255 - 230));
            return Color.rgb(Math.max(0, Math.min(255, r)), Math.max(0, Math.min(255, g)), Math.max(0, Math.min(255, b)));
        } else {
            float t = (v - 0.5f) / 0.5f;
            int r = (int) (10 + t * (255 - 10));
            int g = (int) (132 + t * (69 - 132));
            int b = (int) (255 + t * (58 - 255));
            return Color.rgb(Math.max(0, Math.min(255, r)), Math.max(0, Math.min(255, g)), Math.max(0, Math.min(255, b)));
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (clip == null) return;

        final int width = getWidth();
        final int height = getHeight();
        final float gridBottom = height - velocityLaneHeight;
        final List<Integer> displayedNotes = getActiveMidiNotes();
        final int totalRows = displayedNotes.size();

        paint.setColor(Color.parseColor("#121316"));
        canvas.drawRect(0, 0, width, height, paint);

        canvas.save();
        canvas.clipRect(keyWidth, rulerHeaderHeight, width, gridBottom);

        // 1. Grid Background & Scales
        for (int i = 0; i < totalRows; i++) {
            int midiNote = displayedNotes.get(i);
            float y = rulerHeaderHeight + (i * noteRowHeight) - scrollY;
            if (y + noteRowHeight < rulerHeaderHeight || y > gridBottom) continue;

            boolean isBlackKey = isBlackKey(midiNote);
            boolean inScale = scale.isNoteInScale(midiNote, rootKey);

            if (isBlackKey) {
                paint.setColor(inScale ? Color.parseColor("#171922") : Color.parseColor("#13141A"));
            } else {
                paint.setColor(inScale ? Color.parseColor("#1E212C") : Color.parseColor("#181A22"));
            }
            canvas.drawRect(keyWidth, y, width, y + noteRowHeight, paint);

            paint.setColor(Color.parseColor("#262936"));
            paint.setStrokeWidth(1f);
            canvas.drawLine(keyWidth, y + noteRowHeight, width, y + noteRowHeight, paint);
        }

        // 2. Bar & Beat Grid Lines
        final long totalClipTicks = clip.getLengthTicks();
        final int snapTicks = snapGrid.getTicks();
        for (long t = 0; t <= totalClipTicks; t += snapTicks) {
            float x = keyWidth + (t * pixelsPerTick) - scrollX;
            if (x < keyWidth || x > width) continue;

            boolean isBar = (t % (PPQ * 4) == 0);
            boolean isBeat = (t % PPQ == 0);

            paint.setColor(isBar ? Color.parseColor("#3B4052") : (isBeat ? Color.parseColor("#272A36") : Color.parseColor("#1C1E26")));
            paint.setStrokeWidth(isBar ? 2f : 1f);
            canvas.drawLine(x, rulerHeaderHeight, x, gridBottom, paint);
        }

        // 3. Loop Region Highlight
        if (isLoopEnabled) {
            float loopX1 = keyWidth + (loopStartTick * pixelsPerTick) - scrollX;
            float loopX2 = keyWidth + (loopEndTick * pixelsPerTick) - scrollX;
            paint.setColor(Color.parseColor("#180A84FF"));
            canvas.drawRect(Math.max(keyWidth, loopX1), rulerHeaderHeight, Math.min(width, loopX2), gridBottom, paint);
        }

        // 4. Render Notes
        for (ClipItem.Note note : clip.getNotes()) {
            if (isScaleFolded && !scale.isNoteInScale(note.note, rootKey)) continue;

            int noteIndex = getRowIndexForMidiNote(note.note);
            float nx = keyWidth + (note.startOffsetTicks * pixelsPerTick) - scrollX;
            float nw = Math.max(12f, note.lengthTicks * pixelsPerTick);
            float ny = rulerHeaderHeight + (noteIndex * noteRowHeight) + 3f - scrollY;
            float nh = noteRowHeight - 6f;

            if (nx + nw < keyWidth || nx > width || ny + nh < rulerHeaderHeight || ny > gridBottom) continue;

            rectF.set(nx, ny, nx + nw, ny + nh);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(getVelocityHeatmapColor(note.velocity, note.isSelected, note.isMuted));
            canvas.drawRoundRect(rectF, 6f, 6f, paint);

            paint.setStyle(Paint.Style.STROKE);
            if (note.isSelected) {
                paint.setStrokeWidth(3.5f);
                paint.setColor(Color.parseColor("#FFFFFF"));
            } else {
                paint.setStrokeWidth(1.2f);
                paint.setColor(note.isMuted ? Color.parseColor("#44FFFFFF") : Color.parseColor("#99FFFFFF"));
            }
            canvas.drawRoundRect(rectF, 6f, 6f, paint);

            if (note.isSelected && nw > 36f) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.WHITE);
                canvas.drawRect(nx + 2f, ny + 4f, nx + 6f, ny + nh - 4f, paint);
                canvas.drawRect(nx + nw - 6f, ny + 4f, nx + nw - 2f, ny + nh - 4f, paint);
            }

            if (note.isMuted) {
                paint.setColor(Color.parseColor("#AAFF453A"));
                paint.setStrokeWidth(2f);
                canvas.drawLine(nx + 4f, ny + nh / 2f, nx + nw - 4f, ny + nh / 2f, paint);
            }

            if (noteRowHeight >= 24f) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(note.isMuted ? Color.parseColor("#8E8E93") : Color.WHITE);
                paint.setTextSize(Math.max(11f, noteRowHeight * 0.42f));
                paint.setFakeBoldText(true);

                String label = getNoteLabel(note.note);
                if (nw > 65f) {
                    label += " • " + (int)(note.velocity * 100) + "%";
                }
                canvas.drawText(label, nx + 8f, ny + nh - (nh * 0.25f), paint);
            }
        }

        // 5. Marquee Box
        if (isMarqueeActive) {
            marqueePaint.setStyle(Paint.Style.FILL);
            marqueePaint.setColor(Color.parseColor("#250A84FF"));
            canvas.drawRect(marqueeRect, marqueePaint);

            marqueePaint.setStyle(Paint.Style.STROKE);
            marqueePaint.setStrokeWidth(2f);
            marqueePaint.setColor(Color.parseColor("#0A84FF"));
            canvas.drawRect(marqueeRect, marqueePaint);
        }

        // 6. Live Playhead Needle
        long clipRelTick = currentPlayheadTick - clip.getStartTick();
        if (clipRelTick >= 0 && clipRelTick <= clip.getLengthTicks()) {
            float playheadX = keyWidth + (clipRelTick * pixelsPerTick) - scrollX;
            if (playheadX >= keyWidth && playheadX <= width) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(Color.parseColor("#FF453A"));
                paint.setStrokeWidth(2.5f);
                canvas.drawLine(playheadX, rulerHeaderHeight, playheadX, gridBottom, paint);

                paint.setStyle(Paint.Style.FILL);
                Path triangle = new Path();
                triangle.moveTo(playheadX - 8f, rulerHeaderHeight);
                triangle.lineTo(playheadX + 8f, rulerHeaderHeight);
                triangle.lineTo(playheadX, rulerHeaderHeight + 12f);
                triangle.close();
                canvas.drawPath(triangle, paint);
            }
        }

        canvas.restore();

        // 7. Top Ruler Strip
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#181A24"));
        canvas.drawRect(keyWidth, 0, width, rulerHeaderHeight, paint);
        paint.setColor(Color.parseColor("#2C3040"));
        canvas.drawLine(keyWidth, rulerHeaderHeight, width, rulerHeaderHeight, paint);

        if (isLoopEnabled) {
            float loopX1 = keyWidth + (loopStartTick * pixelsPerTick) - scrollX;
            float loopX2 = keyWidth + (loopEndTick * pixelsPerTick) - scrollX;

            paint.setColor(Color.parseColor("#440A84FF"));
            canvas.drawRect(Math.max(keyWidth, loopX1), 0, Math.min(width, loopX2), rulerHeaderHeight, paint);

            if (loopX1 >= keyWidth - 16 && loopX1 <= width) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.parseColor("#0A84FF"));
                Path lMarker = new Path();
                lMarker.moveTo(loopX1, 0);
                lMarker.lineTo(loopX1 + 16f, 0);
                lMarker.lineTo(loopX1 + 16f, 16f);
                lMarker.lineTo(loopX1, 24f);
                lMarker.close();
                canvas.drawPath(lMarker, paint);
                paint.setColor(Color.WHITE);
                paint.setTextSize(11f);
                canvas.drawText("L", loopX1 + 3f, 13f, paint);
            }

            if (loopX2 >= keyWidth - 16 && loopX2 <= width) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.parseColor("#0A84FF"));
                Path rMarker = new Path();
                rMarker.moveTo(loopX2 - 16f, 0);
                rMarker.lineTo(loopX2, 0);
                rMarker.lineTo(loopX2, 24f);
                rMarker.lineTo(loopX2 - 16f, 16f);
                rMarker.close();
                canvas.drawPath(rMarker, paint);
                paint.setColor(Color.WHITE);
                paint.setTextSize(11f);
                canvas.drawText("R", loopX2 - 12f, 13f, paint);
            }
        }

        // 8. Left Keybed Column
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#181A22"));
        canvas.drawRect(0, 0, keyWidth, gridBottom, paint);

        for (int i = 0; i < totalRows; i++) {
            int midiNote = displayedNotes.get(i);
            float y = rulerHeaderHeight + (i * noteRowHeight) - scrollY;
            if (y + noteRowHeight < rulerHeaderHeight || y > gridBottom) continue;

            boolean isBlackKey = isBlackKey(midiNote);
            paint.setColor(isBlackKey ? Color.parseColor("#14151B") : Color.parseColor("#D1D5DB"));

            if (activeAuditionPitch == midiNote) {
                paint.setColor(Color.parseColor("#0A84FF"));
            }

            canvas.drawRect(0, y, keyWidth, y + noteRowHeight, paint);
            paint.setColor(isBlackKey ? Color.parseColor("#262934") : Color.parseColor("#9CA3AF"));
            paint.setStrokeWidth(1f);
            canvas.drawLine(0, y + noteRowHeight, keyWidth, y + noteRowHeight, paint);

            boolean isRoot = ((midiNote % 12) == rootKey);
            if (isRoot || midiNote % 12 == 0 || isScaleFolded || noteRowHeight >= 34f) {
                paint.setColor((activeAuditionPitch == midiNote || isBlackKey) ? Color.WHITE : Color.parseColor("#111827"));
                paint.setTextSize(Math.max(11f, noteRowHeight * 0.40f));
                paint.setFakeBoldText(true);
                String label = getNoteLabel(midiNote);
                if (isRoot && scale != MusicalScale.CHROMATIC) {
                    label += " (Root)";
                }
                canvas.drawText(label, 10f, y + (noteRowHeight * 0.65f), paint);
            }
        }

        paint.setColor(Color.parseColor("#343848"));
        paint.setStrokeWidth(2f);
        canvas.drawLine(keyWidth, 0, keyWidth, gridBottom, paint);

        // 9. Velocity Lane Drawer with Studio Guidelines & Stalks
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#14151B"));
        canvas.drawRect(0, gridBottom, width, height, paint);
        paint.setColor(Color.parseColor("#262936"));
        canvas.drawLine(0, gridBottom, width, gridBottom, paint);

        float maxStalkH = velocityLaneHeight - 40f;

        paint.setColor(Color.parseColor("#1D202A"));
        paint.setStrokeWidth(1f);
        for (float pct : new float[]{0.25f, 0.50f, 0.75f, 1.00f}) {
            float guideY = height - 8f - (pct * maxStalkH);
            canvas.drawLine(keyWidth, guideY, width, guideY, paint);
        }

        paint.setColor(Color.parseColor("#8E8E93"));
        paint.setTextSize(12f);
        paint.setFakeBoldText(true);
        canvas.drawText("VELOCITY", 14f, gridBottom + 20f, paint);

        for (ClipItem.Note note : clip.getNotes()) {
            float vx = keyWidth + (note.startOffsetTicks * pixelsPerTick) - scrollX;
            if (vx < keyWidth || vx > width) continue;

            float stalkHeight = note.velocity * maxStalkH;
            float vy = height - 8f - stalkHeight;

            int stalkColor = getVelocityHeatmapColor(note.velocity, note.isSelected, note.isMuted);

            paint.setColor(stalkColor);
            paint.setStrokeWidth(4f);
            canvas.drawLine(vx, height - 8f, vx, vy, paint);

            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(vx, vy, (activeVelocityDragNote == note) ? 7f : 5f, paint);

            paint.setColor(Color.parseColor("#C7C7CC"));
            paint.setTextSize(10f);
            paint.setFakeBoldText(false);
            canvas.drawText(String.valueOf(note.getMidiVelocity()), vx - 7f, vy - 6f, paint);
        }

        if (isDrawingVelocityRamp) {
            canvas.drawPath(velocityRampPath, rampPaint);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.parseColor("#DD000000"));
            rectF.set(lastVelocityTouchX - 50f, lastVelocityTouchY - 40f, lastVelocityTouchX + 50f, lastVelocityTouchY - 10f);
            canvas.drawRoundRect(rectF, 6f, 6f, paint);

            paint.setColor(Color.parseColor("#FFD60A"));
            paint.setTextSize(12f);
            paint.setFakeBoldText(true);
            int midiVal = Math.round(currentHoverVelocity * 127f);
            canvas.drawText(String.format("Vel: %d", midiVal), lastVelocityTouchX - 24f, lastVelocityTouchY - 20f, paint);
        }
    }

    private boolean isBlackKey(int midiNote) {
        int pitch = midiNote % 12;
        return (pitch == 1 || pitch == 3 || pitch == 6 || pitch == 8 || pitch == 10);
    }

    private String getNoteLabel(int midiNote) {
        int octave = (midiNote / 12) - 1;
        return NOTE_NAMES[midiNote % 12] + octave;
    }

    private ClipItem.Note findNoteAt(float x, float y) {
        for (ClipItem.Note note : clip.getNotes()) {
            if (isScaleFolded && !scale.isNoteInScale(note.note, rootKey)) continue;

            int noteIndex = getRowIndexForMidiNote(note.note);
            float nx = keyWidth + (note.startOffsetTicks * pixelsPerTick) - scrollX;
            float nw = Math.max(12f, note.lengthTicks * pixelsPerTick);
            float ny = rulerHeaderHeight + (noteIndex * noteRowHeight) - scrollY;

            if (x >= nx && x <= nx + nw && y >= ny && y <= ny + noteRowHeight) {
                return note;
            }
        }
        return null;
    }

    private void updateMarqueeSelection() {
        if (clip == null) return;
        for (ClipItem.Note note : clip.getNotes()) {
            int noteIndex = getRowIndexForMidiNote(note.note);
            float nx = keyWidth + (note.startOffsetTicks * pixelsPerTick) - scrollX;
            float nw = Math.max(12f, note.lengthTicks * pixelsPerTick);
            float ny = rulerHeaderHeight + (noteIndex * noteRowHeight) + 3f - scrollY;
            float nh = noteRowHeight - 6f;

            rectF.set(nx, ny, nx + nw, ny + nh);
            note.isSelected = RectF.intersects(marqueeRect, rectF);
        }
    }

    private void applyVelocityAtPoint(float touchX, float touchY) {
        float maxStalkH = velocityLaneHeight - 40f;
        float newVel = (getHeight() - 8f - touchY) / maxStalkH;
        newVel = Math.max(0.05f, Math.min(1.0f, newVel));
        currentHoverVelocity = newVel;

        if (activeVelocityDragNote != null) {
            activeVelocityDragNote.velocity = newVel;
            hasModifiedNotesInGesture = true;
            if (listener != null) listener.onNotesChanged();
            return;
        }

        long touchTick = (long) ((touchX - keyWidth + scrollX) / pixelsPerTick);
        long searchWindow = (long) (22f / pixelsPerTick);

        boolean changed = false;
        for (ClipItem.Note n : clip.getNotes()) {
            if (Math.abs(n.startOffsetTicks - touchTick) <= searchWindow) {
                n.velocity = newVel;
                changed = true;
            }
        }
        if (changed) {
            hasModifiedNotesInGesture = true;
            if (listener != null) listener.onNotesChanged();
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final int pointerCount = event.getPointerCount();
        final float gridBottom = getHeight() - velocityLaneHeight;

        // 1. Two-Finger Pan Navigation (Cancels accidental single-finger note drawing)
        if (pointerCount >= 2) {
            float midX = (event.getX(0) + event.getX(1)) / 2f;
            float midY = (event.getY(0) + event.getY(1)) / 2f;

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_POINTER_DOWN:
                    isTwoFingerPanning = true;
                    lastPanMidX = midX;
                    lastPanMidY = midY;
                    stopAudition();

                    // Instantly roll back any note created by finger 1 prior to finger 2 landing
                    if (isCreatingNewNote && pendingNewNote != null && clip != null) {
                        clip.removeNote(pendingNewNote);
                        pendingNewNote = null;
                        isCreatingNewNote = false;
                        hasModifiedNotesInGesture = false;
                        gestureStartSnapshot = null;
                    }

                    isDrawingVelocityRamp = false;
                    activeVelocityDragNote = null;
                    isDraggingLoopStart = false;
                    isDraggingLoopEnd = false;
                    dragAnchors.clear();
                    primaryDragNote = null;
                    isResizingRight = false;
                    isResizingLeft = false;
                    isMovingNoteBody = false;
                    isMarqueeActive = false;
                    invalidate();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    if (isTwoFingerPanning) {
                        float dx = midX - lastPanMidX;
                        float dy = midY - lastPanMidY;

                        scrollX = Math.max(0, scrollX - dx);
                        float maxScrollY = Math.max(0, (getActiveMidiNotes().size() * noteRowHeight) - gridBottom);
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

        // 2. Single-Touch Processing
        final float x = event.getX();
        final float y = event.getY();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                isTwoFingerPanning = false;
                dragStartRawX = x;
                dragStartRawY = y;
                hasModifiedNotesInGesture = false;
                lastBrushTick = -1;
                lastBrushPitch = -1;
                activeVelocityDragNote = null;
                pendingNewNote = null;
                isMovingNoteBody = false;
                isCreatingNewNote = false;

                if (clip != null) {
                    gestureStartSnapshot = clip.cloneNotesList();
                }

                // 2a. Loop Markers
                if (y <= rulerHeaderHeight && x > keyWidth) {
                    float loopX1 = keyWidth + (loopStartTick * pixelsPerTick) - scrollX;
                    float loopX2 = keyWidth + (loopEndTick * pixelsPerTick) - scrollX;

                    if (isLoopEnabled && Math.abs(x - loopX1) <= 24f) {
                        isDraggingLoopStart = true;
                        return true;
                    } else if (isLoopEnabled && Math.abs(x - loopX2) <= 24f) {
                        isDraggingLoopEnd = true;
                        return true;
                    }
                }

                // 2b. Keybed Audition
                if (x <= keyWidth && y < gridBottom) {
                    int midiNote = getMidiNoteFromY(y);
                    playAudition(midiNote, 0.9f);
                    return true;
                }

                // 2c. Velocity Lane Stalk Grab & Ramp Swipe
                if (y >= gridBottom && x > keyWidth) {
                    float maxStalk = velocityLaneHeight - 40f;
                    for (ClipItem.Note n : clip.getNotes()) {
                        float vx = keyWidth + (n.startOffsetTicks * pixelsPerTick) - scrollX;
                        float vy = getHeight() - 8f - (n.velocity * maxStalk);
                        if (Math.abs(x - vx) <= 16f && Math.abs(y - vy) <= 20f) {
                            activeVelocityDragNote = n;
                            break;
                        }
                    }

                    isDrawingVelocityRamp = true;
                    lastVelocityTouchX = x;
                    lastVelocityTouchY = y;
                    velocityRampPath.reset();
                    velocityRampPath.moveTo(x, y);
                    applyVelocityAtPoint(x, y);
                    invalidate();
                    return true;
                }

                // 2d. Timeline Note Canvas
                if (x > keyWidth && y > rulerHeaderHeight && y < gridBottom) {
                    ClipItem.Note hit = findNoteAt(x, y);
                    int midiNote = getMidiNoteFromY(y);
                    long touchTick = snapGrid.snap((long) ((x - keyWidth + scrollX) / pixelsPerTick));

                    if (toolMode == ToolMode.ERASER) {
                        if (hit != null) {
                            if (hit.isSelected) {
                                clip.deleteSelected();
                            } else {
                                clip.removeNote(hit);
                            }
                            hasModifiedNotesInGesture = true;
                            if (listener != null) {
                                listener.onNotesChanged();
                                if (gestureStartSnapshot != null) listener.onTransactionCommitted(gestureStartSnapshot);
                            }
                            invalidate();
                            return true;
                        }
                    } else if (toolMode == ToolMode.SPLIT) {
                        if (hit != null) {
                            if (clip.splitNoteAt(hit, touchTick)) {
                                hasModifiedNotesInGesture = true;
                                if (listener != null) {
                                    listener.onNotesChanged();
                                    if (gestureStartSnapshot != null) listener.onTransactionCommitted(gestureStartSnapshot);
                                }
                                invalidate();
                                return true;
                            }
                        }
                    } else if (toolMode == ToolMode.GLUE) {
                        int glued = clip.glue();
                        if (glued > 0) {
                            hasModifiedNotesInGesture = true;
                            if (listener != null) {
                                listener.onNotesChanged();
                                if (gestureStartSnapshot != null) listener.onTransactionCommitted(gestureStartSnapshot);
                            }
                            invalidate();
                            return true;
                        }
                    } else if (toolMode == ToolMode.CHOP) {
                        int chopped = clip.chop(snapGrid.getTicks());
                        if (chopped > 0) {
                            hasModifiedNotesInGesture = true;
                            if (listener != null) {
                                listener.onNotesChanged();
                                if (gestureStartSnapshot != null) listener.onTransactionCommitted(gestureStartSnapshot);
                            }
                            invalidate();
                            return true;
                        }
                    } else if (toolMode == ToolMode.BRUSH) {
                        long len = Math.max(60, snapGrid.getTicks());
                        clip.addNote(midiNote, 0.85f, touchTick, len);
                        lastBrushTick = touchTick;
                        lastBrushPitch = midiNote;
                        hasModifiedNotesInGesture = true;
                        playAudition(midiNote, 0.85f);
                        if (listener != null) listener.onNotesChanged();
                        invalidate();
                        return true;
                    } else if (toolMode == ToolMode.PENCIL) {
                        if (activeChordIntervals != null && hit == null) {
                            long chordLen = Math.max(snapGrid.getTicks() * 2, PPQ);
                            clip.stampChord(midiNote, activeChordIntervals, touchTick, chordLen, 0.85f);
                            hasModifiedNotesInGesture = true;
                            playAudition(midiNote, 0.85f);
                            if (listener != null) {
                                listener.onNotesChanged();
                                if (gestureStartSnapshot != null) listener.onTransactionCommitted(gestureStartSnapshot);
                            }
                            invalidate();
                            return true;
                        }

                        if (hit != null) {
                            // Hit an existing note in Pencil Mode: Differentiate Body Move vs Edge Resize
                            primaryDragNote = hit;
                            dragAnchors.clear();
                            dragAnchors.add(new NoteAnchor(hit));
                            isCreatingNewNote = false;

                            float noteLeft = keyWidth + (hit.startOffsetTicks * pixelsPerTick) - scrollX;
                            float noteRight = keyWidth + (hit.getEndOffsetTicks() * pixelsPerTick) - scrollX;
                            float noteWidthPx = hit.lengthTicks * pixelsPerTick;
                            float handleMargin = Math.min(24f, Math.max(12f, noteWidthPx * 0.28f));

                            isResizingLeft = (Math.abs(x - noteLeft) <= handleMargin);
                            isResizingRight = !isResizingLeft && (Math.abs(x - noteRight) <= handleMargin);
                            isMovingNoteBody = !isResizingLeft && !isResizingRight;

                            playAudition(hit.note, hit.velocity);
                            invalidate();
                            return true;
                        } else {
                            // Tapped empty canvas: Create new note using remembered length
                            clip.selectAll(false);
                            long defaultLen = lastDrawnNoteLengthTicks > 0 ? lastDrawnNoteLengthTicks : Math.max(snapGrid.getTicks(), PPQ);
                            ClipItem.Note newNote = new ClipItem.Note(midiNote, 0.85f, touchTick, defaultLen);
                            newNote.isSelected = true;

                            clip.addNote(midiNote, 0.85f, touchTick, defaultLen);

                            pendingNewNote = newNote;
                            primaryDragNote = newNote;
                            dragAnchors.clear();
                            dragAnchors.add(new NoteAnchor(newNote));

                            isCreatingNewNote = true;
                            isResizingRight = true;  // Immediate horizontal drag right extends note
                            isResizingLeft = false;
                            isMovingNoteBody = false;
                            hasModifiedNotesInGesture = true;

                            playAudition(midiNote, 0.85f);
                            if (listener != null) listener.onNotesChanged();
                            invalidate();
                            return true;
                        }
                    } else if (toolMode == ToolMode.SELECT) {
                        if (hit != null) {
                            if (!hit.isSelected) {
                                clip.selectAll(false);
                                hit.isSelected = true;
                            }

                            primaryDragNote = hit;
                            dragAnchors.clear();
                            for (ClipItem.Note n : clip.getSelectedNotes()) {
                                dragAnchors.add(new NoteAnchor(n));
                            }
                            isCreatingNewNote = false;

                            float noteLeft = keyWidth + (hit.startOffsetTicks * pixelsPerTick) - scrollX;
                            float noteRight = keyWidth + (hit.getEndOffsetTicks() * pixelsPerTick) - scrollX;
                            float noteWidthPx = hit.lengthTicks * pixelsPerTick;
                            float handleMargin = Math.min(24f, noteWidthPx * 0.35f);

                            isResizingLeft = (Math.abs(x - noteLeft) <= handleMargin);
                            isResizingRight = !isResizingLeft && (Math.abs(x - noteRight) <= handleMargin);
                            isMovingNoteBody = !isResizingLeft && !isResizingRight;

                            playAudition(hit.note, hit.velocity);
                            invalidate();
                            return true;
                        } else {
                            isMarqueeActive = true;
                            marqueeStartX = x;
                            marqueeStartY = y;
                            marqueeCurrentX = x;
                            marqueeCurrentY = y;
                            marqueeRect.set(x, y, x, y);
                            clip.selectAll(false);
                            invalidate();
                            return true;
                        }
                    }
                }
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                if (isDraggingLoopStart) {
                    long snapped = snapGrid.snap((long) ((x - keyWidth + scrollX) / pixelsPerTick));
                    loopStartTick = Math.max(0, Math.min(loopEndTick - snapGrid.getTicks(), snapped));
                    if (listener != null) listener.onLoopRangeChanged(loopStartTick, loopEndTick, isLoopEnabled);
                    invalidate();
                    return true;
                }

                if (isDraggingLoopEnd) {
                    long snapped = snapGrid.snap((long) ((x - keyWidth + scrollX) / pixelsPerTick));
                    loopEndTick = Math.max(loopStartTick + snapGrid.getTicks(), snapped);
                    if (listener != null) listener.onLoopRangeChanged(loopStartTick, loopEndTick, isLoopEnabled);
                    invalidate();
                    return true;
                }

                if (x <= keyWidth && y < gridBottom) {
                    int midiNote = getMidiNoteFromY(y);
                    playAudition(midiNote, 0.9f);
                    return true;
                }

                if (isDrawingVelocityRamp) {
                    lastVelocityTouchX = x;
                    lastVelocityTouchY = y;
                    velocityRampPath.lineTo(x, y);
                    applyVelocityAtPoint(x, y);
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

                if (toolMode == ToolMode.BRUSH && x > keyWidth && y > rulerHeaderHeight && y < gridBottom) {
                    long touchTick = snapGrid.snap((long) ((x - keyWidth + scrollX) / pixelsPerTick));
                    int midiNote = getMidiNoteFromY(y);

                    if (touchTick != lastBrushTick || midiNote != lastBrushPitch) {
                        long len = Math.max(60, snapGrid.getTicks());
                        clip.addNote(midiNote, 0.85f, touchTick, len);
                        lastBrushTick = touchTick;
                        lastBrushPitch = midiNote;
                        hasModifiedNotesInGesture = true;
                        playAudition(midiNote, 0.85f);
                        if (listener != null) listener.onNotesChanged();
                        invalidate();
                    }
                    return true;
                }

                if (!dragAnchors.isEmpty() && toolMode != ToolMode.ERASER) {
                    float totalDeltaX = x - dragStartRawX;
                    float totalDeltaY = y - dragStartRawY;

                    if (isResizingRight) {
                        long totalDeltaTicks = (long) (totalDeltaX / pixelsPerTick);
                        for (NoteAnchor anchor : dragAnchors) {
                            long newLength = snapGrid.snap(anchor.initialLength + totalDeltaTicks);
                            anchor.note.lengthTicks = Math.max(Math.max(10, snapGrid.getTicks()), newLength);
                            lastDrawnNoteLengthTicks = anchor.note.lengthTicks;
                        }
                        hasModifiedNotesInGesture = true;
                    } else if (isResizingLeft) {
                        long deltaTicks = snapGrid.snap((long) (totalDeltaX / pixelsPerTick));
                        for (NoteAnchor anchor : dragAnchors) {
                            long newStart = Math.max(0, anchor.initialOffset + deltaTicks);
                            long actualShift = newStart - anchor.initialOffset;
                            long newLen = anchor.initialLength - actualShift;
                            if (newLen >= Math.max(10, snapGrid.getTicks())) {
                                anchor.note.startOffsetTicks = newStart;
                                anchor.note.lengthTicks = newLen;
                                lastDrawnNoteLengthTicks = newLen;
                                hasModifiedNotesInGesture = true;
                            }
                        }
                    } else if (isMovingNoteBody || toolMode == ToolMode.SELECT) {
                        long totalDeltaTicks = (long) (totalDeltaX / pixelsPerTick);
                        int deltaPitchSteps = (int) ((dragStartRawY - y) / noteRowHeight);

                        int allowedDeltaPitch = deltaPitchSteps;
                        for (NoteAnchor anchor : dragAnchors) {
                            int targetPitch = anchor.initialPitch + allowedDeltaPitch;
                            if (targetPitch < MIN_MIDI_NOTE) allowedDeltaPitch = MIN_MIDI_NOTE - anchor.initialPitch;
                            else if (targetPitch > MAX_MIDI_NOTE) allowedDeltaPitch = MAX_MIDI_NOTE - anchor.initialPitch;
                        }

                        for (NoteAnchor anchor : dragAnchors) {
                            long newOffset = snapGrid.snap(anchor.initialOffset + totalDeltaTicks);
                            anchor.note.startOffsetTicks = Math.max(0, newOffset);
                            int targetPitch = anchor.initialPitch + allowedDeltaPitch;
                            anchor.note.note = snapPitchToScale(Math.max(MIN_MIDI_NOTE, Math.min(MAX_MIDI_NOTE, targetPitch)));
                        }
                        hasModifiedNotesInGesture = true;

                        if (primaryDragNote != null && primaryDragNote.note != activeAuditionPitch) {
                            playAudition(primaryDragNote.note, primaryDragNote.velocity);
                        }
                    }
                    if (listener != null) listener.onNotesChanged();
                    invalidate();
                    return true;
                }
                break;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                stopAudition();
                isMarqueeActive = false;
                isDrawingVelocityRamp = false;
                activeVelocityDragNote = null;
                velocityRampPath.reset();

                if (isDraggingLoopStart || isDraggingLoopEnd) {
                    if (listener != null) listener.onLoopRangeChanged(loopStartTick, loopEndTick, isLoopEnabled);
                    isDraggingLoopStart = false;
                    isDraggingLoopEnd = false;
                }

                if (hasModifiedNotesInGesture && gestureStartSnapshot != null && listener != null) {
                    listener.onTransactionCommitted(gestureStartSnapshot);
                }

                if (pendingNewNote != null) {
                    lastDrawnNoteLengthTicks = pendingNewNote.lengthTicks;
                    pendingNewNote = null;
                }

                if (!dragAnchors.isEmpty() && listener != null) {
                    listener.onNotesChanged();
                }

                dragAnchors.clear();
                primaryDragNote = null;
                isCreatingNewNote = false;
                isResizingRight = false;
                isResizingLeft = false;
                isMovingNoteBody = false;
                hasModifiedNotesInGesture = false;
                gestureStartSnapshot = null;
                lastBrushTick = -1;
                lastBrushPitch = -1;
                invalidate();
                break;
            }
        }
        return true;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopAudition();
    }
}
