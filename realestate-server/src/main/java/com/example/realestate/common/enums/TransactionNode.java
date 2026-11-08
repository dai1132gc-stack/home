package com.example.realestate.common.enums;

/**
 * 交易节点（最小闭环，不做财务流水）：
 * DEPOSIT（付定金）→ SIGN（签约）→ HANDOVER（交房）。
 * SIGN 在合同生效时自动完成，其余由 ADMIN 手工确认。
 */
public enum TransactionNode {

    DEPOSIT("付定金"),
    SIGN("签约"),
    HANDOVER("交房");

    private final String label;

    TransactionNode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static TransactionNode of(String value) {
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
