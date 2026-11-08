package com.example.realestate.common.enums;

/** 还款方式：等额本息 / 等额本金 */
public enum RepaymentMethod {

    EQUAL_INSTALLMENT("等额本息"),
    EQUAL_PRINCIPAL("等额本金");

    private final String label;

    RepaymentMethod(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static RepaymentMethod of(String value) {
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
