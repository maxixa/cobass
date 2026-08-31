package com.maxica.cobass.model;

public class PluginSlotItem {
    private int slotIndex;
    private String pluginId;
    private String name;
    private boolean bypassed;
    private float mix = 1.0f;

    public PluginSlotItem(int slotIndex, String pluginId, String name, boolean bypassed, float mix) {
        this.slotIndex = slotIndex;
        this.pluginId = pluginId != null ? pluginId : "";
        this.name = name != null ? name : "";
        this.bypassed = bypassed;
        this.mix = Math.max(0.0f, Math.min(1.0f, mix));
    }

    public int getSlotIndex() { return slotIndex; }
    public void setSlotIndex(int slotIndex) { this.slotIndex = slotIndex; }

    public String getPluginId() { return pluginId; }
    public void setPluginId(String pluginId) { this.pluginId = pluginId != null ? pluginId : ""; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name != null ? name : ""; }

    public boolean isBypassed() { return bypassed; }
    public void setBypassed(boolean bypassed) { this.bypassed = bypassed; }

    public float getMix() { return mix; }
    public void setMix(float mix) { this.mix = Math.max(0.0f, Math.min(1.0f, mix)); }

    public boolean isEmpty() { return pluginId == null || pluginId.isEmpty(); }
}
