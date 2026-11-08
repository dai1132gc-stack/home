package com.example.realestate.modules.owner.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 客源跟进记录。W2 仅建表与实体，接口在 W3 交易/跟进阶段启用。
 */
@Data
@NoArgsConstructor
@TableName("customer_follow")
public class CustomerFollow {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ownerId;

    private String content;

    private String followType;

    private Instant nextFollowAt;

    private Long createdBy;

    private Instant createdAt;
}
