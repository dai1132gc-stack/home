package com.example.realestate.modules.owner.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.realestate.modules.owner.model.entity.CustomerFollow;
import com.example.realestate.modules.owner.model.vo.CustomerFollowRow;
import com.example.realestate.modules.owner.model.vo.CustomerFollowVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 客源跟进 Mapper（W4 启用）：表与实体由 W2 提供，本阶段只新增查询与写入。
 * 列表按 (owner_id, created_at) 索引分页，不在应用层过滤。
 */
@Mapper
public interface CustomerFollowMapper extends BaseMapper<CustomerFollow> {

    List<CustomerFollowRow> selectPageList(@Param("ownerId") Long ownerId,
                                           @Param("offset") long offset,
                                           @Param("size") long size);

    long countByOwner(@Param("ownerId") Long ownerId);

    CustomerFollowRow selectVoById(@Param("id") Long id);
}
