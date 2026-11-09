package com.example.realestate.modules.ai.model.vo;

import com.example.realestate.modules.house.model.vo.HouseListItemVO;

import java.util.List;
import java.util.Map;

/**
 * 自然语言选房响应（W6）。
 * parsedConditions 只包含已通过白名单校验的条件；降级时为空或仅保留已校验条件。
 */
public record HouseSearchResponse(
        List<HouseListItemVO> items,
        long total,
        long page,
        long size,
        Map<String, Object> parsedConditions,
        boolean degraded,
        String degradedReason
) {
}
