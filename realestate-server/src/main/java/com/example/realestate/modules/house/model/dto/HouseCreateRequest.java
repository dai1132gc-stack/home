package com.example.realestate.modules.house.model.dto;

import com.example.realestate.common.enums.HouseType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** 新建房源（仅 ADMIN）；创建后状态固定为 PENDING，不允许客户端指定 */
@Schema(description = "新建房源请求")
public record HouseCreateRequest(

        @NotBlank(message = "标题不能为空")
        @Size(max = 120, message = "标题不能超过 120 字")
        String title,

        @NotNull(message = "房源类型不能为空")
        HouseType houseType,

        @NotBlank(message = "小区不能为空")
        @Size(max = 100, message = "小区名称不能超过 100 字")
        String community,

        @NotBlank(message = "区域不能为空")
        @Size(max = 60, message = "区域不能超过 60 字")
        String region,

        @NotBlank(message = "地址不能为空")
        @Size(max = 255, message = "地址不能超过 255 字")
        String address,

        @NotNull(message = "价格不能为空")
        @DecimalMin(value = "0.01", message = "价格必须大于 0")
        @Digits(integer = 10, fraction = 2, message = "价格最多 10 位整数、2 位小数")
        BigDecimal price,

        @NotNull(message = "面积不能为空")
        @DecimalMin(value = "0.01", message = "面积必须大于 0")
        @Digits(integer = 10, fraction = 2, message = "面积最多 10 位整数、2 位小数")
        BigDecimal area,

        @NotNull(message = "室不能为空")
        @Min(value = 0, message = "室不能小于 0")
        @Max(value = 99, message = "室不能大于 99")
        Integer rooms,

        @NotNull(message = "卫不能为空")
        @Min(value = 0, message = "卫不能小于 0")
        @Max(value = 99, message = "卫不能大于 99")
        Integer bathrooms,

        @Size(max = 32, message = "楼层描述不能超过 32 字")
        String floor,

        @Size(max = 5000, message = "描述不能超过 5000 字")
        String description,

        @Schema(description = "房东/客源ID，可为空；传入时校验存在")
        Long ownerId
) {
}
