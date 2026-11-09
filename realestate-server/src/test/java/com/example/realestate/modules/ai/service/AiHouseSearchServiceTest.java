package com.example.realestate.modules.ai.service;

import com.example.realestate.common.api.PageResponse;
import com.example.realestate.config.AiProperties;
import com.example.realestate.modules.ai.client.AiClient;
import com.example.realestate.modules.ai.client.AiUpstreamException;
import com.example.realestate.modules.ai.model.dto.HouseSearchRequest;
import com.example.realestate.modules.ai.model.vo.HouseSearchResponse;
import com.example.realestate.modules.house.model.query.HouseQuery;
import com.example.realestate.modules.house.model.vo.HouseListItemVO;
import com.example.realestate.modules.house.service.HouseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
@DisplayName("智能选房：意图解析、白名单校验与降级口径")
class AiHouseSearchServiceTest {

    @Mock
    private AiClient aiClient;
    @Mock
    private AiAccessGuard guard;
    @Mock
    private AiInteractionLogger interactionLogger;
    @Mock
    private HouseService houseService;

    private AiProperties properties = new AiProperties(true, "http://x/v1", "k", "chat", "embed",
            12000, 200, 800, 100000, 20, 4, 0.30, 4);
    private AiHouseSearchService service;

    @BeforeEach
    void setUp() {
        service = new AiHouseSearchService(aiClient, guard, interactionLogger, houseService, properties);
        lenient().when(guard.tryAcquire()).thenReturn(true);
        lenient().when(aiClient.available()).thenReturn(true);
        lenient().when(aiClient.chatModel()).thenReturn("chat");
        lenient().when(aiClient.provider()).thenReturn("stub");
        lenient().when(houseService.list(any())).thenReturn(PageResponse.of(List.of(new HouseListItemVO()), 1, 1, 10));
    }

    private HouseSearchRequest req() {
        return new HouseSearchRequest("找一套三室一厅", 1, 10);
    }

    @Test
    @DisplayName("AI 关闭：降级为公开列表，degraded=AI_DISABLED")
    void disabledDegrades() {
        when(aiClient.available()).thenReturn(false);
        HouseSearchResponse resp = service.search(req(), "1.1.1.1", null, "WEB");
        assertThat(resp.degraded()).isTrue();
        assertThat(resp.degradedReason()).isEqualTo(AiHouseSearchService.DEGRADE_DISABLED);
        assertThat(resp.parsedConditions()).isEmpty();
        verify(houseService).list(any());
    }

    @Test
    @DisplayName("上游超时：degraded=AI_TIMEOUT")
    void timeoutDegrades() {
        when(aiClient.chat(anyString(), anyString(), anyInt()))
                .thenThrow(new AiUpstreamException(AiUpstreamException.Kind.TIMEOUT, "timeout"));
        HouseSearchResponse resp = service.search(req(), "1.1.1.1", null, "MINI");
        assertThat(resp.degradedReason()).isEqualTo(AiHouseSearchService.DEGRADE_TIMEOUT);
    }

    @Test
    @DisplayName("上游 429/5xx：degraded=AI_UPSTREAM")
    void upstreamDegrades() {
        org.mockito.Mockito.doThrow(new AiUpstreamException(AiUpstreamException.Kind.RATE_LIMITED, "429"))
                .when(aiClient).chat(anyString(), anyString(), anyInt());
        assertThat(service.search(req(), "1.1.1.1", null, null).degradedReason())
                .isEqualTo(AiHouseSearchService.DEGRADE_UPSTREAM);
        org.mockito.Mockito.doThrow(new AiUpstreamException(AiUpstreamException.Kind.SERVER_ERROR, "500"))
                .when(aiClient).chat(anyString(), anyString(), anyInt());
        assertThat(service.search(req(), "1.1.1.1", null, null).degradedReason())
                .isEqualTo(AiHouseSearchService.DEGRADE_UPSTREAM);
    }

    @Test
    @DisplayName("非法 JSON：degraded=AI_INVALID_JSON")
    void invalidJsonDegrades() {
        when(aiClient.chat(anyString(), anyString(), anyInt()))
                .thenReturn(new AiClient.ChatCompletion("无法解析的文字", null, null));
        assertThat(service.search(req(), "1.1.1.1", null, null).degradedReason())
                .isEqualTo(AiHouseSearchService.DEGRADE_INVALID_JSON);
    }

    @Test
    @DisplayName("字段全非法（含越权 status）：degraded=AI_INVALID_INTENT")
    void invalidIntentDegrades() {
        when(aiClient.chat(anyString(), anyString(), anyInt()))
                .thenReturn(new AiClient.ChatCompletion(
                        "{\"houseType\":\"BAD\",\"rooms\":99,\"status\":\"PENDING\",\"sort\":\"drop table\"}", null, null));
        HouseSearchResponse resp = service.search(req(), "1.1.1.1", null, null);
        assertThat(resp.degradedReason()).isEqualTo(AiHouseSearchService.DEGRADE_INVALID_INTENT);
    }

    @Test
    @DisplayName("成功：围栏剥离 + 字段映射（rooms/价格区间/排序）")
    void successMapping() {
        when(aiClient.chat(anyString(), anyString(), anyInt()))
                .thenReturn(new AiClient.ChatCompletion(
                        "```json\n{\"houseType\":\"rent\",\"region\":\"朝阳\",\"communityKeyword\":\"和平里\","
                                + "\"minPrice\":3000,\"maxPrice\":5000,\"rooms\":3,\"sort\":\"price_asc\"}\n```",
                        100, 20));
        HouseSearchResponse resp = service.search(req(), "1.1.1.1", 7L, "WEB");
        assertThat(resp.degraded()).isFalse();
        assertThat(resp.parsedConditions()).containsKeys("houseType", "region", "keyword", "minPrice", "maxPrice", "rooms", "sort");

        ArgumentCaptor<HouseQuery> captor = ArgumentCaptor.forClass(HouseQuery.class);
        verify(houseService).list(captor.capture());
        HouseQuery q = captor.getValue();
        assertThat(q.getHouseType()).isEqualTo("RENT");
        assertThat(q.getRegion()).isEqualTo("朝阳");
        assertThat(q.getKeyword()).isEqualTo("和平里");
        assertThat(q.getMinPrice()).isEqualByComparingTo(new BigDecimal("3000"));
        assertThat(q.getMaxPrice()).isEqualByComparingTo(new BigDecimal("5000"));
        assertThat(q.getRooms()).isEqualTo(3);
        assertThat(q.getSort()).isEqualTo("price_asc");
        assertThat(q.getStatus()).isNull(); // 不信任模型 status
        verify(interactionLogger).record(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("价格 min>max 自动交换")
    void priceSwap() {
        when(aiClient.chat(anyString(), anyString(), anyInt()))
                .thenReturn(new AiClient.ChatCompletion("{\"minPrice\":5000,\"maxPrice\":3000}", null, null));
        HouseSearchResponse resp = service.search(req(), "1.1.1.1", null, null);
        assertThat(resp.degraded()).isFalse();
        assertThat((BigDecimal) resp.parsedConditions().get("minPrice")).isEqualByComparingTo("3000");
        assertThat((BigDecimal) resp.parsedConditions().get("maxPrice")).isEqualByComparingTo("5000");
    }
}
