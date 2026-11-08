package com.example.realestate.modules.file.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 媒体存储配置。
 * dev：type=local，文件落盘到 localRoot，对外路径为 publicBaseUrl；
 * prod：type=oss，由 OSS 实现接管（W4 接入），业务 Service 不感知。
 */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        String type,
        String localRoot,
        String publicBaseUrl,
        long maxImageSizeBytes
) {

    public String typeOrDefault() {
        return type == null || type.isBlank() ? "local" : type;
    }

    public String localRootOrDefault() {
        return localRoot == null || localRoot.isBlank() ? "uploads" : localRoot;
    }

    public String publicBaseUrlOrDefault() {
        String base = publicBaseUrl == null || publicBaseUrl.isBlank() ? "/uploads" : publicBaseUrl;
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    public long maxImageSizeBytesOrDefault() {
        return maxImageSizeBytes > 0 ? maxImageSizeBytes : 10L * 1024 * 1024;
    }
}
