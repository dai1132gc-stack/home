package com.example.realestate.modules.user.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 系统用户（ADMIN/CUSTOMER）。
 */
@Data
@NoArgsConstructor
@TableName("sys_user")
public class SysUser {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_CUSTOMER = "CUSTOMER";
    public static final String STATUS_ACTIVE = "ACTIVE";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String passwordHash;

    private String role;

    private String nickname;

    private String phone;

    private String status;

    private Instant createdAt;

    private Instant updatedAt;
}
