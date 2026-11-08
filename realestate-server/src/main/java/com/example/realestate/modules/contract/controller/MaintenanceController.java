package com.example.realestate.modules.contract.controller;

import com.example.realestate.common.api.ApiResponse;
import com.example.realestate.common.enums.ErrorCode;
import com.example.realestate.common.exception.BusinessException;
import com.example.realestate.modules.contract.service.ContractExpiryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运维维护接口（ADMIN）：手动触发合同到期扫描，便于验收与定时任务补偿。
 */
@RestController
@RequestMapping("/api/v1/admin/maintenance")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "运维维护", description = "合同到期扫描（ADMIN）")
public class MaintenanceController {

    private final ContractExpiryService contractExpiryService;

    public MaintenanceController(ContractExpiryService contractExpiryService) {
        this.contractExpiryService = contractExpiryService;
    }

    @PostMapping("/expire-contracts")
    @Operation(summary = "手动执行合同到期扫描（幂等，Redis 锁防重复执行）")
    public ApiResponse<ContractExpiryService.ExpiryResult> expireContracts() {
        ContractExpiryService.ExpiryResult result = contractExpiryService.expireOverdueContracts();
        if (result.skippedByLock()) {
            throw new BusinessException(ErrorCode.CONFLICT, "到期任务正在执行，请稍后重试");
        }
        return ApiResponse.ok(result);
    }
}
