package com.example.realestate.modules.owner.controller;

import com.example.realestate.common.api.ApiResponse;
import com.example.realestate.common.api.PageResponse;
import com.example.realestate.modules.owner.model.dto.OwnerCreateRequest;
import com.example.realestate.modules.owner.model.dto.OwnerUpdateRequest;
import com.example.realestate.modules.owner.model.query.OwnerQuery;
import com.example.realestate.modules.owner.model.vo.OwnerVO;
import com.example.realestate.modules.owner.service.OwnerService;
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
 * 房东 / 客源管理：全部接口仅 ADMIN。
 * Controller 只做参数绑定与权限声明，业务与数据权限在 Service。
 */
@RestController
@RequestMapping("/api/v1/owners")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "客源/房东", description = "房东与客源档案管理（ADMIN）")
public class OwnerController {

    private final OwnerService ownerService;

    public OwnerController(OwnerService ownerService) {
        this.ownerService = ownerService;
    }

    @GetMapping
    @Operation(summary = "客源分页列表")
    public ApiResponse<PageResponse<OwnerVO>> list(OwnerQuery query) {
        return ApiResponse.ok(ownerService.list(query));
    }

    @GetMapping("/{id}")
    @Operation(summary = "客源详情（ADMIN 可见手机号明文）")
    public ApiResponse<OwnerVO> detail(@PathVariable Long id) {
        return ApiResponse.ok(ownerService.get(id));
    }

    @PostMapping
    @Operation(summary = "新建房东/客源")
    public ApiResponse<OwnerVO> create(@Valid @RequestBody OwnerCreateRequest request) {
        return ApiResponse.ok(ownerService.create(request));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "编辑房东/客源")
    public ApiResponse<OwnerVO> update(@PathVariable Long id,
                                       @Valid @RequestBody OwnerUpdateRequest request) {
        return ApiResponse.ok(ownerService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "逻辑删除房东/客源")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        ownerService.delete(id);
        return ApiResponse.ok();
    }
}
