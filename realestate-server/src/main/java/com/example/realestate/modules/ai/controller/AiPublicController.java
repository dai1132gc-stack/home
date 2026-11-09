package com.example.realestate.modules.ai.controller;

import com.example.realestate.common.api.ApiResponse;
import com.example.realestate.infrastructure.web.ClientIpResolver;
import com.example.realestate.modules.ai.client.AiClient;
import com.example.realestate.modules.ai.model.dto.HouseSearchRequest;
import com.example.realestate.modules.ai.model.dto.PolicyAnswerRequest;
import com.example.realestate.modules.ai.model.vo.AiStatusResponse;
import com.example.realestate.modules.ai.model.vo.HouseSearchResponse;
import com.example.realestate.modules.ai.model.vo.PolicyAnswerResponse;
import com.example.realestate.modules.ai.service.AiHouseSearchService;
import com.example.realestate.modules.ai.service.PolicyIndexService;
import com.example.realestate.modules.ai.service.PolicyQaService;
import com.example.realestate.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开 AI 接口（W6）：路径与方法在 SecurityConfig 精确放行；独立 AI 限流。
 * clientType 仅取自 X-Client-Type 头（WEB/MINI），用于统计，不影响权限。
 */
@RestController
@RequestMapping("/api/v1/ai")
@Tag(name = "智能服务", description = "自然语言选房与政策问答（公开，可降级）")
public class AiPublicController {

    private final AiHouseSearchService houseSearchService;
    private final PolicyQaService policyQaService;
    private final PolicyIndexService indexService;
    private final AiClient aiClient;
    private final ClientIpResolver clientIpResolver;
    private final CurrentUserProvider currentUser;

    public AiPublicController(AiHouseSearchService houseSearchService, PolicyQaService policyQaService,
                              PolicyIndexService indexService, AiClient aiClient,
                              ClientIpResolver clientIpResolver, CurrentUserProvider currentUser) {
        this.houseSearchService = houseSearchService;
        this.policyQaService = policyQaService;
        this.indexService = indexService;
        this.aiClient = aiClient;
        this.clientIpResolver = clientIpResolver;
        this.currentUser = currentUser;
    }

    @PostMapping("/house-search")
    @Operation(summary = "自然语言选房（公开；AI 异常时降级为公开列表，degraded=true）")
    public ApiResponse<HouseSearchResponse> houseSearch(@Valid @RequestBody HouseSearchRequest request,
                                                        @RequestHeader(value = "X-Client-Type", required = false) String clientType,
                                                        HttpServletRequest http) {
        return ApiResponse.ok(houseSearchService.search(request, clientIpResolver.resolve(http),
                currentUser.currentUserIdOrNull(), normalizeClientType(clientType)));
    }

    @PostMapping("/policy-answer")
    @Operation(summary = "政策向量问答（公开；检索不足 insufficient=true，不编造）")
    public ApiResponse<PolicyAnswerResponse> policyAnswer(@Valid @RequestBody PolicyAnswerRequest request,
                                                          @RequestHeader(value = "X-Client-Type", required = false) String clientType,
                                                          HttpServletRequest http) {
        return ApiResponse.ok(policyQaService.answer(request, clientIpResolver.resolve(http),
                currentUser.currentUserIdOrNull(), normalizeClientType(clientType)));
    }

    @GetMapping("/status")
    @Operation(summary = "AI 状态（公开；不含 Key/base-url）")
    public ApiResponse<AiStatusResponse> status() {
        boolean enabled = aiClient.available();
        return ApiResponse.ok(new AiStatusResponse(
                enabled,
                enabled ? aiClient.chatModel() : null,
                enabled ? aiClient.embeddingModel() : null,
                indexService.countIndexed(),
                PolicyQaService.DISCLAIMER,
                enabled ? aiClient.provider() : null));
    }

    private static String normalizeClientType(String clientType) {
        if (clientType == null) {
            return "ANON";
        }
        String upper = clientType.trim().toUpperCase();
        return ("WEB".equals(upper) || "MINI".equals(upper)) ? upper : "ANON";
    }
}
