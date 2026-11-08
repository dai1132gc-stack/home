package com.example.realestate.modules.appointment.controller;

import com.example.realestate.common.api.ApiResponse;
import com.example.realestate.common.api.PageResponse;
import com.example.realestate.modules.appointment.model.dto.AppointmentCreateRequest;
import com.example.realestate.modules.appointment.model.query.AppointmentQuery;
import com.example.realestate.modules.appointment.model.vo.AppointmentVO;
import com.example.realestate.modules.appointment.service.AppointmentService;
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
 * 预约带看接口：提交仅 CUSTOMER；确认/完成/爽约仅 ADMIN；取消为客户本人或 ADMIN。
 * 数据权限由 Service + SQL 双重校验，CUSTOMER 访问他人预约返回 404。
 */
@RestController
@RequestMapping("/api/v1/appointments")
@Tag(name = "预约带看", description = "客户预约、管理员确认与带看结果")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    @Operation(summary = "预约分页列表（ADMIN 全部，CUSTOMER 本人）")
    public ApiResponse<PageResponse<AppointmentVO>> list(AppointmentQuery query) {
        return ApiResponse.ok(appointmentService.list(query));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    @Operation(summary = "预约详情（本人或 ADMIN，他人 404）")
    public ApiResponse<AppointmentVO> detail(@PathVariable Long id) {
        return ApiResponse.ok(appointmentService.detail(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "提交预约（CUSTOMER，房源需 PUBLISHED）")
    public ApiResponse<AppointmentVO> create(@Valid @RequestBody AppointmentCreateRequest request) {
        return ApiResponse.ok(appointmentService.create(request));
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "确认预约（PENDING → CONFIRMED，房源置 RESERVED）")
    public ApiResponse<AppointmentVO> confirm(@PathVariable Long id) {
        return ApiResponse.ok(appointmentService.confirm(id));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    @Operation(summary = "取消预约（本人或 ADMIN，PENDING/CONFIRMED → CANCELLED）")
    public ApiResponse<AppointmentVO> cancel(@PathVariable Long id) {
        return ApiResponse.ok(appointmentService.cancel(id));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "标记带看完成（CONFIRMED → COMPLETED）")
    public ApiResponse<AppointmentVO> complete(@PathVariable Long id) {
        return ApiResponse.ok(appointmentService.complete(id));
    }

    @PostMapping("/{id}/no-show")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "标记爽约（CONFIRMED → NO_SHOW）")
    public ApiResponse<AppointmentVO> noShow(@PathVariable Long id) {
        return ApiResponse.ok(appointmentService.noShow(id));
    }
}
