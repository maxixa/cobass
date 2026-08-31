package com.maxica.cobass.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ClipItem {
    public static class Note {
        public int note;
        public float velocity;
        public long startOffsetTicks;
        public long lengthTicks;
        public boolean isSelected = false;
        public boolean isMuted = false;

        public Note(int note, float velocity, long startOffsetTicks, long lengthTicks) {
            this.note = Math.max(0, Math.min(127, note));
            this.velocity = Math.max(0.05f, Math.min(1.0f, velocity));
            this.startOffsetTicks = Math.max(0, startOffsetTicks);
            this.lengthTicks = Math.max(1, lengthTicks);
        }

        public Note(int note, float velocity, long startOffsetTicks, long lengthTicks, boolean isMuted) {
            this.note = Math.max(0, Math.min(127, note));
            this.velocity = Math.max(0.05f, Math.min(1.0f, velocity));
            this.startOffsetTicks = Math.max(0, startOffsetTicks);
            this.lengthTicks = Math.max(1, lengthTicks);
            this.isMuted = isMuted;
        }

        public long getEndOffsetTicks() { return startOffsetTicks + lengthTicks; }
        public int getMidiVelocity() { return Math.max(1, Math.min(127, Math.round(velocity * 127f))); }

        public Note copy() {
            Note clone = new Note(note, velocity, startOffsetTicks, lengthTicks, isMuted);
            clone.isSelected = isSelected;
            return clone;
        }
    }

    private int id;
    private int trackId;
    private long startTick;
    private long lengthTicks;
    private String name;
    private int color;
    private final TrackItem.Type type;
    private final List<Note> notes = new ArrayList<>();
    private float[] sampleData = null;
    private final List<Float> sliceFractions = new ArrayList<>();

    private boolean isSelected = false;
    private boolean isMuted = false;

    public ClipItem(int id, int trackId, long startTick, long lengthTicks, String name, int color, TrackItem.Type type) {
        this.id = id;
        this.trackId = trackId;
        this.startTick = startTick;
        this.lengthTicks = lengthTicks;
        this.name = name;
        this.color = color;
        this.type = type;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getTrackId() { return trackId; }
    public void setTrackId(int trackId) { this.trackId = trackId; }

    public long getStartTick() { return startTick; }
    public void setStartTick(long startTick) { this.startTick = startTick; }

    public long getLengthTicks() { return lengthTicks; }
    public void setLengthTicks(long lengthTicks) { this.lengthTicks = lengthTicks; }
    public long getEndTick() { return startTick + lengthTicks; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getColor() { return color; }
    public void setColor(int color) { this.color = color; }

    public TrackItem.Type getType() { return type; }

    public boolean isSelected() { return isSelected; }
    public void setSelected(boolean selected) { this.isSelected = selected; }

    public boolean isMuted() { return isMuted; }
    public void setMuted(boolean muted) { this.isMuted = muted; }

    public List<Note> getNotes() { return notes; }

    public ClipItem copy() {
        ClipItem clone = new ClipItem(id, trackId, startTick, lengthTicks, name, color, type);
        clone.isSelected = this.isSelected;
        clone.isMuted = this.isMuted;
        for (Note n : this.notes) {
            clone.notes.add(n.copy());
        }
        if (this.sampleData != null) {
            clone.sampleData = this.sampleData.clone();
        }
        clone.sliceFractions.addAll(this.sliceFractions);
        return clone;
    }

    // --- NOTE LEVEL HELPERS ---
    public List<Note> cloneNotesList() {
        List<Note> copy = new ArrayList<>(notes.size());
        for (Note n : notes) {
            copy.add(n.copy());
        }
        return copy;
    }

    public void restoreNotesList(List<Note> snapshot) {
        notes.clear();
        if (snapshot != null) {
            for (Note n : snapshot) {
                notes.add(n.copy());
            }
        }
    }

    public void addNote(Note note) {
        if (note != null) {
            notes.add(note);
        }
    }

    public void addNote(int note, float vel, long offset, long len) {
        notes.add(new Note(note, vel, offset, len));
    }

    public void addNote(int note, float vel, long offset, long len, boolean muted) {
        notes.add(new Note(note, vel, offset, len, muted));
    }

    public void removeNote(Note note) { notes.remove(note); }
    public void clearNotes() { notes.clear(); }

    public List<Note> getSelectedNotes() {
        List<Note> selected = new ArrayList<>();
        for (Note n : notes) {
            if (n.isSelected) selected.add(n);
        }
        return selected;
    }

    public void selectAll(boolean select) {
        for (Note n : notes) n.isSelected = select;
    }

    public void deleteSelected() {
        notes.removeIf(n -> n.isSelected);
    }

    public void toggleMuteSelected() {
        List<Note> selected = getSelectedNotes();
        if (selected.isEmpty()) return;
        boolean anyUnmuted = false;
        for (Note n : selected) {
            if (!n.isMuted) {
                anyUnmuted = true;
                break;
            }
        }
        for (Note n : selected) {
            n.isMuted = anyUnmuted;
        }
    }

    public List<Note> duplicateSelected(long snapTicks) {
        List<Note> selected = getSelectedNotes();
        if (selected.isEmpty()) return new ArrayList<>();

        long minOffset = Long.MAX_VALUE;
        long maxEndOffset = Long.MIN_VALUE;
        for (Note n : selected) {
            minOffset = Math.min(minOffset, n.startOffsetTicks);
            maxEndOffset = Math.max(maxEndOffset, n.getEndOffsetTicks());
        }

        long selectionDuration = maxEndOffset - minOffset;
        long shiftTicks = snapTicks > 0 ? (((selectionDuration + snapTicks - 1) / snapTicks) * snapTicks) : selectionDuration;
        if (shiftTicks <= 0) shiftTicks = 480;

        selectAll(false);
        List<Note> newDuplicates = new ArrayList<>();
        for (Note n : selected) {
            Note duplicate = n.copy();
            duplicate.startOffsetTicks += shiftTicks;
            duplicate.isSelected = true;
            newDuplicates.add(duplicate);
            notes.add(duplicate);
        }
        return newDuplicates;
    }

    public void transpose(int semitones) {
        List<Note> targetNotes = getSelectedNotes();
        if (targetNotes.isEmpty()) targetNotes = notes;
        for (Note n : targetNotes) {
            n.note = Math.max(0, Math.min(127, n.note + semitones));
        }
    }

    public boolean splitNoteAt(Note target, long splitOffsetTicks) {
        if (target == null) return false;
        if (splitOffsetTicks <= target.startOffsetTicks || splitOffsetTicks >= target.getEndOffsetTicks()) {
            return false;
        }

        long originalEnd = target.getEndOffsetTicks();
        long firstLen = splitOffsetTicks - target.startOffsetTicks;
        long secondLen = originalEnd - splitOffsetTicks;

        target.lengthTicks = Math.max(1, firstLen);
        Note secondPart = new Note(target.note, target.velocity, splitOffsetTicks, Math.max(1, secondLen), target.isMuted);
        secondPart.isSelected = target.isSelected;
        notes.add(secondPart);
        return true;
    }

    public int chop(int subdivisionTicks) {
        if (subdivisionTicks <= 0) return 0;
        List<Note> targets = getSelectedNotes().isEmpty() ? new ArrayList<>(notes) : getSelectedNotes();
        if (targets.isEmpty()) return 0;

        List<Note> chopped = new ArrayList<>();
        for (Note n : targets) {
            long offset = n.startOffsetTicks;
            long end = n.getEndOffsetTicks();
            long noteDuration = end - offset;

            if (noteDuration <= subdivisionTicks) {
                chopped.add(n);
                continue;
            }

            while (offset + subdivisionTicks <= end) {
                long chunkLen = Math.max(10, subdivisionTicks - 2);
                Note chunk = new Note(n.note, n.velocity, offset, chunkLen, n.isMuted);
                chunk.isSelected = n.isSelected;
                chopped.add(chunk);
                offset += subdivisionTicks;
            }

            if (offset < end) {
                Note remainder = new Note(n.note, n.velocity, offset, end - offset, n.isMuted);
                remainder.isSelected = n.isSelected;
                chopped.add(remainder);
            }
        }

        notes.removeAll(targets);
        notes.addAll(chopped);
        return chopped.size();
    }

    public int glue() {
        List<Note> targets = getSelectedNotes().isEmpty() ? new ArrayList<>(notes) : getSelectedNotes();
        if (targets.size() <= 1) return 0;

        targets.sort((a, b) -> {
            if (a.note != b.note) return Integer.compare(a.note, b.note);
            return Long.compare(a.startOffsetTicks, b.startOffsetTicks);
        });

        int mergedCount = 0;
        List<Note> toRemove = new ArrayList<>();

        for (int i = 0; i < targets.size() - 1; i++) {
            Note curr = targets.get(i);
            Note next = targets.get(i + 1);

            if (curr.note == next.note && !toRemove.contains(curr)) {
                long gap = next.startOffsetTicks - curr.getEndOffsetTicks();
                if (gap >= -40 && gap <= 60) {
                    curr.lengthTicks = (next.startOffsetTicks + next.lengthTicks) - curr.startOffsetTicks;
                    toRemove.add(next);
                    mergedCount++;
                }
            }
        }

        notes.removeAll(toRemove);
        return mergedCount;
    }

    public void stampChord(int rootMidi, int[] chordIntervals, long startOffsetTicks, long lengthTicks, float velocity) {
        if (chordIntervals == null || chordIntervals.length == 0) return;
        selectAll(false);
        for (int interval : chordIntervals) {
            int pitch = Math.max(0, Math.min(127, rootMidi + interval));
            Note chordNote = new Note(pitch, velocity, startOffsetTicks, lengthTicks);
            chordNote.isSelected = true;
            notes.add(chordNote);
        }
    }

    public int legato() {
        List<Note> targets = getSelectedNotes();
        if (targets.isEmpty()) targets = notes;
        if (targets.size() <= 1) return 0;

        List<Note> sorted = new ArrayList<>(targets);
        sorted.sort(Comparator.comparingLong(a -> a.startOffsetTicks));

        int applied = 0;
        for (int i = 0; i < sorted.size() - 1; i++) {
            Note current = sorted.get(i);
            Note next = sorted.get(i + 1);
            if (next.startOffsetTicks > current.startOffsetTicks) {
                long newLen = next.startOffsetTicks - current.startOffsetTicks;
                current.lengthTicks = Math.max(60, newLen);
                applied++;
            }
        }
        return applied;
    }

    public int humanize(long maxTimingTicks, float maxVelDelta) {
        List<Note> targets = getSelectedNotes();
        if (targets.isEmpty()) targets = notes;
        for (Note n : targets) {
            long timeJitter = (long) ((Math.random() * 2.0 - 1.0) * maxTimingTicks);
            n.startOffsetTicks = Math.max(0, n.startOffsetTicks + timeJitter);

            float velJitter = (float) ((Math.random() * 2.0 - 1.0) * maxVelDelta);
            n.velocity = Math.max(0.05f, Math.min(1.0f, n.velocity + velJitter));
        }
        return targets.size();
    }

    public int strum(long strumDelayTicks, boolean ascendingPitch) {
        List<Note> targets = getSelectedNotes();
        if (targets.isEmpty()) targets = notes;
        if (targets.size() <= 1) return 0;

        List<Note> sorted = new ArrayList<>(targets);
        sorted.sort(Comparator.comparingLong(a -> a.startOffsetTicks));

        List<List<Note>> chords = new ArrayList<>();
        List<Note> currentChord = new ArrayList<>();

        for (Note n : sorted) {
            if (currentChord.isEmpty()) {
                currentChord.add(n);
            } else {
                long firstOffset = currentChord.get(0).startOffsetTicks;
                if (Math.abs(n.startOffsetTicks - firstOffset) <= 40) {
                    currentChord.add(n);
                } else {
                    chords.add(currentChord);
                    currentChord = new ArrayList<>();
                    currentChord.add(n);
                }
            }
        }
        if (!currentChord.isEmpty()) chords.add(currentChord);

        int count = 0;
        for (List<Note> chord : chords) {
            if (chord.size() <= 1) continue;
            if (ascendingPitch) {
                chord.sort(Comparator.comparingInt(a -> a.note));
            } else {
                chord.sort((a, b) -> Integer.compare(b.note, a.note));
            }
            for (int i = 0; i < chord.size(); i++) {
                chord.get(i).startOffsetTicks += (i * strumDelayTicks);
                count++;
            }
        }
        return count;
    }

    public void quantizeAdvanced(long snapTicks, float strength, float swingPercent, boolean quantizeStart, boolean quantizeLength) {
        if (snapTicks <= 1) return;
        List<Note> targets = getSelectedNotes();
        if (targets.isEmpty()) targets = notes;

        float clampedStrength = Math.max(0.0f, Math.min(1.0f, strength));

        for (Note n : targets) {
            if (quantizeStart) {
                long gridIndex = (n.startOffsetTicks + (snapTicks / 2)) / snapTicks;
                long targetStart = gridIndex * snapTicks;

                if (swingPercent > 0.0f && (gridIndex % 2 == 1)) {
                    long swingShift = (long) (snapTicks * (swingPercent / 100.0f) * 0.333f);
                    targetStart += swingShift;
                }

                long deltaStart = targetStart - n.startOffsetTicks;
                n.startOffsetTicks = Math.max(0, n.startOffsetTicks + (long) (deltaStart * clampedStrength));
            }

            if (quantizeLength) {
                long targetLen = Math.max(snapTicks, ((n.lengthTicks + (snapTicks / 2)) / snapTicks) * snapTicks);
                long deltaLen = targetLen - n.lengthTicks;
                n.lengthTicks = Math.max(snapTicks / 4, n.lengthTicks + (long) (deltaLen * clampedStrength));
            }
        }
    }

    public void quantizeAdvanced(long snapTicks, float strength) {
        quantizeAdvanced(snapTicks, strength, 0.0f, true, true);
    }

    public void applyCrescendo(float startVel, float endVel) {
        List<Note> targets = getSelectedNotes();
        if (targets.isEmpty()) targets = notes;
        if (targets.isEmpty()) return;

        List<Note> sorted = new ArrayList<>(targets);
        sorted.sort(Comparator.comparingLong(a -> a.startOffsetTicks));

        int total = sorted.size();
        for (int i = 0; i < total; i++) {
            float progress = total > 1 ? (float) i / (total - 1) : 0.5f;
            float targetVel = startVel + progress * (endVel - startVel);
            sorted.get(i).velocity = Math.max(0.05f, Math.min(1.0f, targetVel));
        }
    }

    public void compressVelocities(float minVel, float maxVel) {
        List<Note> targets = getSelectedNotes().isEmpty() ? notes : getSelectedNotes();
        if (targets.isEmpty()) return;

        float actualMin = 1.0f;
        float actualMax = 0.0f;
        for (Note n : targets) {
            if (n.velocity < actualMin) actualMin = n.velocity;
            if (n.velocity > actualMax) actualMax = n.velocity;
        }

        float spread = actualMax - actualMin;
        float targetSpread = maxVel - minVel;

        for (Note n : targets) {
            if (spread > 0.001f) {
                float normalized = (n.velocity - actualMin) / spread;
                n.velocity = Math.max(0.05f, Math.min(1.0f, minVel + (normalized * targetSpread)));
            } else {
                n.velocity = Math.max(0.05f, Math.min(1.0f, (minVel + maxVel) / 2.0f));
            }
        }
    }

    public void scaleVelocities(float factor) {
        List<Note> targets = getSelectedNotes().isEmpty() ? notes : getSelectedNotes();
        for (Note n : targets) {
            n.velocity = Math.max(0.05f, Math.min(1.0f, n.velocity * factor));
        }
    }

    public void randomizeVelocities(float amount) {
        List<Note> targets = getSelectedNotes().isEmpty() ? notes : getSelectedNotes();
        for (Note n : targets) {
            float delta = (float) ((Math.random() * 2.0 - 1.0) * amount);
            n.velocity = Math.max(0.05f, Math.min(1.0f, n.velocity + delta));
        }
    }

    public void invertVelocities() {
        List<Note> targets = getSelectedNotes().isEmpty() ? notes : getSelectedNotes();
        for (Note n : targets) {
            n.velocity = Math.max(0.05f, Math.min(1.0f, 1.05f - n.velocity));
        }
    }

    public void setAllVelocities(float targetVel) {
        List<Note> targets = getSelectedNotes().isEmpty() ? notes : getSelectedNotes();
        float clamped = Math.max(0.05f, Math.min(1.0f, targetVel));
        for (Note n : targets) {
            n.velocity = clamped;
        }
    }

    public float[] getSampleData() { return sampleData; }
    public void setSampleData(float[] sampleData) { this.sampleData = sampleData; }

    public List<Float> getSliceFractions() { return sliceFractions; }
    public void setSliceFractions(List<Float> slices) {
        sliceFractions.clear();
        if (slices != null) sliceFractions.addAll(slices);
    }
    public void addSliceFraction(float fraction) {
        if (!sliceFractions.contains(fraction)) {
            sliceFractions.add(fraction);
            Collections.sort(sliceFractions);
        }
    }
    public void clearSlices() { sliceFractions.clear(); }
}
