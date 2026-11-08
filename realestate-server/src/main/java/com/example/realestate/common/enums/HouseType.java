package com.example.realestate.common.enums;

/** 房源交易类型：出租 / 出售 */
public enum HouseType {

    RENT("出租"),
    SALE("出售");

    private final String label;

    HouseType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
