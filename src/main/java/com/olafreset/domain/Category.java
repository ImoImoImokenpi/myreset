package com.olafreset.domain;

public enum Category {
    BODY("体", "activity"),
    MIND("心", "moon"),
    SKILL("スキル", "book"),
    LIFE("生活", "sun");

    private final String label;
    /** templates/icons.html のアイコン名。 */
    private final String icon;

    Category(String label, String icon) {
        this.label = label;
        this.icon = icon;
    }

    public String getLabel() {
        return label;
    }

    public String getIcon() {
        return icon;
    }
}
