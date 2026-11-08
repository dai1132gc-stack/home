package com.example.realestate.modules.calculator.calc;

import com.example.realestate.common.enums.RepaymentMethod;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 房贷纯计算器（不依赖 Spring / 数据库），便于确定性单元测试。
 *
 * 约定：
 *  - 金额一律 BigDecimal、保留 2 位、HALF_UP；
 *  - 月利率 r = 年利率 / 12（10 位中间精度）；
 *  - 等额本息：每月还款额按公式四舍五入到分，末期本金取剩余本金，吸收舍入尾差；
 *  - 等额本金：每月本金 = 贷款额 / 期数（2 位 HALF_UP），末期取剩余本金，保证本金合计精确；
 *  - 总还款 = 贷款本金 + 总利息，不使用“月供 × 期数”避免尾差导致对不上账。
 */
public final class MortgageCalculator {

    private static final int SCALE = 2;
    private static final MathContext MC = MathContext.DECIMAL128;

    private MortgageCalculator() {
    }

    public static Result calculate(BigDecimal principal, BigDecimal annualRate, int years,
                                   RepaymentMethod method) {
        if (years <= 0) {
            throw new IllegalArgumentException("贷款年限必须大于 0");
        }
        return calculateByMonths(principal, annualRate, years * 12, method);
    }

    public static Result calculateByMonths(BigDecimal principal, BigDecimal annualRate, int months,
                                           RepaymentMethod method) {
        if (months <= 0) {
            throw new IllegalArgumentException("还款期数必须大于 0");
        }
        BigDecimal loan = principal == null ? BigDecimal.ZERO : principal;
        if (loan.signum() < 0) {
            throw new IllegalArgumentException("贷款金额不能为负");
        }
        loan = loan.setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal rate = annualRate == null ? BigDecimal.ZERO : annualRate;
        BigDecimal monthlyRate = rate.divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);
        RepaymentMethod safeMethod = method == null ? RepaymentMethod.EQUAL_INSTALLMENT : method;

        if (loan.signum() == 0) {
            return new Result(BigDecimal.ZERO.setScale(SCALE), rate, monthlyRate, months,
                    BigDecimal.ZERO.setScale(SCALE), BigDecimal.ZERO.setScale(SCALE),
                    BigDecimal.ZERO.setScale(SCALE), BigDecimal.ZERO.setScale(SCALE),
                    BigDecimal.ZERO.setScale(SCALE), List.of());
        }

        BigDecimal balance = loan;
        BigDecimal totalInterest = BigDecimal.ZERO.setScale(SCALE);
        BigDecimal firstPayment = null;
        BigDecimal lastPayment = null;
        List<YearSummary> yearly = new ArrayList<>();
        BigDecimal yearPrincipal = BigDecimal.ZERO.setScale(SCALE);
        BigDecimal yearInterest = BigDecimal.ZERO.setScale(SCALE);

        BigDecimal fixedPayment = safeMethod == RepaymentMethod.EQUAL_INSTALLMENT
                ? equalInstallmentPayment(loan, monthlyRate, months) : null;
        BigDecimal fixedPrincipal = safeMethod == RepaymentMethod.EQUAL_PRINCIPAL
                ? loan.divide(BigDecimal.valueOf(months), SCALE, RoundingMode.HALF_UP) : null;

        for (int period = 1; period <= months; period++) {
            boolean last = period == months;
            BigDecimal interest = balance.multiply(monthlyRate).setScale(SCALE, RoundingMode.HALF_UP);
            BigDecimal principalPart;
            if (last) {
                principalPart = balance;
            } else if (safeMethod == RepaymentMethod.EQUAL_INSTALLMENT) {
                principalPart = fixedPayment.subtract(interest);
                if (principalPart.signum() < 0) {
                    // 极端高利率下月供不足以覆盖利息：本金不再减少，由末期兜底
                    principalPart = BigDecimal.ZERO.setScale(SCALE);
                }
                if (principalPart.compareTo(balance) > 0) {
                    principalPart = balance;
                }
            } else {
                principalPart = fixedPrincipal.min(balance);
            }
            BigDecimal payment = principalPart.add(interest);

            balance = balance.subtract(principalPart);
            totalInterest = totalInterest.add(interest);
            yearPrincipal = yearPrincipal.add(principalPart);
            yearInterest = yearInterest.add(interest);
            if (firstPayment == null) {
                firstPayment = payment;
            }
            lastPayment = payment;

            if (last || period % 12 == 0) {
                yearly.add(new YearSummary((period + 11) / 12, yearPrincipal, yearInterest, balance));
                yearPrincipal = BigDecimal.ZERO.setScale(SCALE);
                yearInterest = BigDecimal.ZERO.setScale(SCALE);
            }
        }

        BigDecimal totalRepayment = loan.add(totalInterest);
        return new Result(loan, rate, monthlyRate, months,
                firstPayment == null ? BigDecimal.ZERO.setScale(SCALE) : firstPayment,
                lastPayment == null ? BigDecimal.ZERO.setScale(SCALE) : lastPayment,
                totalInterest, totalRepayment, loan, yearly);
    }

    /**
     * 等额本息月供：P·r·(1+r)^n / ((1+r)^n − 1)；r=0 时按本金/期数。
     */
    private static BigDecimal equalInstallmentPayment(BigDecimal principal, BigDecimal monthlyRate, int months) {
        if (monthlyRate.signum() == 0) {
            return principal.divide(BigDecimal.valueOf(months), SCALE, RoundingMode.HALF_UP);
        }
        BigDecimal factor = BigDecimal.ONE.add(monthlyRate).pow(months, MC);
        return principal.multiply(monthlyRate).multiply(factor)
                .divide(factor.subtract(BigDecimal.ONE), SCALE, RoundingMode.HALF_UP);
    }

    /**
     * @param loanAmount        贷款本金
     * @param annualRate        年利率（小数，如 0.0385）
     * @param monthlyRate       月利率（10 位）
     * @param months            期数
     * @param firstMonthPayment 首月还款
     * @param lastMonthPayment  末月还款（吸收尾差后）
     * @param totalInterest     总利息
     * @param totalRepayment    总还款 = 本金 + 总利息
     * @param totalPrincipal    本金合计（恒等于 loanAmount）
     * @param yearlySummary     按年汇总
     */
    public record Result(
            BigDecimal loanAmount,
            BigDecimal annualRate,
            BigDecimal monthlyRate,
            int months,
            BigDecimal firstMonthPayment,
            BigDecimal lastMonthPayment,
            BigDecimal totalInterest,
            BigDecimal totalRepayment,
            BigDecimal totalPrincipal,
            List<YearSummary> yearlySummary
    ) {
    }

    public record YearSummary(
            int year,
            BigDecimal principal,
            BigDecimal interest,
            BigDecimal endingBalance
    ) {
    }
}
