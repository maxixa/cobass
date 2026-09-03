package com.maxica.cobass.model;

public class TransformLockMasks {
    public boolean lockDownbeats = false;
    public boolean lockPitches = false;
    public boolean lockRhythm = false;
    public boolean lockVelocities = false;
    public boolean lockBassNotes = false;

    public TransformLockMasks() {}

    public TransformLockMasks(boolean lockDownbeats, boolean lockPitches, boolean lockRhythm, boolean lockVelocities, boolean lockBassNotes) {
        this.lockDownbeats = lockDownbeats;
        this.lockPitches = lockPitches;
        this.lockRhythm = lockRhythm;
        this.lockVelocities = lockVelocities;
        this.lockBassNotes = lockBassNotes;
    }

    public TransformLockMasks copy() {
        return new TransformLockMasks(lockDownbeats, lockPitches, lockRhythm, lockVelocities, lockBassNotes);
    }
}
