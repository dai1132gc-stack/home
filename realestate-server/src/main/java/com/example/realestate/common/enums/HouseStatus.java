package com.example.realestate.common.enums;

/**
 * 房源状态机（W3 扩展）：
 * PENDING（待发布）→ PUBLISHED（已发布）；
 * PUBLISHED ↔ OFFLINE（下架/重新上架，W2 语义保留）；
 * PUBLISHED → RESERVED（预约确认）→ CONTRACTED（合同生效）；
 * PUBLISHED → CONTRACTED（无预约直接签约）；
 * RESERVED → PUBLISHED（预约取消/完成/爽约且无其他有效预约）；
 * CONTRACTED → PUBLISHED（合同终止）。
 * 非法迁移由 Service 拒绝并返回 409，不静默覆盖。
 */
public enum HouseStatus {

    PENDING("待发布"),
    PUBLISHED("已发布"),
    RESERVED("已预定"),
    CONTRACTED("已签约"),
    OFFLINE("已下架");

    private final String label;

    HouseStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 是否对外可见（游客/客户可浏览） */
    public boolean isPubliclyVisible() {
        return this == PUBLISHED;
    }

    /** 是否允许从当前状态迁移到目标状态 */
    public boolean canTransitionTo(HouseStatus target) {
        if (target == null || target == this) {
            return false;
        }
        return switch (this) {
            case PENDING -> target == PUBLISHED;
            case PUBLISHED -> target == OFFLINE || target == RESERVED || target == CONTRACTED;
            case RESERVED -> target == PUBLISHED || target == CONTRACTED;
            case CONTRACTED -> target == PUBLISHED;
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
