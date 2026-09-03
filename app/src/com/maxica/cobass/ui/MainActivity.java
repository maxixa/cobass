package com.maxica.cobass.ui;

import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.maxica.cobass.R;
import com.maxica.cobass.audio.AudioEngineNative;
import com.maxica.cobass.model.ClipItem;
import com.maxica.cobass.model.PluginDescriptorItem;
import com.maxica.cobass.model.SnapGrid;
import com.maxica.cobass.model.StepPatternItem;
import com.maxica.cobass.model.ToolMode;
import com.maxica.cobass.model.TrackItem;
import com.maxica.cobass.plugin.PluginApkInstaller;
import com.maxica.cobass.plugin.PluginHostManager;
import com.maxica.cobass.project.PresetUnpacker;
import com.maxica.cobass.project.ProjectData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements ArrangerTimelineView.OnArrangerListener, ProjectDialog.OnProjectActionListener {

    public static final int REQUEST_CODE_IMPORT_PLUGIN_APK = 3001;

    private static final String PREF_NAME = "CobassPrefs";
    private static final String KEY_UI_SCALE = "ui_scale_percent";
    private static final String KEY_BPM = "project_bpm";

    private final List<TrackItem> tracks = new ArrayList<>();
    private final List<ClipItem> clips = new ArrayList<>();
    private final Map<Integer, StepPatternItem> trackStepPatterns = new HashMap<>();
    private String currentProjectName = "Cobass_Master";

    private ArrangerTimelineView arrangerView;
    private WaveEditorDialog activeWaveEditorDialog = null;
    private TextView txtTimeCounter;
    private Button btnPlay, btnStop, btnLoop, btnSnapGrid, btnPreferences, btnOpenMixer, btnOpenProject;
    private Button btnToolSelect, btnToolPencil, btnToolSplit, btnToolGlue, btnToolSlip, btnToolEraser;
    private Button btnArrUndo, btnArrRedo;

    private final Handler tickerHandler = new Handler(Looper.getMainLooper());
    private boolean isLooping = true;
    private long loopStartTick = 0;
    private long loopEndTick = 1920 * 4;

    private int currentUiScalePercent = 100;
    private float currentBpm = 120.0f;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        currentUiScalePercent = prefs.getInt(KEY_UI_SCALE, 100);
        currentBpm = prefs.getFloat(KEY_BPM, 120.0f);

        arrangerView = findViewById(R.id.arrangerView);
        txtTimeCounter = findViewById(R.id.txtTimeCounter);
        btnPlay = findViewById(R.id.btnPlay);
        btnStop = findViewById(R.id.btnStop);
        btnLoop = findViewById(R.id.btnLoop);
        btnSnapGrid = findViewById(R.id.btnSnapGrid);
        btnPreferences = findViewById(R.id.btnPreferences);
        btnOpenMixer = findViewById(R.id.btnOpenMixer);
        btnOpenProject = findViewById(R.id.btnOpenProject);

        btnToolSelect = findViewById(R.id.btnToolSelect);
        btnToolPencil = findViewById(R.id.btnToolPencil);
        btnToolSplit = findViewById(R.id.btnToolSplit);
        btnToolEraser = findViewById(R.id.btnToolEraser);
        btnToolGlue = findViewById(R.id.btnToolGlue);
        btnToolSlip = findViewById(R.id.btnToolSlip);

        btnArrUndo = findViewById(R.id.btnArrUndo);
        btnArrRedo = findViewById(R.id.btnArrRedo);

        Button btnAddSynth = findViewById(R.id.btnAddSynth);
        Button btnAddAudio = findViewById(R.id.btnAddAudio);
        Button btnAddStepSeq = findViewById(R.id.btnAddStepSeq);

        Button btnLoopHalve = findViewById(R.id.btnLoopHalve);
        Button btnLoopDouble = findViewById(R.id.btnLoopDouble);
        Button btnLoopToSel = findViewById(R.id.btnLoopToSel);

        Button btnArrSelectAll = findViewById(R.id.btnArrSelectAll);
        Button btnArrDeselect = findViewById(R.id.btnArrDeselect);
        Button btnArrDuplicate = findViewById(R.id.btnArrDuplicate);
        Button btnArrMuteClip = findViewById(R.id.btnArrMuteClip);
        Button btnArrDeleteClips = findViewById(R.id.btnArrDeleteClips);

        if (arrangerView != null) {
            arrangerView.setArrangerListener(this);
            arrangerView.setUiScale(currentUiScalePercent / 100.0f);
            arrangerView.setLoopRange(loopStartTick, loopEndTick, isLooping);
        }

        try {
            if (AudioEngineNative.isLoaded()) {
                AudioEngineNative.nativeInit();
                AudioEngineNative.nativeStart();
                AudioEngineNative.nativeSetBpm(currentBpm);
                AudioEngineNative.nativeSetLoop(loopStartTick, loopEndTick, isLooping);

                PresetUnpacker.unpackFactoryPresets(this);
                PluginApkInstaller.scanAndMountInstalledPluginApks(this);
                PluginHostManager.getInstance().scanPlugins(this);
            }
        } catch (Throwable e) {
            Log.e("CobassDAW", "Native startup exception: " + e.getMessage(), e);
        }

        if (btnPlay != null) {
            btnPlay.setOnClickListener(v -> {
                if (AudioEngineNative.isLoaded()) {
                    AudioEngineNative.nativeTransportPlayFromStart();
                    btnPlay.setText("▶");
                    btnPlay.setBackgroundColor(Color.parseColor("#30D158"));
                }
            });
        }

        if (btnStop != null) {
            btnStop.setOnClickListener(v -> {
                if (AudioEngineNative.isLoaded()) {
                    AudioEngineNative.nativeTransportStop();
                    long resetTick = isLooping ? loopStartTick : 0;
                    if (arrangerView != null) arrangerView.setPlayheadTick(resetTick);
                    updateCounter(resetTick);
                    if (btnPlay != null) {
                        btnPlay.setText("▶");
                        btnPlay.setBackgroundColor(Color.parseColor("#1C3B60"));
                    }
                }
            });
        }

        if (btnLoop != null) {
            updateLoopButtonUI();
            btnLoop.setOnClickListener(v -> {
                isLooping = !isLooping;
                updateLoopButtonUI();
                if (arrangerView != null) arrangerView.setLoopRange(loopStartTick, loopEndTick, isLooping);
                if (AudioEngineNative.isLoaded()) {
                    AudioEngineNative.nativeSetLoop(loopStartTick, loopEndTick, isLooping);
                }
            });
        }

        if (btnLoopHalve != null) btnLoopHalve.setOnClickListener(v -> { if (arrangerView != null) arrangerView.halveLoopLength(); });
        if (btnLoopDouble != null) btnLoopDouble.setOnClickListener(v -> { if (arrangerView != null) arrangerView.doubleLoopLength(); });
        if (btnLoopToSel != null) btnLoopToSel.setOnClickListener(v -> {
            if (arrangerView != null) {
                if (arrangerView.loopToSelectedClips()) {
                    Toast.makeText(this, "Loop wrapped around selection", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Select clip(s) first", Toast.LENGTH_SHORT).show();
                }
            }
        });

        if (btnArrUndo != null) {
            btnArrUndo.setOnClickListener(v -> {
                if (arrangerView != null) {
                    arrangerView.performUndo();
                    syncAllClipsToNative();
                    updateUndoRedoUI();
                    Toast.makeText(this, "↶ Undo", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (btnArrRedo != null) {
            btnArrRedo.setOnClickListener(v -> {
                if (arrangerView != null) {
                    arrangerView.performRedo();
                    syncAllClipsToNative();
                    updateUndoRedoUI();
                    Toast.makeText(this, "↷ Redo", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (btnArrSelectAll != null) btnArrSelectAll.setOnClickListener(v -> { if (arrangerView != null) arrangerView.selectAllClips(true); });
        if (btnArrDeselect != null) btnArrDeselect.setOnClickListener(v -> { if (arrangerView != null) arrangerView.selectAllClips(false); });

        if (btnArrDuplicate != null) {
            btnArrDuplicate.setOnClickListener(v -> {
                if (arrangerView != null) {
                    List<ClipItem> dups = arrangerView.duplicateSelectedClips(1920);
                    if (!dups.isEmpty()) {
                        syncAllClipsToNative();
                        updateUndoRedoUI();
                        Toast.makeText(this, "Duplicated " + dups.size() + " clip(s)", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Select clip(s) to duplicate", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        if (btnArrMuteClip != null) {
            btnArrMuteClip.setOnClickListener(v -> {
                if (arrangerView != null) {
                    arrangerView.toggleMuteSelectedClips();
                    syncAllClipsToNative();
                    updateUndoRedoUI();
                }
            });
        }

        if (btnArrDeleteClips != null) {
            btnArrDeleteClips.setOnClickListener(v -> {
                if (arrangerView != null) {
                    arrangerView.deleteSelectedClips();
                    syncAllClipsToNative();
                    updateUndoRedoUI();
                }
            });
        }

        Button btnArrTransform = findViewById(R.id.btnArrTransform);
        if (btnArrTransform != null) {
            btnArrTransform.setOnClickListener(v -> {
                if (arrangerView == null) return;
                List<ClipItem> selected = arrangerView.getSelectedClips();
                if (selected.isEmpty() && arrangerView.getSelectedClip() != null) {
                    selected.add(arrangerView.getSelectedClip());
                }
                if (selected.isEmpty()) {
                    selected = arrangerView.getClips();
                }

                if (!selected.isEmpty()) {
                    arrangerView.captureUndoPoint();
                    new MidiTransformStudioDialog(
                        this,
                        selected,
                        com.maxica.cobass.model.MusicalScale.MAJOR,
                        0,
                        () -> {
                            arrangerView.clearGhostClips();
                            syncAllClipsToNative();
                            arrangerView.invalidate();
                            updateUndoRedoUI();
                        }
                    ).setLivePreviewListener(previewNotes -> {
                        arrangerView.invalidate();
                    }).show();
                } else {
                    Toast.makeText(this, "No clips available to transform", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (btnSnapGrid != null) {
            btnSnapGrid.setOnClickListener(v -> {
                new SnapStudioDialog(this, arrangerView.getSnapGrid(), sg -> {
                    arrangerView.setSnapGrid(sg);
                    btnSnapGrid.setText("Snap: " + sg.getLabel());
                }).show();
            });
        }

        if (btnToolSelect != null) btnToolSelect.setOnClickListener(v -> setTool(ToolMode.SELECT));
        if (btnToolPencil != null) btnToolPencil.setOnClickListener(v -> setTool(ToolMode.PENCIL));
        if (btnToolSplit != null) btnToolSplit.setOnClickListener(v -> setTool(ToolMode.SPLIT));
        if (btnToolEraser != null) btnToolEraser.setOnClickListener(v -> setTool(ToolMode.ERASER));
        if (btnToolGlue != null) btnToolGlue.setOnClickListener(v -> setTool(ToolMode.GLUE));
        if (btnToolSlip != null) btnToolSlip.setOnClickListener(v -> setTool(ToolMode.SLIP));

        if (btnAddSynth != null) btnAddSynth.setOnClickListener(v -> addSynthTrackWithDemoClip());
        if (btnAddAudio != null) btnAddAudio.setOnClickListener(v -> addAudioTrackWithDemoClip());
        if (btnAddStepSeq != null) btnAddStepSeq.setOnClickListener(v -> addStepSequencerTrackWithDemoPattern());
        if (btnPreferences != null) btnPreferences.setOnClickListener(v -> showPreferencesDialog());

        if (btnOpenMixer != null) {
            btnOpenMixer.setOnClickListener(v -> {
                MixerConsoleDialog mixerDialog = new MixerConsoleDialog(this, tracks, () -> {
                    if (arrangerView != null) arrangerView.invalidate();
                });
                mixerDialog.show();
            });
        }

        if (btnOpenProject != null) {
            btnOpenProject.setOnClickListener(v -> {
                ProjectDialog projectDialog = new ProjectDialog(this, this);
                projectDialog.show();
            });
        }

        setTool(ToolMode.SELECT);
        onNewProjectTemplate();
        startPlayheadTicker();
        updateUndoRedoUI();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == WaveEditorDialog.REQUEST_CODE_IMPORT_AUDIO && resultCode == RESULT_OK && data != null && data.getData() != null) {
            if (activeWaveEditorDialog != null && activeWaveEditorDialog.isShowing()) {
                activeWaveEditorDialog.importAudioFromUri(data.getData());
            }
        }

        if (requestCode == REQUEST_CODE_IMPORT_PLUGIN_APK && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri pluginApkUri = data.getData();
            new Thread(() -> {
                boolean ok = PluginApkInstaller.installPluginFromUri(this, pluginApkUri);
                runOnUiThread(() -> {
                    if (ok) {
                        int total = PluginHostManager.getInstance().getAvailablePlugins().size();
                        Toast.makeText(this, "✓ Plugin APK mounted successfully! Total: " + total + " plugins", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, "Failed to mount Plugin APK. Ensure it contains arm64-v8a binaries.", Toast.LENGTH_LONG).show();
                    }
                });
            }).start();
        }
    }

    private void updateUndoRedoUI() {
        if (btnArrUndo != null && arrangerView != null) {
            btnArrUndo.setEnabled(arrangerView.canUndo());
            btnArrUndo.setAlpha(arrangerView.canUndo() ? 1.0f : 0.35f);
        }
        if (btnArrRedo != null && arrangerView != null) {
            btnArrRedo.setEnabled(arrangerView.canRedo());
            btnArrRedo.setAlpha(arrangerView.canRedo() ? 1.0f : 0.35f);
        }
    }

    private void updateLoopButtonUI() {
        if (btnLoop == null) return;
        btnLoop.setBackgroundColor(isLooping ? Color.parseColor("#0A84FF") : Color.parseColor("#2C2C2E"));
        btnLoop.setText(isLooping ? "LOOP ON" : "LOOP OFF");
    }

    @Override
    public void onLoopRangeChanged(long startTick, long endTick, boolean enabled) {
        this.loopStartTick = startTick;
        this.loopEndTick = endTick;
        this.isLooping = enabled;
        updateLoopButtonUI();
        if (AudioEngineNative.isLoaded()) {
            AudioEngineNative.nativeSetLoop(startTick, endTick, enabled);
        }
    }

    @Override
    public ProjectData getCurrentProjectData() {
        ProjectData d = new ProjectData();
        d.projectName = currentProjectName;
        d.bpm = currentBpm;
        d.isLooping = isLooping;
        d.loopStart = loopStartTick;
        d.loopEnd = loopEndTick;
        d.uiScalePercent = currentUiScalePercent;
        d.tracks.clear();
        d.tracks.addAll(arrangerView != null ? arrangerView.getTracks() : tracks);
        d.clips.clear();
        d.clips.addAll(arrangerView != null ? arrangerView.getClips() : clips);

        for (TrackItem t : d.tracks) {
            if (t.getType() == TrackItem.Type.STEP_SEQUENCER) {
                t.setStepPattern(trackStepPatterns.get(t.getId()));
            }
        }

        if (AudioEngineNative.isLoaded()) {
            for (TrackItem t : d.tracks) {
                if (t.getType() == TrackItem.Type.SYNTH || t.getType() == TrackItem.Type.STEP_SEQUENCER) {
                    t.setInstrumentPluginId(AudioEngineNative.nativeGetTrackSynthPluginId(t.getId()));
                    t.setInstrumentPluginStateJson(AudioEngineNative.nativeGetPluginStateJson(t.getId(), -1));
                }

                t.getInsertFxSlots().clear();
                for (int slot = 0; slot < 8; slot++) {
                    String fxId = AudioEngineNative.nativeGetTrackFxPluginId(t.getId(), slot);
                    if (fxId != null && !fxId.isEmpty()) {
                        boolean bypassed = AudioEngineNative.nativeIsTrackFxBypassed(t.getId(), slot);
                        String stateJson = AudioEngineNative.nativeGetPluginStateJson(t.getId(), slot);
                        t.getInsertFxSlots().add(new TrackItem.PluginSlotState(slot, fxId, bypassed, 1.0f, stateJson));
                    }
                }
            }
        }
        return d;
    }

    @Override
    public void onProjectLoaded(ProjectData data) {
        if (data == null) return;

        currentProjectName = data.projectName;
        currentBpm = data.bpm;
        isLooping = data.isLooping;
        loopStartTick = data.loopStart;
        loopEndTick = data.loopEnd;
        currentUiScalePercent = data.uiScalePercent;

        if (arrangerView != null) {
            arrangerView.setUiScale(currentUiScalePercent / 100.0f);
            arrangerView.setLoopRange(loopStartTick, loopEndTick, isLooping);
        }
        updateLoopButtonUI();
        if (btnPlay != null) btnPlay.setText("▶");
        if (prefs != null) prefs.edit().putFloat(KEY_BPM, currentBpm).putInt(KEY_UI_SCALE, currentUiScalePercent).apply();

        if (AudioEngineNative.isLoaded()) {
            AudioEngineNative.nativeTransportStop();
            AudioEngineNative.nativeResetProject();
            AudioEngineNative.nativeSetBpm(currentBpm);
            AudioEngineNative.nativeSetLoop(loopStartTick, loopEndTick, isLooping);
        }

        tracks.clear();
        clips.clear();
        trackStepPatterns.clear();

        Map<Integer, Integer> trackIdMap = new HashMap<>();

        for (TrackItem t : data.tracks) {
            int newTrackId = t.getId();
            if (AudioEngineNative.isLoaded()) {
                if (t.getType() == TrackItem.Type.STEP_SEQUENCER) {
                    newTrackId = AudioEngineNative.nativeAddStepSequencerTrack(t.getName());
                    StepPatternItem restoredPat = t.getStepPattern();
                    if (restoredPat == null) {
                        restoredPat = createFactoryDrumPattern(newTrackId);
                    }
                    trackStepPatterns.put(newTrackId, restoredPat);

                    String synthId = t.getInstrumentPluginId();
                    if (synthId == null || synthId.isEmpty()) {
                        synthId = "com.maxica.cobass.plugins.cobalt_drums";
                    }
                    AudioEngineNative.nativeSetTrackSynthPlugin(newTrackId, synthId);
                    if (t.getInstrumentPluginStateJson() != null && !t.getInstrumentPluginStateJson().isEmpty()) {
                        AudioEngineNative.nativeSetPluginStateJson(newTrackId, -1, t.getInstrumentPluginStateJson());
                    }

                    for (int l = 0; l < restoredPat.getLanes().size(); l++) {
                        StepPatternItem.Lane lane = restoredPat.getLanes().get(l);
                        AudioEngineNative.nativeSetStepSequencerLaneParams(
                            newTrackId, l, lane.midiNote, lane.stepCount, lane.subdivision.getTicks(), lane.volume, lane.pan, lane.isMuted, lane.isSolo
                        );
                        for (int s = 0; s < lane.stepCount; s++) {
                            StepPatternItem.Step step = lane.steps.get(s);
                            AudioEngineNative.nativeSetStepSequencerStep(
                                newTrackId, l, s, step.active, step.velocity, step.pitchOffset, step.gate, step.nudge, step.ratchets, step.probability
                            );
                        }
                    }
                } else if (t.getType() == TrackItem.Type.SYNTH) {
                    newTrackId = AudioEngineNative.nativeAddSynthTrack(t.getName());

                    if (t.getInstrumentPluginId() != null && !t.getInstrumentPluginId().isEmpty()) {
                        AudioEngineNative.nativeSetTrackSynthPlugin(newTrackId, t.getInstrumentPluginId());
                        if (t.getInstrumentPluginStateJson() != null && !t.getInstrumentPluginStateJson().isEmpty()) {
                            AudioEngineNative.nativeSetPluginStateJson(newTrackId, -1, t.getInstrumentPluginStateJson());
                        }
                    }
                } else {
                    newTrackId = AudioEngineNative.nativeAddAudioTrack(t.getName());
                    float[] sample = generate808Kick(48000);
                    AudioEngineNative.nativeLoadSample(newTrackId, sample, sample.length, 1);
                }

                for (TrackItem.PluginSlotState slot : t.getInsertFxSlots()) {
                    if (slot != null && slot.pluginId != null && !slot.pluginId.isEmpty()) {
                        AudioEngineNative.nativeAddTrackFxPlugin(newTrackId, slot.slotIndex, slot.pluginId);
                        AudioEngineNative.nativeSetTrackFxBypass(newTrackId, slot.slotIndex, slot.bypassed);
                        if (slot.stateJson != null && !slot.stateJson.isEmpty()) {
                            AudioEngineNative.nativeSetPluginStateJson(newTrackId, slot.slotIndex, slot.stateJson);
                        }
                    }
                }

                AudioEngineNative.nativeSetTrackVolume(newTrackId, t.getVolume());
                AudioEngineNative.nativeSetTrackPan(newTrackId, t.getPan());
                AudioEngineNative.nativeSetTrackMute(newTrackId, t.isMuted());
                AudioEngineNative.nativeSetTrackSolo(newTrackId, t.isSolo());
                AudioEngineNative.nativeSetTrackFxParam(newTrackId, 0, 1, t.getEqLow());
                AudioEngineNative.nativeSetTrackFxParam(newTrackId, 0, 2, t.getEqMid());
                AudioEngineNative.nativeSetTrackFxParam(newTrackId, 0, 4, t.getEqHigh());
                AudioEngineNative.nativeSetTrackFxParam(newTrackId, 1, 1, t.getCompThresh());
                AudioEngineNative.nativeSetTrackFxParam(newTrackId, 1, 2, t.getCompRatio());
                AudioEngineNative.nativeSetTrackFxParam(newTrackId, 2, 3, t.getDelayMix());
                AudioEngineNative.nativeSetTrackFxParam(newTrackId, 3, 3, t.getReverbMix());
            }
            trackIdMap.put(t.getId(), newTrackId);

            TrackItem restoredTrack = new TrackItem(newTrackId, t.getName(), t.getType());
            restoredTrack.setVolume(t.getVolume());
            restoredTrack.setPan(t.getPan());
            restoredTrack.setMuted(t.isMuted());
            restoredTrack.setSolo(t.isSolo());
            restoredTrack.setInstrumentPluginId(t.getInstrumentPluginId());
            restoredTrack.setInstrumentPluginStateJson(t.getInstrumentPluginStateJson());
            restoredTrack.setStepPattern(trackStepPatterns.get(newTrackId));
            for (TrackItem.PluginSlotState slot : t.getInsertFxSlots()) {
                restoredTrack.getInsertFxSlots().add(slot.copy());
            }
            restoredTrack.setEqLow(t.getEqLow());
            restoredTrack.setEqMid(t.getEqMid());
            restoredTrack.setEqHigh(t.getEqHigh());
            restoredTrack.setCompThresh(t.getCompThresh());
            restoredTrack.setCompRatio(t.getCompRatio());
            restoredTrack.setReverbMix(t.getReverbMix());
            restoredTrack.setDelayMix(t.getDelayMix());
            tracks.add(restoredTrack);
        }

        for (ClipItem c : data.clips) {
            int remappedTrackId = trackIdMap.getOrDefault(c.getTrackId(), c.getTrackId());
            int newClipId = c.getId();

            if (AudioEngineNative.isLoaded()) {
                newClipId = AudioEngineNative.nativeAddClip(remappedTrackId, c.getStartTick(), c.getLengthTicks(), c.getName());
                if (!c.isMuted()) {
                    for (ClipItem.Note n : c.getNotes()) {
                        AudioEngineNative.nativeAddNoteToClip(newClipId, n.note, n.velocity, n.startOffsetTicks, n.lengthTicks);
                    }
                }
            }

            ClipItem restoredClip = new ClipItem(newClipId, remappedTrackId, c.getStartTick(), c.getLengthTicks(), c.getName(), c.getColor(), c.getType());
            restoredClip.setMuted(c.isMuted());
            for (ClipItem.Note n : c.getNotes()) {
                restoredClip.addNote(n.note, n.velocity, n.startOffsetTicks, n.lengthTicks);
            }
            if (c.getType() == TrackItem.Type.AUDIO) {
                restoredClip.setSampleData(generate808Kick(48000));
            }
            clips.add(restoredClip);
        }

        if (arrangerView != null) {
            arrangerView.setTracksAndClips(tracks, clips);
            arrangerView.setPlayheadTick(loopStartTick);
        }
        updateCounter(loopStartTick);
        updateUndoRedoUI();
    }

    @Override
    public void onNewProjectTemplate() {
        loopStartTick = 0;
        loopEndTick = 1920 * 4;
        isLooping = true;

        if (AudioEngineNative.isLoaded()) {
            AudioEngineNative.nativeTransportStop();
            AudioEngineNative.nativeResetProject();
            AudioEngineNative.nativeSetBpm(120.0f);
            AudioEngineNative.nativeSetLoop(loopStartTick, loopEndTick, isLooping);
        }
        tracks.clear();
        clips.clear();
        trackStepPatterns.clear();
        addSynthTrackWithDemoClip();
        addAudioTrackWithDemoClip();
        addStepSequencerTrackWithDemoPattern();
        if (arrangerView != null) {
            arrangerView.setLoopRange(loopStartTick, loopEndTick, isLooping);
            arrangerView.setPlayheadTick(0);
        }
        updateLoopButtonUI();
        updateCounter(0);
        updateUndoRedoUI();
    }

    private void syncAllClipsToNative() {
        if (!AudioEngineNative.isLoaded() || arrangerView == null) return;
        for (ClipItem c : arrangerView.getClips()) {
            AudioEngineNative.nativeMoveClip(c.getId(), c.getTrackId(), c.getStartTick());
            AudioEngineNative.nativeResizeClip(c.getId(), c.getLengthTicks());
            AudioEngineNative.nativeClearClipNotes(c.getId());
            if (!c.isMuted()) {
                for (ClipItem.Note n : c.getNotes()) {
                    if (!n.isMuted) {
                        AudioEngineNative.nativeAddNoteToClip(c.getId(), n.note, n.velocity, n.startOffsetTicks, n.lengthTicks);
                    }
                }
            }
        }
    }

    private void showPreferencesDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_preferences);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        TextView txtScalePercent = dialog.findViewById(R.id.txtScalePercent);
        SeekBar seekUiScale = dialog.findViewById(R.id.seekUiScale);
        TextView txtBpmDisplay = dialog.findViewById(R.id.txtBpmDisplay);
        SeekBar seekBpm = dialog.findViewById(R.id.seekBpm);
        Button btnResetDefaults = dialog.findViewById(R.id.btnResetDefaults);
        Button btnClosePrefs = dialog.findViewById(R.id.btnClosePrefs);

        TextView txtPluginSummary = dialog.findViewById(R.id.txtPluginSummary);
        Button btnSideload = dialog.findViewById(R.id.btnSideloadPluginApk);
        Button btnRescan = dialog.findViewById(R.id.btnRescanPlugins);

        updatePluginSummaryText(txtPluginSummary);

        if (btnSideload != null) {
            btnSideload.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                String[] mimeTypes = {"application/vnd.android.package-archive", "application/octet-stream", "application/zip", "*/*"};
                intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
                startActivityForResult(intent, REQUEST_CODE_IMPORT_PLUGIN_APK);
            });
        }

        if (btnRescan != null) {
            btnRescan.setOnClickListener(v -> {
                int mounted = PluginApkInstaller.scanAndMountInstalledPluginApks(this);
                PluginHostManager.getInstance().scanPlugins(this);
                updatePluginSummaryText(txtPluginSummary);
                int total = PluginHostManager.getInstance().getAvailablePlugins().size();
                Toast.makeText(this, "Rescanned: " + total + " plugins active (" + mounted + " from installed APKs)", Toast.LENGTH_SHORT).show();
            });
        }

        if (seekUiScale != null) {
            seekUiScale.setProgress(currentUiScalePercent - 70);
            seekUiScale.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    int percent = 70 + progress;
                    currentUiScalePercent = percent;
                    if (txtScalePercent != null) txtScalePercent.setText(percent + "%");
                    if (arrangerView != null) arrangerView.setUiScale(percent / 100.0f);
                    prefs.edit().putInt(KEY_UI_SCALE, percent).apply();
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }
        if (txtScalePercent != null) txtScalePercent.setText(currentUiScalePercent + "%");

        if (seekBpm != null) {
            seekBpm.setProgress((int) (currentBpm - 40));
            seekBpm.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    currentBpm = 40 + progress;
                    if (txtBpmDisplay != null) txtBpmDisplay.setText(String.format("%.0f BPM", currentBpm));
                    if (AudioEngineNative.isLoaded()) AudioEngineNative.nativeSetBpm(currentBpm);
                    prefs.edit().putFloat(KEY_BPM, currentBpm).apply();
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }
        if (txtBpmDisplay != null) txtBpmDisplay.setText(String.format("%.0f BPM", currentBpm));

        if (btnResetDefaults != null) {
            btnResetDefaults.setOnClickListener(v -> {
                if (seekUiScale != null) seekUiScale.setProgress(30);
                if (seekBpm != null) seekBpm.setProgress(80);
            });
        }

        if (btnClosePrefs != null) btnClosePrefs.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void updatePluginSummaryText(TextView txtPluginSummary) {
        if (txtPluginSummary == null) return;
        int total = PluginHostManager.getInstance().getAvailablePlugins().size();
        int synths = PluginHostManager.getInstance().getSynthPlugins().size();
        int fxs = PluginHostManager.getInstance().getEffectPlugins().size();
        txtPluginSummary.setText(String.format(Locale.US, "Plugins Available: %d (%d Synths, %d FX)", total, synths, fxs));
    }

    private void setTool(ToolMode mode) {
        if (arrangerView != null) arrangerView.setToolMode(mode);
        if (btnToolSelect != null) btnToolSelect.setBackgroundColor(mode == ToolMode.SELECT ? Color.parseColor("#0A84FF") : Color.parseColor("#2C2C2E"));
        if (btnToolPencil != null) btnToolPencil.setBackgroundColor(mode == ToolMode.PENCIL ? Color.parseColor("#0A84FF") : Color.parseColor("#2C2C2E"));
        if (btnToolSplit != null) btnToolSplit.setBackgroundColor(mode == ToolMode.SPLIT ? Color.parseColor("#0A84FF") : Color.parseColor("#2C2C2E"));
        if (btnToolGlue != null) btnToolGlue.setBackgroundColor(mode == ToolMode.GLUE ? Color.parseColor("#0A84FF") : Color.parseColor("#2C2C2E"));
        if (btnToolSlip != null) btnToolSlip.setBackgroundColor(mode == ToolMode.SLIP ? Color.parseColor("#0A84FF") : Color.parseColor("#2C2C2E"));
        if (btnToolEraser != null) btnToolEraser.setBackgroundColor(mode == ToolMode.ERASER ? Color.parseColor("#FF453A") : Color.parseColor("#2C2C2E"));
    }

    private void addSynthTrackWithDemoClip() {
        int idx = tracks.size() + 1;
        String name = "Synth " + idx;
        int trackId = AudioEngineNative.isLoaded() ? AudioEngineNative.nativeAddSynthTrack(name) : idx;
        TrackItem track = new TrackItem(trackId, name, TrackItem.Type.SYNTH);
        tracks.add(track);

        int clipId = AudioEngineNative.isLoaded() ? AudioEngineNative.nativeAddClip(trackId, 0, 1920 * 2, "Lead Melody") : (idx * 10);
        ClipItem clip = new ClipItem(clipId, trackId, 0, 1920 * 2, "Lead Melody", Color.parseColor("#1C6DD0"), TrackItem.Type.SYNTH);
        clip.addNote(60, 0.9f, 0, 480);
        clip.addNote(64, 0.9f, 480, 480);
        clip.addNote(67, 0.9f, 960, 480);
        clip.addNote(71, 0.9f, 1440, 960);

        if (AudioEngineNative.isLoaded()) {
            AudioEngineNative.nativeAddNoteToClip(clipId, 60, 0.9f, 0, 480);
            AudioEngineNative.nativeAddNoteToClip(clipId, 64, 0.9f, 480, 480);
            AudioEngineNative.nativeAddNoteToClip(clipId, 67, 0.9f, 960, 480);
            AudioEngineNative.nativeAddNoteToClip(clipId, 71, 0.9f, 1440, 960);
        }

        clips.add(clip);
        if (arrangerView != null) arrangerView.setTracksAndClips(tracks, clips);
        updateUndoRedoUI();
    }

    private void addAudioTrackWithDemoClip() {
        int idx = tracks.size() + 1;
        String name = "808 Sub " + idx;
        int trackId = AudioEngineNative.isLoaded() ? AudioEngineNative.nativeAddAudioTrack(name) : idx;
        TrackItem track = new TrackItem(trackId, name, TrackItem.Type.AUDIO);
        tracks.add(track);

        float[] sample = generate808Kick(48000);
        if (AudioEngineNative.isLoaded()) {
            AudioEngineNative.nativeLoadSample(trackId, sample, sample.length, 1);
        }

        int clipId = AudioEngineNative.isLoaded() ? AudioEngineNative.nativeAddClip(trackId, 0, 1920, "808 Bass") : (idx * 10);
        ClipItem clip = new ClipItem(clipId, trackId, 0, 1920, "808 Bass", Color.parseColor("#D97706"), TrackItem.Type.AUDIO);
        clip.setSampleData(sample);
        clips.add(clip);

        if (arrangerView != null) arrangerView.setTracksAndClips(tracks, clips);
        updateUndoRedoUI();
    }

    private TrackItem getTrackById(int trackId) {
        for (TrackItem t : tracks) {
            if (t.getId() == trackId) return t;
        }
        return new TrackItem(trackId, "Track", TrackItem.Type.STEP_SEQUENCER);
    }

    private void addStepSequencerTrackWithDemoPattern() {
        int idx = tracks.size() + 1;
        String name = "Drum Machine " + idx;
        int trackId = AudioEngineNative.isLoaded() ? AudioEngineNative.nativeAddStepSequencerTrack(name) : idx;
        TrackItem track = new TrackItem(trackId, name, TrackItem.Type.STEP_SEQUENCER);
        track.setColor(Color.parseColor("#9333EA"));
        tracks.add(track);

        // Mount Cobalt Drum Synth directly to the step track
        String drumSynthId = "com.maxica.cobass.plugins.cobalt_drums";
        if (AudioEngineNative.isLoaded()) {
            AudioEngineNative.nativeSetTrackSynthPlugin(trackId, drumSynthId);
        }
        track.setInstrumentPluginId(drumSynthId);

        StepPatternItem pattern = createFactoryDrumPattern(trackId);
        track.setStepPattern(pattern);
        trackStepPatterns.put(trackId, pattern);

        if (AudioEngineNative.isLoaded()) {
            for (int l = 0; l < pattern.getLanes().size(); l++) {
                StepPatternItem.Lane lane = pattern.getLanes().get(l);
                AudioEngineNative.nativeSetStepSequencerLaneParams(
                    trackId, l, lane.midiNote, lane.stepCount, lane.subdivision.getTicks(), lane.volume, lane.pan, lane.isMuted, lane.isSolo
                );
                for (int s = 0; s < lane.stepCount; s++) {
                    StepPatternItem.Step step = lane.steps.get(s);
                    AudioEngineNative.nativeSetStepSequencerStep(
                        trackId, l, s, step.active, step.velocity, step.pitchOffset, step.gate, step.nudge, step.ratchets, step.probability
                    );
                }
            }
        }

        int clipId = AudioEngineNative.isLoaded() ? AudioEngineNative.nativeAddClip(trackId, 0, 1920 * 2, "Drum Groove") : (idx * 10);
        ClipItem clip = new ClipItem(clipId, trackId, 0, 1920 * 2, "Drum Groove", Color.parseColor("#9333EA"), TrackItem.Type.STEP_SEQUENCER);
        clips.add(clip);

        if (arrangerView != null) arrangerView.setTracksAndClips(tracks, clips);
        updateUndoRedoUI();
        Toast.makeText(this, "Created Cobalt Drum Machine Track", Toast.LENGTH_SHORT).show();
    }

    private StepPatternItem createFactoryDrumPattern(int trackId) {
        StepPatternItem pat = new StepPatternItem(1, "Trap Heat 01");
        pat.setBaseLength(16);

        StepPatternItem.Lane kick = new StepPatternItem.Lane(0, "Kick 808", 36, 16);
        kick.steps.get(0).active = true;
        kick.steps.get(6).active = true;
        kick.steps.get(10).active = true;
        pat.getLanes().add(kick);

        StepPatternItem.Lane snare = new StepPatternItem.Lane(1, "Snare 909", 38, 16);
        snare.steps.get(4).active = true;
        snare.steps.get(12).active = true;
        pat.getLanes().add(snare);

        StepPatternItem.Lane hat = new StepPatternItem.Lane(2, "Cl. Hat", 42, 16);
        for (int i = 0; i < 16; i++) {
            hat.steps.get(i).active = true;
            hat.steps.get(i).velocity = (i % 2 == 0) ? 0.85f : 0.60f;
        }
        hat.steps.get(14).ratchets = 2;
        hat.steps.get(15).ratchets = 4;
        pat.getLanes().add(hat);

        StepPatternItem.Lane oHat = new StepPatternItem.Lane(3, "Op. Hat", 46, 16);
        oHat.steps.get(2).active = true;
        oHat.steps.get(10).active = true;
        pat.getLanes().add(oHat);

        StepPatternItem.Lane clap = new StepPatternItem.Lane(4, "Clap", 39, 16);
        clap.steps.get(4).active = true;
        clap.steps.get(12).active = true;
        pat.getLanes().add(clap);

        StepPatternItem.Lane perc = new StepPatternItem.Lane(5, "Cowbell", 56, 16);
        perc.steps.get(3).active = true;
        perc.steps.get(11).active = true;
        pat.getLanes().add(perc);

        return pat;
    }

    private float[] generate808Kick(int sampleRate) {
        int length = (int) (sampleRate * 0.8);
        float[] buffer = new float[length];
        double phase = 0.0;
        for (int i = 0; i < length; i++) {
            double progress = (double) i / length;
            double freq = 135.0 * Math.exp(-progress * 8.5) + 40.0;
            double env = Math.exp(-progress * 4.2);
            phase += (2.0 * Math.PI * freq) / sampleRate;
            buffer[i] = (float) (Math.sin(phase) * env);
        }
        return buffer;
    }

    private void startPlayheadTicker() {
        tickerHandler.post(new Runnable() {
            @Override
            public void run() {
                if (AudioEngineNative.isLoaded() && AudioEngineNative.nativeIsPlaying()) {
                    long tick = AudioEngineNative.nativeGetCurrentTick();
                    if (arrangerView != null) arrangerView.setPlayheadTick(tick);
                    updateCounter(tick);
                }
                tickerHandler.postDelayed(this, 16);
            }
        });
    }

    private void updateCounter(long tick) {
        if (txtTimeCounter == null) return;
        long bar = (tick / 1920) + 1;
        long beat = ((tick % 1920) / 480) + 1;
        long subTick = tick % 480;
        txtTimeCounter.setText(String.format("%03d:%02d:%03d", bar, beat, subTick));
    }

    @Override
    public void onTrackMuteToggled(TrackItem track) {
        if (AudioEngineNative.isLoaded()) {
            AudioEngineNative.nativeSetTrackMute(track.getId(), track.isMuted());
        }
        if (arrangerView != null) arrangerView.invalidate();
    }

    @Override
    public void onTrackSoloToggled(TrackItem track) {
        if (AudioEngineNative.isLoaded()) {
            AudioEngineNative.nativeSetTrackSolo(track.getId(), track.isSolo());
        }
        if (arrangerView != null) arrangerView.invalidate();
    }

    @Override
    public void onTrackFxRequested(TrackItem track) {
        FxRackDialog fxDialog = new FxRackDialog(this, track);
        fxDialog.show();
    }

    @Override
    public void onTrackInspectorRequested(TrackItem track) {
        new TrackInspectorDialog(this, track, new TrackInspectorDialog.OnTrackInspectorActionListener() {
            @Override
            public void onTrackUpdated(TrackItem t) {
                if (arrangerView != null) arrangerView.invalidate();
            }

            @Override
            public void onMoveTrackUp(TrackItem t) {
                int idx = tracks.indexOf(t);
                if (idx > 0) {
                    tracks.remove(idx);
                    tracks.add(idx - 1, t);
                    if (arrangerView != null) arrangerView.setTracksAndClips(tracks, clips);
                    Toast.makeText(MainActivity.this, "Moved track up", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onMoveTrackDown(TrackItem t) {
                int idx = tracks.indexOf(t);
                if (idx >= 0 && idx < tracks.size() - 1) {
                    tracks.remove(idx);
                    tracks.add(idx + 1, t);
                    if (arrangerView != null) arrangerView.setTracksAndClips(tracks, clips);
                    Toast.makeText(MainActivity.this, "Moved track down", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onDuplicateTrack(TrackItem t) {
                duplicateTrackWithClips(t);
            }

            @Override
            public void onDeleteTrack(TrackItem t) {
                deleteTrackWithClips(t);
            }

            @Override
            public void onOpenFxRack(TrackItem t) {
                onTrackFxRequested(t);
            }
        }).show();
    }

    private void duplicateTrackWithClips(TrackItem source) {
        String newName = source.getName() + " (Copy)";
        int newTrackId = source.getId();
        if (AudioEngineNative.isLoaded()) {
            if (source.getType() == TrackItem.Type.SYNTH) {
                newTrackId = AudioEngineNative.nativeAddSynthTrack(newName);
            } else if (source.getType() == TrackItem.Type.STEP_SEQUENCER) {
                newTrackId = AudioEngineNative.nativeAddStepSequencerTrack(newName);
            } else {
                newTrackId = AudioEngineNative.nativeAddAudioTrack(newName);
                float[] sample = generate808Kick(48000);
                AudioEngineNative.nativeLoadSample(newTrackId, sample, sample.length, 1);
            }

            String synthId = AudioEngineNative.nativeGetTrackSynthPluginId(source.getId());
            if (synthId != null && !synthId.isEmpty()) {
                AudioEngineNative.nativeSetTrackSynthPlugin(newTrackId, synthId);
                String synthState = AudioEngineNative.nativeGetPluginStateJson(source.getId(), -1);
                AudioEngineNative.nativeSetPluginStateJson(newTrackId, -1, synthState);
            }

            for (int slot = 0; slot < 8; slot++) {
                String fxId = AudioEngineNative.nativeGetTrackFxPluginId(source.getId(), slot);
                if (fxId != null && !fxId.isEmpty()) {
                    boolean bypassed = AudioEngineNative.nativeIsTrackFxBypassed(source.getId(), slot);
                    String fxState = AudioEngineNative.nativeGetPluginStateJson(source.getId(), slot);
                    AudioEngineNative.nativeAddTrackFxPlugin(newTrackId, slot, fxId);
                    AudioEngineNative.nativeSetTrackFxBypass(newTrackId, slot, bypassed);
                    AudioEngineNative.nativeSetPluginStateJson(newTrackId, slot, fxState);
                }
            }

            AudioEngineNative.nativeSetTrackVolume(newTrackId, source.getVolume());
            AudioEngineNative.nativeSetTrackPan(newTrackId, source.getPan());
        }

        TrackItem dupTrack = new TrackItem(newTrackId, newName, source.getType());
        dupTrack.setVolume(source.getVolume());
        dupTrack.setPan(source.getPan());
        dupTrack.setColor(source.getColor());
        dupTrack.setInstrumentPluginId(source.getInstrumentPluginId());
        dupTrack.setInstrumentPluginStateJson(source.getInstrumentPluginStateJson());
        for (TrackItem.PluginSlotState s : source.getInsertFxSlots()) {
            dupTrack.getInsertFxSlots().add(s.copy());
        }
        tracks.add(dupTrack);

        for (ClipItem c : new ArrayList<>(clips)) {
            if (c.getTrackId() == source.getId()) {
                int newClipId = (int) ((System.currentTimeMillis() + clips.size() + 10) & 0xFFFF);
                if (AudioEngineNative.isLoaded()) {
                    newClipId = AudioEngineNative.nativeAddClip(newTrackId, c.getStartTick(), c.getLengthTicks(), c.getName());
                    for (ClipItem.Note n : c.getNotes()) {
                        AudioEngineNative.nativeAddNoteToClip(newClipId, n.note, n.velocity, n.startOffsetTicks, n.lengthTicks);
                    }
                }
                ClipItem dupClip = c.copy();
                dupClip.setId(newClipId);
                dupClip.setTrackId(newTrackId);
                clips.add(dupClip);
            }
        }

        if (arrangerView != null) arrangerView.setTracksAndClips(tracks, clips);
        Toast.makeText(this, "Duplicated Track: " + newName, Toast.LENGTH_SHORT).show();
    }

    private void deleteTrackWithClips(TrackItem track) {
        if (tracks.size() <= 1) {
            Toast.makeText(this, "Cannot delete the only remaining track", Toast.LENGTH_SHORT).show();
            return;
        }

        tracks.remove(track);
        clips.removeIf(c -> c.getTrackId() == track.getId());
        trackStepPatterns.remove(track.getId());

        if (AudioEngineNative.isLoaded()) {
            AudioEngineNative.nativeRemoveTrack(track.getId());
        }

        if (arrangerView != null) arrangerView.setTracksAndClips(tracks, clips);
        Toast.makeText(this, "Deleted Track: " + track.getName(), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onClipSelected(ClipItem clip) {}

    @Override
    public void onClipDoubleTap(ClipItem clip) {
        if (clip.getType() == TrackItem.Type.STEP_SEQUENCER) {
            StepPatternItem pattern = trackStepPatterns.get(clip.getTrackId());
            if (pattern == null) {
                pattern = createFactoryDrumPattern(clip.getTrackId());
                trackStepPatterns.put(clip.getTrackId(), pattern);
            }
            TrackItem track = getTrackById(clip.getTrackId());
            new StepSequencerDialog(this, track, pattern, new StepSequencerDialog.OnStepSequencerActionListener() {
                @Override
                public void onPatternBakeToClip(ClipItem bakedClip) {
                    onClipCreated(bakedClip);
                    if (arrangerView != null) arrangerView.setTracksAndClips(tracks, clips);
                }
                @Override
                public void onPatternModified() {
                    if (arrangerView != null) arrangerView.invalidate();
                }
            }, () -> {
                if (arrangerView != null) arrangerView.invalidate();
            }).show();
            return;
        }

        if (clip.getType() == TrackItem.Type.SYNTH) {
            PianoRollEditorDialog dialog = new PianoRollEditorDialog(this, clip, () -> {
                if (arrangerView != null) arrangerView.invalidate();
                updateUndoRedoUI();
            });
            dialog.show();
        } else {
            WaveEditorDialog dialog = new WaveEditorDialog(this, clip, new WaveEditorDialog.OnWaveActionListener() {
                @Override
                public void onSlicesExportedToArranger(List<ClipItem> slicedClips) {
                    if (slicedClips != null && !slicedClips.isEmpty()) {
                        clips.remove(clip);
                        if (AudioEngineNative.isLoaded()) {
                            AudioEngineNative.nativeRemoveClip(clip.getId());
                        }
                        for (ClipItem sc : slicedClips) {
                            onClipCreated(sc);
                        }
                        if (arrangerView != null) {
                            arrangerView.setTracksAndClips(tracks, clips);
                        }
                    }
                }

                @Override
                public void onWaveModified() {
                    if (arrangerView != null) arrangerView.invalidate();
                    updateUndoRedoUI();
                }
            }, () -> {
                if (arrangerView != null) arrangerView.invalidate();
                updateUndoRedoUI();
            });
            activeWaveEditorDialog = dialog;
            dialog.show();
        }
    }

    @Override
    public void onClipCreated(ClipItem clip) {
        int id = clip.getId();
        if (AudioEngineNative.isLoaded()) {
            id = AudioEngineNative.nativeAddClip(clip.getTrackId(), clip.getStartTick(), clip.getLengthTicks(), clip.getName());
            if (!clip.isMuted()) {
                for (ClipItem.Note n : clip.getNotes()) {
                    AudioEngineNative.nativeAddNoteToClip(id, n.note, n.velocity, n.startOffsetTicks, n.lengthTicks);
                }
            }
        }
        clip.setId(id);
        if (!clips.contains(clip)) {
            clips.add(clip);
        }
        updateUndoRedoUI();
    }

    @Override
    public void onClipModified(ClipItem clip) {
        if (AudioEngineNative.isLoaded()) {
            AudioEngineNative.nativeMoveClip(clip.getId(), clip.getTrackId(), clip.getStartTick());
            AudioEngineNative.nativeResizeClip(clip.getId(), clip.getLengthTicks());
            AudioEngineNative.nativeClearClipNotes(clip.getId());
            if (!clip.isMuted()) {
                for (ClipItem.Note n : clip.getNotes()) {
                    if (!n.isMuted) {
                        AudioEngineNative.nativeAddNoteToClip(clip.getId(), n.note, n.velocity, n.startOffsetTicks, n.lengthTicks);
                    }
                }
            }
        }
        updateUndoRedoUI();
    }

    @Override
    public void onClipDeleted(ClipItem clip) {
        clips.remove(clip);
        if (AudioEngineNative.isLoaded()) {
            AudioEngineNative.nativeRemoveClip(clip.getId());
        }
        updateUndoRedoUI();
    }

    @Override
    public void onClipsBatchChanged() {
        syncAllClipsToNative();
        updateUndoRedoUI();
    }

    @Override
    public void onPlayheadScrubbed(long tick) {
        if (AudioEngineNative.isLoaded()) {
            AudioEngineNative.nativeTransportSeek(tick);
            updateCounter(tick);
            if (arrangerView != null) arrangerView.setPlayheadTick(tick);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        tickerHandler.removeCallbacksAndMessages(null);
        if (AudioEngineNative.isLoaded()) AudioEngineNative.nativeStop();
    }
}
