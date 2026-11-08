package com.example.realestate.modules.system.controller;

import com.example.realestate.common.api.ApiResponse;
import com.example.realestate.security.model.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * W1 受保护冒烟接口：验证 ADMIN/CUSTOMER 角色隔离。
 * W2 起由业务模块替代，不承载业务功能。
 */
@Tag(name = "W1 冒烟验证")
@RestController
@RequestMapping("/api/v1/smoke")
public class SmokeController {

    @Operation(summary = "任意已登录用户")
    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me(@AuthenticationPrincipal LoginUser loginUser) {
        return ApiResponse.ok(Map.of(
                "userId", loginUser.userId(),
                "username", loginUser.username(),
                "role", loginUser.role(),
                "client", loginUser.client().name()));
    }

    @Operation(summary = "仅 ADMIN")
    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> adminOnly(@AuthenticationPrincipal LoginUser loginUser) {
        return ApiResponse.ok(Map.of("message", "ADMIN 权限验证通过", "username", loginUser.username()));
    }

    @Operation(summary = "仅 CUSTOMER")
    @GetMapping("/customer")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<Map<String, Object>> customerOnly(@AuthenticationPrincipal LoginUser loginUser) {
        return ApiResponse.ok(Map.of("message", "CUSTOMER 权限验证通过", "username", loginUser.username()));
    }
}
