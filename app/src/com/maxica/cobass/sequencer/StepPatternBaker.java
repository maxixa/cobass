package com.maxica.cobass.sequencer;

import android.graphics.Color;
import com.maxica.cobass.model.ClipItem;
import com.maxica.cobass.model.StepPatternItem;
import com.maxica.cobass.model.TrackItem;

public final class StepPatternBaker {

    private StepPatternBaker() {}

    /**
     * Calculates the total tick duration required to capture all polymeter lanes in a pattern.
     */
    public static long calculatePatternTotalTicks(StepPatternItem pattern) {
        if (pattern == null || pattern.getLanes().isEmpty()) return 1920; // 1 Bar @ PPQ=480

        long maxTicks = 0;
        for (StepPatternItem.Lane lane : pattern.getLanes()) {
            long laneTicks = (long) lane.stepCount * lane.subdivision.getTicks();
            if (laneTicks > maxTicks) {
                maxTicks = laneTicks;
            }
        }
        return Math.max(480, maxTicks);
    }

    /**
     * Bakes an entire multi-lane StepPattern into a standard Arranger ClipItem
     * preserving all sub-tick ratchets, micro-nudges, gate durations, and pitch offsets.
     *
     * @param pattern   Source step sequencer pattern
     * @param trackId   Destination track ID
     * @param startTick Project timeline start tick offset
     * @param clipName  Display name for the baked clip
     * @return Fully populated ClipItem ready for native insertion
     */
    public static ClipItem bakePatternToMidiClip(StepPatternItem pattern, int trackId, long startTick, String clipName) {
        if (pattern == null) return null;

        long totalTicks = calculatePatternTotalTicks(pattern);
        int tempId = (int) (System.currentTimeMillis() & 0xFFFF);
        ClipItem clip = new ClipItem(tempId, trackId, startTick, totalTicks, clipName, Color.parseColor("#9333EA"), TrackItem.Type.SYNTH);

        for (StepPatternItem.Lane lane : pattern.getLanes()) {
            if (lane.isMuted) continue;

            final int stepTicks = lane.subdivision.getTicks();
            final int laneTotalSteps = lane.stepCount;

            for (int s = 0; s < laneTotalSteps; s++) {
                StepPatternItem.Step step = lane.steps.get(s);
                if (!step.active) continue;

                final int ratchetCount = Math.max(1, step.ratchets);
                final long subStepDuration = stepTicks / ratchetCount;
                final long baseOffset = (s * stepTicks) + (long) (step.nudge * stepTicks);

                for (int r = 0; r < ratchetCount; r++) {
                    long noteOffset = Math.max(0, baseOffset + (r * subStepDuration));
                    long noteLength = Math.max(15, (long) (subStepDuration * step.gate));
                    int finalPitch = Math.max(0, Math.min(127, lane.midiNote + step.pitchOffset));

                    clip.addNote(finalPitch, step.velocity, noteOffset, noteLength);
                }
            }
        }

        return clip;
    }

    /**
     * Bakes a single isolated lane into an independent ClipItem (useful for multi-track drum splitting).
     */
    public static ClipItem bakeLaneToMidiClip(StepPatternItem.Lane lane, int trackId, long startTick, String clipName) {
        if (lane == null) return null;

        long totalTicks = (long) lane.stepCount * lane.subdivision.getTicks();
        int tempId = (int) (System.currentTimeMillis() & 0xFFFF);
        ClipItem clip = new ClipItem(tempId, trackId, startTick, totalTicks, clipName, Color.parseColor("#3B82F6"), TrackItem.Type.SYNTH);

        final int stepTicks = lane.subdivision.getTicks();
        for (int s = 0; s < lane.stepCount; s++) {
            StepPatternItem.Step step = lane.steps.get(s);
            if (!step.active) continue;

            final int ratchetCount = Math.max(1, step.ratchets);
            final long subStepDuration = stepTicks / ratchetCount;
            final long baseOffset = (s * stepTicks) + (long) (step.nudge * stepTicks);

            for (int r = 0; r < ratchetCount; r++) {
                long noteOffset = Math.max(0, baseOffset + (r * subStepDuration));
                long noteLength = Math.max(15, (long) (subStepDuration * step.gate));
                int finalPitch = Math.max(0, Math.min(127, lane.midiNote + step.pitchOffset));

                clip.addNote(finalPitch, step.velocity, noteOffset, noteLength);
            }
        }

        return clip;
    }
}
