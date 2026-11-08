package com.example.realestate.modules.content.model.vo;

import java.time.Instant;

/** 内容详情：content 为纯文本，前端按换行渲染，禁止 v-html。 */
public record GuideDetailVO(
        Long id,
        String title,
        String category,
        String categoryLabel,
        String summary,
        String content,
        String status,
        String statusLabel,
        Integer sort,
        Integer version,
        Instant createdAt,
        Instant updatedAt
) {
}
