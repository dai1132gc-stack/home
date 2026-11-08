package com.example.realestate.modules.house.service;

import com.example.realestate.common.api.PageQuery;
import com.example.realestate.common.api.PageResponse;
import com.example.realestate.modules.house.model.vo.FavoriteVO;

public interface HouseFavoriteService {

    /** 收藏：房源必须存在且 PUBLISHED；重复收藏幂等成功 */
    void add(Long houseId);

    /** 取消收藏：仅能操作本人收藏；不存在时幂等成功 */
    void remove(Long houseId);

    PageResponse<FavoriteVO> myFavorites(PageQuery query);
}
