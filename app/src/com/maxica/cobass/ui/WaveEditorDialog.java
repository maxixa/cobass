package com.maxica.cobass.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import com.maxica.cobass.R;
import com.maxica.cobass.audio.AudioEngineNative;
import com.maxica.cobass.model.ClipItem;
import com.maxica.cobass.model.TrackItem;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

public class WaveEditorDialog extends Dialog {

    public interface OnWaveActionListener {
        void onSlicesExportedToArranger(List<ClipItem> slicedClips);
        void onWaveModified();
    }

    private static class WaveSnapshot {
        float[] sampleData;
        float trimStart;
        float trimEnd;
        float fadeIn;
        float fadeOut;
        List<Float> slices;

        WaveSnapshot(float[] data, float start, float end, float fIn, float fOut, List<Float> sl) {
            this.sampleData = data != null ? data.clone() : null;
            this.trimStart = start;
            this.trimEnd = end;
            this.fadeIn = fIn;
            this.fadeOut = fOut;
            this.slices = new ArrayList<>(sl);
        }
    }

    private static float[] sSampleClipboard = null;

    private final Activity activity;
    private final ClipItem clip;
    private final OnWaveActionListener actionListener;
    private final Runnable onDismissCallback;
    private WaveEditorCanvasView waveCanvas;

    private final Handler transportHandler = new Handler(Looper.getMainLooper());
    private boolean isPlaying = false;
    private boolean isLooping = false;
    private boolean isFollowing = true;
    private boolean isRunning = true;

    private static final int MAX_UNDO_STACK = 30;
    private final Deque<WaveSnapshot> undoStack = new ArrayDeque<>();
    private final Deque<WaveSnapshot> redoStack = new ArrayDeque<>();

    private Button btnUndo;
    private Button btnRedo;
    private Button btnPlay;
    private Button btnStop;
    private Button btnLoop;
    private Button btnFollow;
    private Button btnZeroSnap;
    private Button btnToolSelect;
    private Button btnToolTrim;
    private Button btnToolSlice;

    // Microphone Recording State
    private AudioRecord audioRecordInstance = null;
    private boolean isRecordingMic = false;

    public WaveEditorDialog(@NonNull Activity activity, ClipItem clip, Runnable onDismissCallback) {
        super(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        this.activity = activity;
        this.clip = clip;
        this.actionListener = null;
        this.onDismissCallback = onDismissCallback;
    }

    public WaveEditorDialog(@NonNull Activity activity, ClipItem clip, OnWaveActionListener listener, Runnable onDismissCallback) {
        super(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        this.activity = activity;
        this.clip = clip;
        this.actionListener = listener;
        this.onDismissCallback = onDismissCallback;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_wave_editor);

        if (getWindow() != null) {
            getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.parseColor("#121316")));
        }

        TextView txtTitle = findViewById(R.id.txtWaveClipTitle);
        txtTitle.setText("Wave: " + clip.getName());

        waveCanvas = findViewById(R.id.waveCanvas);
        waveCanvas.setClip(clip);

        waveCanvas.setEventListener(new WaveEditorCanvasView.OnWaveEventListener() {
            @Override
            public void onTrimAndFadeChanged(float startRatio, float endRatio, float fadeInRatio, float fadeOutRatio) {
                syncTrimAndFadeToNative();
            }

            @Override
            public void onSelectionChanged(float selStartRatio, float selEndRatio) {}

            @Override
            public void onSliceMarkerAudition(float sliceStartFrac, float sliceEndFrac) {
                if (AudioEngineNative.isLoaded()) {
                    AudioEngineNative.nativeSetTrackTrimAndFade(clip.getTrackId(), sliceStartFrac, sliceEndFrac, 0.01f, 0.01f);
                    AudioEngineNative.nativeNoteOn(clip.getTrackId(), 60, 1.0f);
                }
            }

            @Override
            public void onScrubRequested(float fraction) {
                if (AudioEngineNative.isLoaded()) {
                    AudioEngineNative.nativeNoteOn(clip.getTrackId(), 60, 1.0f);
                }
            }

            @Override
            public void onGestureActionCommitted() {
                captureUndoPoint();
            }
        });

        // 1. Undo & Redo Controls
        btnUndo = findViewById(R.id.btnWaveUndo);
        btnRedo = findViewById(R.id.btnWaveRedo);

        btnUndo.setOnClickListener(v -> performUndo());
        btnRedo.setOnClickListener(v -> performRedo());
        updateUndoRedoUI();

        // 2. Transport Controls
        btnPlay = findViewById(R.id.btnWavePlay);
        btnStop = findViewById(R.id.btnWaveStop);
        btnLoop = findViewById(R.id.btnWaveLoop);
        btnFollow = findViewById(R.id.btnWaveFollow);
        Button btnZoomDialog = findViewById(R.id.btnWaveZoomDialog);

        btnPlay.setOnClickListener(v -> {
            if (AudioEngineNative.isLoaded()) {
                isPlaying = true;
                syncTrimAndFadeToNative();
                AudioEngineNative.nativeNoteOn(clip.getTrackId(), 60, 1.0f);
                updateTransportUI();
            }
        });

        btnStop.setOnClickListener(v -> {
            if (AudioEngineNative.isLoaded()) {
                isPlaying = false;
                AudioEngineNative.nativeNoteOff(clip.getTrackId(), 60);
                waveCanvas.setPlaybackState(0.0f, false);
                updateTransportUI();
            }
        });

        btnLoop.setOnClickListener(v -> {
            isLooping = !isLooping;
            if (AudioEngineNative.isLoaded()) {
                AudioEngineNative.nativeSetTrackParam(clip.getTrackId(), 0, isLooping ? 1.0f : 0.0f);
            }
            updateTransportUI();
        });

        btnFollow.setOnClickListener(v -> {
            isFollowing = !isFollowing;
            waveCanvas.setFollowPlayhead(isFollowing);
            updateTransportUI();
        });

        btnZoomDialog.setOnClickListener(v -> showZoomDialog());

        // 3. Tool Modes
        btnToolSelect = findViewById(R.id.btnWaveToolSelect);
        btnToolTrim = findViewById(R.id.btnWaveToolTrim);
        btnToolSlice = findViewById(R.id.btnWaveToolSlice);

        btnToolSelect.setOnClickListener(v -> setToolMode(WaveEditorCanvasView.Mode.RANGE_SELECT));
        btnToolTrim.setOnClickListener(v -> setToolMode(WaveEditorCanvasView.Mode.TRIM_FADE));
        btnToolSlice.setOnClickListener(v -> setToolMode(WaveEditorCanvasView.Mode.SLICE));
        setToolMode(WaveEditorCanvasView.Mode.RANGE_SELECT);

        // 4. Zero-Crossing Snap Toggle
        btnZeroSnap = findViewById(R.id.btnWaveZeroSnap);
        btnZeroSnap.setOnClickListener(v -> {
            boolean nextState = !waveCanvas.isSnapZeroCrossing();
            waveCanvas.setSnapZeroCrossing(nextState);
            btnZeroSnap.setText(nextState ? "Ø SNAP: ON" : "Ø SNAP: OFF");
            btnZeroSnap.setTextColor(nextState ? Color.parseColor("#30D158") : Color.parseColor("#8E8E93"));
            btnZeroSnap.setBackgroundColor(nextState ? Color.parseColor("#163824") : Color.parseColor("#242734"));
        });

        // 5. Phase 6 I/O: Import, Record, Export
        Button btnImport = findViewById(R.id.btnWaveImport);
        btnImport.setOnClickListener(v -> launchSafAudioPicker());

        Button btnRecord = findViewById(R.id.btnWaveRecord);
        btnRecord.setOnClickListener(v -> showRecordMicDialog());

        Button btnExport = findViewById(R.id.btnWaveExport);
        btnExport.setOnClickListener(v -> exportAndShareWav());

        // 6. Studios & DSP
        Button btnPitchStudio = findViewById(R.id.btnWavePitchStudio);
        btnPitchStudio.setOnClickListener(v -> showPitchStretchStudioDialog());

        Button btnSliceStudio = findViewById(R.id.btnWaveSliceStudio);
        btnSliceStudio.setOnClickListener(v -> showSliceStudioDialog());

        Button btnDspStudio = findViewById(R.id.btnWaveDspStudio);
        btnDspStudio.setOnClickListener(v -> showDspStudioDialog());

        Button btnAudition = findViewById(R.id.btnWaveAudition);
        btnAudition.setOnClickListener(v -> {
            if (AudioEngineNative.isLoaded()) {
                syncTrimAndFadeToNative();
                AudioEngineNative.nativeNoteOn(clip.getTrackId(), 60, 1.0f);
            }
        });

        Button btnClose = findViewById(R.id.btnWaveClose);
        btnClose.setOnClickListener(v -> dismiss());

        updateTransportUI();
        syncTrimAndFadeToNative();
        startPlayheadTicker();
    }

    private void setToolMode(WaveEditorCanvasView.Mode mode) {
        waveCanvas.setMode(mode);
        CobassInteraction.applyToolState(btnToolSelect, mode == WaveEditorCanvasView.Mode.RANGE_SELECT, false);
        CobassInteraction.applyToolState(btnToolTrim, mode == WaveEditorCanvasView.Mode.TRIM_FADE, false);
        CobassInteraction.applyToolState(btnToolSlice, mode == WaveEditorCanvasView.Mode.SLICE, false);
    }

    private void captureUndoPoint() {
        if (clip == null) return;
        if (undoStack.size() >= MAX_UNDO_STACK) undoStack.removeLast();
        undoStack.push(new WaveSnapshot(clip.getSampleData(), waveCanvas.getTrimStartRatio(), waveCanvas.getTrimEndRatio(), waveCanvas.getFadeInRatio(), waveCanvas.getFadeOutRatio(), waveCanvas.getSliceMarkers()));
        redoStack.clear();
        updateUndoRedoUI();
    }

    private void performUndo() {
        if (undoStack.isEmpty() || clip == null) return;
        redoStack.push(new WaveSnapshot(clip.getSampleData(), waveCanvas.getTrimStartRatio(), waveCanvas.getTrimEndRatio(), waveCanvas.getFadeInRatio(), waveCanvas.getFadeOutRatio(), waveCanvas.getSliceMarkers()));
        WaveSnapshot prev = undoStack.pop();

        clip.setSampleData(prev.sampleData != null ? prev.sampleData.clone() : null);
        waveCanvas.setTrimAndFadeRatios(prev.trimStart, prev.trimEnd, prev.fadeIn, prev.fadeOut);
        waveCanvas.setSliceMarkers(prev.slices);
        syncSampleToNative();
        syncTrimAndFadeToNative();
        waveCanvas.rebuildMipmaps();
        waveCanvas.invalidate();
        updateUndoRedoUI();
        Toast.makeText(getContext(), "↶ Undo", Toast.LENGTH_SHORT).show();
    }

    private void performRedo() {
        if (redoStack.isEmpty() || clip == null) return;
        undoStack.push(new WaveSnapshot(clip.getSampleData(), waveCanvas.getTrimStartRatio(), waveCanvas.getTrimEndRatio(), waveCanvas.getFadeInRatio(), waveCanvas.getFadeOutRatio(), waveCanvas.getSliceMarkers()));
        WaveSnapshot next = redoStack.pop();

        clip.setSampleData(next.sampleData != null ? next.sampleData.clone() : null);
        waveCanvas.setTrimAndFadeRatios(next.trimStart, next.trimEnd, next.fadeIn, next.fadeOut);
        waveCanvas.setSliceMarkers(next.slices);
        syncSampleToNative();
        syncTrimAndFadeToNative();
        waveCanvas.rebuildMipmaps();
        waveCanvas.invalidate();
        updateUndoRedoUI();
        Toast.makeText(getContext(), "↷ Redo", Toast.LENGTH_SHORT).show();
    }

    private void updateUndoRedoUI() {
        CobassInteraction.applyUndoRedoState(btnUndo, !undoStack.isEmpty());
        CobassInteraction.applyUndoRedoState(btnRedo, !redoStack.isEmpty());
    }

    // --- PHASE 6: SAF FILE IMPORT ---
    public static final int REQUEST_CODE_IMPORT_AUDIO = 2048;

    private void launchSafAudioPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        String[] mimeTypes = {"audio/wav", "audio/x-wav", "audio/mpeg", "audio/mp3", "audio/flac", "audio/ogg", "audio/aac", "audio/mp4", "audio/m4a"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        activity.startActivityForResult(intent, REQUEST_CODE_IMPORT_AUDIO);
    }

    public void importAudioFromUri(Uri uri) {
        if (uri == null) return;
        new Thread(() -> {
            try {
                float[] decodedPcm = decodeAudioUriToPcm(getContext(), uri, 48000);
                if (decodedPcm != null && decodedPcm.length > 0) {
                    activity.runOnUiThread(() -> {
                        captureUndoPoint();
                        clip.setSampleData(decodedPcm);
                        waveCanvas.setTrimAndFadeRatios(0.0f, 1.0f, 0.02f, 0.02f);
                        syncSampleToNative();
                        syncTrimAndFadeToNative();
                        waveCanvas.rebuildMipmaps();
                        waveCanvas.invalidate();
                        updateUndoRedoUI();
                        Toast.makeText(getContext(), "Imported: " + (decodedPcm.length / 48000.0f) + "s audio", Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                activity.runOnUiThread(() -> Toast.makeText(getContext(), "Import Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    public static float[] decodeAudioUriToPcm(Context context, Uri uri, int targetSampleRate) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(context, uri, null);

        int audioTrackIndex = -1;
        MediaFormat format = null;
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat mf = extractor.getTrackFormat(i);
            String mime = mf.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) {
                audioTrackIndex = i;
                format = mf;
                break;
            }
        }
        if (audioTrackIndex < 0 || format == null) {
            extractor.release();
            throw new Exception("No audio stream found in selected file");
        }

        extractor.selectTrack(audioTrackIndex);
        String mime = format.getString(MediaFormat.KEY_MIME);
        MediaCodec codec = MediaCodec.createDecoderByType(mime);
        codec.configure(format, null, null, 0);
        codec.start();

        int srcRate = format.containsKey(MediaFormat.KEY_SAMPLE_RATE) ? format.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 48000;
        int channels = format.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ? format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 1;

        ByteBuffer[] inputBuffers = codec.getInputBuffers();
        ByteBuffer[] outputBuffers = codec.getOutputBuffers();
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

        ByteArrayOutputStream pcmStream = new ByteArrayOutputStream();
        boolean sawInputEOS = false;
        boolean sawOutputEOS = false;

        while (!sawOutputEOS) {
            if (!sawInputEOS) {
                int inIdx = codec.dequeueInputBuffer(10000);
                if (inIdx >= 0) {
                    ByteBuffer dstBuf = inputBuffers[inIdx];
                    int sampleSize = extractor.readSampleData(dstBuf, 0);
                    if (sampleSize < 0) {
                        sawInputEOS = true;
                        codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    } else {
                        codec.queueInputBuffer(inIdx, 0, sampleSize, extractor.getSampleTime(), 0);
                        extractor.advance();
                    }
                }
            }

            int outIdx = codec.dequeueOutputBuffer(info, 10000);
            if (outIdx >= 0) {
                ByteBuffer buf = outputBuffers[outIdx];
                byte[] chunk = new byte[info.size];
                buf.position(info.offset);
                buf.get(chunk);
                buf.clear();
                pcmStream.write(chunk);
                codec.releaseOutputBuffer(outIdx, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    sawOutputEOS = true;
                }
            } else if (outIdx == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                outputBuffers = codec.getOutputBuffers();
            }
        }

        codec.stop();
        codec.release();
        extractor.release();

        byte[] rawBytes = pcmStream.toByteArray();
        int totalShorts = rawBytes.length / 2;
        int totalFrames = totalShorts / channels;
        float[] pcmFloat = new float[totalFrames];

        ByteBuffer bb = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN);
        final float invScale = 1.0f / (32768.0f * channels);
        for (int i = 0; i < totalFrames; i++) {
            float sum = 0.0f;
            for (int c = 0; c < channels; c++) {
                sum += bb.getShort();
            }
            pcmFloat[i] = sum * invScale;
        }

        if (srcRate != targetSampleRate && srcRate > 0) {
            return resamplePcm(pcmFloat, srcRate, targetSampleRate);
        }
        return pcmFloat;
    }

    public static float[] resamplePcm(float[] input, int srcRate, int dstRate) {
        if (srcRate == dstRate || input == null || input.length == 0) return input;
        double ratio = (double) srcRate / dstRate;
        int outLen = (int) (input.length / ratio);
        float[] output = new float[outLen];
        for (int i = 0; i < outLen; i++) {
            double srcPos = i * ratio;
            int idx = (int) srcPos;
            float frac = (float) (srcPos - idx);
            if (idx < input.length - 1) {
                output[i] = input[idx] + frac * (input[idx + 1] - input[idx]);
            } else if (idx < input.length) {
                output[i] = input[idx];
            }
        }
        return output;
    }

    // --- PHASE 6: IN-DIALOG MICROPHONE RECORDING ---
    private void showRecordMicDialog() {
        Dialog recDialog = new Dialog(getContext());
        recDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.parseColor("#1C1E26"));
        layout.setPadding(28, 24, 28, 24);

        TextView title = new TextView(getContext());
        title.setText("🎙 Live Microphone Recorder");
        title.setTextColor(Color.parseColor("#FF453A"));
        title.setTextSize(16f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(title);

        TextView txtStatus = new TextView(getContext());
        txtStatus.setText("Ready to record (48 kHz Studio PCM)");
        txtStatus.setTextColor(Color.parseColor("#8E8E93"));
        txtStatus.setTextSize(12f);
        txtStatus.setPadding(0, 4, 0, 12);
        layout.addView(txtStatus);

        ProgressBar meter = new ProgressBar(getContext(), null, android.R.attr.progressBarStyleHorizontal);
        meter.setMax(100);
        meter.setProgress(0);
        layout.addView(meter);

        Button btnToggleRec = new Button(getContext());
        btnToggleRec.setText("🔴 START RECORDING");
        btnToggleRec.setBackgroundColor(Color.parseColor("#FF453A"));
        btnToggleRec.setTextColor(Color.WHITE);
        btnToggleRec.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        layout.addView(btnToggleRec);

        ByteArrayOutputStream recStream = new ByteArrayOutputStream();
        Handler meterHandler = new Handler(Looper.getMainLooper());

        btnToggleRec.setOnClickListener(v -> {
            if (!isRecordingMic) {
                // Start
                try {
                    int bufSize = AudioRecord.getMinBufferSize(48000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                    audioRecordInstance = new AudioRecord(MediaRecorder.AudioSource.MIC, 48000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(bufSize, 4096));
                    audioRecordInstance.startRecording();
                    isRecordingMic = true;
                    recStream.reset();

                    btnToggleRec.setText("⏹ STOP & LOAD RECORDING");
                    btnToggleRec.setBackgroundColor(Color.parseColor("#D97706"));
                    txtStatus.setText("🔴 Recording Live Mic Take...");

                    new Thread(() -> {
                        byte[] buffer = new byte[2048];
                        while (isRecordingMic && audioRecordInstance != null) {
                            int read = audioRecordInstance.read(buffer, 0, buffer.length);
                            if (read > 0) {
                                recStream.write(buffer, 0, read);
                                float maxPeak = 0f;
                                ByteBuffer sbb = ByteBuffer.wrap(buffer, 0, read).order(ByteOrder.LITTLE_ENDIAN);
                                while (sbb.remaining() >= 2) {
                                    maxPeak = Math.max(maxPeak, Math.abs(sbb.getShort() / 32768.0f));
                                }
                                final int prog = (int) (maxPeak * 100);
                                meterHandler.post(() -> meter.setProgress(prog));
                            }
                        }
                    }).start();
                } catch (Exception e) {
                    Toast.makeText(getContext(), "Mic Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            } else {
                // Stop & Commit
                isRecordingMic = false;
                if (audioRecordInstance != null) {
                    audioRecordInstance.stop();
                    audioRecordInstance.release();
                    audioRecordInstance = null;
                }

                byte[] recordedBytes = recStream.toByteArray();
                if (recordedBytes.length > 256) {
                    int totalFrames = recordedBytes.length / 2;
                    float[] pcm = new float[totalFrames];
                    ByteBuffer bb = ByteBuffer.wrap(recordedBytes).order(ByteOrder.LITTLE_ENDIAN);
                    for (int i = 0; i < totalFrames; i++) {
                        pcm[i] = bb.getShort() / 32768.0f;
                    }

                    captureUndoPoint();
                    clip.setSampleData(pcm);
                    waveCanvas.setTrimAndFadeRatios(0.0f, 1.0f, 0.02f, 0.02f);
                    syncSampleToNative();
                    syncTrimAndFadeToNative();
                    waveCanvas.rebuildMipmaps();
                    waveCanvas.invalidate();
                    updateUndoRedoUI();
                    Toast.makeText(getContext(), "Loaded recorded take (" + (totalFrames / 48000.0f) + "s)", Toast.LENGTH_SHORT).show();
                }
                recDialog.dismiss();
            }
        });

        Button btnCancel = new Button(getContext());
        btnCancel.setText("Cancel");
        btnCancel.setBackgroundColor(Color.parseColor("#2C2F3C"));
        btnCancel.setTextColor(Color.WHITE);
        btnCancel.setOnClickListener(v -> {
            if (isRecordingMic && audioRecordInstance != null) {
                isRecordingMic = false;
                audioRecordInstance.stop();
                audioRecordInstance.release();
                audioRecordInstance = null;
            }
            recDialog.dismiss();
        });
        layout.addView(btnCancel);

        recDialog.setContentView(layout);
        CobassDialogShell.configureWindow(recDialog);
        recDialog.show();
    }

    // --- PHASE 6: DIRECT WAV EXPORTER & SHARING ---
    private void exportAndShareWav() {
        float[] pcm = clip.getSampleData();
        if (pcm == null || pcm.length == 0) {
            Toast.makeText(getContext(), "No sample buffer to export", Toast.LENGTH_SHORT).show();
            return;
        }

        File exportsDir = new File(getContext().getExternalFilesDir(null), "Exports");
        if (!exportsDir.exists()) exportsDir.mkdirs();

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String fileName = clip.getName().replaceAll("[^a-zA-Z0-9_-]", "_") + "_" + timeStamp + ".wav";
        File targetWav = new File(exportsDir, fileName);

        // Extract active trimmed and faded slice buffer
        int startSpl = (int) (waveCanvas.getTrimStartRatio() * pcm.length);
        int endSpl = (int) (waveCanvas.getTrimEndRatio() * pcm.length);
        int activeLen = Math.max(128, endSpl - startSpl);
        float[] exportPcm = new float[activeLen];
        System.arraycopy(pcm, startSpl, exportPcm, 0, activeLen);

        int fadeInFrames = (int) (waveCanvas.getFadeInRatio() * activeLen);
        int fadeOutFrames = (int) (waveCanvas.getFadeOutRatio() * activeLen);

        for (int i = 0; i < activeLen; i++) {
            float env = 1.0f;
            if (fadeInFrames > 0 && i < fadeInFrames) {
                env *= Math.sin((float) i / fadeInFrames * 1.57079632679f);
            }
            if (fadeOutFrames > 0 && i >= activeLen - fadeOutFrames) {
                env *= Math.sin((float) (activeLen - i) / fadeOutFrames * 1.57079632679f);
            }
            exportPcm[i] *= env;
        }

        writePcmFloatToWav(targetWav, exportPcm, 48000);

        try {
            Uri contentUri = FileProvider.getUriForFile(getContext(), "com.maxica.cobass.fileprovider", targetWav);
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("audio/wav");
            shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            getContext().startActivity(Intent.createChooser(shareIntent, "Share Sample WAV: " + targetWav.getName()));
        } catch (Exception e) {
            Toast.makeText(getContext(), "Saved to " + targetWav.getAbsolutePath(), Toast.LENGTH_LONG).show();
        }
    }

    // --- PHASE 5: PITCH SHIFT, TIME STRETCH & TAPE STOP DSP STUDIO ---
    private void showPitchStretchStudioDialog() {
        Dialog dialog = new Dialog(getContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        ScrollView scroll = new ScrollView(getContext());
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.parseColor("#1A1A24"));
        layout.setPadding(28, 20, 28, 20);
        scroll.addView(layout);

        TextView title = new TextView(getContext());
        title.setText("🎚️ Pitch Transpose & Time-Stretch Studio");
        title.setTextColor(Color.parseColor("#BF5AF2"));
        title.setTextSize(16f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(title);

        TextView subTitle = new TextView(getContext());
        subTitle.setText(waveCanvas.hasSelection() ? "Scope: Selected Region" : "Scope: Entire Audio Sample");
        subTitle.setTextColor(Color.parseColor("#8E8E93"));
        subTitle.setTextSize(11f);
        subTitle.setPadding(0, 4, 0, 14);
        layout.addView(subTitle);

        // 1. Granular Pitch Shift (Preserves Duration)
        TextView sec1 = new TextView(getContext());
        sec1.setText("1. GRANULAR PITCH SHIFT (PRESERVES DURATION)");
        sec1.setTextColor(Color.WHITE);
        sec1.setTextSize(12f);
        sec1.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(sec1);

        TextView txtPitchVal = new TextView(getContext());
        txtPitchVal.setText("Transpose: +0 Semitones");
        txtPitchVal.setTextColor(Color.parseColor("#BF5AF2"));
        txtPitchVal.setTextSize(11f);
        layout.addView(txtPitchVal);

        SeekBar seekPitch = new SeekBar(getContext());
        seekPitch.setMax(48);
        seekPitch.setProgress(24);
        seekPitch.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                int st = p - 24;
                txtPitchVal.setText(String.format("Transpose: %+d Semitones (%.2fx)", st, Math.pow(2.0, st / 12.0)));
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        layout.addView(seekPitch);

        Button btnApplyPitchShift = new Button(getContext());
        btnApplyPitchShift.setText("Apply Pitch Shift (Granular)");
        btnApplyPitchShift.setBackgroundColor(Color.parseColor("#BF5AF2"));
        btnApplyPitchShift.setTextColor(Color.WHITE);
        btnApplyPitchShift.setOnClickListener(v -> {
            int semitones = seekPitch.getProgress() - 24;
            if (semitones != 0) {
                applyGranularPitchShift(semitones);
                dialog.dismiss();
                Toast.makeText(getContext(), String.format("Transposed %+d semitones", semitones), Toast.LENGTH_SHORT).show();
            }
        });
        layout.addView(btnApplyPitchShift);

        // 2. Granular Time Stretch (Preserves Pitch)
        TextView sec2 = new TextView(getContext());
        sec2.setText("2. GRANULAR TIME STRETCH (PRESERVES PITCH)");
        sec2.setTextColor(Color.WHITE);
        sec2.setTextSize(12f);
        sec2.setPadding(0, 14, 0, 4);
        sec2.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(sec2);

        TextView txtStretchVal = new TextView(getContext());
        txtStretchVal.setText("Time Duration: 100% (1.00x Speed)");
        txtStretchVal.setTextColor(Color.parseColor("#0A84FF"));
        txtStretchVal.setTextSize(11f);
        layout.addView(txtStretchVal);

        SeekBar seekStretch = new SeekBar(getContext());
        seekStretch.setMax(150);
        seekStretch.setProgress(50);
        seekStretch.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                float factor = 0.5f + (p / 100f);
                txtStretchVal.setText(String.format("Time Duration: %d%% (%.2fx Length)", (int)(factor * 100), factor));
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        layout.addView(seekStretch);

        Button btnApplyStretch = new Button(getContext());
        btnApplyStretch.setText("Apply Time Stretch (WSOLA)");
        btnApplyStretch.setBackgroundColor(Color.parseColor("#0A84FF"));
        btnApplyStretch.setTextColor(Color.WHITE);
        btnApplyStretch.setOnClickListener(v -> {
            float factor = 0.5f + (seekStretch.getProgress() / 100f);
            if (Math.abs(factor - 1.0f) > 0.01f) {
                applyGranularTimeStretch(factor);
                dialog.dismiss();
                Toast.makeText(getContext(), String.format("Stretched to %.2fx length", factor), Toast.LENGTH_SHORT).show();
            }
        });
        layout.addView(btnApplyStretch);

        // 3. Classic Tape Repitch
        TextView sec3 = new TextView(getContext());
        sec3.setText("3. CLASSIC TAPE VARISPEED (PITCH & SPEED LINKED)");
        sec3.setTextColor(Color.WHITE);
        sec3.setTextSize(12f);
        sec3.setPadding(0, 14, 0, 4);
        sec3.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(sec3);

        LinearLayout rowVarispeed = new LinearLayout(getContext());
        rowVarispeed.setOrientation(LinearLayout.HORIZONTAL);

        Button btnOctUp = new Button(getContext());
        btnOctUp.setText("+1 Oct (2x Speed)");
        btnOctUp.setBackgroundColor(Color.parseColor("#242734"));
        btnOctUp.setTextColor(Color.WHITE);
        btnOctUp.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        btnOctUp.setOnClickListener(v -> {
            applyTapeVarispeed(2.0f);
            dialog.dismiss();
        });
        rowVarispeed.addView(btnOctUp);

        Button btnOctDown = new Button(getContext());
        btnOctDown.setText("-1 Oct (0.5x Speed)");
        btnOctDown.setBackgroundColor(Color.parseColor("#242734"));
        btnOctDown.setTextColor(Color.WHITE);
        btnOctDown.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        btnOctDown.setOnClickListener(v -> {
            applyTapeVarispeed(0.5f);
            dialog.dismiss();
        });
        rowVarispeed.addView(btnOctDown);
        layout.addView(rowVarispeed);

        // 4. Turntable Tape Stop FX
        TextView sec4 = new TextView(getContext());
        sec4.setText("4. TURNTABLE BRAKE & TAPE STOP FX");
        sec4.setTextColor(Color.WHITE);
        sec4.setTextSize(12f);
        sec4.setPadding(0, 14, 0, 4);
        sec4.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(sec4);

        LinearLayout rowBrake = new LinearLayout(getContext());
        rowBrake.setOrientation(LinearLayout.HORIZONTAL);

        Button btnTapeStopShort = new Button(getContext());
        btnTapeStopShort.setText("⏹ Tape Stop (250ms)");
        btnTapeStopShort.setBackgroundColor(Color.parseColor("#D97706"));
        btnTapeStopShort.setTextColor(Color.WHITE);
        btnTapeStopShort.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        btnTapeStopShort.setOnClickListener(v -> {
            applyTurntableTapeStop(0.25f);
            dialog.dismiss();
            Toast.makeText(getContext(), "Tape Stop (250ms) applied", Toast.LENGTH_SHORT).show();
        });
        rowBrake.addView(btnTapeStopShort);

        Button btnTapeStopLong = new Button(getContext());
        btnTapeStopLong.setText("⏹ Tape Stop (600ms)");
        btnTapeStopLong.setBackgroundColor(Color.parseColor("#D97706"));
        btnTapeStopLong.setTextColor(Color.WHITE);
        btnTapeStopLong.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        btnTapeStopLong.setOnClickListener(v -> {
            applyTurntableTapeStop(0.60f);
            dialog.dismiss();
            Toast.makeText(getContext(), "Tape Stop (600ms) applied", Toast.LENGTH_SHORT).show();
        });
        rowBrake.addView(btnTapeStopLong);
        layout.addView(rowBrake);

        Button btnCloseDialog = new Button(getContext());
        btnCloseDialog.setText("Close");
        btnCloseDialog.setBackgroundColor(Color.parseColor("#2C2F3C"));
        btnCloseDialog.setTextColor(Color.WHITE);
        btnCloseDialog.setOnClickListener(v -> dialog.dismiss());
        layout.addView(btnCloseDialog);

        dialog.setContentView(scroll);
        CobassDialogShell.configureWindow(dialog);
        dialog.show();
    }

    // --- WSOLA & GRANULAR DSP ALGORITHMS ---
    private void applyGranularPitchShift(int semitones) {
        float[] pcm = clip.getSampleData();
        if (pcm == null || pcm.length == 0) return;

        captureUndoPoint();
        int start = (int) (waveCanvas.getSelectionStartRatio() * pcm.length);
        int end = (int) (waveCanvas.getSelectionEndRatio() * pcm.length);
        int len = end - start;
        if (len <= 512) return;

        float pitchRatio = (float) Math.pow(2.0, semitones / 12.0);
        int grainSize = 1024;
        int hopSize = 256;
        float[] output = new float[len];

        for (int outPos = 0; outPos < len - grainSize; outPos += hopSize) {
            float inPos = outPos * pitchRatio;
            for (int k = 0; k < grainSize; k++) {
                int srcIdx = start + (int) (inPos + k);
                if (srcIdx < end - 1 && (outPos + k) < len) {
                    float frac = inPos + k - (int)(inPos + k);
                    float sample = pcm[srcIdx] * (1.0f - frac) + pcm[srcIdx + 1] * frac;
                    float window = 0.5f * (1.0f - (float) Math.cos(2.0 * Math.PI * k / (grainSize - 1)));
                    output[outPos + k] += sample * window * 0.5f;
                }
            }
        }

        System.arraycopy(output, 0, pcm, start, len);
        syncSampleToNative();
        waveCanvas.rebuildMipmaps();
        waveCanvas.invalidate();
        updateUndoRedoUI();
    }

    private void applyGranularTimeStretch(float stretchFactor) {
        float[] pcm = clip.getSampleData();
        if (pcm == null || pcm.length == 0) return;

        captureUndoPoint();
        int start = (int) (waveCanvas.getSelectionStartRatio() * pcm.length);
        int end = (int) (waveCanvas.getSelectionEndRatio() * pcm.length);
        int origLen = end - start;
        int newLen = (int) (origLen * stretchFactor);
        if (newLen <= 512) return;

        int grainSize = 1536;
        int hopOut = 384;
        int hopIn = (int) (hopOut / stretchFactor);

        float[] stretched = new float[newLen];
        int inPos = 0;
        for (int outPos = 0; outPos < newLen - grainSize && inPos < origLen - grainSize; outPos += hopOut, inPos += hopIn) {
            for (int k = 0; k < grainSize; k++) {
                int srcIdx = start + inPos + k;
                if (srcIdx < end && (outPos + k) < newLen) {
                    float window = 0.5f * (1.0f - (float) Math.cos(2.0 * Math.PI * k / (grainSize - 1)));
                    stretched[outPos + k] += pcm[srcIdx] * window * 0.5f;
                }
            }
        }

        float[] fullResult = new float[pcm.length - origLen + newLen];
        System.arraycopy(pcm, 0, fullResult, 0, start);
        System.arraycopy(stretched, 0, fullResult, start, newLen);
        if (end < pcm.length) {
            System.arraycopy(pcm, end, fullResult, start + newLen, pcm.length - end);
        }

        clip.setSampleData(fullResult);
        syncSampleToNative();
        waveCanvas.rebuildMipmaps();
        waveCanvas.invalidate();
        updateUndoRedoUI();
    }

    private void applyTapeVarispeed(float speedFactor) {
        float[] pcm = clip.getSampleData();
        if (pcm == null || pcm.length == 0) return;

        captureUndoPoint();
        int newLen = (int) (pcm.length / speedFactor);
        float[] resampled = new float[newLen];

        for (int i = 0; i < newLen; i++) {
            float srcPos = i * speedFactor;
            int idx = (int) srcPos;
            float frac = srcPos - idx;
            if (idx < pcm.length - 1) {
                resampled[i] = pcm[idx] + frac * (pcm[idx + 1] - pcm[idx]);
            } else if (idx < pcm.length) {
                resampled[i] = pcm[idx];
            }
        }

        clip.setSampleData(resampled);
        syncSampleToNative();
        waveCanvas.rebuildMipmaps();
        waveCanvas.invalidate();
        updateUndoRedoUI();
    }

    private void applyTurntableTapeStop(float durationSec) {
        float[] pcm = clip.getSampleData();
        if (pcm == null || pcm.length == 0) return;

        captureUndoPoint();
        int sampleRate = 48000;
        int stopSamples = (int) (durationSec * sampleRate);
        int endSample = (int) (waveCanvas.getSelectionEndRatio() * pcm.length);
        int startSample = Math.max(0, endSample - stopSamples);

        double readPos = startSample;
        for (int i = startSample; i < endSample; i++) {
            float progress = (float) (i - startSample) / (endSample - startSample);
            float speed = (1.0f - progress) * (1.0f - progress);

            int idx = (int) readPos;
            float frac = (float) (readPos - idx);
            if (idx < pcm.length - 1) {
                pcm[i] = pcm[idx] * (1.0f - frac) + pcm[idx + 1] * frac;
            }
            readPos += speed;
        }

        syncSampleToNative();
        waveCanvas.rebuildMipmaps();
        waveCanvas.invalidate();
        updateUndoRedoUI();
    }

    // --- TRANSIENT DETECTION & SLICE STUDIO ---
    private void showSliceStudioDialog() {
        Dialog dialog = new Dialog(getContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        ScrollView scroll = new ScrollView(getContext());
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.parseColor("#1C1E26"));
        layout.setPadding(28, 20, 28, 20);
        scroll.addView(layout);

        TextView title = new TextView(getContext());
        title.setText("🥁 Transient Detection & Slicing Studio");
        title.setTextColor(Color.parseColor("#D97706"));
        title.setTextSize(16f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(title);

        TextView txtSliceCount = new TextView(getContext());
        txtSliceCount.setText(String.format("Active Slices: %d detected", waveCanvas.getSliceMarkers().size()));
        txtSliceCount.setTextColor(Color.parseColor("#30D158"));
        txtSliceCount.setTextSize(12f);
        txtSliceCount.setPadding(0, 4, 0, 14);
        layout.addView(txtSliceCount);

        TextView txtSens = new TextView(getContext());
        txtSens.setText("Onset Sensitivity: 75%");
        txtSens.setTextColor(Color.WHITE);
        txtSens.setTextSize(12f);
        layout.addView(txtSens);

        SeekBar seekSens = new SeekBar(getContext());
        seekSens.setMax(100);
        seekSens.setProgress(75);
        layout.addView(seekSens);

        TextView txtDist = new TextView(getContext());
        txtDist.setText("Min Distance: 120 ms");
        txtDist.setTextColor(Color.WHITE);
        txtDist.setTextSize(12f);
        txtDist.setPadding(0, 10, 0, 0);
        layout.addView(txtDist);

        SeekBar seekDist = new SeekBar(getContext());
        seekDist.setMax(500);
        seekDist.setProgress(120);
        layout.addView(seekDist);

        SeekBar.OnSeekBarChangeListener detectListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                float sens = Math.max(0.05f, seekSens.getProgress() / 100f);
                float distSec = Math.max(0.02f, seekDist.getProgress() / 1000f);
                txtSens.setText(String.format("Onset Sensitivity: %d%%", (int)(sens * 100)));
                txtDist.setText(String.format("Min Distance: %d ms", (int)(distSec * 1000)));

                captureUndoPoint();
                int detectedCount = waveCanvas.detectTransients(sens, distSec);
                txtSliceCount.setText(String.format("Active Slices: %d detected", detectedCount));
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        };

        seekSens.setOnSeekBarChangeListener(detectListener);
        seekDist.setOnSeekBarChangeListener(detectListener);

        Button btnSliceToArranger = new Button(getContext());
        btnSliceToArranger.setText("✂️ Slice to Arranger Track");
        btnSliceToArranger.setBackgroundColor(Color.parseColor("#0A84FF"));
        btnSliceToArranger.setTextColor(Color.WHITE);
        btnSliceToArranger.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        btnSliceToArranger.setOnClickListener(v -> {
            performSliceToArranger();
            dialog.dismiss();
            dismiss();
        });
        layout.addView(btnSliceToArranger);

        Button btnExportWavSlices = new Button(getContext());
        btnExportWavSlices.setText("📁 Export Slices as WAV Files");
        btnExportWavSlices.setBackgroundColor(Color.parseColor("#30D158"));
        btnExportWavSlices.setTextColor(Color.WHITE);
        btnExportWavSlices.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        btnExportWavSlices.setOnClickListener(v -> {
            exportSlicesToDisk();
            dialog.dismiss();
        });
        layout.addView(btnExportWavSlices);

        Button btnClearSlices = new Button(getContext());
        btnClearSlices.setText("Clear All Slices");
        btnClearSlices.setBackgroundColor(Color.parseColor("#2C2F3C"));
        btnClearSlices.setTextColor(Color.parseColor("#FF453A"));
        btnClearSlices.setOnClickListener(v -> {
            captureUndoPoint();
            waveCanvas.clearSlices();
            txtSliceCount.setText("Active Slices: 0 detected");
        });
        layout.addView(btnClearSlices);

        Button btnDone = new Button(getContext());
        btnDone.setText("Done");
        btnDone.setBackgroundColor(Color.parseColor("#2C2F3C"));
        btnDone.setTextColor(Color.WHITE);
        btnDone.setOnClickListener(v -> dialog.dismiss());
        layout.addView(btnDone);

        dialog.setContentView(scroll);
        CobassDialogShell.configureWindow(dialog);
        dialog.show();
    }

    private void performSliceToArranger() {
        float[] pcm = clip.getSampleData();
        List<Float> slices = waveCanvas.getSliceMarkers();
        if (pcm == null || pcm.length == 0 || slices.isEmpty()) {
            Toast.makeText(getContext(), "No slices detected to split", Toast.LENGTH_SHORT).show();
            return;
        }

        List<Float> bounds = new ArrayList<>();
        bounds.add(0.0f);
        bounds.addAll(slices);
        bounds.add(1.0f);

        long originalStartTick = clip.getStartTick();
        long originalLengthTicks = clip.getLengthTicks();
        List<ClipItem> newClips = new ArrayList<>();

        for (int i = 0; i < bounds.size() - 1; i++) {
            float bStart = bounds.get(i);
            float bEnd = bounds.get(i + 1);

            int splStart = (int) (bStart * pcm.length);
            int splEnd = Math.min(pcm.length, (int) (bEnd * pcm.length));
            int subLen = Math.max(128, splEnd - splStart);

            float[] subPcm = new float[subLen];
            System.arraycopy(pcm, splStart, subPcm, 0, subLen);

            long subStartTick = originalStartTick + (long) (bStart * originalLengthTicks);
            long subLengthTicks = Math.max(60, (long) ((bEnd - bStart) * originalLengthTicks));

            int newId = (int) ((System.currentTimeMillis() + i + 100) & 0xFFFF);
            ClipItem sliceClip = new ClipItem(
                newId,
                clip.getTrackId(),
                subStartTick,
                subLengthTicks,
                clip.getName() + " (S" + (i + 1) + ")",
                Color.parseColor("#D97706"),
                TrackItem.Type.AUDIO
            );
            sliceClip.setSampleData(subPcm);
            newClips.add(sliceClip);
        }

        if (actionListener != null) {
            actionListener.onSlicesExportedToArranger(newClips);
        }
        Toast.makeText(getContext(), "Sliced into " + newClips.size() + " arranger clips", Toast.LENGTH_SHORT).show();
    }

    private void exportSlicesToDisk() {
        float[] pcm = clip.getSampleData();
        List<Float> slices = waveCanvas.getSliceMarkers();
        if (pcm == null || pcm.length == 0 || slices.isEmpty()) return;

        File slicesDir = new File(getContext().getExternalFilesDir(null), "Exports/Slices");
        if (!slicesDir.exists()) slicesDir.mkdirs();

        List<Float> bounds = new ArrayList<>();
        bounds.add(0.0f);
        bounds.addAll(slices);
        bounds.add(1.0f);

        int exported = 0;
        for (int i = 0; i < bounds.size() - 1; i++) {
            float bStart = bounds.get(i);
            float bEnd = bounds.get(i + 1);
            int splStart = (int) (bStart * pcm.length);
            int splEnd = Math.min(pcm.length, (int) (bEnd * pcm.length));
            int len = splEnd - splStart;
            if (len <= 0) continue;

            float[] sliceBuffer = new float[len];
            System.arraycopy(pcm, splStart, sliceBuffer, 0, len);

            File wavOut = new File(slicesDir, clip.getName().replaceAll("[^a-zA-Z0-9_-]", "_") + "_slice_" + (i + 1) + ".wav");
            writePcmFloatToWav(wavOut, sliceBuffer, 48000);
            exported++;
        }

        Toast.makeText(getContext(), "Exported " + exported + " slices to " + slicesDir.getAbsolutePath(), Toast.LENGTH_LONG).show();
    }

    private static void writePcmFloatToWav(File file, float[] pcm, int sampleRate) {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            int totalAudioLen = pcm.length * 2;
            int totalDataLen = totalAudioLen + 36;
            int byteRate = sampleRate * 2;

            byte[] header = new byte[44];
            ByteBuffer bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
            bb.put("RIFF".getBytes());
            bb.putInt(totalDataLen);
            bb.put("WAVE".getBytes());
            bb.put("fmt ".getBytes());
            bb.putInt(16);
            bb.putShort((short) 1);
            bb.putShort((short) 1);
            bb.putInt(sampleRate);
            bb.putInt(byteRate);
            bb.putShort((short) 2);
            bb.putShort((short) 16);
            bb.put("data".getBytes());
            bb.putInt(totalAudioLen);
            fos.write(header);

            byte[] data = new byte[totalAudioLen];
            ByteBuffer db = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
            for (float s : pcm) {
                short val = (short) (Math.max(-1.0f, Math.min(1.0f, s)) * 32767.0f);
                db.putShort(val);
            }
            fos.write(data);
        } catch (Exception ignored) {}
    }

    private void showDspStudioDialog() {
        Dialog dspDialog = new Dialog(getContext());
        dspDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        ScrollView scroll = new ScrollView(getContext());
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.parseColor("#1A1C24"));
        layout.setPadding(28, 20, 28, 20);
        scroll.addView(layout);

        TextView title = new TextView(getContext());
        title.setText("⚡ Waveform DSP Processing Studio");
        title.setTextColor(Color.parseColor("#0A84FF"));
        title.setTextSize(16f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(title);

        TextView subTitle = new TextView(getContext());
        subTitle.setText(waveCanvas.hasSelection() ? "Scope: Selected Region" : "Scope: Entire Audio File");
        subTitle.setTextColor(Color.parseColor("#8E8E93"));
        subTitle.setTextSize(11f);
        subTitle.setPadding(0, 4, 0, 14);
        layout.addView(subTitle);

        // Normalization
        LinearLayout rowNorm = new LinearLayout(getContext());
        rowNorm.setOrientation(LinearLayout.HORIZONTAL);
        String[] targets = {"0 dB", "-1 dB", "-3 dB", "-6 dB"};
        float[] factors = {0.999f, 0.891f, 0.707f, 0.501f};

        for (int i = 0; i < targets.length; i++) {
            Button btn = new Button(getContext());
            btn.setText(targets[i]);
            btn.setBackgroundColor(Color.parseColor("#242734"));
            btn.setTextColor(Color.WHITE);
            btn.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            final float targetScale = factors[i];
            final String label = targets[i];
            btn.setOnClickListener(v -> {
                applyNormalization(targetScale);
                dspDialog.dismiss();
                Toast.makeText(getContext(), "Normalized to " + label, Toast.LENGTH_SHORT).show();
            });
            rowNorm.addView(btn);
        }
        layout.addView(rowNorm);

        // Correction & Phase
        LinearLayout rowCorr = new LinearLayout(getContext());
        rowCorr.setOrientation(LinearLayout.HORIZONTAL);
        rowCorr.setPadding(0, 8, 0, 8);

        Button btnDcOffset = new Button(getContext());
        btnDcOffset.setText("DC Offset (20Hz)");
        btnDcOffset.setBackgroundColor(Color.parseColor("#242734"));
        btnDcOffset.setTextColor(Color.WHITE);
        btnDcOffset.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        btnDcOffset.setOnClickListener(v -> {
            applyDcOffsetRemoval();
            dspDialog.dismiss();
            Toast.makeText(getContext(), "DC Offset Filter Applied", Toast.LENGTH_SHORT).show();
        });
        rowCorr.addView(btnDcOffset);

        Button btnInvertPhase = new Button(getContext());
        btnInvertPhase.setText("Invert Phase (Ø)");
        btnInvertPhase.setBackgroundColor(Color.parseColor("#242734"));
        btnInvertPhase.setTextColor(Color.WHITE);
        btnInvertPhase.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        btnInvertPhase.setOnClickListener(v -> {
            applyPhaseInversion();
            dspDialog.dismiss();
            Toast.makeText(getContext(), "Phase Inverted", Toast.LENGTH_SHORT).show();
        });
        rowCorr.addView(btnInvertPhase);
        layout.addView(rowCorr);

        Button btnCloseDialog = new Button(getContext());
        btnCloseDialog.setText("Close");
        btnCloseDialog.setBackgroundColor(Color.parseColor("#2C2F3C"));
        btnCloseDialog.setTextColor(Color.WHITE);
        btnCloseDialog.setOnClickListener(v -> dspDialog.dismiss());
        layout.addView(btnCloseDialog);

        dspDialog.setContentView(scroll);
        CobassDialogShell.configureWindow(dspDialog);
        dspDialog.show();
    }

    private void applyNormalization(float targetPeak) {
        float[] pcm = clip.getSampleData();
        if (pcm == null || pcm.length == 0) return;

        captureUndoPoint();
        int startSample = (int) (waveCanvas.getSelectionStartRatio() * pcm.length);
        int endSample = (int) (waveCanvas.getSelectionEndRatio() * pcm.length);

        float maxPeak = 0.0001f;
        for (int i = startSample; i < endSample; i++) maxPeak = Math.max(maxPeak, Math.abs(pcm[i]));
        float gain = targetPeak / maxPeak;
        for (int i = startSample; i < endSample; i++) pcm[i] = Math.max(-1.0f, Math.min(1.0f, pcm[i] * gain));

        syncSampleToNative();
        waveCanvas.rebuildMipmaps();
        waveCanvas.invalidate();
        updateUndoRedoUI();
    }

    private void applyDcOffsetRemoval() {
        float[] pcm = clip.getSampleData();
        if (pcm == null || pcm.length == 0) return;

        captureUndoPoint();
        int startSample = (int) (waveCanvas.getSelectionStartRatio() * pcm.length);
        int endSample = (int) (waveCanvas.getSelectionEndRatio() * pcm.length);

        float x1 = 0.0f, y1 = 0.0f;
        final float R = 0.995f;
        for (int i = startSample; i < endSample; i++) {
            float x0 = pcm[i];
            float y0 = x0 - x1 + R * y1;
            x1 = x0;
            y1 = y0;
            pcm[i] = Math.max(-1.0f, Math.min(1.0f, y0));
        }

        syncSampleToNative();
        waveCanvas.rebuildMipmaps();
        waveCanvas.invalidate();
        updateUndoRedoUI();
    }

    private void applyPhaseInversion() {
        float[] pcm = clip.getSampleData();
        if (pcm == null || pcm.length == 0) return;

        captureUndoPoint();
        int startSample = (int) (waveCanvas.getSelectionStartRatio() * pcm.length);
        int endSample = (int) (waveCanvas.getSelectionEndRatio() * pcm.length);

        for (int i = startSample; i < endSample; i++) {
            pcm[i] = -pcm[i];
        }

        syncSampleToNative();
        waveCanvas.rebuildMipmaps();
        waveCanvas.invalidate();
        updateUndoRedoUI();
    }

    private void syncTrimAndFadeToNative() {
        if (AudioEngineNative.isLoaded()) {
            AudioEngineNative.nativeSetTrackTrimAndFade(
                clip.getTrackId(),
                waveCanvas.getTrimStartRatio(),
                waveCanvas.getTrimEndRatio(),
                waveCanvas.getFadeInRatio(),
                waveCanvas.getFadeOutRatio()
            );
        }
    }

    private void updateTransportUI() {
        CobassInteraction.applyPlayState(btnPlay, isPlaying);
        CobassInteraction.applyTransportToggle(btnLoop, isLooping);
        CobassInteraction.applyTransportToggle(btnFollow, isFollowing);
    }

    private void showZoomDialog() {
        Dialog zoomDialog = new Dialog(getContext());
        zoomDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.parseColor("#1C1E24"));
        layout.setPadding(28, 20, 28, 20);

        TextView title = new TextView(getContext());
        title.setText("Waveform Zoom Settings");
        title.setTextColor(Color.parseColor("#0A84FF"));
        title.setTextSize(15f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(title);

        TextView txtZoom = new TextView(getContext());
        txtZoom.setText(String.format("Time Magnification: %.1fx", waveCanvas.getZoomLevel()));
        txtZoom.setTextColor(Color.WHITE);
        txtZoom.setPadding(0, 14, 0, 6);
        layout.addView(txtZoom);

        SeekBar seekZoom = new SeekBar(getContext());
        seekZoom.setMax(100);
        seekZoom.setProgress((int) (((waveCanvas.getZoomLevel() - 1.0f) / 49.0f) * 100f));
        layout.addView(seekZoom);

        seekZoom.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float zoom = 1.0f + (progress / 100.0f) * 49.0f;
                txtZoom.setText(String.format("Time Magnification: %.1fx", zoom));
                waveCanvas.setZoomLevel(zoom);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        Button btnReset = new Button(getContext());
        btnReset.setText("Overview (1.0x Full View)");
        btnReset.setBackgroundColor(Color.parseColor("#242734"));
        btnReset.setTextColor(Color.WHITE);
        btnReset.setOnClickListener(v -> {
            seekZoom.setProgress(0);
            waveCanvas.resetZoom();
        });
        layout.addView(btnReset);

        Button btnDone = new Button(getContext());
        btnDone.setText("Done");
        btnDone.setBackgroundColor(Color.parseColor("#0A84FF"));
        btnDone.setTextColor(Color.WHITE);
        btnDone.setOnClickListener(v -> zoomDialog.dismiss());
        layout.addView(btnDone);

        zoomDialog.setContentView(layout);
        CobassDialogShell.configureWindow(zoomDialog);
        zoomDialog.show();
    }

    private void startPlayheadTicker() {
        transportHandler.post(new Runnable() {
            @Override
            public void run() {
                if (isRunning && AudioEngineNative.isLoaded()) {
                    float pos = AudioEngineNative.nativeGetTrackPlaybackPosition(clip.getTrackId());
                    waveCanvas.setPlaybackState(pos, isPlaying);
                }
                if (isRunning) transportHandler.postDelayed(this, 16);
            }
        });
    }

    private void syncSampleToNative() {
        if (AudioEngineNative.isLoaded() && clip.getSampleData() != null) {
            AudioEngineNative.nativeLoadSample(clip.getTrackId(), clip.getSampleData(), clip.getSampleData().length, 1);
        }
    }

    @Override
    public void dismiss() {
        isRunning = false;
        transportHandler.removeCallbacksAndMessages(null);
        if (isRecordingMic && audioRecordInstance != null) {
            isRecordingMic = false;
            audioRecordInstance.stop();
            audioRecordInstance.release();
            audioRecordInstance = null;
        }
        super.dismiss();
        if (onDismissCallback != null) onDismissCallback.run();
    }
}
