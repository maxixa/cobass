package com.maxica.cobass.ui;

import android.graphics.Color;
import com.maxica.cobass.core.Constants;

public final class CobassTheme {

    private CobassTheme() {}

    // 1. Semantic Surfaces (Deepest to Elevated)
    public static final int SURFACE_0 = 0xFF121316; // Deep app background
    public static final int SURFACE_1 = 0xFF181A22; // Panel & card background
    public static final int SURFACE_2 = 0xFF242734; // Elevated control background
    public static final int SURFACE_3 = 0xFF2C2F3C; // Control border / divider / pressed
    public static final int SURFACE_4 = 0xFF3A3D4E; // Highlighted surface / active container

    // 2. Semantic Accents & Status Tones
    public static final int ACCENT_PRIMARY = 0xFF0A84FF; // Main blue (interactive, tool select)
    public static final int ACCENT_SUCCESS = 0xFF30D158; // Active transport, pass, confirmation
    public static final int ACCENT_WARNING = 0xFFFFD60A; // Selection border, solo active
    public static final int ACCENT_DANGER  = 0xFFFF453A; // Mute, delete, record, error
    public static final int ACCENT_INFO    = 0xFF64D2FF; // Cyan snap lines, playhead flags
    public static final int ACCENT_PURPLE  = 0xFFBF5AF2; // Step sequencer, special FX
    public static final int ACCENT_ORANGE  = 0xFFFF9F0A; // Secondary sliders, warnings
    public static final int ACCENT_AMBER   = 0xFFD97706; // Audio waveforms, transients

    // 3. Text & Content Tiers
    public static final int TEXT_PRIMARY   = 0xFFF2F2F7; // Headings, active values, high contrast
    public static final int TEXT_SECONDARY = 0xFF8E8E93; // Labels, inactive icons, descriptions
    public static final int TEXT_DISABLED  = 0xFF636366; // Muted items, disabled buttons

    // 4. Canvas Grid, Playhead & Selection Tokens
    public static final int GRID_BAR         = 0xFF262934;
    public static final int GRID_BEAT        = 0xFF1E202A;
    public static final int GRID_SUBDIVISION = 0xFF161820;

    public static final int PLAYHEAD_NEEDLE  = 0xFFFF453A;
    public static final int LOOP_OVERLAY     = 0x180A84FF;
    public static final int LOOP_BORDER      = 0xFF0A84FF;
    public static final int SELECTION_BORDER = 0xFFFFD60A;
    public static final int MARQUEE_FILL     = 0x250A84FF;
    public static final int MARQUEE_BORDER   = 0xFF0A84FF;
    public static final int GHOST_FILL       = 0x7864D2FF;
    public static final int GHOST_BORDER     = 0xFF64D2FF;

    // 5. Track Archetype Colors
    public static final int TRACK_SYNTH = Constants.COLOR_TRACK_SYNTH;
    public static final int TRACK_AUDIO = Constants.COLOR_TRACK_AUDIO;
    public static final int TRACK_STEP  = Constants.COLOR_TRACK_STEP;

    // 6. Standard Drum Matrix Palette (Pre-cached 32-bit ARGB ints)
    public static final int[] DRUM_PALETTE = {
        0xFF0A84FF, // Kick (Blue)
        0xFFFF9F0A, // Snare (Orange)
        0xFF30D158, // Closed Hat (Green)
        0xFFBF5AF2, // Open Hat (Purple)
        0xFFFF453A, // Tom / Perc (Red)
        0xFF64D2FF, // Clap (Cyan)
        0xFFFFD60A, // Ride (Yellow)
        0xFFAC8E68  // Shaker / Tan
    };

    // 7. Corner Radii
    public static final float RADIUS_SM = 4.0f; // Chips, badges
    public static final float RADIUS_MD = 6.0f; // Buttons, input fields, note boxes
    public static final float RADIUS_LG = 8.0f; // Cards, dialog containers, canvas clips

    // 8. Stroke & Border Widths
    public static final float BORDER_THIN      = 1.0f;
    public static final float BORDER_STANDARD  = 1.5f;
    public static final float BORDER_ACTIVE    = 2.5f;
    public static final float BORDER_SELECTION = 3.5f;

    /**
     * Unified Velocity Heatmap Gradient generator.
     * Computes real-time ARGB color based on normalized velocity [0.0, 1.0].
     */
    public static int getVelocityHeatmapColor(float vel, boolean isSelected, boolean isMuted) {
        if (isMuted) return 0x5A6E7382; // #5A6E7382 muted steel gray
        if (isSelected) return SELECTION_BORDER;

        float v = Math.max(0.0f, Math.min(1.0f, vel));
        if (v <= 0.5f) {
            float t = v / 0.5f;
            int r = (int) (50 + t * (10 - 50));
            int g = (int) (173 + t * (132 - 173));
            int b = (int) (230 + t * (255 - 230));
            return Color.rgb(Math.max(0, Math.min(255, r)), Math.max(0, Math.min(255, g)), Math.max(0, Math.min(255, b)));
        } else {
            float t = (v - 0.5f) / 0.5f;
            int r = (int) (10 + t * (255 - 10));
            int g = (int) (132 + t * (69 - 132));
            int b = (int) (255 + t * (58 - 255));
            return Color.rgb(Math.max(0, Math.min(255, r)), Math.max(0, Math.min(255, g)), Math.max(0, Math.min(255, b)));
        }
    }
}
