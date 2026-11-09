package com.example.realestate.modules.ai.service;

import com.example.realestate.common.api.PageResponse;
import com.example.realestate.common.enums.ErrorCode;
import com.example.realestate.common.enums.HouseType;
import com.example.realestate.common.exception.BusinessException;
import com.example.realestate.config.AiProperties;
import com.example.realestate.modules.ai.client.AiClient;
import com.example.realestate.modules.ai.client.AiUpstreamException;
import com.example.realestate.modules.ai.model.dto.HouseSearchIntent;
import com.example.realestate.modules.ai.model.dto.HouseSearchRequest;
import com.example.realestate.modules.ai.model.vo.HouseSearchResponse;
import com.example.realestate.modules.ai.util.ModelJsonExtractor;
import com.example.realestate.modules.ai.util.SensitiveTextSanitizer;
import com.example.realestate.modules.house.model.query.HouseQuery;
import com.example.realestate.modules.house.model.vo.HouseListItemVO;
import com.example.realestate.modules.house.service.HouseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 自然语言选房（W6）：模型只输出受约束 JSON 意图，后端校验后映射为 HouseQuery 白名单字段；
 * 模型不得生成/执行 SQL；任何失败降级为公开列表（最新发布），返回 200 + degraded。
 */
@Service
public class AiHouseSearchService {

    private static final Logger log = LoggerFactory.getLogger(AiHouseSearchService.class);

    public static final String DEGRADE_DISABLED = "AI_DISABLED";
    public static final String DEGRADE_TIMEOUT = "AI_TIMEOUT";
    public static final String DEGRADE_UPSTREAM = "AI_UPSTREAM";
    public static final String DEGRADE_INVALID_JSON = "AI_INVALID_JSON";
    public static final String DEGRADE_INVALID_INTENT = "AI_INVALID_INTENT";

    private static final Set<String> SORT_WHITELIST =
            Set.of("created_desc", "price_asc", "price_desc", "area_asc", "area_desc");

    private static final String SYSTEM_PROMPT = """
            你是房产筛选意图解析器。只输出一个 JSON 对象，不要输出任何其他文字。
            字段：houseType(RENT出租/SALE出售)、region(城区前缀字符串)、communityKeyword(小区或标题关键词)、
            minPrice/maxPrice(数字，元或万元按语境，出售按万元×10000)、rooms(居室整数1-10)、
            sort(created_desc最新/price_asc价格升/price_desc价格降/area_asc/area_desc)。
            无法判断的字段省略。禁止输出 SQL、status、ownerId 等字段。
            """;

    private final AiClient aiClient;
    private final AiAccessGuard guard;
    private final AiInteractionLogger logger2;
    private final HouseService houseService;
    private final AiProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiHouseSearchService(AiClient aiClient, AiAccessGuard guard, AiInteractionLogger logger2,
                                HouseService houseService, AiProperties properties) {
        this.aiClient = aiClient;
        this.guard = guard;
        this.logger2 = logger2;
        this.houseService = houseService;
        this.properties = properties;
    }

    public HouseSearchResponse search(HouseSearchRequest request, String clientIp, Long userId, String clientType) {
        guard.checkRateLimit(clientIp);
        long start = System.currentTimeMillis();
        if (!aiClient.available()) {
            return degraded(request, userId, clientType, DEGRADE_DISABLED, start);
        }
        if (!guard.tryAcquire()) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, "智能服务繁忙，请稍后重试");
        }
        try {
            String sanitized = SensitiveTextSanitizer.sanitize(request.query().trim());
            guard.checkDailyTokenBudget(AiAccessGuard.estimateTokens(sanitized)
                    + properties.maxOutputTokens());
            AiClient.ChatCompletion completion = aiClient.chat(SYSTEM_PROMPT, sanitized, properties.maxOutputTokens());
            HouseSearchIntent intent = parseIntent(completion.content());
            HouseQuery query = new HouseQuery();
            Map<String, Object> conditions = applyIntent(intent, query);
            if (conditions.isEmpty()) {
                return degraded(request, userId, clientType, DEGRADE_INVALID_INTENT, start);
            }
            PageResponse<HouseListItemVO> page = execute(query, request);
            logger2.record(AiInteractionLogger.SCENE_SMART_SEARCH, userId, clientType,
                    AiInteractionLogger.STATUS_SUCCESS, aiClient.provider(), aiClient.chatModel(),
                    elapsed(start),
                    completion.promptTokens() != null ? completion.promptTokens() : AiAccessGuard.estimateTokens(sanitized),
                    completion.completionTokens(), null);
            return new HouseSearchResponse(page.items(), page.total(), page.page(), page.size(),
                    conditions, false, null);
        } catch (AiUpstreamException e) {
            String reason = e.kind() == AiUpstreamException.Kind.TIMEOUT ? DEGRADE_TIMEOUT : DEGRADE_UPSTREAM;
            return degraded(request, userId, clientType, reason, start);
        } catch (InvalidIntentJsonException e) {
            return degraded(request, userId, clientType, DEGRADE_INVALID_JSON, start);
        } finally {
            guard.release();
        }
    }

    private HouseSearchIntent parseIntent(String content) {
        String json = ModelJsonExtractor.extractJsonObject(content);
        if (json == null) {
            throw new InvalidIntentJsonException();
        }
        try {
            return objectMapper.readValue(json, HouseSearchIntent.class);
        } catch (Exception e) {
            throw new InvalidIntentJsonException();
        }
    }

    /** 白名单校验并映射到 HouseQuery；非法字段丢弃。返回已生效条件（用于回显）。 */
    private Map<String, Object> applyIntent(HouseSearchIntent intent, HouseQuery query) {
        Map<String, Object> conditions = new LinkedHashMap<>();
        if (intent == null) {
            return conditions;
        }
        if (intent.houseType() != null) {
            try {
                HouseType type = HouseType.valueOf(intent.houseType().trim().toUpperCase());
                query.setHouseType(type.name());
                conditions.put("houseType", type.name());
            } catch (IllegalArgumentException ignored) {
                // 非法类型丢弃
            }
        }
        if (intent.region() != null && !intent.region().isBlank() && intent.region().length() <= 50) {
            query.setRegion(intent.region().trim());
            conditions.put("region", intent.region().trim());
        }
        if (intent.communityKeyword() != null && !intent.communityKeyword().isBlank()
                && intent.communityKeyword().length() <= 100) {
            query.setKeyword(intent.communityKeyword().trim());
            conditions.put("keyword", intent.communityKeyword().trim());
        }
        BigDecimal min = nonNegative(intent.minPrice());
        BigDecimal max = nonNegative(intent.maxPrice());
        if (min != null && max != null && min.compareTo(max) > 0) {
            BigDecimal tmp = min;
            min = max;
            max = tmp;
        }
        if (min != null) {
            query.setMinPrice(min);
            conditions.put("minPrice", min);
        }
        if (max != null) {
            query.setMaxPrice(max);
            conditions.put("maxPrice", max);
        }
        if (intent.rooms() != null && intent.rooms() >= 1 && intent.rooms() <= 10) {
            query.setRooms(intent.rooms());
            conditions.put("rooms", intent.rooms());
        }
        if (intent.sort() != null && SORT_WHITELIST.contains(intent.sort().trim())) {
            query.setSort(intent.sort().trim());
            conditions.put("sort", intent.sort().trim());
        }
        return conditions;
    }

    private static BigDecimal nonNegative(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) >= 0 ? value : null;
    }

    /** 降级：返回公开列表（最新发布），200 + degraded 标记，不 500 */
    private HouseSearchResponse degraded(HouseSearchRequest request, Long userId, String clientType,
                                         String reason, long start) {
        HouseQuery query = new HouseQuery();
        query.setSort("created_desc");
        PageResponse<HouseListItemVO> page = execute(query, request);
        logger2.record(AiInteractionLogger.SCENE_SMART_SEARCH, userId, clientType,
                AiInteractionLogger.STATUS_DEGRADED,
                aiClient.available() ? aiClient.provider() : null,
                aiClient.available() ? aiClient.chatModel() : null,
                elapsed(start), null, null, reason);
        return new HouseSearchResponse(page.items(), page.total(), page.page(), page.size(),
                Map.of(), true, reason);
    }

    private PageResponse<HouseListItemVO> execute(HouseQuery query, HouseSearchRequest request) {
        query.setPage(request.page() == null ? 1 : request.page());
        query.setSize(request.size() == null ? 10 : request.size());
        // 匿名/非 ADMIN 强制 PUBLISHED 由 HouseService 内部下推 SQL
        return houseService.list(query);
    }

    private static int elapsed(long start) {
        return (int) Math.min(Integer.MAX_VALUE, System.currentTimeMillis() - start);
    }

    private static class InvalidIntentJsonException extends RuntimeException {
    }
}
