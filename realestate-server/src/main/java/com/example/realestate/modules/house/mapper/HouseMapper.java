package com.example.realestate.modules.house.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.realestate.modules.house.model.entity.House;
import com.example.realestate.modules.house.model.query.HouseQuery;
import com.example.realestate.modules.house.model.vo.HouseListItemVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 房源 Mapper。
 * 列表查询为手写 XML：筛选、数据权限 status、排序白名单、LIMIT 全部下推 SQL。
 */
@Mapper
public interface HouseMapper extends BaseMapper<House> {

    List<HouseListItemVO> selectPageList(@Param("q") HouseQuery query,
                                         @Param("forcedStatus") String forcedStatus,
                                         @Param("offset") long offset,
                                         @Param("size") long size);

    long countByQuery(@Param("q") HouseQuery query,
                      @Param("forcedStatus") String forcedStatus);
}
