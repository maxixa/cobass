package com.maxica.cobass.plugin;

import com.maxica.cobass.model.PluginDescriptorItem;
import com.maxica.cobass.model.PluginParamItem;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.Random;

public final class PatchVariationEngine {

    private static final Random RNG = new Random();
    private static final int[] CONSONANT_INTERVALS = {-24, -19, -12, -7, -5, 0, 5, 7, 12, 19, 24};

    public static class LockMasks {
        public boolean lockOscillators = false;
        public boolean lockFilter = false;
        public boolean lockEnvelopes = false;
        public boolean lockLfo = false;
        public boolean lockFx = false;
        public boolean lockMaster = true; // Master gain/pitch locked by default
    }

    private PatchVariationEngine() {}

    /**
     * Mutates a plugin JSON state using controlled Gaussian variance and musical constraints.
     *
     * @param descriptor       Plugin descriptor containing parameter metadata
     * @param currentJsonState Current JSON snapshot of the patch
     * @param intensity        Variation intensity: 0.05 (Light) to 1.00 (Extreme)
     * @param locks            Sectional lock masks
     * @param snapHarmonics    Whether pitch parameters snap to consonant intervals
     * @param autoGainStage    Whether to compensate master headroom automatically
     * @return Mutated JSON patch state
     */
    public static String mutatePatch(
        PluginDescriptorItem descriptor,
        String currentJsonState,
        float intensity,
        LockMasks locks,
        boolean snapHarmonics,
        boolean autoGainStage
    ) {
        if (descriptor == null || currentJsonState == null || currentJsonState.isEmpty()) {
            return currentJsonState != null ? currentJsonState : "{}";
        }

        try {
            JSONObject root = new JSONObject(currentJsonState);
            float clampedIntensity = Math.max(0.05f, Math.min(1.0f, intensity));
            LockMasks activeLocks = (locks != null) ? locks : new LockMasks();

            float totalDriveSum = 0.0f;
            float totalResonanceSum = 0.0f;

            for (PluginParamItem param : descriptor.getParameters()) {
                String strKey = String.valueOf(param.getId());
                if (!root.has(strKey)) continue;

                double currentVal = root.getDouble(strKey);
                if (isParameterLocked(descriptor, param, activeLocks)) {
                    continue; // Skip locked parameter
                }

                double mutatedVal = mutateSingleParameter(
                    param,
                    (float) currentVal,
                    clampedIntensity,
                    snapHarmonics
                );

                // Track drive and resonance for auto-gain compensation
                String pNameLower = param.getName().toLowerCase();
                if (pNameLower.contains("drive") || pNameLower.contains("sat")) {
                    totalDriveSum += (float) mutatedVal;
                }
                if (pNameLower.contains("resonance") || pNameLower.contains("q")) {
                    totalResonanceSum += (float) mutatedVal;
                }

                root.put(strKey, mutatedVal);
            }

            // Headroom Auto-Gain Staging Protection
            if (autoGainStage && !activeLocks.lockMaster) {
                applyHeadroomCompensation(descriptor, root, totalDriveSum, totalResonanceSum);
            }

            // Enforce Envelope Energy Integrity Guard
            enforceEnvelopeIntegrity(descriptor, root);

            return root.toString();
        } catch (Exception e) {
            return currentJsonState;
        }
    }

    private static boolean isParameterLocked(
        PluginDescriptorItem descriptor,
        PluginParamItem param,
        LockMasks locks
    ) {
        if (locks == null) return false;
        String pluginId = descriptor.getPluginId();
        String name = param.getName().toLowerCase();
        int id = param.getId();

        // 1. Hyperion Synth Specific Module Boundaries
        if (pluginId.contains("hyperion")) {
            if (id >= 0 && id <= 21 && locks.lockOscillators) return true;
            if (id >= 22 && id <= 28 && locks.lockFilter) return true;
            if (id >= 29 && id <= 38 && locks.lockEnvelopes) return true;
            if (id >= 39 && id <= 42 && locks.lockLfo) return true;
            if (id >= 43 && id <= 51 && locks.lockFx) return true;
            if (id >= 52 && locks.lockMaster) return true;
            return false;
        }

        // 2. Cobalt Drum Synth Specific Module Boundaries
        if (pluginId.contains("drums")) {
            if (id >= 0 && id <= 3 && locks.lockMaster) return true;
            if (id >= 4 && id <= 8 && locks.lockOscillators) return true; // Kick
            if (id >= 9 && id <= 13 && locks.lockFilter) return true;     // Snare
            if (id >= 14 && id <= 22 && locks.lockEnvelopes) return true;  // Clap / Hats
            if (id >= 23 && locks.lockFx) return true;                    // Perc
            return false;
        }

        // 3. Generic Plugin Name-Based Module Masking
        if (locks.lockFilter && (name.contains("cutoff") || name.contains("filter") || name.contains("res") || name.contains("vowel"))) return true;
        if (locks.lockEnvelopes && (name.contains("attack") || name.contains("decay") || name.contains("sustain") || name.contains("release") || name.contains("punch"))) return true;
        if (locks.lockLfo && (name.contains("lfo") || name.contains("rate") || name.contains("depth"))) return true;
        if (locks.lockFx && (name.contains("fx") || name.contains("reverb") || name.contains("delay") || name.contains("drive") || name.contains("chorus") || name.contains("ott"))) return true;
        if (locks.lockMaster && (name.contains("master") || name.contains("volume") || name.contains("out") || name.contains("portamento"))) return true;

        return false;
    }

    private static double mutateSingleParameter(
        PluginParamItem param,
        float currentVal,
        float intensity,
        boolean snapHarmonics
    ) {
        float min = param.getMinValue();
        float max = param.getMaxValue();
        float range = max - min;
        if (range <= 0.0001f) return currentVal;

        // 1. Continuous Float Parameters
        if (param.getType() == PluginParamItem.Type.FLOAT) {
            float sigma = intensity * 0.45f;
            float jitter = (float) (RNG.nextGaussian() * sigma * range);
            float newVal = currentVal + jitter;

            // Harmonic Snapping for Semitone / Pitch Parameters
            if (snapHarmonics && isPitchParameter(param.getName())) {
                newVal = snapToNearestHarmonic(newVal);
            }

            return Math.max(min, Math.min(max, newVal));
        }

        // 2. Discrete Integer Stepper Parameters
        if (param.getType() == PluginParamItem.Type.INT) {
            if (isPitchParameter(param.getName()) && snapHarmonics) {
                return snapToNearestHarmonic(currentVal + (float)(RNG.nextGaussian() * intensity * 12.0f));
            }
            int stepSpread = Math.max(1, Math.round(intensity * (range * 0.5f)));
            int stepDelta = RNG.nextInt(stepSpread * 2 + 1) - stepSpread;
            return Math.max(min, Math.min(max, Math.round(currentVal + stepDelta)));
        }

        // 3. Dropdown Choice Parameters
        if (param.getType() == PluginParamItem.Type.CHOICE) {
            int numChoices = !param.getChoices().isEmpty() ? param.getChoices().size() : (int)(max - min + 1);
            if (numChoices <= 1) return currentVal;

            if (intensity <= 0.20f) {
                // Light: 85% chance keep original, 15% chance step adjacent
                if (RNG.nextFloat() > 0.15f) return currentVal;
                int dir = RNG.nextBoolean() ? 1 : -1;
                return Math.max(0, Math.min(numChoices - 1, Math.round(currentVal) + dir));
            } else {
                // Medium / Extreme: Explore valid choice index
                return RNG.nextInt(numChoices);
            }
        }

        // 4. Boolean Toggle Switches
        if (param.getType() == PluginParamItem.Type.BOOL) {
            if (intensity <= 0.25f) return currentVal; // Light retains toggles
            float flipChance = intensity * 0.40f;
            return (RNG.nextFloat() < flipChance) ? (currentVal > 0.5f ? 0.0 : 1.0) : currentVal;
        }

        return currentVal;
    }

    private static boolean isPitchParameter(String name) {
        String n = name.toLowerCase();
        return n.contains("semi") || n.contains("pitch") || n.contains("tune") || n.contains("bend") || n.contains("drop");
    }

    private static float snapToNearestHarmonic(float rawSemitones) {
        int bestInterval = 0;
        float minDelta = 999.0f;
        for (int interval : CONSONANT_INTERVALS) {
            float delta = Math.abs(rawSemitones - interval);
            if (delta < minDelta) {
                minDelta = delta;
                bestInterval = interval;
            }
        }
        return bestInterval;
    }

    private static void applyHeadroomCompensation(
        PluginDescriptorItem descriptor,
        JSONObject root,
        float totalDrive,
        float totalResonance
    ) {
        for (PluginParamItem p : descriptor.getParameters()) {
            String n = p.getName().toLowerCase();
            if (n.contains("master gain") || n.contains("master out") || n.contains("output trim")) {
                String strK = String.valueOf(p.getId());
                if (root.has(strK)) {
                    double currentOut = root.optDouble(strK, 0.0);
                    double trimCompensation = 0.0;

                    if (totalDrive > 10.0f) trimCompensation -= (totalDrive - 10.0f) * 0.18;
                    if (totalResonance > 6.0f) trimCompensation -= (totalResonance - 6.0f) * 0.25;

                    double compensated = Math.max(p.getMinValue(), Math.min(p.getMaxValue(), currentOut + trimCompensation));
                    try {
                        root.put(strK, compensated);
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    private static void enforceEnvelopeIntegrity(PluginDescriptorItem descriptor, JSONObject root) {
        String attackKey = null;
        String sustainKey = null;
        String decayKey = null;

        for (PluginParamItem p : descriptor.getParameters()) {
            String n = p.getName().toLowerCase();
            if (n.equals("amp attack")) attackKey = String.valueOf(p.getId());
            if (n.equals("amp sustain")) sustainKey = String.valueOf(p.getId());
            if (n.equals("amp decay")) decayKey = String.valueOf(p.getId());
        }

        if (attackKey != null && sustainKey != null && root.has(attackKey) && root.has(sustainKey)) {
            double att = root.optDouble(attackKey, 5.0);
            double sus = root.optDouble(sustainKey, 0.75);

            // Prevent silence: if attack is long, sustain must be audible
            if (att > 400.0 && sus < 0.35) {
                try { root.put(sustainKey, 0.45); } catch (Exception ignored) {}
            }
        }
    }
}
