package com.example.realestate.modules.owner.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.realestate.modules.owner.model.entity.OwnerProfile;
import com.example.realestate.modules.owner.model.query.OwnerQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OwnerProfileMapper extends BaseMapper<OwnerProfile> {

    /** 分页列表：条件与 LIMIT 全部下推 SQL */
    List<OwnerProfile> selectPageList(@Param("q") OwnerQuery query,
                                      @Param("offset") long offset,
                                      @Param("size") long size);

    long countByQuery(@Param("q") OwnerQuery query);
}
