package com.example.realestate.modules.house.model.dto;

import com.example.realestate.common.enums.HouseType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 编辑房源（PATCH 语义，null 表示不修改）；状态通过 publish/offline 接口迁移，此处不可改。
 * version 由详情页回传，用于乐观锁冲突检测；不传则使用数据库当前版本。
 */
@Schema(description = "编辑房源请求")
public record HouseUpdateRequest(

        @Size(max = 120, message = "标题不能超过 120 字")
        String title,

        HouseType houseType,

        @Size(max = 100, message = "小区名称不能超过 100 字")
        String community,

        @Size(max = 60, message = "区域不能超过 60 字")
        String region,

        @Size(max = 255, message = "地址不能超过 255 字")
        String address,

        @DecimalMin(value = "0.01", message = "价格必须大于 0")
        @Digits(integer = 10, fraction = 2, message = "价格最多 10 位整数、2 位小数")
        BigDecimal price,

        @DecimalMin(value = "0.01", message = "面积必须大于 0")
        @Digits(integer = 10, fraction = 2, message = "面积最多 10 位整数、2 位小数")
        BigDecimal area,

        @Min(value = 0, message = "室不能小于 0")
        @Max(value = 99, message = "室不能大于 99")
        Integer rooms,

        @Min(value = 0, message = "卫不能小于 0")
        @Max(value = 99, message = "卫不能大于 99")
        Integer bathrooms,

        @Size(max = 32, message = "楼层描述不能超过 32 字")
        String floor,

        @Size(max = 5000, message = "描述不能超过 5000 字")
        String description,

        Long ownerId,

        Integer version
) {
}
