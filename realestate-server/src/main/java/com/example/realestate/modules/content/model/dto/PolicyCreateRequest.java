package com.example.realestate.modules.content.model.dto;

import com.example.realestate.common.enums.PolicyType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** 新建政策文档（ADMIN，初始 DRAFT） */
@Schema(description = "新建政策文档")
public record PolicyCreateRequest(

        @NotBlank(message = "请填写标题")
        @Size(max = 200, message = "标题不能超过 200 字")
        String title,

        @Size(max = 80, message = "文号不能超过 80 字")
        String docNo,

        @NotNull(message = "请选择文档类型")
        PolicyType docType,

        @Size(max = 200, message = "来源不能超过 200 字")
        String source,

        LocalDate publishDate,

        LocalDate effectiveDate,

        @NotBlank(message = "请填写正文")
        @Size(max = 40000, message = "正文不能超过 40000 字")
        String content
) {
}
