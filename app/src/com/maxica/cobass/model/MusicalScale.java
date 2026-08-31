package com.maxica.cobass.model;

public enum MusicalScale {
    CHROMATIC("Chromatic (All Notes)", new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11}),
    MAJOR("Major (Ionian)", new int[]{0, 2, 4, 5, 7, 9, 11}),
    NATURAL_MINOR("Natural Minor (Aeolian)", new int[]{0, 2, 3, 5, 7, 8, 10}),
    DORIAN("Dorian Minor", new int[]{0, 2, 3, 5, 7, 9, 10}),
    MINOR_PENTATONIC("Minor Pentatonic", new int[]{0, 3, 5, 7, 10}),
    MAJOR_PENTATONIC("Major Pentatonic", new int[]{0, 2, 4, 7, 9});

    private final String label;
    private final int[] intervals;

    MusicalScale(String label, int[] intervals) {
        this.label = label;
        this.intervals = intervals;
    }

    public String getLabel() { return label; }

    public boolean isNoteInScale(int midiNote, int rootKey) {
        if (this == CHROMATIC) return true;
        int notePitch = (midiNote - rootKey) % 12;
        if (notePitch < 0) notePitch += 12;
        for (int interval : intervals) {
            if (notePitch == interval) return true;
        }
        return false;
    }
}
