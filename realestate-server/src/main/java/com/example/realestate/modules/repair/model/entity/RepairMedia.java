package com.example.realestate.modules.repair.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 报修图片：url 为私有存储相对 key，不对外暴露公开地址。 */
@Data
@NoArgsConstructor
@TableName("repair_media")
public class RepairMedia {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ticketId;

    private String url;

    private String mediaType;

    private Integer sort;

    private Instant createdAt;
}
