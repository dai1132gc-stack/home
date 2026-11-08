package com.example.realestate.modules.house.model.vo;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/** 我的收藏列表项（仅包含 PUBLISHED 房源，已下架/删除的房源不出现在收藏列表） */
@Data
@NoArgsConstructor
public class FavoriteVO {

    private Long houseId;

    private String title;

    private String houseType;

    private String region;

    private BigDecimal price;

    private BigDecimal area;

    private Integer rooms;

    private String coverUrl;

    private Instant favoritedAt;
}
