package com.maxica.cobass.model;

import java.util.ArrayList;
import java.util.List;

public class StepPatternItem {

    public static class Step {
        public boolean active = false;
        public float velocity = 0.85f;
        public int pitchOffset = 0;       // Semitone shift (-24 to +24)
        public float gate = 0.75f;        // Step gate duration (0.05 to 2.0)
        public float nudge = 0.0f;        // Micro-timing offset (-0.5 to +0.5)
        public int ratchets = 1;          // 1=Single, 2=Double, 3=Triplet, 4=Quad, 8=Roll
        public float probability = 1.0f;  // 0.0 to 1.0

        public Step copy() {
            Step s = new Step();
            s.active = this.active;
            s.velocity = this.velocity;
            s.pitchOffset = this.pitchOffset;
            s.gate = this.gate;
            s.nudge = this.nudge;
            s.ratchets = this.ratchets;
            s.probability = this.probability;
            return s;
        }
    }

    public static class Lane {
        public int id;
        public String name = "Lane";
        public int midiNote = 60;         // Default note or sampler pad index (36=Kick, 38=Snare, 42=CHH)
        public int stepCount = 16;        // Polymeter length (1 to 64)
        public SnapGrid subdivision = SnapGrid.BEAT_1_4; // Step resolution (default 1/16)
        public float volume = 0.8f;
        public float pan = 0.0f;
        public boolean isMuted = false;
        public boolean isSolo = false;
        public float[] sampleData = null; // One-shot PCM for sampler drum lanes
        public final List<Step> steps = new ArrayList<>();

        public Lane(int id, String name, int midiNote, int stepCount) {
            this.id = id;
            this.name = name;
            this.midiNote = midiNote;
            this.stepCount = stepCount;
            for (int i = 0; i < 64; i++) {
                steps.add(new Step());
            }
        }

        public Lane copy() {
            Lane l = new Lane(id, name, midiNote, stepCount);
            l.subdivision = subdivision;
            l.volume = volume;
            l.pan = pan;
            l.isMuted = isMuted;
            l.isSolo = isSolo;
            if (sampleData != null) l.sampleData = sampleData.clone();
            l.steps.clear();
            for (Step s : steps) l.steps.add(s.copy());
            return l;
        }
    }

    private int id;
    private String name = "Pattern 1";
    private int baseLength = 16;
    private final List<Lane> lanes = new ArrayList<>();

    public StepPatternItem(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public List<Lane> getLanes() { return lanes; }
    public int getBaseLength() { return baseLength; }
    public void setBaseLength(int len) { this.baseLength = Math.max(1, Math.min(64, len)); }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getId() { return id; }

    public StepPatternItem copy() {
        StepPatternItem clone = new StepPatternItem(id, name);
        clone.baseLength = this.baseLength;
        for (Lane l : this.lanes) clone.lanes.add(l.copy());
        return clone;
    }
}
