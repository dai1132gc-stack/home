package com.example.realestate.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * prod 环境密钥强度附加校验：拒绝内置开发默认密钥（fail-fast）。
 */
@Component
public class ProdSecretGuard implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(ProdSecretGuard.class);

    private final String profile;
    private final AppProperties properties;

    public ProdSecretGuard(@Value("${spring.profiles.active:dev}") String profile,
                           AppProperties properties) {
        this.profile = profile;
        this.properties = properties;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!"prod".equals(profile)) {
            return;
        }
        String access = properties.jwt().accessSecret();
        String refresh = properties.jwt().refreshSecret();
        if (access == null || refresh == null
                || access.startsWith(AppProperties.DEV_SECRET_PREFIX)
                || refresh.startsWith(AppProperties.DEV_SECRET_PREFIX)) {
            throw new IllegalStateException(
                    "生产环境禁止使用内置开发默认 JWT 密钥：请通过环境变量 JWT_ACCESS_SECRET / JWT_REFRESH_SECRET 提供独立强随机密钥");
        }
        log.info("prod 密钥强度校验通过");
    }
}
