package com.maxica.cobass.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PluginDescriptorItem {
    public enum Type {
        SYNTH,
        EFFECT
    }

    private final String pluginId;
    private final String name;
    private final String vendor;
    private final String version;
    private final String libraryPath;
    private final Type type;
    private final boolean supportsMidi;
    private final boolean supportsSidechain;
    private final List<PluginParamItem> parameters = new ArrayList<>();

    public PluginDescriptorItem(String pluginId, String name, String vendor, String version,
                                String libraryPath, int typeOrdinal,
                                boolean supportsMidi, boolean supportsSidechain) {
        this.pluginId = pluginId != null ? pluginId : "";
        this.name = name != null ? name : "";
        this.vendor = vendor != null ? vendor : "";
        this.version = version != null ? version : "";
        this.libraryPath = libraryPath != null ? libraryPath : "";
        this.type = (typeOrdinal == 0) ? Type.SYNTH : Type.EFFECT;
        this.supportsMidi = supportsMidi;
        this.supportsSidechain = supportsSidechain;
    }

    public void addParameter(PluginParamItem param) {
        if (param != null) {
            parameters.add(param);
        }
    }

    public String getPluginId() { return pluginId; }
    public String getName() { return name; }
    public String getVendor() { return vendor; }
    public String getVersion() { return version; }
    public String getLibraryPath() { return libraryPath; }
    public Type getType() { return type; }
    public boolean isSupportsMidi() { return supportsMidi; }
    public boolean isSupportsSidechain() { return supportsSidechain; }
    public List<PluginParamItem> getParameters() { return Collections.unmodifiableList(parameters); }
}
