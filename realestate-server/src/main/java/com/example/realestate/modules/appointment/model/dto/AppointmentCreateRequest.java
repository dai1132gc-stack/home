package com.example.realestate.modules.appointment.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 客户提交预约。preferredTime 接受 ISO-8601（2026-11-10T10:00:00Z / +08:00）
 * 或本地时间（2026-11-10T10:00，按 Asia/Shanghai 解释）。
 */
@Schema(description = "提交预约带看请求")
public record AppointmentCreateRequest(

        @NotNull(message = "房源不能为空")
        Long houseId,

        @NotBlank(message = "看房时间不能为空")
        String preferredTime,

        @NotBlank(message = "联系电话不能为空")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
        String contactPhone,

        @Size(max = 500, message = "备注不能超过 500 字")
        String remark
) {
}
