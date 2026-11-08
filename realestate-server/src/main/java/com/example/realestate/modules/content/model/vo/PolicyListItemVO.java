package com.example.realestate.modules.content.model.vo;

import java.time.Instant;
import java.time.LocalDate;

/** 政策列表项 */
public record PolicyListItemVO(
        Long id,
        String title,
        String docNo,
        String docType,
        String docTypeLabel,
        String source,
        LocalDate publishDate,
        LocalDate effectiveDate,
        String status,
        String statusLabel,
        boolean hasAttachment,
        Integer version,
        Instant createdAt,
        Instant updatedAt
) {
}
