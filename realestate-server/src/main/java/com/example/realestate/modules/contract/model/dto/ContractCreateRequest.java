package com.example.realestate.modules.contract.model.dto;

import com.example.realestate.common.enums.PaymentCycle;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * ADMIN 起草合同。出租方默认取房源关联的房东档案，可用 lessorName/lessorPhone 覆盖；
 * 承租方默认取客户账号资料，可用 customerName/customerPhone 覆盖。
 */
@Schema(description = "起草合同请求")
public record ContractCreateRequest(

        @NotNull(message = "房源不能为空")
        Long houseId,

        @NotNull(message = "承租客户不能为空")
        Long customerId,

        @NotBlank(message = "合同标题不能为空")
        @Size(max = 200, message = "合同标题不能超过 200 字")
        String title,

        @NotNull(message = "金额不能为空")
        @DecimalMin(value = "0.01", message = "金额必须大于 0")
        @Digits(integer = 10, fraction = 2, message = "金额最多 10 位整数、2 位小数")
        BigDecimal amount,

        @NotNull(message = "押金不能为空")
        @DecimalMin(value = "0.00", message = "押金不能为负数")
        @Digits(integer = 10, fraction = 2, message = "押金最多 10 位整数、2 位小数")
        BigDecimal deposit,

        @NotNull(message = "付款周期不能为空")
        PaymentCycle paymentCycle,

        @NotNull(message = "租期开始日期不能为空")
        LocalDate startDate,

        @NotNull(message = "租期结束日期不能为空")
        LocalDate endDate,

        @Size(max = 64, message = "出租方姓名不能超过 64 字")
        String lessorName,

        @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "出租方手机号格式不正确")
        String lessorPhone,

        @Size(max = 64, message = "承租方姓名不能超过 64 字")
        String customerName,

        @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "承租方手机号格式不正确")
        String customerPhone,

        @Size(max = 32, message = "证件号长度不合法")
        String lessorIdCard,

        @Size(max = 32, message = "证件号长度不合法")
        String customerIdCard
) {
}
