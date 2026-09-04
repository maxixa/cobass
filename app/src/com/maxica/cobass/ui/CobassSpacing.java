package com.maxica.cobass.ui;

public final class CobassSpacing {

    private CobassSpacing() {}

    // 4dp Metric Rhythm Grid (dp)
    public static final int SPACE_XS = 4;
    public static final int SPACE_SM = 8;
    public static final int SPACE_MD = 12;
    public static final int SPACE_LG = 16;
    public static final int SPACE_XL = 24;

    // Component Rhythm Defaults
    public static final int DIALOG_PADDING = SPACE_LG; // 16dp
    public static final int ELEMENT_GAP    = SPACE_SM; // 8dp
    public static final int SECTION_GAP    = SPACE_XL; // 24dp

    // Standard Button Heights
    public static final int BTN_HEIGHT_COMPACT  = 34; // Toolbar icon & toggle buttons
    public static final int BTN_HEIGHT_STANDARD = 40; // Dialog buttons & regular actions
    public static final int BTN_HEIGHT_LARGE    = 48; // Primary Call-To-Action buttons
}
