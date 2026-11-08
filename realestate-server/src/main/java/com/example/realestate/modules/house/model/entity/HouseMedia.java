package com.example.realestate.modules.house.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 房源媒体（图片/视频）。W2 仅启用 IMAGE。 */
@Data
@NoArgsConstructor
@TableName("house_media")
public class HouseMedia {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long houseId;

    private String mediaType;

    private String url;

    private Integer sort;

    private Instant createdAt;
}
