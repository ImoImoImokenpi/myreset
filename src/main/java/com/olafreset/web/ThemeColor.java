package com.olafreset.web;

import java.util.List;
import java.util.regex.Pattern;

/** 設定画面で選べるメインカラー。 */
public record ThemeColor(String name, String hex) {

    public static final List<ThemeColor> PRESETS = List.of(
            new ThemeColor("インディゴ", "#4f46e5"),
            new ThemeColor("ブルー", "#2563eb"),
            new ThemeColor("シアン", "#0891b2"),
            new ThemeColor("ティール", "#0d9488"),
            new ThemeColor("グリーン", "#16a34a"),
            new ThemeColor("オレンジ", "#ea580c"),
            new ThemeColor("ローズ", "#e11d48"),
            new ThemeColor("パープル", "#9333ea"),
            new ThemeColor("グラファイト", "#334155"));

    private static final Pattern HEX = Pattern.compile("#[0-9a-fA-F]{6}");

    /** CSS に埋め込むので、#rrggbb 形式以外は受け付けない。 */
    public static boolean isValid(String value) {
        return value != null && HEX.matcher(value).matches();
    }
}
