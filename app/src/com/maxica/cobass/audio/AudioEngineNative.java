package com.maxica.cobass.audio;

import android.util.Log;
import com.maxica.cobass.core.Constants;
import com.maxica.cobass.model.PluginDescriptorItem;

public final class AudioEngineNative {
    private static boolean sLibraryLoaded = false;
    private static String sLoadError = null;

    static {
        try {
            try { System.loadLibrary("c++_shared"); } catch (Throwable ignored) {}
            System.loadLibrary(Constants.NATIVE_LIB_NAME);
            sLibraryLoaded = true;
            Log.i(Constants.APP_TAG, "libcobass_audio.so loaded successfully.");
        } catch (Throwable e) {
            sLoadError = e.getClass().getSimpleName() + ": " + e.getMessage();
            Log.e(Constants.APP_TAG, "Failed to load native library: " + sLoadError, e);
        }
    }

    public static boolean isLoaded() { return sLibraryLoaded; }
    public static String getLoadError() { return sLoadError; }

    public static native boolean nativeInit();
    public static native boolean nativeStart();
    public static native void nativeStop();
    public static native void nativeResetProject();

    // Multi-track Dynamic API
    public static native int nativeAddSynthTrack(String name);
    public static native int nativeAddAudioTrack(String name);
    public static native void nativeRemoveTrack(int trackId);

    public static native void nativeNoteOn(int trackId, int note, float velocity);
    public static native void nativeNoteOff(int trackId, int note);

    public static native void nativeSetTrackVolume(int trackId, float volume);
    public static native void nativeSetTrackPan(int trackId, float pan);
    public static native void nativeSetTrackMute(int trackId, boolean mute);
    public static native void nativeSetTrackSolo(int trackId, boolean solo);
    public static native void nativeSetTrackPhaseInvert(int trackId, boolean invert);
    public static native void nativeSetTrackParam(int trackId, int paramId, float value);
    public static native void nativeSetTrackFxParam(int trackId, int fxSlot, int paramId, float value);

    // Modular Plugin System JNI API
    public static native int nativeScanPlugins(String searchDirectory);
    public static native int nativeGetPluginCount();
    public static native PluginDescriptorItem nativeGetPluginDescriptor(int index);
    public static native PluginDescriptorItem nativeGetPluginDescriptorById(String pluginId);

    public static native boolean nativeSetTrackSynthPlugin(int trackId, String pluginId);
    public static native void nativeRemoveTrackSynthPlugin(int trackId);
    public static native String nativeGetTrackSynthPluginId(int trackId);

    public static native boolean nativeAddTrackFxPlugin(int trackId, int slotIndex, String pluginId);
    public static native void nativeRemoveTrackFxPlugin(int trackId, int slotIndex);
    public static native void nativeSetTrackFxBypass(int trackId, int slotIndex, boolean bypass);
    public static native boolean nativeIsTrackFxBypassed(int trackId, int slotIndex);
    public static native void nativeMoveTrackFxSlot(int trackId, int fromSlot, int toSlot);
    public static native String nativeGetTrackFxPluginId(int trackId, int slotIndex);

    public static native void nativeSetPluginParameter(int trackId, int slotIndex, int paramId, float value);
    public static native float nativeGetPluginParameter(int trackId, int slotIndex, int paramId);
    public static native String nativeGetPluginStateJson(int trackId, int slotIndex);
    public static native boolean nativeSetPluginStateJson(int trackId, int slotIndex, String jsonState);

    // Master & Telemetry API
    public static native void nativeSetMasterVolume(float volume);
    public static native void nativeSetMasterLimiter(boolean enabled);
    public static native float nativeGetTrackPeakL(int trackId);
    public static native float nativeGetTrackPeakR(int trackId);
    public static native float nativeGetMasterPeakL();
    public static native float nativeGetMasterPeakR();

    public static native void nativeLoadSample(int trackId, float[] data, int length, int channels);
    public static native float nativeGetTrackPlaybackPosition(int trackId);
    public static native void nativeSetTrackTrimAndFade(int trackId, float trimStart, float trimEnd, float fadeIn, float fadeOut);

    // Sequencer & Transport API
    public static native void nativeTransportPlayFromStart();
    public static native void nativeTransportPlay();
    public static native void nativeTransportPause();
    public static native void nativeTransportStop();
    public static native void nativeTransportSeek(long tick);
    public static native void nativeSetBpm(float bpm);
    public static native void nativeSetLoop(long startTick, long endTick, boolean enabled);
    public static native long nativeGetLoopStart();
    public static native long nativeGetLoopEnd();
    public static native boolean nativeIsLoopEnabled();

    public static native int nativeAddClip(int trackId, long startTick, long lengthTicks, String name);
    public static native void nativeRemoveClip(int clipId);
    public static native void nativeMoveClip(int clipId, int newTrackId, long newStartTick);
    public static native void nativeResizeClip(int clipId, long newLengthTicks);
    public static native void nativeClearClipNotes(int clipId);
    public static native void nativeAddNoteToClip(int clipId, int note, float vel, long startOffset, long len);

    // Fast Offline Audio Export API
    public static native boolean nativeExportWav(String path, float sampleRate, long totalTicks);
    public static native void nativeCancelExport();
    public static native float nativeGetExportProgress();

    public static native long nativeGetCurrentTick();
    public static native boolean nativeIsPlaying();
    public static native float nativeGetBpm();

    public static native int nativeGetSampleRate();
    public static native int nativeGetFramesPerBurst();
    public static native boolean nativeIsLowLatency();
    public static native int nativeGetTrackCount();
}
