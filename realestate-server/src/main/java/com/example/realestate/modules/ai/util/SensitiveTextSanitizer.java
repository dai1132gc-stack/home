package com.example.realestate.modules.ai.util;

import java.util.regex.Pattern;

/**
 * 发送模型前的敏感串清理：手机号、身份证号、邮箱替换为占位符。
 * 不做语义改写，仅防隐私外泄；清理发生在长度校验之后。
 */
public final class SensitiveTextSanitizer {

    // 边界约束避免误伤：先证件号后手机号（手机号正则可能命中证件号内部 11 位数字）
    private static final Pattern ID_CARD = Pattern.compile("(?<![0-9])(\\d{17}[\\dXx]|\\d{15})(?![0-9Xx])");
    private static final Pattern PHONE = Pattern.compile("(?<![0-9])1[3-9]\\d{9}(?![0-9])");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    private SensitiveTextSanitizer() {
    }

    public static String sanitize(String input) {
        if (input == null) {
            return null;
        }
        String result = EMAIL.matcher(input).replaceAll("[邮箱]");
        result = ID_CARD.matcher(result).replaceAll("[证件号]");
        result = PHONE.matcher(result).replaceAll("[手机号]");
        return result;
    }
}
