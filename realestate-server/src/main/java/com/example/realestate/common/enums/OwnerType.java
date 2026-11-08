package com.example.realestate.common.enums;

/** 客源档案类型：房东 / 潜在客户（客源） */
public enum OwnerType {

    LANDLORD("房东"),
    CUSTOMER_LEAD("客源");

    private final String label;

    OwnerType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
