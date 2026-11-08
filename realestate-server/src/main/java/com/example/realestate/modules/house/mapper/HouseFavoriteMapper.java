package com.example.realestate.modules.house.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.realestate.modules.house.model.entity.HouseFavorite;
import com.example.realestate.modules.house.model.vo.FavoriteVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface HouseFavoriteMapper extends BaseMapper<HouseFavorite> {

    /** 我的收藏（仅 PUBLISHED 房源），分页与过滤在 SQL 内完成 */
    List<FavoriteVO> selectFavoritePage(@Param("userId") Long userId,
                                        @Param("offset") long offset,
                                        @Param("size") long size);

    long countFavorites(@Param("userId") Long userId);
}
