package com.example.realestate.common.util;

/**
 * 展示层脱敏工具。
 * 手机号规则：保留前 3 后 2，中间以 * 填充（138******78）。
 * 日志脱敏见 infrastructure.logging.LogMasker，两者用途不同，避免业务 VO 依赖日志组件。
 */
public final class PhoneMasker {

    private PhoneMasker() {
    }

    public static String mask(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String trimmed = phone.trim();
        if (trimmed.length() < 5) {
            return "***";
        }
        int len = trimmed.length();
        return trimmed.substring(0, 3) + "*".repeat(len - 5) + trimmed.substring(len - 2);
    }
}
