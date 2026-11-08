package com.example.realestate.infrastructure.logging;

/**
 * 日志脱敏工具：手机号保留区号+掩码；其余敏感字段完全排除（不记录）。
 * 密码、令牌、Authorization、Cookie、openid 等一律不进入日志。
 */
public final class LogMasker {

    private LogMasker() {
    }

    /** 手机号脱敏：保留前 3 后 2，如 138******12 */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 5) {
            return "***";
        }
        int len = phone.length();
        return phone.substring(0, 3) + "*".repeat(Math.max(0, len - 5)) + phone.substring(len - 2);
    }

    /** IP 脱敏：IPv4 保留前两段 */
    public static String maskIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return "";
        }
        String[] parts = ip.split("\\.");
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + ".*.*";
        }
        return "remote";
    }
}
