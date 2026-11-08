package com.example.realestate.modules.content.model.dto;

import com.example.realestate.common.enums.GuideCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 新建教程/指引/公告（ADMIN，初始 DRAFT） */
@Schema(description = "新建内容")
public record GuideCreateRequest(

        @NotBlank(message = "请填写标题")
        @Size(max = 200, message = "标题不能超过 200 字")
        String title,

        @NotNull(message = "请选择分类")
        GuideCategory category,

        @Size(max = 500, message = "摘要不能超过 500 字")
        String summary,

        @NotBlank(message = "请填写正文")
        @Size(max = 20000, message = "正文不能超过 20000 字")
        String content,

        Integer sort
) {
}
