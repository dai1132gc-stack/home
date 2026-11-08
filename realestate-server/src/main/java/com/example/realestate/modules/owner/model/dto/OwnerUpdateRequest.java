package com.example.realestate.modules.owner.model.dto;

import com.example.realestate.common.enums.OwnerType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 编辑房东 / 客源（PATCH 语义）：字段为 null 表示不修改。
 * version 传入详情页取到的值，用于乐观锁冲突检测。
 */
@Schema(description = "编辑房东/客源请求")
public record OwnerUpdateRequest(

        @Size(max = 64, message = "姓名不能超过 64 字")
        String name,

        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
        String phone,

        OwnerType type,

        @Size(max = 500, message = "备注不能超过 500 字")
        String remark,

        Integer version
) {
}
