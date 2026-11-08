package com.example.realestate.common.enums;

/**
 * 预约带看状态机：
 * PENDING（待确认）→ CONFIRMED（已确认）→ COMPLETED（已完成）；
 * PENDING/CONFIRMED 可 CANCELLED（客户或 ADMIN）；CONFIRMED 可 NO_SHOW（ADMIN 标记爽约）。
 * 终态：COMPLETED / CANCELLED / NO_SHOW，均不可再迁移。
 */
public enum AppointmentStatus {

    PENDING("待确认"),
    CONFIRMED("已确认"),
    COMPLETED("已完成"),
    CANCELLED("已取消"),
    NO_SHOW("已爽约");

    private final String label;

    AppointmentStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED || this == NO_SHOW;
    }

    public boolean canTransitionTo(AppointmentStatus target) {
        if (target == null || target == this) {
            return false;
        }
        return switch (this) {
            case PENDING -> target == CONFIRMED || target == CANCELLED;
            case CONFIRMED -> target == COMPLETED || target == CANCELLED || target == NO_SHOW;
            case COMPLETED, CANCELLED, NO_SHOW -> false;
        };
    }

    public static AppointmentStatus of(String value) {
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
