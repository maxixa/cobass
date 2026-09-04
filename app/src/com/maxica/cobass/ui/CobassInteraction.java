package com.maxica.cobass.ui;

import android.annotation.SuppressLint;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import com.maxica.cobass.audio.AudioEngineNative;

public final class CobassInteraction {

    private CobassInteraction() {}

    /**
     * Unified tool palette button styling.
     * Active tool receives ACCENT_PRIMARY (or ACCENT_DANGER for destructive actions),
     * while inactive tools receive standard SECONDARY surface styling.
     */
    public static void applyToolState(Button btn, boolean isActive, boolean isDestructive) {
        if (btn == null) return;
        if (isActive) {
            CobassButton.apply(btn, isDestructive ? CobassButton.Variant.DANGER : CobassButton.Variant.PRIMARY, CobassButton.Size.COMPACT);
        } else {
            CobassButton.apply(btn, CobassButton.Variant.SECONDARY, CobassButton.Size.COMPACT);
        }
    }

    /**
     * Unified transport Play/Pause button styling and glyph synchronization.
     */
    public static void applyPlayState(Button btnPlay, boolean isPlaying) {
        if (btnPlay == null) return;
        btnPlay.setText(isPlaying ? "⏸" : "▶");
        if (isPlaying) {
            CobassButton.apply(btnPlay, CobassButton.Variant.SUCCESS, CobassButton.Size.COMPACT);
        } else {
            CobassButton.apply(btnPlay, CobassButton.Variant.SECONDARY, CobassButton.Size.COMPACT);
        }
    }

    /**
     * Unified transport toggle state (Loop, Follow Playhead, etc.).
     */
    public static void applyTransportToggle(Button btn, boolean isEnabled) {
        if (btn == null) return;
        if (isEnabled) {
            CobassButton.apply(btn, CobassButton.Variant.PRIMARY, CobassButton.Size.COMPACT);
        } else {
            CobassButton.apply(btn, CobassButton.Variant.SECONDARY, CobassButton.Size.COMPACT);
        }
    }

    /**
     * Unified Undo/Redo button state and alpha transparency.
     */
    public static void applyUndoRedoState(Button btn, boolean canPerform) {
        if (btn == null) return;
        btn.setEnabled(canPerform);
        btn.setAlpha(canPerform ? 1.0f : 0.35f);
    }

    /**
     * Unified Audition Touch Listener (Touch-Down triggers noteOn, Touch-Up/Cancel triggers noteOff).
     */
    @SuppressLint("ClickableViewAccessibility")
    public static void attachAuditionTouch(View view, int trackId, int midiNote, float velocity) {
        if (view == null) return;
        view.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (AudioEngineNative.isLoaded()) {
                        AudioEngineNative.nativeNoteOn(trackId, midiNote, velocity);
                    }
                    v.setPressed(true);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (AudioEngineNative.isLoaded()) {
                        AudioEngineNative.nativeNoteOff(trackId, midiNote);
                    }
                    v.setPressed(false);
                    return true;
            }
            return false;
        });
    }
}
