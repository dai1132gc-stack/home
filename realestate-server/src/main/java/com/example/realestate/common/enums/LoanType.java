package com.example.realestate.common.enums;

/** 贷款类型：商业贷款 / 公积金贷款 */
public enum LoanType {

    COMMERCIAL("商业贷款"),
    FUND("公积金贷款");

    private final String label;

    LoanType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static LoanType of(String value) {
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
