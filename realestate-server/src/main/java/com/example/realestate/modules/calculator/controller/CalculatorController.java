package com.example.realestate.modules.calculator.controller;

import com.example.realestate.common.api.ApiResponse;
import com.example.realestate.modules.calculator.model.dto.MortgageRequest;
import com.example.realestate.modules.calculator.model.dto.TaxRequest;
import com.example.realestate.modules.calculator.model.vo.CalculatorConfigVO;
import com.example.realestate.modules.calculator.model.vo.MortgageResultVO;
import com.example.realestate.modules.calculator.model.vo.TaxResultVO;
import com.example.realestate.modules.calculator.service.CalculatorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开测算接口：房贷/税费/配置，每 IP 每分钟 60 次（Service 内 Redis 计数）。
 * Controller 只负责请求绑定与客户端 IP 提取，不做计算。
 */
@RestController
@RequestMapping("/api/v1/calculators")
@Tag(name = "测算", description = "房贷与交易税费测算（公开，限流）")
public class CalculatorController {

    private final CalculatorService calculatorService;

    public CalculatorController(CalculatorService calculatorService) {
        this.calculatorService = calculatorService;
    }

    @PostMapping("/mortgage")
    @Operation(summary = "房贷测算（等额本息/等额本金，公开限流）")
    public ApiResponse<MortgageResultVO> mortgage(@Valid @RequestBody MortgageRequest request,
                                                  HttpServletRequest httpRequest) {
        return ApiResponse.ok(calculatorService.mortgage(request, clientIp(httpRequest)));
    }

    @PostMapping("/tax")
    @Operation(summary = "税费测算（契税/增值税/个税分档，公开限流）")
    public ApiResponse<TaxResultVO> tax(@Valid @RequestBody TaxRequest request,
                                        HttpServletRequest httpRequest) {
        return ApiResponse.ok(calculatorService.tax(request, clientIp(httpRequest)));
    }

    @GetMapping("/config")
    @Operation(summary = "生效中的费率与税费规则（含来源与生效日期）")
    public ApiResponse<CalculatorConfigVO> config() {
        return ApiResponse.ok(calculatorService.config());
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
