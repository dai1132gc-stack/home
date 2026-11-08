package com.example.realestate.common.enums;

/** 交易税费类型：契税 / 增值税 / 个人所得税 */
public enum TaxType {

    DEED_TAX("契税"),
    VAT("增值税"),
    INCOME_TAX("个人所得税");

    private final String label;

    TaxType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static TaxType of(String value) {
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
