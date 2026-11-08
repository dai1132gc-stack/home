package com.example.realestate.modules.repair.model.vo;

import java.time.Instant;

/** 报修图片：仅返回受鉴权的 API 路径，不返回私有存储 key 或公开 URL。 */
public record RepairMediaVO(
        Long id,
        String previewUrl,
        String mediaType,
        Integer sort,
        Instant createdAt
) {
}
