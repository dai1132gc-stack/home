package com.example.realestate.infrastructure.pdf;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 人民币金额大写转换（合同 PDF 使用）。
 * 规则：整数部分按 4 位分组（个/万/亿），小数部分角、分；零元整、壹拾、壹佰零壹等边界按财务惯例输出。
 * 金额上限受 DECIMAL(12,2) 约束（≤ 999,999,999,999.99）。
 */
public final class ChineseAmountConverter {

    private static final char[] DIGITS = "零壹贰叁肆伍陆柒捌玖".toCharArray();
    private static final String[] POSITIONS = {"仟", "佰", "拾", ""};
    private static final String[] GROUP_UNITS = {"", "万", "亿", "万亿"};

    private ChineseAmountConverter() {
    }

    public static String toChinese(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        BigDecimal value = amount.setScale(2, RoundingMode.HALF_UP);
        if (value.signum() < 0) {
            throw new IllegalArgumentException("金额不能为负数");
        }
        if (value.compareTo(new BigDecimal("1000000000000")) >= 0) {
            throw new IllegalArgumentException("金额超出支持范围");
        }
        long integerPart = value.longValue();
        int cents = value.subtract(new BigDecimal(integerPart)).movePointRight(2).intValue();
        int jiao = cents / 10;
        int fen = cents % 10;

        StringBuilder result = new StringBuilder();
        if (integerPart > 0) {
            result.append(integerToChinese(integerPart)).append('元');
        }
        if (jiao == 0 && fen == 0) {
            result.append(integerPart > 0 ? "整" : "零元整");
            return result.toString();
        }
        if (jiao > 0) {
            result.append(DIGITS[jiao]).append('角');
        } else if (fen > 0 && integerPart > 0) {
            result.append('零');
        }
        if (fen > 0) {
            result.append(DIGITS[fen]).append('分');
        }
        return result.toString();
    }

    private static String integerToChinese(long value) {
        StringBuilder result = new StringBuilder();
        boolean pendingZero = false;
        for (int group = 3; group >= 0; group--) {
            int unit = (int) Math.pow(10000, group);
            int part = (int) (value / unit % 10000);
            if (part == 0) {
                if (result.length() > 0) {
                    pendingZero = true;
                }
                continue;
            }
            if (result.length() > 0 && (pendingZero || part < 1000)) {
                result.append('零');
            }
            result.append(groupToChinese(part)).append(GROUP_UNITS[group]);
            pendingZero = false;
        }
        return result.toString();
    }

    private static String groupToChinese(int group) {
        StringBuilder result = new StringBuilder();
        boolean pendingZero = false;
        for (int i = 0; i < 4; i++) {
            int digit = group / (int) Math.pow(10, 3 - i) % 10;
            if (digit == 0) {
                if (result.length() > 0) {
                    pendingZero = true;
                }
                continue;
            }
            if (pendingZero) {
                result.append('零');
                pendingZero = false;
            }
            result.append(DIGITS[digit]).append(POSITIONS[i]);
        }
        return result.toString();
    }
}
