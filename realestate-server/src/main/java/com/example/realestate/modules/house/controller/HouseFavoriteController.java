package com.example.realestate.modules.house.controller;

import com.example.realestate.common.api.ApiResponse;
import com.example.realestate.common.api.PageQuery;
import com.example.realestate.common.api.PageResponse;
import com.example.realestate.modules.house.model.vo.FavoriteVO;
import com.example.realestate.modules.house.service.HouseFavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 收藏接口：仅 CUSTOMER。
 * user_id 由服务端从 Access Token 解析，接口不接受客户端传入的用户标识。
 */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('CUSTOMER')")
@Tag(name = "收藏", description = "客户收藏房源与我的收藏")
public class HouseFavoriteController {

    private final HouseFavoriteService favoriteService;

    public HouseFavoriteController(HouseFavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @PostMapping("/houses/{houseId}/favorite")
    @Operation(summary = "收藏房源（CUSTOMER，幂等）")
    public ApiResponse<Void> add(@PathVariable Long houseId) {
        favoriteService.add(houseId);
        return ApiResponse.ok();
    }

    @DeleteMapping("/houses/{houseId}/favorite")
    @Operation(summary = "取消收藏（CUSTOMER，仅本人）")
    public ApiResponse<Void> remove(@PathVariable Long houseId) {
        favoriteService.remove(houseId);
        return ApiResponse.ok();
    }

    @GetMapping("/me/favorites")
    @Operation(summary = "我的收藏列表（CUSTOMER，仅 PUBLISHED 房源）")
    public ApiResponse<PageResponse<FavoriteVO>> myFavorites(PageQuery query) {
        return ApiResponse.ok(favoriteService.myFavorites(query));
    }
}
