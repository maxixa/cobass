package com.maxica.cobass.sequencer;

import com.maxica.cobass.model.StepPatternItem;

import java.util.Random;

public final class StepPatternVariationEngine {

    private static final Random RNG = new Random();
    private static final int[] RATCHET_POOL = {1, 2, 3, 4, 8};

    private StepPatternVariationEngine() {}

    /**
     * Mutates an entire polymetric step pattern based on selectable variance intensity.
     *
     * @param pattern       The target pattern to mutate
     * @param intensity     Variation intensity: 0.05 (Light) to 1.00 (Extreme)
     * @param mutateVel     Mutate step velocities
     * @param mutateRolls   Mutate sub-step ratchets / rolls
     * @param mutateNudge   Mutate micro-timing swing/nudges
     * @param mutateProb    Mutate probability gates
     */
    public static void mutateGroove(
        StepPatternItem pattern,
        float intensity,
        boolean mutateVel,
        boolean mutateRolls,
        boolean mutateNudge,
        boolean mutateProb
    ) {
        if (pattern == null || pattern.getLanes().isEmpty()) return;
        float sigma = Math.max(0.05f, Math.min(1.0f, intensity));

        for (int l = 0; l < pattern.getLanes().size(); l++) {
            StepPatternItem.Lane lane = pattern.getLanes().get(l);
            if (lane.isMuted) continue;

            for (int s = 0; s < lane.stepCount && s < lane.steps.size(); s++) {
                StepPatternItem.Step step = lane.steps.get(s);

                // 1. Velocity Humanization & Accents
                if (mutateVel && step.active) {
                    float velJitter = (float) (RNG.nextGaussian() * sigma * 0.25f);
                    step.velocity = Math.max(0.20f, Math.min(1.0f, step.velocity + velJitter));
                }

                // 2. Micro-Timing Swing & Nudges
                if (mutateNudge && step.active) {
                    float nudgeJitter = (float) (RNG.nextGaussian() * sigma * 0.12f);
                    step.nudge = Math.max(-0.45f, Math.min(0.45f, step.nudge + nudgeJitter));
                }

                // 3. Sub-Step Ratchet Rolls (e.g. 2x, 3x, 4x, 8x)
                if (mutateRolls) {
                    if (step.active) {
                        float rollChance = sigma * 0.35f;
                        if (RNG.nextFloat() < rollChance) {
                            int rIdx = 1 + RNG.nextInt(Math.min(RATCHET_POOL.length - 1, Math.round(sigma * 4.0f)));
                            step.ratchets = RATCHET_POOL[rIdx];
                        } else if (sigma < 0.25f) {
                            step.ratchets = 1; // Light intensity clears rogue rolls
                        }
                    }
                }

                // 4. Probability Gates
                if (mutateProb && step.active) {
                    if (sigma >= 0.30f) {
                        float probDip = (s % 4 == 0) ? 1.0f : (1.0f - (sigma * RNG.nextFloat() * 0.40f));
                        step.probability = Math.max(0.35f, Math.min(1.0f, probDip));
                    } else {
                        step.probability = 1.0f;
                    }
                }

                // 5. Strong / Extreme Ghost Note Generation
                if (sigma >= 0.50f && !step.active) {
                    float ghostChance = (sigma - 0.45f) * 0.22f;
                    if (RNG.nextFloat() < ghostChance) {
                        step.active = true;
                        step.velocity = 0.35f + (RNG.nextFloat() * 0.25f); // Soft ghost tap
                        step.ratchets = 1;
                        step.nudge = (RNG.nextFloat() * 0.10f) - 0.05f;
                    }
                }
            }
        }
    }

    /**
     * Injects an algorithmic Euclidean rhythm fill into a designated lane.
     */
    public static void applyEuclideanFill(StepPatternItem.Lane lane, int pulses, float intensity) {
        if (lane == null || lane.stepCount <= 0) return;
        boolean[] euclidean = EuclideanGenerator.generateEuclideanPattern(pulses, lane.stepCount, 0);

        for (int i = 0; i < lane.stepCount && i < lane.steps.size(); i++) {
            StepPatternItem.Step s = lane.steps.get(i);
            s.active = euclidean[i];
            if (s.active) {
                s.velocity = 0.70f + (RNG.nextFloat() * 0.25f * intensity);
                s.ratchets = (i == lane.stepCount - 1 && intensity > 0.4f) ? 2 : 1;
            }
        }
    }
}
