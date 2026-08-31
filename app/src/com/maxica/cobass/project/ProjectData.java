package com.maxica.cobass.project;

import com.maxica.cobass.model.ClipItem;
import com.maxica.cobass.model.TrackItem;
import java.util.ArrayList;
import java.util.List;

public class ProjectData {
    public String projectName = "Untitled Project";
    public float bpm = 120.0f;
    public long loopStart = 0;
    public long loopEnd = 1920 * 4;
    public boolean isLooping = true;
    public int uiScalePercent = 100;

    public List<TrackItem> tracks = new ArrayList<>();
    public List<ClipItem> clips = new ArrayList<>();
}
