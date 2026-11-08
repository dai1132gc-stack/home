package com.example.realestate.modules.calculator.model.vo;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 测算配置：生效中的贷款费率与税费规则，含来源、生效日期与免责声明。 */
public record CalculatorConfigVO(
        List<LoanRateVO> rates,
        List<TaxRuleVO> taxRules,
        Instant generatedAt,
        String disclaimer
) {

    public record LoanRateVO(
            String loanType,
            String loanTypeLabel,
            Integer termYears,
            BigDecimal rate,
            BigDecimal ratePercent,
            LocalDate effectiveDate,
            String source
    ) {
    }

    public record TaxRuleVO(
            String city,
            String ruleType,
            String ruleTypeLabel,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            BigDecimal rate,
            BigDecimal ratePercent,
            BigDecimal quickDeduction,
            LocalDate effectiveDate,
            String source
    ) {
    }
}
