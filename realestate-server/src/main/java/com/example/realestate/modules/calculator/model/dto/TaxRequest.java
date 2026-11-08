package com.example.realestate.modules.calculator.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** 税费测算请求：按城市与成交价匹配配置规则，未命中返回 400。 */
@Schema(description = "税费测算请求")
public record TaxRequest(

        @NotBlank(message = "请选择城市")
        @Size(max = 60, message = "城市名称过长")
        String city,

        @NotNull(message = "请填写成交价")
        @DecimalMin(value = "0.01", message = "成交价必须大于 0")
        @DecimalMax(value = "1000000000", message = "成交价超出可测算范围")
        BigDecimal price,

        @Schema(description = "建筑面积（㎡，可选，仅用于假设说明）")
        @DecimalMin(value = "1", message = "面积不能小于 1㎡")
        @DecimalMax(value = "10000", message = "面积超出可测算范围")
        BigDecimal area,

        @Schema(description = "是否首套（可选，仅用于假设说明）")
        Boolean firstHome
) {
}
