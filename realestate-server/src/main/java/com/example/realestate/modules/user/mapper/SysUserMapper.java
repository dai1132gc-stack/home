package com.example.realestate.modules.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.realestate.modules.user.model.entity.SysUser;
import com.example.realestate.modules.user.model.query.CustomerQuery;
import com.example.realestate.modules.user.model.vo.CustomerVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /** 合同起草选择承租方：只查 CUSTOMER 账号，关键字前缀匹配，分页下推 SQL */
    List<CustomerVO> selectCustomers(@Param("q") CustomerQuery query,
                                     @Param("offset") long offset,
                                     @Param("size") long size);

    long countCustomers(@Param("q") CustomerQuery query);
}
