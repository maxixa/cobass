package com.maxica.cobass.sequencer;

import com.maxica.cobass.model.ClipItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class MidiTransformEngine {

    private MidiTransformEngine() {}

    public static boolean splitNoteAt(ClipItem clip, ClipItem.Note target, long splitOffsetTicks) {
        if (clip == null || target == null) return false;
        if (splitOffsetTicks <= target.startOffsetTicks || splitOffsetTicks >= target.getEndOffsetTicks()) {
            return false;
        }

        long originalEnd = target.getEndOffsetTicks();
        long firstLen = splitOffsetTicks - target.startOffsetTicks;
        long secondLen = originalEnd - splitOffsetTicks;

        target.lengthTicks = Math.max(1, firstLen);
        ClipItem.Note secondPart = new ClipItem.Note(target.note, target.velocity, splitOffsetTicks, Math.max(1, secondLen), target.isMuted);
        secondPart.isSelected = target.isSelected;
        clip.addNote(secondPart);
        return true;
    }

    public static int chop(ClipItem clip, int subdivisionTicks) {
        if (clip == null || subdivisionTicks <= 0) return 0;
        List<ClipItem.Note> targets = clip.getSelectedNotes().isEmpty() ? new ArrayList<>(clip.getNotes()) : clip.getSelectedNotes();
        if (targets.isEmpty()) return 0;

        List<ClipItem.Note> chopped = new ArrayList<>();
        for (ClipItem.Note n : targets) {
            long offset = n.startOffsetTicks;
            long end = n.getEndOffsetTicks();
            long noteDuration = end - offset;

            if (noteDuration <= subdivisionTicks) {
                chopped.add(n);
                continue;
            }

            while (offset + subdivisionTicks <= end) {
                long chunkLen = Math.max(10, subdivisionTicks - 2);
                ClipItem.Note chunk = new ClipItem.Note(n.note, n.velocity, offset, chunkLen, n.isMuted);
                chunk.isSelected = n.isSelected;
                chopped.add(chunk);
                offset += subdivisionTicks;
            }

            if (offset < end) {
                ClipItem.Note remainder = new ClipItem.Note(n.note, n.velocity, offset, end - offset, n.isMuted);
                remainder.isSelected = n.isSelected;
                chopped.add(remainder);
            }
        }

        for (ClipItem.Note n : targets) {
            clip.removeNote(n);
        }
        for (ClipItem.Note c : chopped) {
            clip.addNote(c);
        }
        return chopped.size();
    }

    public static int glue(ClipItem clip) {
        if (clip == null) return 0;
        List<ClipItem.Note> targets = clip.getSelectedNotes().isEmpty() ? new ArrayList<>(clip.getNotes()) : clip.getSelectedNotes();
        if (targets.size() <= 1) return 0;

        targets.sort((a, b) -> {
            if (a.note != b.note) return Integer.compare(a.note, b.note);
            return Long.compare(a.startOffsetTicks, b.startOffsetTicks);
        });

        int mergedCount = 0;
        List<ClipItem.Note> toRemove = new ArrayList<>();

        for (int i = 0; i < targets.size() - 1; i++) {
            ClipItem.Note curr = targets.get(i);
            ClipItem.Note next = targets.get(i + 1);

            if (curr.note == next.note && !toRemove.contains(curr)) {
                long gap = next.startOffsetTicks - curr.getEndOffsetTicks();
                if (gap >= -40 && gap <= 60) {
                    curr.lengthTicks = (next.startOffsetTicks + next.lengthTicks) - curr.startOffsetTicks;
                    toRemove.add(next);
                    mergedCount++;
                }
            }
        }

        for (ClipItem.Note r : toRemove) {
            clip.removeNote(r);
        }
        return mergedCount;
    }

    public static void stampChord(ClipItem clip, int rootMidi, int[] chordIntervals, long startOffsetTicks, long lengthTicks, float velocity) {
        if (clip == null || chordIntervals == null || chordIntervals.length == 0) return;
        clip.selectAll(false);
        for (int interval : chordIntervals) {
            int pitch = Math.max(0, Math.min(127, rootMidi + interval));
            ClipItem.Note chordNote = new ClipItem.Note(pitch, velocity, startOffsetTicks, lengthTicks);
            chordNote.isSelected = true;
            clip.addNote(chordNote);
        }
    }

    public static List<ClipItem.Note> duplicateSelected(ClipItem clip, long snapTicks) {
        if (clip == null) return Collections.emptyList();
        List<ClipItem.Note> selected = clip.getSelectedNotes();
        if (selected.isEmpty()) return Collections.emptyList();

        long minOffset = Long.MAX_VALUE;
        long maxEndOffset = Long.MIN_VALUE;
        for (ClipItem.Note n : selected) {
            minOffset = Math.min(minOffset, n.startOffsetTicks);
            maxEndOffset = Math.max(maxEndOffset, n.getEndOffsetTicks());
        }

        long selectionDuration = maxEndOffset - minOffset;
        long shiftTicks = snapTicks > 0 ? (((selectionDuration + snapTicks - 1) / snapTicks) * snapTicks) : selectionDuration;
        if (shiftTicks <= 0) shiftTicks = 480;

        clip.selectAll(false);
        List<ClipItem.Note> newDuplicates = new ArrayList<>();
        for (ClipItem.Note n : selected) {
            ClipItem.Note duplicate = n.copy();
            duplicate.startOffsetTicks += shiftTicks;
            duplicate.isSelected = true;
            newDuplicates.add(duplicate);
            clip.addNote(duplicate);
        }
        return newDuplicates;
    }

    public static void transpose(ClipItem clip, int semitones) {
        if (clip == null) return;
        List<ClipItem.Note> targetNotes = clip.getSelectedNotes();
        if (targetNotes.isEmpty()) targetNotes = clip.getNotes();
        for (ClipItem.Note n : targetNotes) {
            n.note = Math.max(0, Math.min(127, n.note + semitones));
        }
    }

    public static int legato(ClipItem clip) {
        if (clip == null) return 0;
        List<ClipItem.Note> targets = clip.getSelectedNotes();
        if (targets.isEmpty()) targets = clip.getNotes();
        if (targets.size() <= 1) return 0;

        List<ClipItem.Note> sorted = new ArrayList<>(targets);
        sorted.sort(Comparator.comparingLong(a -> a.startOffsetTicks));

        int applied = 0;
        for (int i = 0; i < sorted.size() - 1; i++) {
            ClipItem.Note current = sorted.get(i);
            ClipItem.Note next = sorted.get(i + 1);
            if (next.startOffsetTicks > current.startOffsetTicks) {
                long newLen = next.startOffsetTicks - current.startOffsetTicks;
                current.lengthTicks = Math.max(60, newLen);
                applied++;
            }
        }
        return applied;
    }

    public static int humanize(ClipItem clip, long maxTimingTicks, float maxVelDelta) {
        if (clip == null) return 0;
        List<ClipItem.Note> targets = clip.getSelectedNotes();
        if (targets.isEmpty()) targets = clip.getNotes();
        for (ClipItem.Note n : targets) {
            long timeJitter = (long) ((Math.random() * 2.0 - 1.0) * maxTimingTicks);
            n.startOffsetTicks = Math.max(0, n.startOffsetTicks + timeJitter);

            float velJitter = (float) ((Math.random() * 2.0 - 1.0) * maxVelDelta);
            n.velocity = Math.max(0.05f, Math.min(1.0f, n.velocity + velJitter));
        }
        return targets.size();
    }

    public static int strum(ClipItem clip, long strumDelayTicks, boolean ascendingPitch) {
        if (clip == null) return 0;
        List<ClipItem.Note> targets = clip.getSelectedNotes();
        if (targets.isEmpty()) targets = clip.getNotes();
        if (targets.size() <= 1) return 0;

        List<ClipItem.Note> sorted = new ArrayList<>(targets);
        sorted.sort(Comparator.comparingLong(a -> a.startOffsetTicks));

        List<List<ClipItem.Note>> chords = new ArrayList<>();
        List<ClipItem.Note> currentChord = new ArrayList<>();

        for (ClipItem.Note n : sorted) {
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
        for (List<ClipItem.Note> chord : chords) {
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

    public static void quantizeAdvanced(ClipItem clip, long snapTicks, float strength, float swingPercent, boolean quantizeStart, boolean quantizeLength) {
        if (clip == null || snapTicks <= 1) return;
        List<ClipItem.Note> targets = clip.getSelectedNotes();
        if (targets.isEmpty()) targets = clip.getNotes();

        float clampedStrength = Math.max(0.0f, Math.min(1.0f, strength));

        for (ClipItem.Note n : targets) {
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

    public static void applyCrescendo(ClipItem clip, float startVel, float endVel) {
        if (clip == null) return;
        List<ClipItem.Note> targets = clip.getSelectedNotes();
        if (targets.isEmpty()) targets = clip.getNotes();
        if (targets.isEmpty()) return;

        List<ClipItem.Note> sorted = new ArrayList<>(targets);
        sorted.sort(Comparator.comparingLong(a -> a.startOffsetTicks));

        int total = sorted.size();
        for (int i = 0; i < total; i++) {
            float progress = total > 1 ? (float) i / (total - 1) : 0.5f;
            float targetVel = startVel + progress * (endVel - startVel);
            sorted.get(i).velocity = Math.max(0.05f, Math.min(1.0f, targetVel));
        }
    }

    public static void compressVelocities(ClipItem clip, float minVel, float maxVel) {
        if (clip == null) return;
        List<ClipItem.Note> targets = clip.getSelectedNotes().isEmpty() ? clip.getNotes() : clip.getSelectedNotes();
        if (targets.isEmpty()) return;

        float actualMin = 1.0f;
        float actualMax = 0.0f;
        for (ClipItem.Note n : targets) {
            if (n.velocity < actualMin) actualMin = n.velocity;
            if (n.velocity > actualMax) actualMax = n.velocity;
        }

        float spread = actualMax - actualMin;
        float targetSpread = maxVel - minVel;

        for (ClipItem.Note n : targets) {
            if (spread > 0.001f) {
                float normalized = (n.velocity - actualMin) / spread;
                n.velocity = Math.max(0.05f, Math.min(1.0f, minVel + (normalized * targetSpread)));
            } else {
                n.velocity = Math.max(0.05f, Math.min(1.0f, (minVel + maxVel) / 2.0f));
            }
        }
    }

    public static void scaleVelocities(ClipItem clip, float factor) {
        if (clip == null) return;
        List<ClipItem.Note> targets = clip.getSelectedNotes().isEmpty() ? clip.getNotes() : clip.getSelectedNotes();
        for (ClipItem.Note n : targets) {
            n.velocity = Math.max(0.05f, Math.min(1.0f, n.velocity * factor));
        }
    }

    public static void randomizeVelocities(ClipItem clip, float amount) {
        if (clip == null) return;
        List<ClipItem.Note> targets = clip.getSelectedNotes().isEmpty() ? clip.getNotes() : clip.getSelectedNotes();
        for (ClipItem.Note n : targets) {
            float delta = (float) ((Math.random() * 2.0 - 1.0) * amount);
            n.velocity = Math.max(0.05f, Math.min(1.0f, n.velocity + delta));
        }
    }

    public static void invertVelocities(ClipItem clip) {
        if (clip == null) return;
        List<ClipItem.Note> targets = clip.getSelectedNotes().isEmpty() ? clip.getNotes() : clip.getSelectedNotes();
        for (ClipItem.Note n : targets) {
            n.velocity = Math.max(0.05f, Math.min(1.0f, 1.05f - n.velocity));
        }
    }

    public static void setAllVelocities(ClipItem clip, float targetVel) {
        if (clip == null) return;
        List<ClipItem.Note> targets = clip.getSelectedNotes().isEmpty() ? clip.getNotes() : clip.getSelectedNotes();
        float clamped = Math.max(0.05f, Math.min(1.0f, targetVel));
        for (ClipItem.Note n : targets) {
            n.velocity = clamped;
        }
    }
}
