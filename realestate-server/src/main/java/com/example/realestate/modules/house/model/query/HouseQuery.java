package com.example.realestate.modules.house.model.query;

import com.example.realestate.common.api.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * 房源列表查询：所有条件（含数据权限 status）下推 SQL。
 * sort 仅接受白名单值：created_desc / price_asc / price_desc / area_asc / area_desc。
 */
@Schema(description = "房源列表查询")
public class HouseQuery extends PageQuery {

    private String status;

    private String houseType;

    private String region;

    private BigDecimal minPrice;

    private BigDecimal maxPrice;

    private BigDecimal minArea;

    private BigDecimal maxArea;

    /** 标题或小区前缀匹配（命中索引，避免全表 LIKE %x%） */
    private String keyword;

    /** 居室数（W6 智能选房增量条件，参数化下推 SQL） */
    private Integer rooms;

    private String sort;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getHouseType() {
        return houseType;
    }

    public void setHouseType(String houseType) {
        this.houseType = houseType;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public BigDecimal getMinPrice() {
        return minPrice;
    }

    public void setMinPrice(BigDecimal minPrice) {
        this.minPrice = minPrice;
    }

    public BigDecimal getMaxPrice() {
        return maxPrice;
    }

    public void setMaxPrice(BigDecimal maxPrice) {
        this.maxPrice = maxPrice;
    }

    public BigDecimal getMinArea() {
        return minArea;
    }

    public void setMinArea(BigDecimal minArea) {
        this.minArea = minArea;
    }

    public BigDecimal getMaxArea() {
        return maxArea;
    }

    public void setMaxArea(BigDecimal maxArea) {
        this.maxArea = maxArea;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public Integer getRooms() {
        return rooms;
    }

    public void setRooms(Integer rooms) {
        this.rooms = rooms;
    }

    public String getSort() {
        return sort;
    }

    public void setSort(String sort) {
        this.sort = sort;
    }
}
