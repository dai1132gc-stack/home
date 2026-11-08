package com.example.realestate.modules.house.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 房源。status 迁移由 HouseServiceImpl 的状态机校验，禁止直接 setStatus 后 updateById。
 */
@Data
@NoArgsConstructor
@TableName("house")
public class House {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String houseType;

    private String status;

    private String community;

    private String region;

    private String address;

    private BigDecimal price;

    private BigDecimal area;

    private Integer rooms;

    private Integer bathrooms;

    private String floor;

    private String description;

    private Long ownerId;

    @Version
    private Integer version;

    @TableLogic
    private Integer deleted;

    private Instant createdAt;

    private Instant updatedAt;
}
