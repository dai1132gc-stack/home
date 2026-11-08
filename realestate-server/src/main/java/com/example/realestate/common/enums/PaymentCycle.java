package com.example.realestate.common.enums;

/**
 * 付款周期：月付 / 季付 / 年付。金额计算与展示均按此枚举，不接受自由文本。
 */
public enum PaymentCycle {

    MONTHLY("月付"),
    QUARTERLY("季付"),
    YEARLY("年付");

    private final String label;

    PaymentCycle(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
