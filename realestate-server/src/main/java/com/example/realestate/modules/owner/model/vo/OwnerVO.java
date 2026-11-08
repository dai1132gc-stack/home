package com.example.realestate.modules.owner.model.vo;

import java.time.Instant;

/**
 * 房东 / 客源 VO。
 * phone 规则：ADMIN 明文；非 ADMIN 为 138******78（当前接口仅 ADMIN 可达，脱敏逻辑仍保留并被单测覆盖）。
 */
public record OwnerVO(
        Long id,
        String name,
        String phone,
        String type,
        String typeLabel,
        String remark,
        Integer version,
        Instant createdAt,
        Instant updatedAt
) {
}
