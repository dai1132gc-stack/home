package com.example.realestate.modules.file.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 媒体存储抽象：业务层只依赖本接口，dev 使用本地磁盘，prod 切换 OSS。
 */
public interface StorageService {

    /**
     * 保存文件并返回可访问的相对路径。
     *
     * @param file     上传文件
     * @param category 业务分类（如 house），用于分目录，不接受用户输入拼接
     */
    StoredFile store(MultipartFile file, String category);

    /**
     * 删除对象（本地实现删除磁盘文件；OSS 实现删除对象）。当前仅预留，未接入业务。
     */
    void delete(String url);

    record StoredFile(String url, String contentType, long size) {
    }
}
