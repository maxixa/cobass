package com.maxica.cobass.plugin;

import android.content.Context;
import com.maxica.cobass.audio.AudioEngineNative;
import com.maxica.cobass.model.PluginDescriptorItem;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PluginHostManager {
    private static PluginHostManager sInstance;

    private final List<PluginDescriptorItem> availablePlugins = new ArrayList<>();

    public static synchronized PluginHostManager getInstance() {
        if (sInstance == null) {
            sInstance = new PluginHostManager();
        }
        return sInstance;
    }

    private PluginHostManager() {}

    public void scanPlugins(Context context) {
        availablePlugins.clear();
        if (!AudioEngineNative.isLoaded()) return;

        // 1. Scan native APK library folder
        String nativeLibDir = context.getApplicationInfo().nativeLibraryDir;
        if (nativeLibDir != null) {
            AudioEngineNative.nativeScanPlugins(nativeLibDir);
        }

        // 2. Scan internal app data plugins folder
        File internalPluginDir = new File(context.getFilesDir(), "plugins");
        if (internalPluginDir.exists()) {
            AudioEngineNative.nativeScanPlugins(internalPluginDir.getAbsolutePath());
        }

        int count = AudioEngineNative.nativeGetPluginCount();
        for (int i = 0; i < count; i++) {
            PluginDescriptorItem item = AudioEngineNative.nativeGetPluginDescriptor(i);
            if (item != null) {
                availablePlugins.add(item);
            }
        }
    }

    public List<PluginDescriptorItem> getAvailablePlugins() {
        return Collections.unmodifiableList(availablePlugins);
    }

    public List<PluginDescriptorItem> getSynthPlugins() {
        List<PluginDescriptorItem> synths = new ArrayList<>();
        for (PluginDescriptorItem item : availablePlugins) {
            if (item.getType() == PluginDescriptorItem.Type.SYNTH) synths.add(item);
        }
        return Collections.unmodifiableList(synths);
    }

    public List<PluginDescriptorItem> getEffectPlugins() {
        List<PluginDescriptorItem> effects = new ArrayList<>();
        for (PluginDescriptorItem item : availablePlugins) {
            if (item.getType() == PluginDescriptorItem.Type.EFFECT) effects.add(item);
        }
        return Collections.unmodifiableList(effects);
    }

    public PluginDescriptorItem findPluginById(String pluginId) {
        if (pluginId == null || pluginId.isEmpty()) return null;
        for (PluginDescriptorItem item : availablePlugins) {
            if (pluginId.equals(item.getPluginId())) return item;
        }
        return AudioEngineNative.isLoaded() ? AudioEngineNative.nativeGetPluginDescriptorById(pluginId) : null;
    }
}
