package com.maxica.cobass.plugin;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.util.Log;
import com.maxica.cobass.audio.AudioEngineNative;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public class PluginApkInstaller {

    public static final String ACTION_COBASS_PLUGIN = "com.maxica.cobass.PLUGIN";
    private static final String TARGET_ABI_PREFIX = "lib/arm64-v8a/";

    /**
     * Scans all installed Android applications on the device declaring action "com.maxica.cobass.PLUGIN",
     * extracts their arm64-v8a native shared libraries into the host app internal plugin storage,
     * and triggers a native engine dynamic catalog reload.
     */
    public static int scanAndMountInstalledPluginApks(Context context) {
        PackageManager pm = context.getPackageManager();
        Intent intent = new Intent(ACTION_COBASS_PLUGIN);
        List<ResolveInfo> plugins = pm.queryIntentServices(intent, PackageManager.GET_META_DATA);

        File internalPluginDir = new File(context.getCodeCacheDir(), "plugins");
        if (!internalPluginDir.exists()) internalPluginDir.mkdirs();

        int mountedCount = 0;
        for (ResolveInfo info : plugins) {
            if (info.serviceInfo == null || info.serviceInfo.packageName == null) continue;
            String packageName = info.serviceInfo.packageName;

            try {
                ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
                String apkPath = appInfo.publicSourceDir != null ? appInfo.publicSourceDir : appInfo.sourceDir;
                if (apkPath == null) continue;

                File apkFile = new File(apkPath);
                if (!apkFile.exists()) continue;

                if (extractSoFromApkFile(apkFile, internalPluginDir)) {
                    mountedCount++;
                }
            } catch (Exception e) {
                Log.e("CobassPlugin", "Failed to mount installed plugin APK: " + packageName, e);
            }
        }

        if (AudioEngineNative.isLoaded()) {
            AudioEngineNative.nativeScanPlugins(internalPluginDir.getAbsolutePath());
        }

        return mountedCount;
    }

    /**
     * Sideloads a standalone plugin directly from an APK / bundle file picked via Storage Access Framework.
     */
    public static boolean installPluginFromUri(Context context, Uri uri) {
        File internalPluginDir = new File(context.getCodeCacheDir(), "plugins");
        if (!internalPluginDir.exists()) internalPluginDir.mkdirs();

        boolean success = false;
        try (InputStream is = context.getContentResolver().openInputStream(uri);
             ZipInputStream zis = new ZipInputStream(is)) {

            ZipEntry entry;
            byte[] buffer = new byte[8192];

            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if ((name.startsWith(TARGET_ABI_PREFIX) || name.startsWith("lib/")) && name.endsWith(".so")) {
                    String fileName = new File(name).getName();
                    File outFile = new File(internalPluginDir, fileName);

                    try (FileOutputStream fos = new FileOutputStream(outFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                    outFile.setReadable(true, false);
                    outFile.setExecutable(true, false);
                    success = true;
                }
                zis.closeEntry();
            }
        } catch (Exception e) {
            Log.e("CobassPlugin", "Error extracting plugin from URI: " + uri, e);
            return false;
        }

        if (success && AudioEngineNative.isLoaded()) {
            AudioEngineNative.nativeScanPlugins(internalPluginDir.getAbsolutePath());
            PluginHostManager.getInstance().scanPlugins(context);
        }

        return success;
    }

    private static boolean extractSoFromApkFile(File apkFile, File targetDir) {
        boolean extracted = false;
        try (ZipFile zip = new ZipFile(apkFile)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            byte[] buffer = new byte[8192];

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();

                if ((name.startsWith(TARGET_ABI_PREFIX) || name.startsWith("lib/")) && name.endsWith(".so")) {
                    String fileName = new File(name).getName();
                    File outFile = new File(targetDir, fileName);

                    if (!outFile.exists() || outFile.length() != entry.getSize()) {
                        try (InputStream is = zip.getInputStream(entry);
                             FileOutputStream fos = new FileOutputStream(outFile)) {
                            int len;
                            while ((len = is.read(buffer)) > 0) {
                                fos.write(buffer, 0, len);
                            }
                        }
                        outFile.setReadable(true, false);
                        outFile.setExecutable(true, false);
                    }
                    extracted = true;
                }
            }
        } catch (Exception e) {
            Log.e("CobassPlugin", "Failed to extract .so from APK file: " + apkFile.getName(), e);
            return false;
        }
        return extracted;
    }
}
