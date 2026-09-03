package com.maxica.cobass.model;

public class TransformRecipeItem {
    public enum OperatorType {
        EUCLIDEAN_SLICE(0, "Euclidean Rhythmic Slicer"),
        RATCHET_BURST(1, "Accelerating Ratchet Burst"),
        MARKOV_DRIFT(2, "Markov Melodic Drift"),
        ENCLOSURE_DECORATE(3, "Chromatic Enclosure"),
        MODAL_INVERSION(4, "Modal Axis Inversion"),
        DIATONIC_VOICING(5, "Diatonic Harmony (3rds/6ths)"),
        CALL_RESPONSE_INFILL(6, "Call & Response Infill"),
        CLAVE_SLIP(7, "Clave Syncopation Slip"),
        PALINDROME_MIRROR(8, "Palindrome Reflection"),
        GOLDEN_PHRASE_ARC(9, "Golden Ratio Dynamics"),
        HUMANIZE_GROOVE(10, "Organic Humanize Groove"),
        SCALE_CONSTRAIN(11, "Scale Tone Constrain"),

        // Phase 3 Melodic & Counterpoint Engines
        SCHENKER_LEAD_TOWARD(12, "Schenkerian Cadence Lead"),
        BARTOK_PITCH_WEDGE(13, "Bartók Pitch Wedge"),
        COMPOUND_POLY_WEAVE(14, "Compound Polyphony Weave"),
        DIATONIC_CASCADE_RUN(15, "Diatonic Cascade Run"),

        // Phase 4 Harmonic Voicings & Bass Extractor
        CHORD_DROP_VOICING(16, "Drop-2/3 Voicing Spreader"),
        CONTRARY_COUNTERPOINT(17, "Contrary Counterpoint"),
        SUB_BASS_EXTRACTOR(18, "Sub-Bass Root Extractor"),

        // Phase 5 Strumming, Expression & Dynamics
        GUITAR_STRUM_PHYSICS(19, "Acoustic Guitar Strum"),
        MAQAM_MICROTONAL_BEND(20, "Maqam / Blues Inflector"),
        PARABOLIC_VELOCITY_DOME(21, "Parabolic Dynamics Swell");

        public final int ordinal;
        public final String label;

        OperatorType(int ordinal, String label) {
            this.ordinal = ordinal;
            this.label = label;
        }
    }

    public OperatorType type = OperatorType.SCALE_CONSTRAIN;
    public float intensity = 0.5f;
    public int seed = 12345;
    public float param1 = 0.0f;
    public float param2 = 0.0f;
    public boolean enabled = true;

    public TransformRecipeItem() {}

    public TransformRecipeItem(OperatorType type, float intensity, int seed, float param1, float param2) {
        this.type = type;
        this.intensity = intensity;
        this.seed = seed;
        this.param1 = param1;
        this.param2 = param2;
        this.enabled = true;
    }

    public TransformRecipeItem copy() {
        TransformRecipeItem clone = new TransformRecipeItem(type, intensity, seed, param1, param2);
        clone.enabled = this.enabled;
        return clone;
    }
}
