package com.example.realestate.infrastructure.pdf;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("人民币金额大写")
class ChineseAmountConverterTest {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "0, 零元整",
            "1, 壹元整",
            "10, 壹拾元整",
            "100, 壹佰元整",
            "1001, 壹仟零壹元整",
            "10000, 壹万元整",
            "11000, 壹万壹仟元整",
            "100000, 壹拾万元整",
            "100000001, 壹亿零壹元整",
            "100000000, 壹亿元整",
            "4500.50, 肆仟伍佰元伍角",
            "12345.67, 壹万贰仟叁佰肆拾伍元陆角柒分",
            "0.05, 伍分",
            "0.50, 伍角"
    })
    @DisplayName("整数分组、零位与角分边界")
    void converts(String amount, String expected) {
        assertThat(ChineseAmountConverter.toChinese(new BigDecimal(amount))).isEqualTo(expected);
    }

    @Test
    @DisplayName("负数与超范围金额拒绝")
    void rejectsInvalid() {
        assertThatThrownBy(() -> ChineseAmountConverter.toChinese(new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ChineseAmountConverter.toChinese(new BigDecimal("1000000000000")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("空金额返回空串")
    void handlesNull() {
        assertThat(ChineseAmountConverter.toChinese(null)).isEmpty();
    }
}
