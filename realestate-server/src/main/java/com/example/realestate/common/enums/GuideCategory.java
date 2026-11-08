package com.example.realestate.common.enums;

/** 内容分类：租房教程 / 网签指引 / 公告 */
public enum GuideCategory {

    GUIDE("租房教程"),
    NETWORK_GUIDE("网签指引"),
    NOTICE("公告");

    private final String label;

    GuideCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static GuideCategory of(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
