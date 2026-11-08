package com.example.realestate.modules.user.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 客户账号（合同起草选择承租方使用）。手机号对非 ADMIN 不返回，接口本身仅 ADMIN 可达。
 */
@Schema(description = "客户账号")
public record CustomerVO(
        Long id,
        String username,
        String nickname,
        String phone,
        String status,
        Instant createdAt
) {
}
