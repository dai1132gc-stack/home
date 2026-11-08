package com.example.realestate.modules.repair.model.vo;

import java.time.Instant;

/** 报修状态时间线。 */
public record RepairStatusLogVO(
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
