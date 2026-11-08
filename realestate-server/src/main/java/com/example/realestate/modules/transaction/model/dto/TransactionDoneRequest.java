package com.example.realestate.modules.transaction.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** ADMIN 完成交易节点（可附备注） */
@Schema(description = "完成交易节点请求")
public record TransactionDoneRequest(

        @Size(max = 500, message = "备注不能超过 500 字")
        String remark
) {
}
