package com.example.desktop;

import java.awt.*;

/** Sleek Cosmic palette matching the Android app theme. */
public final class Theme {
    // Backgrounds
    public static final Color MIDNIGHT = new Color(0x1C, 0x1B, 0x1F);
    public static final Color CARD = new Color(0x2B, 0x29, 0x30);
    public static final Color CARD_HOVER = new Color(0x35, 0x33, 0x3A);
    public static final Color INPUT_BG = new Color(0x1E, 0x1D, 0x22);

    // Accents
    public static final Color LAVENDER = new Color(0xD0, 0xBC, 0xFF);
    public static final Color LIME = new Color(0xB2, 0xF0, 0x42);
    public static final Color STATUS_BG = new Color(0xEA, 0xDD, 0xFF);
    public static final Color STATUS_TEXT = new Color(0x21, 0x00, 0x5D);

    // Text
    public static final Color TEXT_PRIMARY = new Color(0xE6, 0xE1, 0xE5);
    public static final Color TEXT_SECONDARY = new Color(0xCA, 0xC4, 0xD0);
    public static final Color MUTED = new Color(0x93, 0x8F, 0x99);

    // Borders
    public static final Color BORDER = new Color(0x49, 0x45, 0x4F);

    // Indicators
    public static final Color RED = new Color(0xF2, 0xB8, 0xB5);
    public static final Color STOP_RED = new Color(0xB4, 0x23, 0x18);
    public static final Color LIVE_RED = new Color(0xFF, 0x44, 0x44);

    // Fonts
    public static final Font TITLE = new Font("SansSerif", Font.BOLD, 22);
    public static final Font HEADING = new Font("SansSerif", Font.BOLD, 14);
    public static final Font BODY = new Font("SansSerif", Font.PLAIN, 13);
    public static final Font LABEL = new Font("SansSerif", Font.BOLD, 10);
    public static final Font MONO = new Font("Monospaced", Font.BOLD, 13);
    public static final Font STAT_VALUE = new Font("SansSerif", Font.BOLD, 20);
    public static final Font STAT_LABEL = new Font("SansSerif", Font.BOLD, 9);

    private Theme() {}
}
