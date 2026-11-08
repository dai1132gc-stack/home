package com.example.realestate.modules.house.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 客户收藏（唯一键 user_id + house_id，服务端幂等处理重复收藏） */
@Data
@NoArgsConstructor
@TableName("house_favorite")
public class HouseFavorite {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long houseId;

    private Long userId;

    private Instant createdAt;
}
