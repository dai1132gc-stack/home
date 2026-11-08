package com.example.realestate.modules.content.controller;

import com.example.realestate.common.api.ApiResponse;
import com.example.realestate.common.api.PageResponse;
import com.example.realestate.modules.content.model.dto.GuideCreateRequest;
import com.example.realestate.modules.content.model.dto.GuideUpdateRequest;
import com.example.realestate.modules.content.model.query.ContentQuery;
import com.example.realestate.modules.content.model.vo.GuideDetailVO;
import com.example.realestate.modules.content.model.vo.GuideListItemVO;
import com.example.realestate.modules.content.service.ContentService;
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
 * 教程 / 网签指引 / 公告：GET 公开只读已发布（ADMIN 可见全部），写接口仅 ADMIN。
 */
@RestController
@RequestMapping("/api/v1/guides")
@Tag(name = "内容", description = "租房教程、网签指引与公告")
public class GuideController {

    private final ContentService contentService;

    public GuideController(ContentService contentService) {
        this.contentService = contentService;
    }

    @GetMapping
    @Operation(summary = "内容分页列表（公开仅 PUBLISHED；ADMIN 可按状态筛选）")
    public ApiResponse<PageResponse<GuideListItemVO>> list(ContentQuery query) {
        return ApiResponse.ok(contentService.listGuides(query));
    }

    @GetMapping("/{id}")
    @Operation(summary = "内容详情（下架/草稿对公开访问 404）")
    public ApiResponse<GuideDetailVO> detail(@PathVariable Long id) {
        return ApiResponse.ok(contentService.getGuide(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "新建内容（ADMIN，初始 DRAFT）")
    public ApiResponse<GuideDetailVO> create(@Valid @RequestBody GuideCreateRequest request) {
        return ApiResponse.ok(contentService.createGuide(request));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "编辑内容（仅 DRAFT/OFFLINE，version 乐观锁）")
    public ApiResponse<GuideDetailVO> update(@PathVariable Long id,
                                             @Valid @RequestBody GuideUpdateRequest request) {
        return ApiResponse.ok(contentService.updateGuide(id, request));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "发布内容（DRAFT/OFFLINE → PUBLISHED）")
    public ApiResponse<GuideDetailVO> publish(@PathVariable Long id) {
        return ApiResponse.ok(contentService.publishGuide(id));
    }

    @PostMapping("/{id}/offline")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "下架内容（PUBLISHED → OFFLINE，公开访问随即 404）")
    public ApiResponse<GuideDetailVO> offline(@PathVariable Long id) {
        return ApiResponse.ok(contentService.offlineGuide(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "逻辑删除内容（已发布需先下架）")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        contentService.deleteGuide(id);
        return ApiResponse.ok();
    }
}
