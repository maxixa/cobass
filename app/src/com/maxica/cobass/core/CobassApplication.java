package com.maxica.cobass.core;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Process;
import android.util.Log;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

public class CobassApplication extends Application {

    public static final String EXTRA_CRASH_LOG = "extra_crash_log";
    public static final String EXTRA_CRASH_SUMMARY = "extra_crash_summary";

    @Override
    public void onCreate() {
        super.onCreate();
        final Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                String crashLog = buildCrashReport(this, thread, throwable);
                String summary = throwable != null ? (throwable.getClass().getSimpleName() + ": " + throwable.getMessage()) : "Unknown Fatal Error";

                Log.e(Constants.APP_TAG, "FATAL CRASH DETECTED:\n" + crashLog, throwable);

                Intent crashIntent = new Intent();
                crashIntent.setClassName(getApplicationContext(), "com.maxica.cobass.ui.CrashActivity");
                crashIntent.putExtra(EXTRA_CRASH_LOG, crashLog);
                crashIntent.putExtra(EXTRA_CRASH_SUMMARY, summary);
                crashIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

                startActivity(crashIntent);
            } catch (Throwable e) {
                Log.e(Constants.APP_TAG, "Error launching crash activity", e);
                if (defaultHandler != null) {
                    defaultHandler.uncaughtException(thread, throwable);
                    return;
                }
            }

            Process.killProcess(Process.myPid());
            System.exit(10);
        });
    }

    public static String buildCrashReport(Context context, Thread thread, Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());

        sb.append("=====================================================\n");
        sb.append("           COBASS DAW - CRASH DIAGNOSTIC LOG         \n");
        sb.append("=====================================================\n");
        sb.append("Timestamp:     ").append(timeStamp).append("\n");
        sb.append("Package:       ").append(context.getPackageName()).append("\n");
        sb.append("Thread:        ").append(thread != null ? thread.getName() : "Unknown").append("\n\n");

        sb.append("--- DEVICE & OS INFO ---\n");
        sb.append("Device:        ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append(" (").append(Build.DEVICE).append(")\n");
        sb.append("Android OS:    Android ").append(Build.VERSION.RELEASE).append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("Supported ABI: ").append(Arrays.toString(Build.SUPPORTED_ABIS)).append("\n");
        sb.append("RAM Info:      Free ").append(Runtime.getRuntime().freeMemory() / 1024).append("KB / Total ")
                .append(Runtime.getRuntime().totalMemory() / 1024).append("KB / Max ")
                .append(Runtime.getRuntime().maxMemory() / 1024).append("KB\n\n");

        sb.append("--- EXCEPTION DETAILS ---\n");
        if (throwable != null) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            throwable.printStackTrace(pw);
            sb.append(sw.toString()).append("\n");
        } else {
            sb.append("No throwable object provided.\n");
        }
        sb.append("=====================================================\n");

        return sb.toString();
    }
}
