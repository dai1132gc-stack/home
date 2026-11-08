package com.example.realestate.modules.file.controller;

import com.example.realestate.common.api.ApiResponse;
import com.example.realestate.modules.file.model.vo.UploadResultVO;
import com.example.realestate.modules.file.service.StorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 媒体上传（仅 ADMIN）。Controller 不写业务逻辑，校验与落盘在 StorageService。
 */
@RestController
@RequestMapping("/api/v1/files")
@Tag(name = "文件", description = "图片上传（dev 本地存储，prod 预留 OSS）")
public class FileController {

    private final StorageService storageService;

    public FileController(StorageService storageService) {
        this.storageService = storageService;
    }

    @PostMapping("/upload")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "上传房源图片（ADMIN）")
    public ApiResponse<UploadResultVO> upload(@RequestParam("file") MultipartFile file) {
        StorageService.StoredFile stored = storageService.store(file, "house");
        return ApiResponse.ok(new UploadResultVO(stored.url(), stored.contentType(), stored.size()));
    }
}
