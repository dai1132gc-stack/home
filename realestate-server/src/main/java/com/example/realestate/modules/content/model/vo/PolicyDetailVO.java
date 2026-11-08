package com.example.realestate.modules.content.model.vo;

import java.time.Instant;
import java.time.LocalDate;

/** 政策详情：content 纯文本；附件通过鉴权接口下载，不返回私有 key。 */
public record PolicyDetailVO(
        Long id,
        String title,
        String docNo,
        String docType,
        String docTypeLabel,
        String source,
        LocalDate publishDate,
        LocalDate effectiveDate,
        String content,
        String status,
        String statusLabel,
        boolean hasAttachment,
        Integer version,
        Instant createdAt,
        Instant updatedAt
) {
}
