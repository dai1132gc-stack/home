package com.example.realestate.modules.content.controller;

import com.example.realestate.common.api.ApiResponse;
import com.example.realestate.common.api.PageResponse;
import com.example.realestate.modules.content.model.dto.PolicyCreateRequest;
import com.example.realestate.modules.content.model.dto.PolicyUpdateRequest;
import com.example.realestate.modules.content.model.query.ContentQuery;
import com.example.realestate.modules.content.model.vo.PolicyDetailVO;
import com.example.realestate.modules.content.model.vo.PolicyListItemVO;
import com.example.realestate.modules.content.service.ContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 政策文档：列表/详情公开只读已发布；附件私有存储，下载需登录鉴权。
 */
@RestController
@RequestMapping("/api/v1/policies")
@Tag(name = "政策", description = "政策文档维护与私有附件")
public class PolicyController {

    private final ContentService contentService;

    public PolicyController(ContentService contentService) {
        this.contentService = contentService;
    }

    @GetMapping
    @Operation(summary = "政策分页列表（公开仅 PUBLISHED；ADMIN 可按状态筛选）")
    public ApiResponse<PageResponse<PolicyListItemVO>> list(ContentQuery query) {
        return ApiResponse.ok(contentService.listPolicies(query));
    }

    @GetMapping("/{id}")
    @Operation(summary = "政策详情（下架/草稿对公开访问 404）")
    public ApiResponse<PolicyDetailVO> detail(@PathVariable Long id) {
        return ApiResponse.ok(contentService.getPolicy(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "新建政策（ADMIN，初始 DRAFT）")
    public ApiResponse<PolicyDetailVO> create(@Valid @RequestBody PolicyCreateRequest request) {
        return ApiResponse.ok(contentService.createPolicy(request));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "编辑政策（仅 DRAFT/OFFLINE）")
    public ApiResponse<PolicyDetailVO> update(@PathVariable Long id,
                                              @Valid @RequestBody PolicyUpdateRequest request) {
        return ApiResponse.ok(contentService.updatePolicy(id, request));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "发布政策（DRAFT/OFFLINE → PUBLISHED）")
    public ApiResponse<PolicyDetailVO> publish(@PathVariable Long id) {
        return ApiResponse.ok(contentService.publishPolicy(id));
    }

    @PostMapping("/{id}/offline")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "下架政策（PUBLISHED → OFFLINE）")
    public ApiResponse<PolicyDetailVO> offline(@PathVariable Long id) {
        return ApiResponse.ok(contentService.offlinePolicy(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "逻辑删除政策（已发布需先下架）")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        contentService.deletePolicy(id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/attachment")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "上传政策附件（PDF，私有存储，仅 DRAFT/OFFLINE）")
    public ApiResponse<PolicyDetailVO> uploadAttachment(@PathVariable Long id,
                                                        @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(contentService.uploadPolicyAttachment(id, file));
    }

    @GetMapping("/{id}/attachment")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    @Operation(summary = "下载政策附件（登录鉴权，attachment 流式返回）")
    public ResponseEntity<byte[]> downloadAttachment(@PathVariable Long id) {
        ContentService.PolicyAttachment attachment = contentService.downloadPolicyAttachment(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + attachment.filename() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(attachment.content());
    }
}
