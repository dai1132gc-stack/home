package com.example.realestate.common.enums;

/**
 * 房源状态机（W2 范围）：
 * PENDING（待发布）→ PUBLISHED（已发布）→ OFFLINE（已下架）→ PUBLISHED（重新上架）。
 * 非法迁移由 Service 拒绝并返回 409，不静默覆盖。
 */
public enum HouseStatus {

    PENDING("待发布"),
    PUBLISHED("已发布"),
    OFFLINE("已下架");

    private final String label;

    HouseStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 是否允许从当前状态迁移到目标状态 */
    public boolean canTransitionTo(HouseStatus target) {
        if (target == null || target == this) {
            return false;
        }
        return switch (this) {
            case PENDING -> target == PUBLISHED;
            case PUBLISHED -> target == OFFLINE;
            case OFFLINE -> target == PUBLISHED;
        };
    }

    public static HouseStatus of(String value) {
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
