package com.example.realestate.modules.calculator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.realestate.modules.calculator.model.entity.LoanRateConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface LoanRateConfigMapper extends BaseMapper<LoanRateConfig> {

    /** 生效中的全部费率（配置展示） */
    List<LoanRateConfig> selectActiveRates(@Param("today") LocalDate today);

    /** 命中指定类型+年限的最新生效费率；无匹配返回 null */
    LoanRateConfig selectLatestRate(@Param("loanType") String loanType,
                                    @Param("termYears") int termYears,
                                    @Param("today") LocalDate today);
}
