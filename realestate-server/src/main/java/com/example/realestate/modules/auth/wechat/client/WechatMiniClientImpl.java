package com.example.realestate.modules.auth.wechat.client;

import com.example.realestate.common.enums.ErrorCode;
import com.example.realestate.common.exception.BusinessException;
import com.example.realestate.config.AppProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Set;

/**
 * 微信 jscode2session 实现（Spring RestClient，不新增 HTTP 客户端依赖）。
 * 连接/读取超时 3～5 秒（默认 4 秒），不重试；AppSecret/code/session_key 一律不写日志。
 */
@Component
public class WechatMiniClientImpl implements WechatMiniClient {

    private static final Logger log = LoggerFactory.getLogger(WechatMiniClientImpl.class);
    private static final String SESSION_PATH = "/sns/jscode2session";
    /** code 无效/已使用/缺失/高风险账号 */
    private static final Set<Integer> CODE_INVALID_ERRCODES = Set.of(40029, 40163, 41008, 40226);
    /** AppID/AppSecret 配置错误（按“未配置”降级，不泄露具体配置细节） */
    private static final Set<Integer> CREDENTIAL_ERRCODES = Set.of(40013, 40125, 41004);

    private final RestClient restClient;
    private final AppProperties.Wechat config;

    public WechatMiniClientImpl(AppProperties properties) {
        this.config = properties.wechat();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(config.timeoutSeconds()));
        factory.setReadTimeout(Duration.ofSeconds(config.timeoutSeconds()));
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(trimTrailingSlash(config.apiBaseUrl()))
                .build();
    }

    @Override
    public WechatSession code2Session(String code) {
        if (!config.configured()) {
            throw new BusinessException(ErrorCode.WECHAT_NOT_CONFIGURED);
        }
        WechatSessionResponse body;
        try {
            body = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(SESSION_PATH)
                            .queryParam("appid", config.appId())
                            .queryParam("secret", config.appSecret())
                            .queryParam("js_code", code)
                            .queryParam("grant_type", "authorization_code")
                            .build())
                    .retrieve()
                    .body(WechatSessionResponse.class);
        } catch (RestClientException e) {
            // 超时、连接失败、上游 4xx/5xx：统一 502，不向上暴露上游细节
            log.warn("微信 jscode2session 调用失败 type={}", e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.WECHAT_UPSTREAM_ERROR);
        }

        if (body == null) {
            log.warn("微信 jscode2session 返回空响应");
            throw new BusinessException(ErrorCode.WECHAT_UPSTREAM_ERROR);
        }
        Integer errcode = body.errcode();
        if (errcode != null && errcode != 0) {
            if (CODE_INVALID_ERRCODES.contains(errcode)) {
                log.info("微信 code 校验失败 errcode={}", errcode);
                throw new BusinessException(ErrorCode.WECHAT_CODE_INVALID);
            }
            if (CREDENTIAL_ERRCODES.contains(errcode)) {
                log.warn("微信登录凭据无效 errcode={}（请检查 WECHAT_MINI_APP_ID/APP_SECRET）", errcode);
                throw new BusinessException(ErrorCode.WECHAT_NOT_CONFIGURED);
            }
            log.warn("微信 jscode2session 上游错误 errcode={}", errcode);
            throw new BusinessException(ErrorCode.WECHAT_UPSTREAM_ERROR);
        }
        String openid = body.openid();
        if (openid == null || openid.isBlank()) {
            log.warn("微信 jscode2session 未返回 openid");
            throw new BusinessException(ErrorCode.WECHAT_UPSTREAM_ERROR);
        }
        // session_key 仅用于确认响应结构，此处直接丢弃，不返回、不落库
        return new WechatSession(openid, body.unionid());
    }

    private static String trimTrailingSlash(String url) {
        if (url == null || url.isBlank()) {
            return "https://api.weixin.qq.com";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** 微信响应体（snake_case）；session_key 仅解析用于丢弃，不外传 */
    record WechatSessionResponse(
            String openid,
            @JsonProperty("session_key") String sessionKey,
            String unionid,
            Integer errcode,
            String errmsg
    ) {
    }
}
