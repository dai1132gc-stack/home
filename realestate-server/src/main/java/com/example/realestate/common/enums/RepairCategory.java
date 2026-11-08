package com.example.realestate.common.enums;

/** 报修分类：水电/电器/结构/其他 */
public enum RepairCategory {

    PLUMBING("水暖管道"),
    ELECTRICAL("电路照明"),
    APPLIANCE("家电设备"),
    STRUCTURE("房屋结构"),
    OTHER("其他");

    private final String label;

    RepairCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static RepairCategory of(String value) {
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
