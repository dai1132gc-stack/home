package com.example.realestate.modules.contract.controller;

import com.example.realestate.common.api.ApiResponse;
import com.example.realestate.common.api.PageResponse;
import com.example.realestate.modules.contract.model.query.ContractQuery;
import com.example.realestate.modules.contract.model.vo.ContractListItemVO;
import com.example.realestate.modules.contract.service.ContractService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客户「我的合同」：customerId 由 Token 决定，不接受客户端指定。
 */
@RestController
@RequestMapping("/api/v1/me/contracts")
@Tag(name = "我的合同", description = "客户查看本人合同")
public class MyContractController {

    private final ContractService contractService;

    public MyContractController(ContractService contractService) {
        this.contractService = contractService;
    }

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "我的合同列表（CUSTOMER）")
    public ApiResponse<PageResponse<ContractListItemVO>> list(ContractQuery query) {
        return ApiResponse.ok(contractService.listMine(query));
    }
}
