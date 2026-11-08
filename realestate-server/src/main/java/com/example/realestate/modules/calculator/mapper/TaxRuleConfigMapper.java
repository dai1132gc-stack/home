package com.example.realestate.modules.calculator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.realestate.modules.calculator.model.entity.TaxRuleConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Mapper
public interface TaxRuleConfigMapper extends BaseMapper<TaxRuleConfig> {

    /** 生效中的全部税费规则（配置展示，按城市/税种/区间排序） */
    List<TaxRuleConfig> selectActiveRules(@Param("today") LocalDate today);

    /**
     * 命中城市+税种+金额区间的最新生效规则：min_amount <= price < max_amount（max 为空表示无上限）。
     * 无匹配返回 null，由 Service 返回 400 而不是猜测费率。
     */
    TaxRuleConfig selectMatchingRule(@Param("city") String city,
                                     @Param("ruleType") String ruleType,
                                     @Param("amount") BigDecimal amount,
                                     @Param("today") LocalDate today);
}
