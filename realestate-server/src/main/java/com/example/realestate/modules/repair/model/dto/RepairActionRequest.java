package com.example.realestate.modules.repair.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** 报修状态操作附加说明（可选）。 */
@Schema(description = "报修状态操作")
public record RepairActionRequest(

        @Size(max = 500, message = "说明不能超过 500 字")
        String reason
) {
}
