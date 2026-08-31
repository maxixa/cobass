package com.maxica.cobass.project;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public final class PresetUnpacker {

    private PresetUnpacker() {}

    public static void unpackFactoryPresets(Context context) {
        try {
            AssetManager am = context.getAssets();
            String[] pluginDirs = am.list("");
            if (pluginDirs == null) return;

            File basePresetDir = new File(context.getFilesDir(), "presets");
            if (!basePresetDir.exists()) basePresetDir.mkdirs();

            for (String item : pluginDirs) {
                if (item.startsWith("com.maxica.cobass.")) {
                    String[] patchFiles = am.list(item);
                    if (patchFiles != null && patchFiles.length > 0) {
                        File targetDir = new File(basePresetDir, item);
                        if (!targetDir.exists()) targetDir.mkdirs();

                        for (String patchName : patchFiles) {
                            File targetPatch = new File(targetDir, patchName);
                            if (!targetPatch.exists() || targetPatch.length() == 0) {
                                try (InputStream is = am.open(item + "/" + patchName);
                                     FileOutputStream fos = new FileOutputStream(targetPatch)) {
                                    byte[] buf = new byte[4096];
                                    int read;
                                    while ((read = is.read(buf)) > 0) {
                                        fos.write(buf, 0, read);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }
}
