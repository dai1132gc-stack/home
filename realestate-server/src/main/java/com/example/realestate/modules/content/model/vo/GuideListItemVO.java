package com.example.realestate.modules.content.model.vo;

import java.time.Instant;

/** 内容列表项（不含正文，减少列表载荷） */
public record GuideListItemVO(
        Long id,
        String title,
        String category,
        String categoryLabel,
        String summary,
        String status,
        String statusLabel,
        Integer sort,
        Integer version,
        Instant createdAt,
        Instant updatedAt
) {
}
