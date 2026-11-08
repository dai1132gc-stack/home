package com.example.realestate.modules.calculator.model.vo;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 房贷测算结果：含假设、费率来源、生效日期、测算日期与免责声明。 */
public record MortgageResultVO(
        String loanType,
        String loanTypeLabel,
        String repaymentMethod,
        String repaymentMethodLabel,
        BigDecimal totalPrice,
        BigDecimal downPayment,
        BigDecimal downPaymentRatio,
        BigDecimal loanAmount,
        Integer termYears,
        Integer months,
        BigDecimal annualRate,
        BigDecimal annualRatePercent,
        BigDecimal monthlyRate,
        BigDecimal firstMonthPayment,
        BigDecimal lastMonthPayment,
        BigDecimal totalInterest,
        BigDecimal totalRepayment,
        List<YearSummaryVO> yearlySummary,
        List<String> assumptions,
        String rateSource,
        LocalDate rateEffectiveDate,
        Instant calculatedAt,
        String disclaimer
) {

    public record YearSummaryVO(
            int year,
            BigDecimal principal,
            BigDecimal interest,
            BigDecimal endingBalance
    ) {
    }
}
