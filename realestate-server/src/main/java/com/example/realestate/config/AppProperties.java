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
        @Valid Bootstrap bootstrap,
        @Valid Wechat wechat
) {

    /** prod 下禁止使用的内置开发默认密钥前缀（见 application-dev.yml / application-test.yml） */
    public static final String DEV_SECRET_PREFIX = "dev-only-";

    /** 微信凭据缺失时的默认超时与官方接口地址 */
    private static final int DEFAULT_WECHAT_TIMEOUT_SECONDS = 4;
    private static final String DEFAULT_WECHAT_API_BASE_URL = "https://api.weixin.qq.com";

    public AppProperties {
        if (wechat == null) {
            wechat = new Wechat(null, null, DEFAULT_WECHAT_TIMEOUT_SECONDS, DEFAULT_WECHAT_API_BASE_URL);
        }
    }

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

    /**
     * 微信小程序 code 登录配置。
     * AppID/AppSecret 允许为空：未配置时接口返回 503，账号密码登录保持可用，不阻塞本地联调。
     */
    @Validated
    @ConfigurationProperties(prefix = "app.wechat")
    public record Wechat(
            String appId,
            String appSecret,
            @Min(3) @Max(5) int timeoutSeconds,
            String apiBaseUrl
    ) {
        public Wechat {
            if (timeoutSeconds <= 0) {
                timeoutSeconds = DEFAULT_WECHAT_TIMEOUT_SECONDS;
            }
            if (apiBaseUrl == null || apiBaseUrl.isBlank()) {
                apiBaseUrl = DEFAULT_WECHAT_API_BASE_URL;
            }
        }

        /** 凭据完整配置才调用微信接口；任一为空按“未配置”处理（降级为账号密码登录） */
        public boolean configured() {
            return appId != null && !appId.isBlank()
                    && appSecret != null && !appSecret.isBlank();
        }
    }
}
