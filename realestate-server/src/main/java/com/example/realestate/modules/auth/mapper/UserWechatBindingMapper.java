package com.example.realestate.modules.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.realestate.modules.auth.wechat.model.entity.UserWechatBinding;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserWechatBindingMapper extends BaseMapper<UserWechatBinding> {
}
