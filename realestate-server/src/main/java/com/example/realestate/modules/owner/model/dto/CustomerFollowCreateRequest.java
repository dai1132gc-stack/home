package com.example.realestate.modules.owner.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** 新增客源跟进：followType 允许 PHONE/WECHAT/VISIT/OTHER。 */
@Schema(description = "新增客源跟进")
public record CustomerFollowCreateRequest(

        @NotBlank(message = "请填写跟进内容")
        @Size(max = 1000, message = "跟进内容不能超过 1000 字")
        String content,

        @Schema(description = "PHONE/WECHAT/VISIT/OTHER，默认 PHONE")
        String followType,

        @Schema(description = "下次跟进时间（可选，ISO-8601）")
        Instant nextFollowAt
) {
}
