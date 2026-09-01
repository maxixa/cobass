package com.maxica.cobass.model;

import android.graphics.Color;
import java.util.ArrayList;
import java.util.List;

public class TrackItem {
    public enum Type { SYNTH, AUDIO, STEP_SEQUENCER }

    public static class PluginSlotState {
        public int slotIndex;
        public String pluginId = "";
        public boolean bypassed = false;
        public float mix = 1.0f;
        public String stateJson = "{}";

        public PluginSlotState() {}

        public PluginSlotState(int slotIndex, String pluginId, boolean bypassed, float mix, String stateJson) {
            this.slotIndex = slotIndex;
            this.pluginId = pluginId != null ? pluginId : "";
            this.bypassed = bypassed;
            this.mix = mix;
            this.stateJson = stateJson != null ? stateJson : "{}";
        }

        public PluginSlotState copy() {
            return new PluginSlotState(slotIndex, pluginId, bypassed, mix, stateJson);
        }
    }

    private final int id;
    private String name;
    private final Type type;
    private float volume = 0.8f;
    private float pan = 0.0f;
    private boolean muted = false;
    private boolean solo = false;
    private boolean phaseInverted = false;
    private int color;
    private int currentNote = 60; // Middle C

    // Persistent Modular Plugin Engine States
    private String instrumentPluginId = "";
    private String instrumentPluginStateJson = "{}";
    private final List<PluginSlotState> insertFxSlots = new ArrayList<>();
    private StepPatternItem stepPattern = null;

    // Persistent Core Utility FX Rack Parameters
    private float eqLow = 0.0f;        // -18.0 to +18.0 dB
    private float eqMid = 0.0f;        // -18.0 to +18.0 dB
    private float eqHigh = 0.0f;       // -18.0 to +18.0 dB
    private float compThresh = -12.0f; // -40.0 to 0.0 dB
    private float compRatio = 3.0f;    // 1.0 to 20.0
    private float reverbMix = 0.30f;   // 0.0 to 1.0
    private float delayMix = 0.35f;    // 0.0 to 1.0

    public TrackItem(int id, String name, Type type) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.color = (type == Type.SYNTH) ? Color.parseColor("#0A84FF") : Color.parseColor("#FF9F0A");
    }

    public int getId() { return id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Type getType() { return type; }

    public float getVolume() { return volume; }
    public void setVolume(float volume) { this.volume = volume; }

    public float getPan() { return pan; }
    public void setPan(float pan) { this.pan = pan; }

    public boolean isMuted() { return muted; }
    public void setMuted(boolean muted) { this.muted = muted; }

    public boolean isSolo() { return solo; }
    public void setSolo(boolean solo) { this.solo = solo; }

    public boolean isPhaseInverted() { return phaseInverted; }
    public void setPhaseInverted(boolean inverted) { this.phaseInverted = inverted; }

    public int getColor() { return color; }
    public void setColor(int color) { this.color = color; }

    public int getCurrentNote() { return currentNote; }
    public void setCurrentNote(int currentNote) { this.currentNote = currentNote; }

    // Modular Plugin Getters & Setters
    public String getInstrumentPluginId() { return instrumentPluginId; }
    public void setInstrumentPluginId(String pluginId) { this.instrumentPluginId = pluginId != null ? pluginId : ""; }

    public String getInstrumentPluginStateJson() { return instrumentPluginStateJson; }
    public void setInstrumentPluginStateJson(String json) { this.instrumentPluginStateJson = json != null ? json : "{}"; }

    public List<PluginSlotState> getInsertFxSlots() { return insertFxSlots; }

    public StepPatternItem getStepPattern() { return stepPattern; }
    public void setStepPattern(StepPatternItem stepPattern) { this.stepPattern = stepPattern; }


    // Core Utility FX Getters & Setters
    public float getEqLow() { return eqLow; }
    public void setEqLow(float eqLow) { this.eqLow = eqLow; }

    public float getEqMid() { return eqMid; }
    public void setEqMid(float eqMid) { this.eqMid = eqMid; }

    public float getEqHigh() { return eqHigh; }
    public void setEqHigh(float eqHigh) { this.eqHigh = eqHigh; }

    public float getCompThresh() { return compThresh; }
    public void setCompThresh(float compThresh) { this.compThresh = compThresh; }

    public float getCompRatio() { return compRatio; }
    public void setCompRatio(float compRatio) { this.compRatio = compRatio; }

    public float getReverbMix() { return reverbMix; }
    public void setReverbMix(float reverbMix) { this.reverbMix = reverbMix; }

    public float getDelayMix() { return delayMix; }
    public void setDelayMix(float delayMix) { this.delayMix = delayMix; }
}
