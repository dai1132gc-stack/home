package com.example.realestate.modules.content.model.dto;

import com.example.realestate.common.enums.GuideCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** 编辑内容：仅 DRAFT/OFFLINE；PUBLISHED 需先下架。version 用于乐观锁。 */
@Schema(description = "编辑内容")
public record GuideUpdateRequest(

        @Size(max = 200, message = "标题不能超过 200 字")
        String title,

        GuideCategory category,

        @Size(max = 500, message = "摘要不能超过 500 字")
        String summary,

        @Size(max = 20000, message = "正文不能超过 20000 字")
        String content,

        Integer sort,

        Integer version
) {
}
