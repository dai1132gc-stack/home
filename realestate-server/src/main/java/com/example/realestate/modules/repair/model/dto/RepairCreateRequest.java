package com.example.realestate.modules.repair.model.dto;

import com.example.realestate.common.enums.RepairCategory;
import com.example.realestate.common.enums.RepairUrgency;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** 客户提交报修。houseId 与 contractId 至少一个；有合同时校验合同归属与状态。 */
@Schema(description = "提交报修")
public record RepairCreateRequest(

        @Schema(description = "房源ID（无合同时必填）")
        Long houseId,

        @Schema(description = "关联合同ID（可空，须为本人 ACTIVE 合同）")
        Long contractId,

        @NotNull(message = "请选择报修分类")
        RepairCategory category,

        @NotNull(message = "请选择紧急程度")
        RepairUrgency urgency,

        @NotBlank(message = "请填写报修标题")
        @Size(min = 4, max = 120, message = "标题长度需在 4-120 字之间")
        String title,

        @NotBlank(message = "请描述报修问题")
        @Size(min = 10, max = 2000, message = "问题描述长度需在 10-2000 字之间")
        String description,

        @NotBlank(message = "请填写联系电话")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "联系电话格式不正确")
        String contactPhone,

        @Schema(description = "期望上门时间（可选，ISO-8601）")
        Instant preferredVisitTime
) {
}
