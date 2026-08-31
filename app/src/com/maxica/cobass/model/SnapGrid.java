package com.maxica.cobass.model;

public enum SnapGrid {
    // Multi-Bar & Bars
    BAR_2("2 Bars", 3840, Category.STRAIGHT),
    BAR_1("1 Bar", 1920, Category.STRAIGHT),
    BAR_HALF("1/2 Bar", 960, Category.STRAIGHT),

    // Straight Grid (Standard & Legacy Identifiers)
    BEAT_1("1/4 Beat", 480, Category.STRAIGHT),
    BEAT_1_2("1/8 Beat", 240, Category.STRAIGHT),
    BEAT_1_4("1/16 Beat", 120, Category.STRAIGHT),
    BEAT_1_8("1/32 Beat", 60, Category.STRAIGHT),
    BEAT_1_8TH("1/8 Beat", 240, Category.STRAIGHT),
    BEAT_1_16TH("1/16 Beat", 120, Category.STRAIGHT),
    BEAT_1_32ND("1/32 Beat", 60, Category.STRAIGHT),

    // Triplet Grid
    TRIPLET_1_4("1/4 Triplet", 320, Category.TRIPLET),
    TRIPLET_1_8("1/8 Triplet", 160, Category.TRIPLET),
    TRIPLET_1_16("1/16 Triplet", 80, Category.TRIPLET),
    TRIPLET_1_32("1/32 Triplet", 40, Category.TRIPLET),

    // Dotted Grid
    DOTTED_1_4("1/4 Dotted", 720, Category.DOTTED),
    DOTTED_1_8("1/8 Dotted", 360, Category.DOTTED),
    DOTTED_1_16("1/16 Dotted", 180, Category.DOTTED),

    // Free
    OFF("Free (Off)", 1, Category.FREE);

    public enum Category {
        STRAIGHT,
        TRIPLET,
        DOTTED,
        FREE
    }

    private final String label;
    private final int ticks;
    private final Category category;

    SnapGrid(String label, int ticks, Category category) {
        this.label = label;
        this.ticks = ticks;
        this.category = category;
    }

    public String getLabel() { return label; }
    public int getTicks() { return ticks; }
    public Category getCategory() { return category; }

    public long snap(long tick) {
        if (ticks <= 1) return Math.max(0, tick);
        return Math.max(0, ((tick + (ticks / 2)) / ticks) * ticks);
    }
}
