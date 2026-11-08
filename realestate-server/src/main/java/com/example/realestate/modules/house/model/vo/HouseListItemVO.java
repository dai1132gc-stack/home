package com.example.realestate.modules.house.model.vo;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 房源列表项（MyBatis 直接映射，需无参构造 + setter）。
 * coverUrl / ownerName 由 Service 批量补齐，避免 N+1 查询。
 */
@Data
@NoArgsConstructor
public class HouseListItemVO {

    private Long id;

    private String title;

    private String houseType;

    private String status;

    private String community;

    private String region;

    private BigDecimal price;

    private BigDecimal area;

    private Integer rooms;

    private Integer bathrooms;

    private String floor;

    private String coverUrl;

    private Long ownerId;

    private String ownerName;

    private Instant createdAt;
}
