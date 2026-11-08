package com.example.realestate.modules.house.model.vo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 房源详情。
 * 数据权限：非 ADMIN 不返回 ownerName/ownerPhone；favorited 仅对已登录 CUSTOMER 有意义。
 */
public record HouseDetailVO(
        Long id,
        String title,
        String houseType,
        String status,
        String community,
        String region,
        String address,
        BigDecimal price,
        BigDecimal area,
        Integer rooms,
        Integer bathrooms,
        String floor,
        String description,
        String coverUrl,
        List<HouseMediaVO> media,
        String ownerName,
        String ownerPhone,
        Integer version,
        Instant createdAt,
        Instant updatedAt,
        boolean favorited
) {
}
