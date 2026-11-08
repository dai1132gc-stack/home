package com.example.realestate.modules.repair.model.vo;

import java.time.Instant;
import java.util.List;

/** 报修详情：含问题描述、私有图片（鉴权路径）与状态时间线。 */
public record RepairDetailVO(
        Long id,
        String ticketNo,
        Long houseId,
        String houseTitle,
        String houseAddress,
        Long contractId,
        String contractNo,
        Long customerId,
        String customerName,
        String category,
        String categoryLabel,
        String urgency,
        String urgencyLabel,
        String title,
        String description,
        String status,
        String statusLabel,
        String contactPhone,
        Instant preferredVisitTime,
        Integer version,
        Instant createdAt,
        Instant updatedAt,
        List<RepairMediaVO> media,
        List<RepairStatusLogVO> statusLogs
) {
}
