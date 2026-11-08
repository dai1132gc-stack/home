package com.example.realestate.modules.user.controller;

import com.example.realestate.common.api.ApiResponse;
import com.example.realestate.common.api.PageResponse;
import com.example.realestate.modules.user.model.query.CustomerQuery;
import com.example.realestate.modules.user.model.vo.CustomerVO;
import com.example.realestate.modules.user.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 客户账号列表（ADMIN）：合同起草时选择承租方 */
@RestController
@RequestMapping("/api/v1/customers")
@Tag(name = "客户账号", description = "合同起草选择承租方")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "客户账号分页列表（ADMIN）")
    public ApiResponse<PageResponse<CustomerVO>> list(CustomerQuery query) {
        return ApiResponse.ok(customerService.list(query));
    }
}
