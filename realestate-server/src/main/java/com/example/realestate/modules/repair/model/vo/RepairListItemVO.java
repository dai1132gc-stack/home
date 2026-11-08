package com.example.realestate.modules.repair.model.vo;

import java.time.Instant;

/** 报修列表项。contactPhone 对非 ADMIN 脱敏。 */
public record RepairListItemVO(
        Long id,
        String ticketNo,
        Long houseId,
        String houseTitle,
        Long contractId,
        String contractNo,
        Long customerId,
        String customerName,
        String category,
        String categoryLabel,
        String urgency,
        String urgencyLabel,
        String title,
        String status,
        String statusLabel,
        String contactPhone,
        Instant preferredVisitTime,
        Instant createdAt,
        Instant updatedAt
) {
}
