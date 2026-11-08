package com.example.realestate.modules.calculator.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** 交易税费规则：按城市 + 税种 + 金额区间分档，版本化。 */
@Data
@NoArgsConstructor
@TableName("tax_rule_config")
public class TaxRuleConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String city;

    private String ruleType;

    private BigDecimal minAmount;

    private BigDecimal maxAmount;

    private BigDecimal rate;

    private BigDecimal quickDeduction;

    private LocalDate effectiveDate;

    private String source;

    private Integer active;

    private Instant createdAt;
}
