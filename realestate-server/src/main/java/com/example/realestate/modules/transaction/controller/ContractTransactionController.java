package com.example.realestate.modules.transaction.controller;

import com.example.realestate.common.api.ApiResponse;
import com.example.realestate.modules.transaction.model.dto.TransactionDoneRequest;
import com.example.realestate.modules.transaction.model.vo.TransactionLogVO;
import com.example.realestate.modules.transaction.model.vo.TransactionVO;
import com.example.realestate.modules.transaction.service.TransactionService;
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

import java.util.List;

/**
 * 合同交易节点：查看为本人或 ADMIN，完成为 ADMIN。
 */
@RestController
@RequestMapping("/api/v1/contracts/{contractId}/transactions")
@Tag(name = "交易节点", description = "定金、签约、交房节点与状态日志")
public class ContractTransactionController {

    private final TransactionService transactionService;

    public ContractTransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    @Operation(summary = "交易节点列表（本人或 ADMIN）")
    public ApiResponse<List<TransactionVO>> list(@PathVariable Long contractId) {
        return ApiResponse.ok(transactionService.listByContract(contractId));
    }

    @GetMapping("/logs")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    @Operation(summary = "交易节点状态日志（本人或 ADMIN）")
    public ApiResponse<List<TransactionLogVO>> logs(@PathVariable Long contractId) {
        return ApiResponse.ok(transactionService.logsByContract(contractId));
    }

    @PostMapping("/{node}/done")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "完成交易节点（ADMIN，PENDING → DONE）")
    public ApiResponse<TransactionVO> done(@PathVariable Long contractId,
                                           @PathVariable String node,
                                           @Valid @RequestBody(required = false) TransactionDoneRequest request) {
        return ApiResponse.ok(transactionService.complete(contractId, node, request));
    }
}
