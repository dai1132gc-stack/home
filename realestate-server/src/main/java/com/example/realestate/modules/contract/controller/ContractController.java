package com.example.realestate.modules.contract.controller;

import com.example.realestate.common.api.ApiResponse;
import com.example.realestate.common.api.PageResponse;
import com.example.realestate.modules.contract.model.dto.ContractCreateRequest;
import com.example.realestate.modules.contract.model.dto.ContractTerminateRequest;
import com.example.realestate.modules.contract.model.dto.ContractUpdateRequest;
import com.example.realestate.modules.contract.model.query.ContractQuery;
import com.example.realestate.modules.contract.model.vo.ContractDetailVO;
import com.example.realestate.modules.contract.model.vo.ContractListItemVO;
import com.example.realestate.modules.contract.service.ContractService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 合同接口：起草/提交/终止仅 ADMIN，客户确认仅 CUSTOMER 本人；
 * PDF 走鉴权流式下载，不返回公开 URL。
 */
@RestController
@RequestMapping("/api/v1/contracts")
@Tag(name = "合同", description = "合同起草、签署、终止与 PDF 下载")
public class ContractController {

    private final ContractService contractService;

    public ContractController(ContractService contractService) {
        this.contractService = contractService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    @Operation(summary = "合同分页列表（ADMIN 全部，CUSTOMER 本人）")
    public ApiResponse<PageResponse<ContractListItemVO>> list(ContractQuery query) {
        return ApiResponse.ok(contractService.list(query));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    @Operation(summary = "合同详情（本人或 ADMIN，他人 404）")
    public ApiResponse<ContractDetailVO> detail(@PathVariable Long id) {
        return ApiResponse.ok(contractService.detail(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "起草合同（ADMIN，初始 DRAFT，含双方与条款快照）")
    public ApiResponse<ContractDetailVO> create(@Valid @RequestBody ContractCreateRequest request) {
        return ApiResponse.ok(contractService.create(request));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "编辑合同（仅 DRAFT，version 乐观锁）")
    public ApiResponse<ContractDetailVO> update(@PathVariable Long id,
                                                @Valid @RequestBody ContractUpdateRequest request) {
        return ApiResponse.ok(contractService.update(id, request));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "提交待签（DRAFT → PENDING_SIGN，需 PDF 生成成功）")
    public ApiResponse<ContractDetailVO> submit(@PathVariable Long id) {
        return ApiResponse.ok(contractService.submit(id));
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "客户确认签署（PENDING_SIGN → ACTIVE，房源置 CONTRACTED）")
    public ApiResponse<ContractDetailVO> confirm(@PathVariable Long id) {
        return ApiResponse.ok(contractService.confirm(id));
    }

    @PostMapping("/{id}/terminate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "终止/作废合同（ADMIN，需填写原因）")
    public ApiResponse<ContractDetailVO> terminate(@PathVariable Long id,
                                                   @Valid @RequestBody ContractTerminateRequest request) {
        return ApiResponse.ok(contractService.terminate(id, request));
    }

    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    @Operation(summary = "下载合同 PDF（本人或 ADMIN，attachment 流式返回）")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        ContractService.ContractPdf pdf = contractService.downloadPdf(id);
        String encoded = URLEncoder.encode(pdf.filename(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + pdf.filename().replace("\"", "") + "\"; filename*=UTF-8''" + encoded)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(pdf.content());
    }
}
