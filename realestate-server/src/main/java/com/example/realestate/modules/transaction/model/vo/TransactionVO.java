package com.example.realestate.modules.transaction.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 交易节点视图：DEPOSIT 定金 / SIGN 签约 / HANDOVER 交房。
 */
@Schema(description = "交易节点")
public record TransactionVO(
        Long id,
        String node,
        String nodeLabel,
        String status,
        String statusLabel,
        Instant doneAt,
        String remark,
        Instant updatedAt
) {
}
