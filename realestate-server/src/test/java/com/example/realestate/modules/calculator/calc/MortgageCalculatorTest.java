package com.example.realestate.modules.calculator.calc;

import com.example.realestate.common.enums.RepaymentMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("房贷计算器：等额本息/等额本金与边界")
class MortgageCalculatorTest {

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    @Test
    @DisplayName("等额本息已知样例：100 万、4.9%、30 年，月供 5307.27")
    void equalInstallmentKnownSample() {
        MortgageCalculator.Result result = MortgageCalculator.calculate(
                d("1000000"), d("0.049"), 30, RepaymentMethod.EQUAL_INSTALLMENT);

        assertThat(result.months()).isEqualTo(360);
        assertThat(result.firstMonthPayment()).isEqualByComparingTo("5307.27");
        assertThat(result.totalRepayment()).isEqualByComparingTo(
                result.loanAmount().add(result.totalInterest()));
        assertThat(result.totalInterest()).isBetween(d("910600"), d("910650"));
        assertThat(result.yearlySummary()).hasSize(30);
        assertThat(result.yearlySummary().get(29).endingBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("零利率：月供 = 本金/期数，总利息为 0")
    void zeroRate() {
        MortgageCalculator.Result result = MortgageCalculator.calculate(
                d("120000"), BigDecimal.ZERO, 1, RepaymentMethod.EQUAL_INSTALLMENT);

        assertThat(result.months()).isEqualTo(12);
        assertThat(result.firstMonthPayment()).isEqualByComparingTo("10000.00");
        assertThat(result.lastMonthPayment()).isEqualByComparingTo("10000.00");
        assertThat(result.totalInterest()).isEqualByComparingTo("0.00");
        assertThat(result.totalRepayment()).isEqualByComparingTo("120000.00");
        assertThat(result.yearlySummary()).hasSize(1);
    }

    @Test
    @DisplayName("1 期边界：首末期相同，利息 = 本金×月利率")
    void onePeriod() {
        MortgageCalculator.Result result = MortgageCalculator.calculateByMonths(
                d("100000"), d("0.12"), 1, RepaymentMethod.EQUAL_INSTALLMENT);

        assertThat(result.months()).isEqualTo(1);
        assertThat(result.firstMonthPayment()).isEqualByComparingTo("101000.00");
        assertThat(result.lastMonthPayment()).isEqualByComparingTo("101000.00");
        assertThat(result.totalInterest()).isEqualByComparingTo("1000.00");
    }

    @Test
    @DisplayName("等额本金：首月 11200、末月 10100、总利息 7800")
    void equalPrincipal() {
        MortgageCalculator.Result result = MortgageCalculator.calculate(
                d("120000"), d("0.12"), 1, RepaymentMethod.EQUAL_PRINCIPAL);

        assertThat(result.firstMonthPayment()).isEqualByComparingTo("11200.00");
        assertThat(result.lastMonthPayment()).isEqualByComparingTo("10100.00");
        assertThat(result.totalInterest()).isEqualByComparingTo("7800.00");
        assertThat(result.totalRepayment()).isEqualByComparingTo("127800.00");
        assertThat(result.totalPrincipal()).isEqualByComparingTo("120000.00");
    }

    @Test
    @DisplayName("末期吸收尾差：本金合计精确等于贷款额（非整年、除不尽）")
    void lastPeriodAbsorbsRounding() {
        for (RepaymentMethod method : RepaymentMethod.values()) {
            MortgageCalculator.Result result = MortgageCalculator.calculateByMonths(
                    d("100000"), d("0.05"), 7, method);

            assertThat(result.totalPrincipal()).isEqualByComparingTo("100000.00");
            assertThat(result.totalRepayment()).isEqualByComparingTo(
                    result.totalPrincipal().add(result.totalInterest()));
            assertThat(result.lastMonthPayment()).isNotNull();
            assertThat(result.yearlySummary()).hasSize(1);
            assertThat(result.yearlySummary().get(0).endingBalance()).isEqualByComparingTo("0.00");
        }
    }

    @Test
    @DisplayName("360 期边界：两种方式本金合计均精确")
    void fullTermBoundary() {
        for (RepaymentMethod method : RepaymentMethod.values()) {
            MortgageCalculator.Result result = MortgageCalculator.calculate(
                    d("888888.88"), d("0.0385"), 30, method);
            assertThat(result.months()).isEqualTo(360);
            assertThat(result.totalPrincipal()).isEqualByComparingTo("888888.88");
            assertThat(result.yearlySummary()).hasSize(30);
        }
    }

    @Test
    @DisplayName("零贷款：所有金额为 0，返回期数")
    void zeroLoan() {
        MortgageCalculator.Result result = MortgageCalculator.calculate(
                BigDecimal.ZERO, d("0.04"), 10, RepaymentMethod.EQUAL_INSTALLMENT);
        assertThat(result.months()).isEqualTo(120);
        assertThat(result.firstMonthPayment()).isEqualByComparingTo("0.00");
        assertThat(result.totalInterest()).isEqualByComparingTo("0.00");
        assertThat(result.yearlySummary()).isEmpty();
    }

    @Test
    @DisplayName("等额本金首月大于末月，利息逐月下降")
    void equalPrincipalDecreases() {
        MortgageCalculator.Result result = MortgageCalculator.calculate(
                d("500000"), d("0.045"), 20, RepaymentMethod.EQUAL_PRINCIPAL);
        assertThat(result.firstMonthPayment()).isGreaterThan(result.lastMonthPayment());
        List<MortgageCalculator.YearSummary> yearly = result.yearlySummary();
        assertThat(yearly.get(0).interest()).isGreaterThan(yearly.get(19).interest());
    }
}
