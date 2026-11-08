package com.example.realestate.modules.content.model.dto;

import com.example.realestate.common.enums.PolicyType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** 编辑政策文档：仅 DRAFT/OFFLINE；PUBLISHED 需先下架。 */
@Schema(description = "编辑政策文档")
public record PolicyUpdateRequest(

        @Size(max = 200, message = "标题不能超过 200 字")
        String title,

        @Size(max = 80, message = "文号不能超过 80 字")
        String docNo,

        PolicyType docType,

        @Size(max = 200, message = "来源不能超过 200 字")
        String source,

        LocalDate publishDate,

        LocalDate effectiveDate,

        @Size(max = 40000, message = "正文不能超过 40000 字")
        String content,

        Integer version
) {
}
