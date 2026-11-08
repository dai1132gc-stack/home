package com.example.realestate.common.enums;

/**
 * 交易节点状态：PENDING（待处理）→ DONE（已完成）；合同终止/作废时 PENDING → CANCELLED。
 * 终态不允许再次迁移，重复完成返回 409。
 */
public enum TransactionStatus {

    PENDING("待处理"),
    DONE("已完成"),
    CANCELLED("已取消");

    private final String label;

    TransactionStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean canTransitionTo(TransactionStatus target) {
        if (target == null || target == this) {
            return false;
        }
        return switch (this) {
            case PENDING -> target == DONE || target == CANCELLED;
            case DONE, CANCELLED -> false;
        };
    }

    public static TransactionStatus of(String value) {
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
