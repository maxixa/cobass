package com.maxica.cobass.sequencer;

import com.maxica.cobass.model.ClipItem;
import com.maxica.cobass.model.MusicalScale;
import com.maxica.cobass.model.SnapGrid;
import com.maxica.cobass.model.TransformLockMasks;
import com.maxica.cobass.model.TransformRecipeItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class MidiTransformEngine {

    private MidiTransformEngine() {}

        // =========================================================================
    // FACTORY MACRO GENRE PRODUCTION PRESETS (MULTI-PASS RECIPE STACKS)
    // =========================================================================

    public static List<TransformRecipeItem> getMacroPreset(String presetName, int baseSeed) {
        List<TransformRecipeItem> stack = new ArrayList<>();
        int seed1 = baseSeed;
        int seed2 = baseSeed + 101;
        int seed3 = baseSeed + 202;

        if ("Future Bass Chords".equalsIgnoreCase(presetName)) {
            stack.add(createDiatonicVoicingRecipe(2, 1, 0.85f, seed1)); // Diatonic 3rds (Drop-2)
            stack.add(createEuclideanSliceRecipe(8, 5, 0.65f, seed2));  // 5/8 Euclidean syncopation
            stack.add(createPhraseArcRecipe());                         // Golden Ratio dynamics
        } else if ("Trap Lead Evolution".equalsIgnoreCase(presetName)) {
            stack.add(createMarkovDriftRecipe(0.40f, seed1));           // Markov Melodic Drift
            stack.add(createRatchetBurstRecipe(4, true, 0.55f, seed2)); // Accelerating 4x ratchets
            stack.add(createEnclosureRecipe(0.60f, seed3));             // Chromatic Bebop Enclosure
        } else if ("Liquid DnB Roller".equalsIgnoreCase(presetName)) {
            stack.add(createEuclideanSliceRecipe(16, 7, 0.70f, seed1)); // 7/16 rolling rhythm
            stack.add(createClaveSlipRecipe(0.50f, seed2));             // Syncopated displacement
            stack.add(createPhraseArcRecipe());                         // Phrasing arc
        } else if ("Neo-Classical Motif".equalsIgnoreCase(presetName)) {
            stack.add(createPalindromeRecipe(seed1));                   // Palindrome reflection
            stack.add(createDiatonicVoicingRecipe(2, 0, 0.75f, seed2)); // Close 3rds harmony
            stack.add(createEnclosureRecipe(0.45f, seed3));             // Grace ornamentation
        } else if ("Human Soul Groove".equalsIgnoreCase(presetName)) {
            stack.add(createHumanizeRecipe(0.30f, seed1));              // Natural timing & velocity jitter
            stack.add(createClaveSlipRecipe(0.35f, seed2));             // Laid-back pocket slip
            stack.add(createPhraseArcRecipe());                         // Dynamics swell
        } else if ("Cyberpunk Industrial Arp".equalsIgnoreCase(presetName)) {
            stack.add(createRatchetBurstRecipe(3, false, 0.65f, seed1));// Decelerating rolls
            stack.add(new TransformRecipeItem(TransformRecipeItem.OperatorType.MODAL_INVERSION, 0.8f, seed2, 60f, 0f));
            stack.add(createScaleConstrainRecipe());                    // Diatonic lock
        } else {
            // Default single-pass pass
            stack.add(createEuclideanSliceRecipe(8, 5, 0.50f, seed1));
        }

        return stack;
    }

// =========================================================================
    // NATIVE C++20 NOTE TRANSFORM ENGINE PIPELINE INTEGRATION
    // =========================================================================

    /**
     * Executes a stack of transformation recipes non-destructively on a preview note list.
     */
    public static List<ClipItem.Note> previewPipeline(
        List<ClipItem.Note> inputNotes,
        MusicalScale scale,
        int rootKey,
        List<TransformRecipeItem> recipeStack,
        TransformLockMasks masks,
        float dryWetRatio
    ) {
        return NoteTransformPipeline.execute(inputNotes, scale, rootKey, recipeStack, masks, dryWetRatio);
    }

    /**
     * Applies the transformation pipeline directly to a ClipItem in-place.
     * Supports targeting selected notes only or the entire clip.
     */
    public static boolean applyPipeline(
        ClipItem clip,
        MusicalScale scale,
        int rootKey,
        List<TransformRecipeItem> recipeStack,
        TransformLockMasks masks,
        float dryWetRatio,
        boolean applyToSelectionOnly
    ) {
        if (clip == null || clip.getNotes().isEmpty()) return false;

        List<ClipItem.Note> targetNotes;
        if (applyToSelectionOnly && !clip.getSelectedNotes().isEmpty()) {
            targetNotes = clip.getSelectedNotes();
        } else {
            targetNotes = clip.getNotes();
        }

        List<ClipItem.Note> transformed = NoteTransformPipeline.execute(
            targetNotes, scale, rootKey, recipeStack, masks, dryWetRatio
        );

        if (transformed == null || transformed.isEmpty()) return false;

        if (applyToSelectionOnly && !clip.getSelectedNotes().isEmpty()) {
            // Remove previous selected notes and insert transformed notes
            clip.getNotes().removeIf(n -> n.isSelected);
            for (ClipItem.Note tn : transformed) {
                tn.isSelected = true;
                clip.addNote(tn);
            }
        } else {
            clip.restoreNotesList(transformed);
        }

        return true;
    }

    /**
     * Applies the transformation pipeline in batch across multiple clips (e.g. from the Arranger).
     */
    public static int applyPipelineBatch(
        List<ClipItem> clips,
        MusicalScale scale,
        int rootKey,
        List<TransformRecipeItem> recipeStack,
        TransformLockMasks masks,
        float dryWetRatio
    ) {
        if (clips == null || clips.isEmpty()) return 0;
        int modifiedCount = 0;

        for (ClipItem clip : clips) {
            if (clip.getType() == com.maxica.cobass.model.TrackItem.Type.SYNTH) {
                if (applyPipeline(clip, scale, rootKey, recipeStack, masks, dryWetRatio, false)) {
                    modifiedCount++;
                }
            }
        }
        return modifiedCount;
    }

    // =========================================================================
    // RECIPE FACTORY BUILDERS
    // =========================================================================

                public static TransformRecipeItem createGuitarStrumRecipe(boolean downStrum, int spreadTicks, float intensity, int seed) {
        return new TransformRecipeItem(TransformRecipeItem.OperatorType.GUITAR_STRUM_PHYSICS, intensity, seed, downStrum ? 1.0f : -1.0f, spreadTicks);
    }

    public static TransformRecipeItem createMaqamInflectorRecipe(float intensity, int seed) {
        return new TransformRecipeItem(TransformRecipeItem.OperatorType.MAQAM_MICROTONAL_BEND, intensity, seed, 0.0f, 0.0f);
    }

    public static TransformRecipeItem createParabolicSwellRecipe(float minVelPct, float maxVelPct, float intensity, int seed) {
        return new TransformRecipeItem(TransformRecipeItem.OperatorType.PARABOLIC_VELOCITY_DOME, intensity, seed, minVelPct, maxVelPct);
    }

public static TransformRecipeItem createChordDropVoicingRecipe(int style, float intensity, int seed) {
        return new TransformRecipeItem(TransformRecipeItem.OperatorType.CHORD_DROP_VOICING, intensity, seed, style, 0.0f);
    }

    public static TransformRecipeItem createContraryCounterpointRecipe(float intensity, int seed) {
        return new TransformRecipeItem(TransformRecipeItem.OperatorType.CONTRARY_COUNTERPOINT, intensity, seed, 0.0f, 0.0f);
    }

    public static TransformRecipeItem createSubBassExtractorRecipe(int style, float intensity, int seed) {
        return new TransformRecipeItem(TransformRecipeItem.OperatorType.SUB_BASS_EXTRACTOR, intensity, seed, style, 0.0f);
    }

public static TransformRecipeItem createSchenkerLeadRecipe(float intensity, int seed) {
        return new TransformRecipeItem(TransformRecipeItem.OperatorType.SCHENKER_LEAD_TOWARD, intensity, seed, 0.0f, 0.0f);
    }

    public static TransformRecipeItem createBartokWedgeRecipe(int axisPitch, boolean expanding, float intensity, int seed) {
        return new TransformRecipeItem(TransformRecipeItem.OperatorType.BARTOK_PITCH_WEDGE, intensity, seed, axisPitch, expanding ? 1.0f : -1.0f);
    }

    public static TransformRecipeItem createCompoundPolyphonyRecipe(float intensity, int seed) {
        return new TransformRecipeItem(TransformRecipeItem.OperatorType.COMPOUND_POLY_WEAVE, intensity, seed, 0.0f, 0.0f);
    }

    public static TransformRecipeItem createDiatonicCascadeRecipe(float intensity, int seed) {
        return new TransformRecipeItem(TransformRecipeItem.OperatorType.DIATONIC_CASCADE_RUN, intensity, seed, 0.0f, 0.0f);
    }

public static TransformRecipeItem createEuclideanSliceRecipe(int steps, int pulses, float intensity, int seed) {
        return new TransformRecipeItem(TransformRecipeItem.OperatorType.EUCLIDEAN_SLICE, intensity, seed, steps, pulses);
    }

    public static TransformRecipeItem createRatchetBurstRecipe(int subdivisions, boolean accelerating, float intensity, int seed) {
        return new TransformRecipeItem(TransformRecipeItem.OperatorType.RATCHET_BURST, intensity, seed, subdivisions, accelerating ? 1.0f : -1.0f);
    }

    public static TransformRecipeItem createMarkovDriftRecipe(float intensity, int seed) {
        return new TransformRecipeItem(TransformRecipeItem.OperatorType.MARKOV_DRIFT, intensity, seed, 0.0f, 0.0f);
    }

    public static TransformRecipeItem createEnclosureRecipe(float intensity, int seed) {
        return new TransformRecipeItem(TransformRecipeItem.OperatorType.ENCLOSURE_DECORATE, intensity, seed, 0.0f, 0.0f);
    }

    public static TransformRecipeItem createDiatonicVoicingRecipe(int degreeShift, int style, float intensity, int seed) {
        return new TransformRecipeItem(TransformRecipeItem.OperatorType.DIATONIC_VOICING, intensity, seed, degreeShift, style);
    }

    public static TransformRecipeItem createCallResponseRecipe(float intensity, int seed) {
        return new TransformRecipeItem(TransformRecipeItem.OperatorType.CALL_RESPONSE_INFILL, intensity, seed, 0.0f, 0.0f);
    }

    public static TransformRecipeItem createClaveSlipRecipe(float intensity, int seed) {
        return new TransformRecipeItem(TransformRecipeItem.OperatorType.CLAVE_SLIP, intensity, seed, 0.0f, 0.0f);
    }

    public static TransformRecipeItem createPalindromeRecipe(int seed) {
        return new TransformRecipeItem(TransformRecipeItem.OperatorType.PALINDROME_MIRROR, 1.0f, seed, 0.0f, 0.0f);
    }

    public static TransformRecipeItem createPhraseArcRecipe() {
        return new TransformRecipeItem(TransformRecipeItem.OperatorType.GOLDEN_PHRASE_ARC, 1.0f, 12345, 0.0f, 0.0f);
    }

    public static TransformRecipeItem createHumanizeRecipe(float intensity, int seed) {
        return new TransformRecipeItem(TransformRecipeItem.OperatorType.HUMANIZE_GROOVE, intensity, seed, 0.0f, 0.0f);
    }

    public static TransformRecipeItem createScaleConstrainRecipe() {
        return new TransformRecipeItem(TransformRecipeItem.OperatorType.SCALE_CONSTRAIN, 1.0f, 12345, 0.0f, 0.0f);
    }

    // =========================================================================
    // LEGACY & INTERACTIVE EDITING HELPERS
    // =========================================================================

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
