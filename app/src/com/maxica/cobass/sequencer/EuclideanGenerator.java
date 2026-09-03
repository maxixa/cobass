package com.maxica.cobass.sequencer;

import com.maxica.cobass.model.StepPatternItem;
import java.util.ArrayList;
import java.util.List;

public final class EuclideanGenerator {

    private EuclideanGenerator() {}

    /**
     * Generates a Euclidean distribution of K pulses over N steps using Björklund's algorithm.
     *
     * @param pulses   Number of active hits/pulses (K)
     * @param steps    Total sequence length (N)
     * @param rotation Step rotation offset (+/- shift)
     * @return boolean array indicating active step triggers
     */
    public static boolean[] generateEuclideanPattern(int pulses, int steps, int rotation) {
        if (steps <= 0) return new boolean[0];
        int k = Math.max(0, Math.min(pulses, steps));
        boolean[] pattern = new boolean[steps];
        if (k == 0) return pattern;
        if (k == steps) {
            for (int i = 0; i < steps; i++) pattern[i] = true;
            return pattern;
        }

        List<List<Boolean>> groups = new ArrayList<>();
        for (int i = 0; i < steps; i++) {
            List<Boolean> g = new ArrayList<>();
            g.add(i < k);
            groups.add(g);
        }

        int countZeros = steps - k;
        int countOnes = k;

        while (countZeros > 1 && countOnes > 1) {
            int numMerges = Math.min(countOnes, countZeros);
            for (int i = 0; i < numMerges; i++) {
                groups.get(i).addAll(groups.get(groups.size() - 1 - i));
            }
            for (int i = 0; i < numMerges; i++) {
                groups.remove(groups.size() - 1);
            }

            int nextZeros = Math.abs(countZeros - countOnes);
            int nextOnes = numMerges;
            countZeros = nextZeros;
            countOnes = nextOnes;
        }

        int idx = 0;
        for (List<Boolean> group : groups) {
            for (Boolean val : group) {
                if (idx < steps) pattern[idx++] = val;
            }
        }

        if (rotation != 0) {
            boolean[] rotated = new boolean[steps];
            for (int i = 0; i < steps; i++) {
                int targetIdx = ((i + rotation) % steps + steps) % steps;
                rotated[targetIdx] = pattern[i];
            }
            return rotated;
        }

        return pattern;
    }

    /**
     * Applies a Euclidean rhythm directly onto a target StepLane.
     */
    public static void applyEuclideanToLane(StepPatternItem.Lane lane, int pulses, int steps, int rotation, float velocity) {
        if (lane == null) return;
        lane.stepCount = Math.max(1, Math.min(64, steps));
        boolean[] pattern = generateEuclideanPattern(pulses, steps, rotation);

        for (int i = 0; i < lane.steps.size(); i++) {
            StepPatternItem.Step s = lane.steps.get(i);
            if (i < pattern.length) {
                s.active = pattern[i];
                if (s.active) {
                    s.velocity = velocity;
                    s.ratchets = 1;
                    s.nudge = 0.0f;
                }
            } else {
                s.active = false;
            }
        }
    }

    /**
     * Generates an algorithmic Euclidean fill across a specific step sub-region of a lane.
     */
    public static void generateEuclideanFill(StepPatternItem.Lane lane, int fillStartStep, int fillLengthSteps, int pulses, float velocity) {
        if (lane == null || fillLengthSteps <= 0) return;
        boolean[] fillPattern = generateEuclideanPattern(pulses, fillLengthSteps, 0);

        for (int i = 0; i < fillLengthSteps; i++) {
            int stepIndex = fillStartStep + i;
            if (stepIndex >= 0 && stepIndex < lane.steps.size() && stepIndex < lane.stepCount) {
                StepPatternItem.Step s = lane.steps.get(stepIndex);
                s.active = fillPattern[i];
                if (s.active) {
                    s.velocity = velocity;
                }
            }
        }
    }
}
