package com.example.realestate.common.enums;

/**
 * 内容状态：DRAFT 草稿 / PUBLISHED 已发布 / OFFLINE 已下架。
 * 仅 PUBLISHED 对公开接口可见；编辑仅允许 DRAFT/OFFLINE，已发布内容需先下架。
 */
public enum ContentStatus {

    DRAFT("草稿"),
    PUBLISHED("已发布"),
    OFFLINE("已下架");

    private final String label;

    ContentStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean isPubliclyVisible() {
        return this == PUBLISHED;
    }

    public static ContentStatus of(String value) {
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
