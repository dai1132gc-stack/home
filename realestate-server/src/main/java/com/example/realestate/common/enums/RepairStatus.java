package com.example.realestate.common.enums;

/**
 * 报修工单状态机（W4）：
 * SUBMITTED → ACCEPTED/CANCELLED；
 * ACCEPTED → PROCESSING/CANCELLED；
 * PROCESSING → RESOLVED；
 * RESOLVED → CLOSED/REOPENED；
 * CLOSED → REOPENED；REOPENED → PROCESSING；CANCELLED 为终态。
 * 非法迁移统一 409，并写入 repair_status_log。
 */
public enum RepairStatus {

    SUBMITTED("待受理"),
    ACCEPTED("已受理"),
    PROCESSING("处理中"),
    RESOLVED("已解决"),
    CLOSED("已关闭"),
    CANCELLED("已撤销"),
    REOPENED("已重新打开");

    private final String label;

    RepairStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean isTerminal() {
        return this == CANCELLED;
    }

    public boolean canTransitionTo(RepairStatus target) {
        if (target == null || target == this) {
            return false;
        }
        return switch (this) {
            case SUBMITTED -> target == ACCEPTED || target == CANCELLED;
            case ACCEPTED -> target == PROCESSING || target == CANCELLED;
            case PROCESSING -> target == RESOLVED;
            case RESOLVED -> target == CLOSED || target == REOPENED;
            case CLOSED -> target == REOPENED;
            case REOPENED -> target == PROCESSING;
            case CANCELLED -> false;
        };
    }

    public static RepairStatus of(String value) {
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
