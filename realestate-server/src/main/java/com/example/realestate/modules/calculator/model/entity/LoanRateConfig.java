package com.example.realestate.modules.calculator.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** 贷款利率配置：版本化，按 loan_type + term_years + effective_date 唯一。 */
@Data
@NoArgsConstructor
@TableName("loan_rate_config")
public class LoanRateConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String loanType;

    private Integer termYears;

    private BigDecimal rate;

    private LocalDate effectiveDate;

    private String source;

    private Integer active;

    private Instant createdAt;
}
