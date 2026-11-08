package com.example.realestate.common.enums;

/** 报修紧急度 */
public enum RepairUrgency {

    NORMAL("普通"),
    URGENT("紧急");

    private final String label;

    RepairUrgency(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static RepairUrgency of(String value) {
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
