package com.maxica.cobass.project;

import android.graphics.Color;
import com.maxica.cobass.model.ClipItem;
import com.maxica.cobass.model.SnapGrid;
import com.maxica.cobass.model.StepPatternItem;
import com.maxica.cobass.model.TrackItem;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

public class ProjectSerializer {

    public static String serialize(ProjectData data) throws Exception {
        JSONObject root = new JSONObject();
        root.put("version", 3);
        root.put("name", data.projectName);
        root.put("bpm", (double) data.bpm);
        root.put("loopStart", data.loopStart);
        root.put("loopEnd", data.loopEnd);
        root.put("isLooping", data.isLooping);
        root.put("uiScale", data.uiScalePercent);

        // 1. Serialize Tracks with Modular Plugin Chains
        JSONArray trackArr = new JSONArray();
        for (TrackItem t : data.tracks) {
            JSONObject tObj = new JSONObject();
            tObj.put("id", t.getId());
            tObj.put("name", t.getName());
            tObj.put("type", t.getType().name());
            tObj.put("volume", (double) t.getVolume());
            tObj.put("pan", (double) t.getPan());
            tObj.put("muted", t.isMuted());
            tObj.put("solo", t.isSolo());
            tObj.put("phaseInverted", t.isPhaseInverted());
            tObj.put("color", t.getColor());

            // Modular Instrument Synth Plugin State
            JSONObject instObj = new JSONObject();
            instObj.put("pluginId", t.getInstrumentPluginId());
            instObj.put("stateJson", t.getInstrumentPluginStateJson());
            tObj.put("instrumentPlugin", instObj);

            // Modular 8-Slot Insert FX Rack States
            JSONArray fxArr = new JSONArray();
            for (TrackItem.PluginSlotState slot : t.getInsertFxSlots()) {
                if (slot != null && slot.pluginId != null && !slot.pluginId.isEmpty()) {
                    JSONObject slotObj = new JSONObject();
                    slotObj.put("slot", slot.slotIndex);
                    slotObj.put("pluginId", slot.pluginId);
                    slotObj.put("bypassed", slot.bypassed);
                    slotObj.put("mix", (double) slot.mix);
                    slotObj.put("stateJson", slot.stateJson);
                    fxArr.put(slotObj);
                }
            }
            tObj.put("insertFxRack", fxArr);

            // Core Analog Utility Strip Settings
            tObj.put("eqLow", (double) t.getEqLow());
            tObj.put("eqMid", (double) t.getEqMid());
            tObj.put("eqHigh", (double) t.getEqHigh());
            tObj.put("compThresh", (double) t.getCompThresh());
            tObj.put("compRatio", (double) t.getCompRatio());
            tObj.put("reverbMix", (double) t.getReverbMix());
            tObj.put("delayMix", (double) t.getDelayMix());

            // Step Sequencer Pattern State
            if (t.getType() == TrackItem.Type.STEP_SEQUENCER && t.getStepPattern() != null) {
                JSONObject patObj = new JSONObject();
                StepPatternItem pat = t.getStepPattern();
                patObj.put("name", pat.getName());
                patObj.put("baseLength", pat.getBaseLength());
                JSONArray lanesArr = new JSONArray();
                for (StepPatternItem.Lane l : pat.getLanes()) {
                    JSONObject lObj = new JSONObject();
                    lObj.put("id", l.id);
                    lObj.put("name", l.name);
                    lObj.put("midiNote", l.midiNote);
                    lObj.put("stepCount", l.stepCount);
                    lObj.put("subdivision", l.subdivision.name());
                    lObj.put("volume", (double) l.volume);
                    lObj.put("pan", (double) l.pan);
                    lObj.put("isMuted", l.isMuted);
                    lObj.put("isSolo", l.isSolo);

                    JSONArray stepsArr = new JSONArray();
                    for (int si = 0; si < l.stepCount && si < l.steps.size(); si++) {
                        StepPatternItem.Step s = l.steps.get(si);
                        if (s.active || s.ratchets > 1 || Math.abs(s.nudge) > 0.01f || s.probability < 0.99f || s.pitchOffset != 0) {
                            JSONObject sObj = new JSONObject();
                            sObj.put("idx", si);
                            sObj.put("act", s.active);
                            sObj.put("vel", (double) s.velocity);
                            sObj.put("pitch", s.pitchOffset);
                            sObj.put("gate", (double) s.gate);
                            sObj.put("nudge", (double) s.nudge);
                            sObj.put("ratchet", s.ratchets);
                            sObj.put("prob", (double) s.probability);
                            stepsArr.put(sObj);
                        }
                    }
                    lObj.put("steps", stepsArr);
                    lanesArr.put(lObj);
                }
                patObj.put("lanes", lanesArr);
                tObj.put("stepPattern", patObj);
            }

            trackArr.put(tObj);
        }
        root.put("tracks", trackArr);

        // 2. Serialize Clips & Detailed MIDI Notes
        JSONArray clipArr = new JSONArray();
        for (ClipItem c : data.clips) {
            JSONObject cObj = new JSONObject();
            cObj.put("id", c.getId());
            cObj.put("trackId", c.getTrackId());
            cObj.put("name", c.getName());
            cObj.put("startTick", c.getStartTick());
            cObj.put("lengthTicks", c.getLengthTicks());
            cObj.put("color", c.getColor());
            cObj.put("type", c.getType().name());
            cObj.put("isMuted", c.isMuted());

            JSONArray notesArr = new JSONArray();
            for (ClipItem.Note n : c.getNotes()) {
                JSONObject nObj = new JSONObject();
                nObj.put("note", n.note);
                nObj.put("vel", (double) n.velocity);
                nObj.put("offset", n.startOffsetTicks);
                nObj.put("len", n.lengthTicks);
                nObj.put("muted", n.isMuted);
                notesArr.put(nObj);
            }
            cObj.put("notes", notesArr);
            clipArr.put(cObj);
        }
        root.put("clips", clipArr);

        return root.toString(2);
    }

    public static ProjectData deserialize(String jsonStr) throws Exception {
        JSONObject root = new JSONObject(jsonStr);
        ProjectData data = new ProjectData();
        data.projectName = root.optString("name", "Untitled Project");
        data.bpm = (float) root.optDouble("bpm", 120.0);
        data.loopStart = root.optLong("loopStart", 0);
        data.loopEnd = root.optLong("loopEnd", 1920 * 4);
        data.isLooping = root.optBoolean("isLooping", true);
        data.uiScalePercent = root.optInt("uiScale", 100);

        JSONArray trackArr = root.optJSONArray("tracks");
        if (trackArr != null) {
            for (int i = 0; i < trackArr.length(); i++) {
                JSONObject tObj = trackArr.getJSONObject(i);
                int id = tObj.getInt("id");
                String name = tObj.getString("name");
                TrackItem.Type type = TrackItem.Type.valueOf(tObj.getString("type"));
                TrackItem item = new TrackItem(id, name, type);
                item.setVolume((float) tObj.optDouble("volume", 0.8));
                item.setPan((float) tObj.optDouble("pan", 0.0));
                item.setMuted(tObj.optBoolean("muted", false));
                item.setSolo(tObj.optBoolean("solo", false));
                item.setPhaseInverted(tObj.optBoolean("phaseInverted", false));
                item.setColor(tObj.optInt("color", item.getColor()));

                // Restore Modular Instrument Synth State
                JSONObject instObj = tObj.optJSONObject("instrumentPlugin");
                if (instObj != null) {
                    item.setInstrumentPluginId(instObj.optString("pluginId", ""));
                    item.setInstrumentPluginStateJson(instObj.optString("stateJson", "{}"));
                }

                // Restore Modular 8-Slot Insert FX Rack
                JSONArray fxArr = tObj.optJSONArray("insertFxRack");
                if (fxArr != null) {
                    for (int f = 0; f < fxArr.length(); f++) {
                        JSONObject sObj = fxArr.getJSONObject(f);
                        int slotIdx = sObj.getInt("slot");
                        String pId = sObj.getString("pluginId");
                        boolean bypassed = sObj.optBoolean("bypassed", false);
                        float mix = (float) sObj.optDouble("mix", 1.0);
                        String stateJson = sObj.optString("stateJson", "{}");
                        item.getInsertFxSlots().add(new TrackItem.PluginSlotState(slotIdx, pId, bypassed, mix, stateJson));
                    }
                }

                // Restore Core Utility Strip Parameters
                item.setEqLow((float) tObj.optDouble("eqLow", 0.0));
                item.setEqMid((float) tObj.optDouble("eqMid", 0.0));
                item.setEqHigh((float) tObj.optDouble("eqHigh", 0.0));
                item.setCompThresh((float) tObj.optDouble("compThresh", -12.0));
                item.setCompRatio((float) tObj.optDouble("compRatio", 3.0));
                item.setReverbMix((float) tObj.optDouble("reverbMix", 0.3));
                item.setDelayMix((float) tObj.optDouble("delayMix", 0.35));

                // Restore Step Sequencer Pattern
                JSONObject patObj = tObj.optJSONObject("stepPattern");
                if (patObj != null) {
                    StepPatternItem pat = new StepPatternItem(item.getId(), patObj.optString("name", "Pattern"));
                    pat.setBaseLength(patObj.optInt("baseLength", 16));
                    JSONArray lanesArr = patObj.optJSONArray("lanes");
                    if (lanesArr != null) {
                        for (int li = 0; li < lanesArr.length(); li++) {
                            JSONObject lObj = lanesArr.getJSONObject(li);
                            int laneId = lObj.optInt("id", li);
                            String laneName = lObj.optString("name", "Lane " + (li + 1));
                            int midiNote = lObj.optInt("midiNote", 60);
                            int stepCount = lObj.optInt("stepCount", 16);
                            StepPatternItem.Lane lane = new StepPatternItem.Lane(laneId, laneName, midiNote, stepCount);
                            try {
                                lane.subdivision = SnapGrid.valueOf(lObj.optString("subdivision", "BEAT_1_4"));
                            } catch (Exception ignored) {
                                lane.subdivision = SnapGrid.BEAT_1_4;
                            }
                            lane.volume = (float) lObj.optDouble("volume", 0.8);
                            lane.pan = (float) lObj.optDouble("pan", 0.0);
                            lane.isMuted = lObj.optBoolean("isMuted", false);
                            lane.isSolo = lObj.optBoolean("isSolo", false);

                            JSONArray stepsArr = lObj.optJSONArray("steps");
                            if (stepsArr != null) {
                                for (int si = 0; si < stepsArr.length(); si++) {
                                    JSONObject sObj = stepsArr.getJSONObject(si);
                                    int sIdx = sObj.getInt("idx");
                                    if (sIdx >= 0 && sIdx < lane.steps.size()) {
                                        StepPatternItem.Step st = lane.steps.get(sIdx);
                                        st.active = sObj.optBoolean("act", true);
                                        st.velocity = (float) sObj.optDouble("vel", 0.85);
                                        st.pitchOffset = sObj.optInt("pitch", 0);
                                        st.gate = (float) sObj.optDouble("gate", 0.75);
                                        st.nudge = (float) sObj.optDouble("nudge", 0.0);
                                        st.ratchets = sObj.optInt("ratchet", 1);
                                        st.probability = (float) sObj.optDouble("prob", 1.0);
                                    }
                                }
                            }
                            pat.getLanes().add(lane);
                        }
                    }
                    item.setStepPattern(pat);
                }

                data.tracks.add(item);
            }
        }

        JSONArray clipArr = root.optJSONArray("clips");
        if (clipArr != null) {
            for (int i = 0; i < clipArr.length(); i++) {
                JSONObject cObj = clipArr.getJSONObject(i);
                int id = cObj.getInt("id");
                int trackId = cObj.getInt("trackId");
                String name = cObj.getString("name");
                long startTick = cObj.getLong("startTick");
                long lengthTicks = cObj.getLong("lengthTicks");
                int color = cObj.optInt("color", Color.parseColor("#1C6DD0"));
                TrackItem.Type type = TrackItem.Type.valueOf(cObj.getString("type"));

                ClipItem clip = new ClipItem(id, trackId, startTick, lengthTicks, name, color, type);
                clip.setMuted(cObj.optBoolean("isMuted", false));

                JSONArray notesArr = cObj.optJSONArray("notes");
                if (notesArr != null) {
                    for (int j = 0; j < notesArr.length(); j++) {
                        JSONObject nObj = notesArr.getJSONObject(j);
                        int note = nObj.getInt("note");
                        float vel = (float) nObj.optDouble("vel", 0.85);
                        long offset = nObj.getLong("offset");
                        long len = nObj.getLong("len");
                        boolean muted = nObj.optBoolean("muted", false);
                        clip.addNote(note, vel, offset, len, muted);
                    }
                }
                data.clips.add(clip);
            }
        }

        return data;
    }

    public static void saveToFile(File file, ProjectData data) throws Exception {
        String json = serialize(data);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(json.getBytes(StandardCharsets.UTF_8));
            fos.flush();
        }
    }

    public static ProjectData loadFromFile(File file) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        try (FileInputStream fis = new FileInputStream(file)) {
            int read;
            while ((read = fis.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
            }
        }
        return deserialize(baos.toString(StandardCharsets.UTF_8.name()));
    }
}
