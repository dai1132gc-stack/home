package com.example.realestate.modules.calculator.model.dto;

import com.example.realestate.common.enums.LoanType;
import com.example.realestate.common.enums.RepaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 房贷测算请求：金额单位元，年利率为小数（0.0385 表示 3.85%）。
 * 不传 annualRate 时按贷款类型 + 年限读取生效中的配置费率。
 */
@Schema(description = "房贷测算请求")
public record MortgageRequest(

        @NotNull(message = "请选择贷款类型")
        LoanType loanType,

        @NotNull(message = "请选择还款方式")
        RepaymentMethod repaymentMethod,

        @NotNull(message = "请填写房屋总价")
        @DecimalMin(value = "10000", message = "房屋总价不能低于 10000 元")
        @DecimalMax(value = "100000000", message = "房屋总价不能超过 1 亿元")
        BigDecimal totalPrice,

        @NotNull(message = "请填写首付金额")
        @DecimalMin(value = "0", message = "首付不能为负")
        BigDecimal downPayment,

        @NotNull(message = "请选择贷款年限")
        @Min(value = 1, message = "贷款年限最少 1 年")
        @Max(value = 30, message = "贷款年限最多 30 年")
        Integer termYears,

        @Schema(description = "年利率（可选，小数形式；不传则读取配置）")
        @DecimalMin(value = "0", message = "年利率不能为负")
        @DecimalMax(value = "0.24", message = "年利率不能超过 24%")
        BigDecimal annualRate
) {
}
