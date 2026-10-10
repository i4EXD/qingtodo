package com.qingtodo.app;

import android.graphics.Color;

/** Paired light/dark palettes; preferences store stable IDs rather than list positions. */
public enum ColorTheme {
    PURPLE("purple", "鸢尾紫", "#5654D5", "#C1BBFF", "#ECEAFF", "#302D48"),
    BLUE("blue", "晴空蓝", "#2064B3", "#A9C8FF", "#E8F0FF", "#25354D"),
    MINT("mint", "薄荷绿", "#236B48", "#9AD6B0", "#E5F3E9", "#233B2B"),
    TEAL("teal", "湖水青", "#006A68", "#8CD5D1", "#DDF3F0", "#193A38"),
    ROSE("rose", "樱花粉", "#A33D69", "#F3B1CC", "#FBE7F0", "#482B3A"),
    ORANGE("orange", "暖杏橙", "#9D5316", "#F6BF89", "#FFF0E2", "#443322"),
    CORAL("coral", "珊瑚红", "#AD403C", "#FFB4AD", "#FFE9E5", "#4B2A29"),
    SLATE("slate", "雾霭灰蓝", "#4A5F78", "#B2C9E6", "#E8EEF7", "#2C3442");

    public final String id, label;
    public final int lightPrimary, darkPrimary, lightSoft, darkSoft;

    ColorTheme(String id, String label, String lightPrimary, String darkPrimary, String lightSoft, String darkSoft) {
        this.id = id; this.label = label;
        this.lightPrimary = Color.parseColor(lightPrimary);
        this.darkPrimary = Color.parseColor(darkPrimary);
        this.lightSoft = Color.parseColor(lightSoft);
        this.darkSoft = Color.parseColor(darkSoft);
    }
    public static ColorTheme fromId(String id) {
        for (ColorTheme theme : values()) if (theme.id.equals(id)) return theme;
        return PURPLE;
    }
    public int primary(boolean dark) { return dark ? darkPrimary : lightPrimary; }
    public int nativeStyle() {
        switch (this) {
            case BLUE: return R.style.AccentBlue;
            case MINT: return R.style.AccentMint;
            case TEAL: return R.style.AccentTeal;
            case ROSE: return R.style.AccentRose;
            case ORANGE: return R.style.AccentOrange;
            case CORAL: return R.style.AccentCoral;
            case SLATE: return R.style.AccentSlate;
            default: return R.style.AccentPurple;
        }
    }
    public int soft(boolean dark) { return dark ? darkSoft : lightSoft; }
    public int onPrimary(boolean dark) { return dark ? blend(Color.BLACK, darkSoft, .70f) : Color.WHITE; }
    public int background(boolean dark) { return dark ? blend(0xFF141418, darkSoft, .12f) : blend(Color.WHITE, lightSoft, .24f); }
    public int surface(boolean dark) { return dark ? blend(0xFF202026, darkSoft, .15f) : Color.WHITE; }
    public int ink(boolean dark) { return dark ? blend(0xFFF3F2F6, darkPrimary, .08f) : blend(0xFF202027, lightPrimary, .08f); }
    public int muted(boolean dark) { return dark ? 0xFFB5B0C1 : 0xFF686574; }
    public int outline(boolean dark) { return dark ? blend(darkSoft, Color.WHITE, .09f) : blend(lightSoft, 0xFF8D8898, .08f); }
    public int track(boolean dark) { return blend(soft(dark), primary(dark), .15f); }
    public int snackBackground(boolean dark) { return dark ? blend(Color.WHITE, lightSoft, .45f) : darkSoft; }
    public int snackInk(boolean dark) { return dark ? darkSoft : Color.WHITE; }
    public int snackAction(boolean dark) { return dark ? lightPrimary : darkPrimary; }
    public static int blend(int first, int second, float ratio) {
        return Color.rgb(Math.round(Color.red(first) * (1 - ratio) + Color.red(second) * ratio),
            Math.round(Color.green(first) * (1 - ratio) + Color.green(second) * ratio),
            Math.round(Color.blue(first) * (1 - ratio) + Color.blue(second) * ratio));
    }
}
