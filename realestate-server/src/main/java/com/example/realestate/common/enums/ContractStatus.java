package com.example.realestate.common.enums;

/**
 * 合同状态机：
 * DRAFT（草稿）→ PENDING_SIGN（待签署）→ ACTIVE（生效）→ TERMINATED（已终止）/ EXPIRED（已到期）；
 * 签署前可 CANCELLED（作废）。ACTIVE 为唯一占用房源的生效态，同一房源同时最多一份 PENDING_SIGN/ACTIVE。
 */
public enum ContractStatus {

    DRAFT("草稿"),
    PENDING_SIGN("待签署"),
    ACTIVE("生效中"),
    EXPIRED("已到期"),
    TERMINATED("已终止"),
    CANCELLED("已作废");

    private final String label;

    ContractStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** PENDING_SIGN/ACTIVE 会占用房源（数据库生成列 active_flag 唯一键据此建立） */
    public boolean occupiesHouse() {
        return this == PENDING_SIGN || this == ACTIVE;
    }

    public boolean isTerminal() {
        return this == EXPIRED || this == TERMINATED || this == CANCELLED;
    }

    public boolean canTransitionTo(ContractStatus target) {
        if (target == null || target == this) {
            return false;
        }
        return switch (this) {
            case DRAFT -> target == PENDING_SIGN || target == CANCELLED;
            case PENDING_SIGN -> target == ACTIVE || target == CANCELLED || target == TERMINATED;
            case ACTIVE -> target == TERMINATED || target == EXPIRED;
            case EXPIRED, TERMINATED, CANCELLED -> false;
        };
    }

    public static ContractStatus of(String value) {
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
