package com.maxica.cobass.sequencer;

import com.maxica.cobass.model.ClipItem;
import com.maxica.cobass.model.SnapGrid;

import java.util.ArrayList;
import java.util.List;

public final class ArrangerSnapEngine {

    public static class SnapResult {
        public final long snappedTick;
        public final long magneticGuideTick;

        public SnapResult(long snappedTick, long magneticGuideTick) {
            this.snappedTick = snappedTick;
            this.magneticGuideTick = magneticGuideTick;
        }
    }

    private ArrangerSnapEngine() {}

    public static SnapResult computeMagneticSnap(
        long targetStartTick,
        long clipLength,
        ClipItem activeClip,
        List<ClipItem> allClips,
        SnapGrid snapGrid,
        boolean isMagneticSnapEnabled,
        float pixelsPerTick,
        float magneticSensitivityPx,
        long loopStartTick,
        long loopEndTick,
        long currentPlayheadTick
    ) {
        long baseSnapped = snapGrid.snap(targetStartTick);
        if (!isMagneticSnapEnabled || pixelsPerTick <= 0.0f) {
            return new SnapResult(baseSnapped, -1);
        }

        long thresholdTicks = (long) (magneticSensitivityPx / pixelsPerTick);
        long bestSnapStart = baseSnapped;
        long bestDelta = thresholdTicks + 1;
        long magneticGuide = -1;

        List<Long> snapTargets = new ArrayList<>();
        snapTargets.add(loopStartTick);
        snapTargets.add(loopEndTick);
        snapTargets.add(currentPlayheadTick);

        if (allClips != null) {
            for (ClipItem c : allClips) {
                if (c == activeClip || (c.isSelected() && activeClip != null && activeClip.isSelected())) continue;
                snapTargets.add(c.getStartTick());
                snapTargets.add(c.getEndTick());
            }
        }

        for (long target : snapTargets) {
            long deltaStart = Math.abs(targetStartTick - target);
            if (deltaStart < bestDelta) {
                bestDelta = deltaStart;
                bestSnapStart = target;
                magneticGuide = target;
            }

            long candidateStartForEndSnap = target - clipLength;
            long deltaEnd = Math.abs(targetStartTick - candidateStartForEndSnap);
            if (deltaEnd < bestDelta) {
                bestDelta = deltaEnd;
                bestSnapStart = candidateStartForEndSnap;
                magneticGuide = target;
            }
        }

        if (bestDelta <= thresholdTicks) {
            return new SnapResult(Math.max(0, bestSnapStart), magneticGuide);
        } else {
            return new SnapResult(baseSnapped, -1);
        }
    }
}
