package com.example.realestate.modules.owner.model.dto;

import com.example.realestate.common.enums.OwnerType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 新建房东 / 客源（仅 ADMIN） */
@Schema(description = "新建房东/客源请求")
public record OwnerCreateRequest(

        @NotBlank(message = "姓名不能为空")
        @Size(max = 64, message = "姓名不能超过 64 字")
        String name,

        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
        String phone,

        @NotNull(message = "类型不能为空")
        OwnerType type,

        @Size(max = 500, message = "备注不能超过 500 字")
        String remark
) {
}
