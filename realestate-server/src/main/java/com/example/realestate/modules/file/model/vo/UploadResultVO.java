package com.example.realestate.modules.file.model.vo;

/** 上传结果：url 为相对路径（/uploads/...），由客户端按环境拼接域名 */
public record UploadResultVO(
        String url,
        String contentType,
        long size
) {
}
