package com.example.realestate.modules.repair.controller;

import com.example.realestate.common.api.ApiResponse;
import com.example.realestate.common.api.PageResponse;
import com.example.realestate.modules.repair.model.dto.RepairActionRequest;
import com.example.realestate.modules.repair.model.dto.RepairCreateRequest;
import com.example.realestate.modules.repair.model.query.RepairQuery;
import com.example.realestate.modules.repair.model.vo.RepairDetailVO;
import com.example.realestate.modules.repair.model.vo.RepairListItemVO;
import com.example.realestate.modules.repair.model.vo.RepairMediaVO;
import com.example.realestate.modules.repair.service.RepairService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 报修接口：客户提交/确认/撤销/重开，ADMIN 受理/处理/解决；
 * 图片走私有存储 + 鉴权流式返回，不产生公开 URL。
 */
@RestController
@RequestMapping("/api/v1/repairs")
@Tag(name = "报修", description = "租后报修工单与私有图片")
public class RepairController {

    private final RepairService repairService;

    public RepairController(RepairService repairService) {
        this.repairService = repairService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    @Operation(summary = "报修分页列表（ADMIN 全部，CUSTOMER 本人）")
    public ApiResponse<PageResponse<RepairListItemVO>> list(RepairQuery query) {
        return ApiResponse.ok(repairService.list(query));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    @Operation(summary = "报修详情（本人或 ADMIN，他人 404）")
    public ApiResponse<RepairDetailVO> detail(@PathVariable Long id) {
        return ApiResponse.ok(repairService.detail(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "客户提交报修（可关联本人 ACTIVE 合同）")
    public ApiResponse<RepairDetailVO> create(@Valid @RequestBody RepairCreateRequest request) {
        return ApiResponse.ok(repairService.create(request));
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "受理（SUBMITTED → ACCEPTED）")
    public ApiResponse<RepairDetailVO> accept(@PathVariable Long id,
                                              @RequestBody(required = false) RepairActionRequest request) {
        return ApiResponse.ok(repairService.accept(id, reasonOf(request)));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "开始处理（ACCEPTED/REOPENED → PROCESSING）")
    public ApiResponse<RepairDetailVO> start(@PathVariable Long id,
                                             @RequestBody(required = false) RepairActionRequest request) {
        return ApiResponse.ok(repairService.start(id, reasonOf(request)));
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "处理完成（PROCESSING → RESOLVED）")
    public ApiResponse<RepairDetailVO> resolve(@PathVariable Long id,
                                               @RequestBody(required = false) RepairActionRequest request) {
        return ApiResponse.ok(repairService.resolve(id, reasonOf(request)));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    @Operation(summary = "确认关闭（RESOLVED → CLOSED，客户本人或 ADMIN）")
    public ApiResponse<RepairDetailVO> close(@PathVariable Long id,
                                             @RequestBody(required = false) RepairActionRequest request) {
        return ApiResponse.ok(repairService.close(id, reasonOf(request)));
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "客户重新打开（RESOLVED/CLOSED → REOPENED）")
    public ApiResponse<RepairDetailVO> reopen(@PathVariable Long id,
                                              @RequestBody(required = false) RepairActionRequest request) {
        return ApiResponse.ok(repairService.reopen(id, reasonOf(request)));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "客户撤销（SUBMITTED/ACCEPTED → CANCELLED）")
    public ApiResponse<RepairDetailVO> cancel(@PathVariable Long id,
                                              @RequestBody(required = false) RepairActionRequest request) {
        return ApiResponse.ok(repairService.cancel(id, reasonOf(request)));
    }

    @PostMapping("/{id}/media")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    @Operation(summary = "上传报修图片（multipart，私有存储，最多 6 张）")
    public ApiResponse<RepairMediaVO> uploadMedia(@PathVariable Long id,
                                                  @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(repairService.addMedia(id, file));
    }

    @GetMapping("/{id}/media/{mediaId}")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    @Operation(summary = "鉴权读取报修图片（private,no-store；他人 404）")
    public ResponseEntity<byte[]> media(@PathVariable Long id, @PathVariable Long mediaId) {
        RepairService.RepairMediaContent media = repairService.downloadMedia(id, mediaId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(media.contentType()))
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + media.filename() + "\"")
                .body(media.content());
    }

    @DeleteMapping("/{id}/media/{mediaId}")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMER')")
    @Operation(summary = "删除报修图片（本人或 ADMIN；已关闭/撤销工单不可删）")
    public ApiResponse<Void> deleteMedia(@PathVariable Long id, @PathVariable Long mediaId) {
        repairService.deleteMedia(id, mediaId);
        return ApiResponse.ok();
    }

    private String reasonOf(RepairActionRequest request) {
        return request == null ? null : request.reason();
    }
}
