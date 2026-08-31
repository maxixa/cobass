package com.maxica.cobass.model;

import java.util.ArrayList;
import java.util.Collections;
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
