package com.example.realestate.modules.owner.controller;

import com.example.realestate.common.api.ApiResponse;
import com.example.realestate.common.api.PageQuery;
import com.example.realestate.common.api.PageResponse;
import com.example.realestate.modules.owner.model.dto.CustomerFollowCreateRequest;
import com.example.realestate.modules.owner.model.vo.CustomerFollowVO;
import com.example.realestate.modules.owner.service.CustomerFollowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客源跟进：仅 ADMIN。表与实体沿用 W2，本阶段只启用接口。
 */
@RestController
@RequestMapping("/api/v1/owners/{ownerId}/follows")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "客源跟进", description = "客源跟进记录（ADMIN）")
public class CustomerFollowController {

    private final CustomerFollowService followService;

    public CustomerFollowController(CustomerFollowService followService) {
        this.followService = followService;
    }

    @GetMapping
    @Operation(summary = "跟进记录分页列表")
    public ApiResponse<PageResponse<CustomerFollowVO>> list(@PathVariable Long ownerId, PageQuery query) {
        return ApiResponse.ok(followService.list(ownerId, query));
    }

    @PostMapping
    @Operation(summary = "新增跟进记录（含下次跟进时间）")
    public ApiResponse<CustomerFollowVO> create(@PathVariable Long ownerId,
                                                @Valid @RequestBody CustomerFollowCreateRequest request) {
        return ApiResponse.ok(followService.create(ownerId, request));
    }
}
