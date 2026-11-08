package com.example.realestate.modules.contract.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 终止/作废合同（ADMIN），原因必填并写入状态日志 */
@Schema(description = "终止合同请求")
public record ContractTerminateRequest(

        @NotBlank(message = "终止原因不能为空")
        @Size(max = 500, message = "原因不能超过 500 字")
        String reason
) {
}
