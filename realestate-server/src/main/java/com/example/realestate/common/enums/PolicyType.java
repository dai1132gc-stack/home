package com.example.realestate.common.enums;

/** 政策文档类型：政策 / 通知公告 */
public enum PolicyType {

    POLICY("政策"),
    NOTICE("通知");

    private final String label;

    PolicyType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static PolicyType of(String value) {
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
