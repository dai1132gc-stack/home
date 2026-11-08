package com.example.realestate.modules.house.controller;

import com.example.realestate.common.api.ApiResponse;
import com.example.realestate.common.api.PageResponse;
import com.example.realestate.modules.house.model.dto.HouseCreateRequest;
import com.example.realestate.modules.house.model.dto.HouseMediaRequest;
import com.example.realestate.modules.house.model.dto.HouseUpdateRequest;
import com.example.realestate.modules.house.model.query.HouseQuery;
import com.example.realestate.modules.house.model.vo.HouseDetailVO;
import com.example.realestate.modules.house.model.vo.HouseListItemVO;
import com.example.realestate.modules.house.model.vo.HouseMediaVO;
import com.example.realestate.modules.house.service.HouseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 房源接口：
 * GET /houses、GET /houses/{id} 对游客开放（Service 强制 PUBLISHED）；
 * 其余写操作仅 ADMIN。
 */
@RestController
@RequestMapping("/api/v1/houses")
@Tag(name = "房源", description = "房源发布、检索、上下架与媒体")
public class HouseController {

    private final HouseService houseService;

    public HouseController(HouseService houseService) {
        this.houseService = houseService;
    }

    @GetMapping
    @Operation(summary = "房源分页列表（游客/客户仅 PUBLISHED）")
    public ApiResponse<PageResponse<HouseListItemVO>> list(HouseQuery query) {
        return ApiResponse.ok(houseService.list(query));
    }

    @GetMapping("/{id}")
    @Operation(summary = "房源详情（非 PUBLISHED 仅 ADMIN）")
    public ApiResponse<HouseDetailVO> detail(@PathVariable Long id) {
        return ApiResponse.ok(houseService.detail(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "新建房源（ADMIN，初始 PENDING）")
    public ApiResponse<HouseDetailVO> create(@Valid @RequestBody HouseCreateRequest request) {
        return ApiResponse.ok(houseService.create(request));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "编辑房源（ADMIN）")
    public ApiResponse<HouseDetailVO> update(@PathVariable Long id,
                                             @Valid @RequestBody HouseUpdateRequest request) {
        return ApiResponse.ok(houseService.update(id, request));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "发布房源（PENDING/OFFLINE → PUBLISHED）")
    public ApiResponse<HouseDetailVO> publish(@PathVariable Long id) {
        return ApiResponse.ok(houseService.publish(id));
    }

    @PostMapping("/{id}/offline")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "下架房源（PUBLISHED → OFFLINE）")
    public ApiResponse<HouseDetailVO> offline(@PathVariable Long id) {
        return ApiResponse.ok(houseService.offline(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "逻辑删除房源（ADMIN）")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        houseService.delete(id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/media")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "关联媒体到房源（ADMIN）")
    public ApiResponse<HouseMediaVO> addMedia(@PathVariable Long id,
                                              @Valid @RequestBody HouseMediaRequest request) {
        return ApiResponse.ok(houseService.addMedia(id, request));
    }

    @DeleteMapping("/{id}/media/{mediaId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "移除房源媒体（ADMIN）")
    public ApiResponse<Void> removeMedia(@PathVariable Long id, @PathVariable Long mediaId) {
        houseService.removeMedia(id, mediaId);
        return ApiResponse.ok();
    }
}
