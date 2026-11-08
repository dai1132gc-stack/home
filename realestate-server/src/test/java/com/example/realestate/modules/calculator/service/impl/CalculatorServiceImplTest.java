package com.example.realestate.modules.calculator.service.impl;

import com.example.realestate.common.enums.ErrorCode;
import com.example.realestate.common.enums.LoanType;
import com.example.realestate.common.enums.RepaymentMethod;
import com.example.realestate.common.exception.BusinessException;
import com.example.realestate.modules.calculator.mapper.LoanRateConfigMapper;
import com.example.realestate.modules.calculator.mapper.TaxRuleConfigMapper;
import com.example.realestate.modules.calculator.model.dto.MortgageRequest;
import com.example.realestate.modules.calculator.model.dto.TaxRequest;
import com.example.realestate.modules.calculator.model.entity.LoanRateConfig;
import com.example.realestate.modules.calculator.model.entity.TaxRuleConfig;
import com.example.realestate.modules.calculator.model.vo.MortgageResultVO;
import com.example.realestate.modules.calculator.model.vo.TaxResultVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("测算服务：费率回退、参数校验与税费分档")
class CalculatorServiceImplTest {

    @Mock
    private LoanRateConfigMapper loanRateMapper;
    @Mock
    private TaxRuleConfigMapper taxRuleMapper;
    @Mock
    private CalculatorRateLimiter rateLimiter;

    private CalculatorServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CalculatorServiceImpl(loanRateMapper, taxRuleMapper, rateLimiter);
    }

    private LoanRateConfig rate(String type, int years, String value) {
        LoanRateConfig config = new LoanRateConfig();
        config.setLoanType(type);
        config.setTermYears(years);
        config.setRate(new BigDecimal(value));
        config.setEffectiveDate(LocalDate.of(2026, 1, 1));
        config.setSource("示例费率（非实时，仅演示）");
        config.setActive(1);
        return config;
    }

    private TaxRuleConfig rule(String type, String rate, String quick) {
        TaxRuleConfig config = new TaxRuleConfig();
        config.setCity("上海");
        config.setRuleType(type);
        config.setMinAmount(BigDecimal.ZERO);
        config.setMaxAmount(null);
        config.setRate(new BigDecimal(rate));
        config.setQuickDeduction(new BigDecimal(quick));
        config.setEffectiveDate(LocalDate.of(2026, 1, 1));
        config.setSource("示例规则（非实时政策）");
        config.setActive(1);
        return config;
    }

    @Test
    @DisplayName("未传利率时读取配置费率并标注来源与生效日期")
    void mortgageUsesConfiguredRate() {
        when(loanRateMapper.selectLatestRate(eq("COMMERCIAL"), eq(30), any()))
                .thenReturn(rate("COMMERCIAL", 30, "0.0385"));

        MortgageResultVO result = service.mortgage(new MortgageRequest(LoanType.COMMERCIAL,
                RepaymentMethod.EQUAL_INSTALLMENT, new BigDecimal("1000000"),
                new BigDecimal("300000"), 30, null), "127.0.0.1");

        assertThat(result.loanAmount()).isEqualByComparingTo("700000");
        assertThat(result.firstMonthPayment()).isEqualByComparingTo("3281.66");
        assertThat(result.annualRatePercent()).isEqualByComparingTo("3.85");
        assertThat(result.rateSource()).isEqualTo("示例费率（非实时，仅演示）");
        assertThat(result.rateEffectiveDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(result.yearlySummary()).hasSize(30);
        assertThat(result.assumptions()).isNotEmpty();
        assertThat(result.disclaimer()).contains("仅供参考");
        verify(rateLimiter).check("127.0.0.1");
    }

    @Test
    @DisplayName("用户手工输入零利率：月供=本金/期数，来源标注用户输入")
    void mortgageWithUserZeroRate() {
        MortgageResultVO result = service.mortgage(new MortgageRequest(LoanType.FUND,
                RepaymentMethod.EQUAL_INSTALLMENT, new BigDecimal("120000"),
                BigDecimal.ZERO, 1, BigDecimal.ZERO), "127.0.0.1");

        assertThat(result.firstMonthPayment()).isEqualByComparingTo("10000.00");
        assertThat(result.totalInterest()).isEqualByComparingTo("0.00");
        assertThat(result.rateSource()).isEqualTo("用户手工输入");
        assertThat(result.rateEffectiveDate()).isNull();
    }

    @Test
    @DisplayName("首付大于总价返回 400")
    void downPaymentTooLarge() {
        assertThatThrownBy(() -> service.mortgage(new MortgageRequest(LoanType.COMMERCIAL,
                RepaymentMethod.EQUAL_INSTALLMENT, new BigDecimal("100000"),
                new BigDecimal("100001"), 10, null), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    @DisplayName("无匹配费率返回 400，提示手工输入")
    void missingRateConfig() {
        when(loanRateMapper.selectLatestRate(any(), anyInt(), any())).thenReturn(null);

        assertThatThrownBy(() -> service.mortgage(new MortgageRequest(LoanType.COMMERCIAL,
                RepaymentMethod.EQUAL_PRINCIPAL, new BigDecimal("500000"),
                new BigDecimal("100000"), 25, null), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未找到")
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    @DisplayName("税费逐项分档：契税 15000 + 增值税 0 + 个税 10000")
    void taxByRules() {
        when(taxRuleMapper.selectMatchingRule(eq("上海"), eq("DEED_TAX"), any(), any()))
                .thenReturn(rule("DEED_TAX", "0.0150", "0"));
        when(taxRuleMapper.selectMatchingRule(eq("上海"), eq("VAT"), any(), any()))
                .thenReturn(rule("VAT", "0.0000", "0"));
        when(taxRuleMapper.selectMatchingRule(eq("上海"), eq("INCOME_TAX"), any(), any()))
                .thenReturn(rule("INCOME_TAX", "0.0100", "0"));

        TaxResultVO result = service.tax(new TaxRequest(" 上海 ", new BigDecimal("1000000"), null, true),
                "127.0.0.1");

        assertThat(result.city()).isEqualTo("上海");
        assertThat(result.items()).hasSize(3);
        assertThat(result.items().get(0).amount()).isEqualByComparingTo("15000.00");
        assertThat(result.items().get(1).amount()).isEqualByComparingTo("0.00");
        assertThat(result.items().get(2).amount()).isEqualByComparingTo("10000.00");
        assertThat(result.totalTax()).isEqualByComparingTo("25000.00");
    }

    @Test
    @DisplayName("速算扣除数参与计算，金额不为负")
    void taxQuickDeduction() {
        when(taxRuleMapper.selectMatchingRule(eq("上海"), eq("DEED_TAX"), any(), any()))
                .thenReturn(rule("DEED_TAX", "0.0300", "5000"));
        when(taxRuleMapper.selectMatchingRule(eq("上海"), eq("VAT"), any(), any()))
                .thenReturn(rule("VAT", "0.0000", "0"));
        when(taxRuleMapper.selectMatchingRule(eq("上海"), eq("INCOME_TAX"), any(), any()))
                .thenReturn(rule("INCOME_TAX", "0.0100", "0"));

        TaxResultVO result = service.tax(new TaxRequest("上海", new BigDecimal("1000000"), null, null),
                "127.0.0.1");
        assertThat(result.items().get(0).amount()).isEqualByComparingTo("25000.00");
    }

    @Test
    @DisplayName("无匹配税费规则返回 400，不猜测费率")
    void missingTaxRule() {
        when(taxRuleMapper.selectMatchingRule(eq("上海"), eq("DEED_TAX"), any(), any()))
                .thenReturn(rule("DEED_TAX", "0.0100", "0"));
        when(taxRuleMapper.selectMatchingRule(eq("上海"), eq("VAT"), any(), any())).thenReturn(null);

        assertThatThrownBy(() -> service.tax(new TaxRequest("上海", new BigDecimal("500000"), null, null),
                "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("增值税")
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }
}
