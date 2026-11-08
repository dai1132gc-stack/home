package com.example.realestate.modules.transaction.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 交易节点状态日志。
 */
@Schema(description = "交易节点状态日志")
public record TransactionLogVO(
        Long id,
        String node,
        String nodeLabel,
        String fromStatus,
        String toStatus,
        Long operatorId,
        String reason,
        Instant createdAt
) {
}
