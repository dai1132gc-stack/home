package com.example.realestate.modules.auth.wechat.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 微信小程序账号绑定（V5）。
 * openid 唯一；不保存 session_key。
 */
@Data
@NoArgsConstructor
@TableName("user_wechat_binding")
public class UserWechatBinding {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String openid;

    private String unionid;

    private Instant createdAt;

    private Instant updatedAt;
}
