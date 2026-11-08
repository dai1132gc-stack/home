package com.example.realestate.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * 应用配置校验：启动时 fail-fast，禁止空密钥/弱密钥进入运行态。
 * 内置开发默认密钥仅在 dev/test profile 由 yaml 提供，prod 由环境变量强制提供。
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @Valid Jwt jwt,
        @Valid Cors cors,
        @Valid Bootstrap bootstrap
) {

    /** prod 下禁止使用的内置开发默认密钥前缀（见 application-dev.yml / application-test.yml） */
    public static final String DEV_SECRET_PREFIX = "dev-only-";

    @Validated
    @ConfigurationProperties(prefix = "app.jwt")
    public record Jwt(
            @NotBlank String accessSecret,
            @NotBlank String refreshSecret,
            @Min(1) @Max(120) int accessTtlMinutes,
            @Min(1) @Max(30) int refreshTtlDays
    ) {
    }

    @Validated
    @ConfigurationProperties(prefix = "app.cors")
    public record Cors(
            @NotEmpty List<@NotBlank String> allowedOrigins
    ) {
    }

    @Validated
    @ConfigurationProperties(prefix = "app.bootstrap")
    public record Bootstrap(
            String adminPassword,
            String customerPassword
    ) {
    }
}
