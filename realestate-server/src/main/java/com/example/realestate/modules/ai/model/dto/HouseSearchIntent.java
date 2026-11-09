package com.example.realestate.modules.ai.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * 模型输出的选房意图（严格 JSON，未知字段忽略）。
 * 所有字段在使用前必须经白名单/范围校验，非法字段丢弃。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HouseSearchIntent(
        String houseType,
        String region,
        String communityKeyword,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Integer rooms,
        String sort
) {
}
