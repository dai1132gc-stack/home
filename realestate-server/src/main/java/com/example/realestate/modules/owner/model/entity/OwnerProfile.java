package com.example.realestate.modules.owner.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 房东 / 客源档案。phone 仅 ADMIN 可见，VO 层按角色脱敏。
 */
@Data
@NoArgsConstructor
@TableName("owner_profile")
public class OwnerProfile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String phone;

    private String type;

    private String remark;

    private Long createdBy;

    @Version
    private Integer version;

    @TableLogic
    private Integer deleted;

    private Instant createdAt;

    private Instant updatedAt;
}
