package com.example.realestate.common.util;

/**
 * 证件号脱敏：保留前 6 后 4，中间以 * 填充（110101********1234）。
 * 数据库只保存脱敏结果，完整证件号不落库、不写日志。
 */
public final class IdCardMasker {

    private IdCardMasker() {
    }

    public static String mask(String idCard) {
        if (idCard == null || idCard.isBlank()) {
            return null;
        }
        String trimmed = idCard.trim();
        if (trimmed.length() <= 10) {
            return "***";
        }
        return trimmed.substring(0, 6)
                + "*".repeat(trimmed.length() - 10)
                + trimmed.substring(trimmed.length() - 4);
    }
}
