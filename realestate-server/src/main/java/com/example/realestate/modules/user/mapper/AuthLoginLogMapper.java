package com.example.realestate.modules.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.realestate.modules.user.model.entity.AuthLoginLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuthLoginLogMapper extends BaseMapper<AuthLoginLog> {
}
