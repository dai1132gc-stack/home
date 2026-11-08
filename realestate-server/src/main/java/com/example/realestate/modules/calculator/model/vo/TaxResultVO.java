package com.example.realestate.modules.calculator.model.vo;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 税费测算结果：逐项列出基数、费率、金额与规则来源，并附假设与免责声明。 */
public record TaxResultVO(
        String city,
        BigDecimal price,
        BigDecimal area,
        Boolean firstHome,
        List<TaxItemVO> items,
        BigDecimal totalTax,
        List<String> assumptions,
        Instant calculatedAt,
        String disclaimer
) {

    public record TaxItemVO(
            String taxType,
            String taxTypeLabel,
            BigDecimal base,
            BigDecimal rate,
            BigDecimal ratePercent,
            BigDecimal quickDeduction,
            BigDecimal amount,
            String source,
            LocalDate effectiveDate
    ) {
    }
}
