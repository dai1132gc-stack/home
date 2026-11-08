package com.example.realestate.modules.contract.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 合同状态日志（fromStatus 为空表示创建）。
 */
@Schema(description = "合同状态日志")
public record ContractStatusLogVO(
        Long id,
        String fromStatus,
        String fromStatusLabel,
        String toStatus,
        String toStatusLabel,
        Long operatorId,
        String reason,
        Instant createdAt
) {
}
