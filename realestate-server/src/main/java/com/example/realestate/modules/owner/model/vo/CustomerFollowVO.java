package com.example.realestate.modules.owner.model.vo;

import java.time.Instant;

/** 客源跟进记录（仅 ADMIN 可见）。 */
public record CustomerFollowVO(
        Long id,
        Long ownerId,
        String content,
        String followType,
        String followTypeLabel,
        Instant nextFollowAt,
        Long createdBy,
        String createdByName,
        Instant createdAt
) {
}
