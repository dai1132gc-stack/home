package com.example.realestate.modules.house.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.realestate.modules.house.model.entity.HouseMedia;
import com.example.realestate.modules.house.model.vo.HouseCover;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface HouseMediaMapper extends BaseMapper<HouseMedia> {

    /** 批量取每个房源排序最靠前的图片，用于列表封面（一次查询替代 N+1） */
    List<HouseCover> selectFirstImages(@Param("houseIds") List<Long> houseIds);
}
