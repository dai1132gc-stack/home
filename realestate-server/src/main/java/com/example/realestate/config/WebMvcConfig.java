package com.example.realestate.config;

import com.example.realestate.modules.file.config.StorageProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 本地媒体静态映射：GET /uploads/** 直接由容器返回磁盘文件。
 * prod 切换 OSS 后不注册该映射（图片走 CDN / 签名 URL）。
 */
@Configuration
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class WebMvcConfig implements WebMvcConfigurer {

    private final StorageProperties storageProperties;

    public WebMvcConfig(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path root = Paths.get(storageProperties.localRootOrDefault()).toAbsolutePath().normalize();
        registry.addResourceHandler(storageProperties.publicBaseUrlOrDefault() + "/**")
                .addResourceLocations(root.toUri().toString());
    }
}
