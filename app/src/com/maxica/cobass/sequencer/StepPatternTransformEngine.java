package com.maxica.cobass.sequencer;

import com.maxica.cobass.model.StepPatternItem;
import java.util.Random;

public final class StepPatternTransformEngine {

    private static final Random RNG = new Random();

    private StepPatternTransformEngine() {}

    /**
     * Randomizes velocities of active steps within a specified range.
     */
    public static void randomizeVelocities(StepPatternItem.Lane lane, float minVel, float maxVel) {
        if (lane == null) return;
        float low = Math.max(0.05f, Math.min(1.0f, minVel));
        float high = Math.max(low, Math.min(1.0f, maxVel));

        for (int i = 0; i < lane.stepCount; i++) {
            StepPatternItem.Step s = lane.steps.get(i);
            if (s.active) {
                s.velocity = low + (RNG.nextFloat() * (high - low));
            }
        }
    }

    /**
     * Adds human timing jitter (micro-nudge) to all active steps.
     */
    public static void humanizeTiming(StepPatternItem.Lane lane, float maxNudgeFraction) {
        if (lane == null) return;
        float maxNudge = Math.max(0.01f, Math.min(0.40f, maxNudgeFraction));

        for (int i = 0; i < lane.stepCount; i++) {
            StepPatternItem.Step s = lane.steps.get(i);
            if (s.active) {
                s.nudge = (RNG.nextFloat() * 2.0f - 1.0f) * maxNudge;
            }
        }
    }

    /**
     * Applies uniform or random probability gates to active steps.
     */
    public static void applyProbability(StepPatternItem.Lane lane, float probability) {
        if (lane == null) return;
        float prob = Math.max(0.0f, Math.min(1.0f, probability));

        for (int i = 0; i < lane.stepCount; i++) {
            StepPatternItem.Step s = lane.steps.get(i);
            if (s.active) {
                s.probability = prob;
            }
        }
    }

    /**
     * Circularly shifts/rotates active steps in a lane by stepShift steps.
     */
    public static void rotateLane(StepPatternItem.Lane lane, int stepShift) {
        if (lane == null || lane.stepCount <= 1 || stepShift == 0) return;

        int n = lane.stepCount;
        StepPatternItem.Step[] temp = new StepPatternItem.Step[n];
        for (int i = 0; i < n; i++) {
            temp[i] = lane.steps.get(i).copy();
        }

        for (int i = 0; i < n; i++) {
            int targetIdx = ((i + stepShift) % n + n) % n;
            StepPatternItem.Step src = temp[i];
            StepPatternItem.Step dst = lane.steps.get(targetIdx);
            dst.active = src.active;
            dst.velocity = src.velocity;
            dst.pitchOffset = src.pitchOffset;
            dst.gate = src.gate;
            dst.nudge = src.nudge;
            dst.ratchets = src.ratchets;
            dst.probability = src.probability;
        }
    }

    /**
     * Generates trap-style hi-hat burst rolls on specified steps.
     */
    public static void generateHiHatRoll(StepPatternItem.Lane lane, int stepIndex, int ratchets, float velocity) {
        if (lane == null || stepIndex < 0 || stepIndex >= lane.stepCount) return;
        StepPatternItem.Step s = lane.steps.get(stepIndex);
        s.active = true;
        s.ratchets = Math.max(1, Math.min(8, ratchets));
        s.velocity = Math.max(0.05f, Math.min(1.0f, velocity));
        s.gate = 0.90f;
    }

    /**
     * Generates a progressive snare crescendo roll across a step span.
     */
    public static void generateSnareRoll(StepPatternItem.Lane lane, int startStep, int lengthSteps, boolean crescendo) {
        if (lane == null || lengthSteps <= 0) return;
        for (int i = 0; i < lengthSteps; i++) {
            int idx = startStep + i;
            if (idx >= 0 && idx < lane.stepCount) {
                StepPatternItem.Step s = lane.steps.get(idx);
                s.active = true;
                s.ratchets = (i >= lengthSteps / 2) ? 2 : 1;
                float progress = (float) i / Math.max(1, lengthSteps - 1);
                s.velocity = crescendo ? (0.35f + progress * 0.60f) : (0.95f - progress * 0.60f);
            }
        }
    }

    /**
     * Inverts the active/inactive state of all steps in a lane.
     */
    public static void invertPattern(StepPatternItem.Lane lane) {
        if (lane == null) return;
        for (int i = 0; i < lane.stepCount; i++) {
            StepPatternItem.Step s = lane.steps.get(i);
            s.active = !s.active;
            if (s.active && s.velocity <= 0.05f) {
                s.velocity = 0.85f;
            }
        }
    }

    /**
     * Clears all step triggers in a lane.
     */
    public static void clearLane(StepPatternItem.Lane lane) {
        if (lane == null) return;
        for (StepPatternItem.Step s : lane.steps) {
            s.active = false;
        }
    }
}
