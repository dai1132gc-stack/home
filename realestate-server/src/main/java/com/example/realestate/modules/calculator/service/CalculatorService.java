package com.example.realestate.modules.calculator.service;

import com.example.realestate.modules.calculator.model.dto.MortgageRequest;
import com.example.realestate.modules.calculator.model.dto.TaxRequest;
import com.example.realestate.modules.calculator.model.vo.CalculatorConfigVO;
import com.example.realestate.modules.calculator.model.vo.MortgageResultVO;
import com.example.realestate.modules.calculator.model.vo.TaxResultVO;

/**
 * 测算服务：公开接口，按 IP 限流；费率/税率来自配置表并标注来源与生效日期。
 * 无匹配税费规则时返回 400，不做猜测。
 */
public interface CalculatorService {

    MortgageResultVO mortgage(MortgageRequest request, String clientIp);

    TaxResultVO tax(TaxRequest request, String clientIp);

    CalculatorConfigVO config();
}
