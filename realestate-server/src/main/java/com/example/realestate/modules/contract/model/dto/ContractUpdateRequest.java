package com.example.realestate.modules.contract.model.dto;

import com.example.realestate.common.enums.PaymentCycle;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 编辑合同（仅 DRAFT）。version 必填以触发乐观锁，冲突返回 409。
 */
@Schema(description = "编辑合同请求（仅草稿）")
public record ContractUpdateRequest(

        @Size(max = 200, message = "合同标题不能超过 200 字")
        String title,

        @DecimalMin(value = "0.01", message = "金额必须大于 0")
        @Digits(integer = 10, fraction = 2, message = "金额最多 10 位整数、2 位小数")
        BigDecimal amount,

        @DecimalMin(value = "0.00", message = "押金不能为负数")
        @Digits(integer = 10, fraction = 2, message = "押金最多 10 位整数、2 位小数")
        BigDecimal deposit,

        PaymentCycle paymentCycle,

        LocalDate startDate,

        LocalDate endDate,

        @NotNull(message = "version 不能为空")
        Integer version
) {
}
