package com.example.realestate.modules.calculator.service.impl;

import com.example.realestate.common.enums.ErrorCode;
import com.example.realestate.common.enums.LoanType;
import com.example.realestate.common.enums.RepaymentMethod;
import com.example.realestate.common.enums.TaxType;
import com.example.realestate.common.exception.BusinessException;
import com.example.realestate.modules.calculator.calc.MortgageCalculator;
import com.example.realestate.modules.calculator.mapper.LoanRateConfigMapper;
import com.example.realestate.modules.calculator.mapper.TaxRuleConfigMapper;
import com.example.realestate.modules.calculator.model.dto.MortgageRequest;
import com.example.realestate.modules.calculator.model.dto.TaxRequest;
import com.example.realestate.modules.calculator.model.entity.LoanRateConfig;
import com.example.realestate.modules.calculator.model.entity.TaxRuleConfig;
import com.example.realestate.modules.calculator.model.vo.CalculatorConfigVO;
import com.example.realestate.modules.calculator.model.vo.MortgageResultVO;
import com.example.realestate.modules.calculator.model.vo.TaxResultVO;
import com.example.realestate.modules.calculator.service.CalculatorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 测算实现（公开接口）：
 *  - 房贷：等额本息/等额本金，BigDecimal + HALF_UP，末期吸收尾差；
 *  - 税费：按城市/税种/金额区间逐项匹配配置，未命中 400，不猜测；
 *  - 结果统一附假设、来源、生效日期、测算日期与免责声明。
 */
@Service
public class CalculatorServiceImpl implements CalculatorService {

    private static final Logger log = LoggerFactory.getLogger(CalculatorServiceImpl.class);
    private static final String DISCLAIMER =
            "测算结果仅供参考，不构成贷款审批、报价或税务意见；实际利率、税费以经办机构与主管部门最新规定为准。";

    private final LoanRateConfigMapper loanRateMapper;
    private final TaxRuleConfigMapper taxRuleMapper;
    private final CalculatorRateLimiter rateLimiter;

    public CalculatorServiceImpl(LoanRateConfigMapper loanRateMapper,
                                 TaxRuleConfigMapper taxRuleMapper,
                                 CalculatorRateLimiter rateLimiter) {
        this.loanRateMapper = loanRateMapper;
        this.taxRuleMapper = taxRuleMapper;
        this.rateLimiter = rateLimiter;
    }

    @Override
    public MortgageResultVO mortgage(MortgageRequest request, String clientIp) {
        rateLimiter.check(clientIp);
        BigDecimal totalPrice = request.totalPrice().setScale(2, RoundingMode.HALF_UP);
        BigDecimal downPayment = request.downPayment().setScale(2, RoundingMode.HALF_UP);
        if (downPayment.compareTo(totalPrice) > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "首付不能大于房屋总价");
        }
        BigDecimal loanAmount = totalPrice.subtract(downPayment);
        int termYears = request.termYears();
        int months = termYears * 12;

        BigDecimal annualRate;
        String rateSource;
        LocalDate rateEffectiveDate;
        if (request.annualRate() != null) {
            annualRate = request.annualRate();
            rateSource = "用户手工输入";
            rateEffectiveDate = null;
        } else {
            LoanRateConfig config = loanRateMapper.selectLatestRate(
                    request.loanType().name(), termYears, LocalDate.now());
            if (config == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "未找到 " + request.loanType().label() + " " + termYears + " 年期的生效费率，请手工输入年利率");
            }
            annualRate = config.getRate();
            rateSource = config.getSource();
            rateEffectiveDate = config.getEffectiveDate();
        }

        MortgageCalculator.Result result = MortgageCalculator.calculate(
                loanAmount, annualRate, termYears, request.repaymentMethod());

        BigDecimal downPaymentRatio = totalPrice.signum() == 0 ? BigDecimal.ZERO.setScale(2)
                : downPayment.multiply(BigDecimal.valueOf(100))
                        .divide(totalPrice, 2, RoundingMode.HALF_UP);

        List<MortgageCalculator.YearSummary> yearly = result.yearlySummary();
        List<MortgageResultVO.YearSummaryVO> yearlyVO = yearly.stream()
                .map(y -> new MortgageResultVO.YearSummaryVO(y.year(), y.principal(), y.interest(),
                        y.endingBalance()))
                .toList();

        List<String> assumptions = new ArrayList<>();
        assumptions.add("贷款本金 = 房屋总价 " + plain(totalPrice) + " 元 − 首付 " + plain(downPayment) + " 元");
        assumptions.add("年利率 " + percent(annualRate) + "%，月利率 = 年利率 / 12");
        assumptions.add(request.repaymentMethod() == RepaymentMethod.EQUAL_INSTALLMENT
                ? "等额本息：每月还款额四舍五入到分，末期本金取剩余本金以吸收舍入尾差"
                : "等额本金：每月本金 = 贷款本金 / 期数，末期取剩余本金，本金合计精确等于贷款额");
        assumptions.add("按月计息，未考虑提前还款、逾期罚息、保险、评估与担保费用");
        if (request.annualRate() != null) {
            assumptions.add("年利率由用户手工输入，非配置费率");
        }

        MortgageResultVO vo = new MortgageResultVO(
                request.loanType().name(), request.loanType().label(),
                request.repaymentMethod().name(), request.repaymentMethod().label(),
                totalPrice, downPayment, downPaymentRatio, result.loanAmount(),
                termYears, months,
                annualRate, percent(annualRate), result.monthlyRate(),
                result.firstMonthPayment(), result.lastMonthPayment(),
                result.totalInterest(), result.totalRepayment(),
                yearlyVO, assumptions, rateSource, rateEffectiveDate, Instant.now(), DISCLAIMER);
        log.info("房贷测算 loanType={} method={} years={} amount={} rate={} source={}",
                request.loanType(), request.repaymentMethod(), termYears, loanAmount, annualRate, rateSource);
        return vo;
    }

    @Override
    public TaxResultVO tax(TaxRequest request, String clientIp) {
        rateLimiter.check(clientIp);
        String city = request.city().trim();
        BigDecimal price = request.price().setScale(2, RoundingMode.HALF_UP);
        LocalDate today = LocalDate.now();
        List<TaxResultVO.TaxItemVO> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO.setScale(2);

        for (TaxType type : TaxType.values()) {
            TaxRuleConfig rule = taxRuleMapper.selectMatchingRule(city, type.name(), price, today);
            if (rule == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "未找到「" + city + "」" + type.label() + "在金额 " + plain(price)
                                + " 元区间的生效规则，请检查税费配置");
            }
            BigDecimal amount = price.multiply(rule.getRate())
                    .subtract(rule.getQuickDeduction() == null ? BigDecimal.ZERO : rule.getQuickDeduction())
                    .setScale(2, RoundingMode.HALF_UP);
            if (amount.signum() < 0) {
                amount = BigDecimal.ZERO.setScale(2);
            }
            total = total.add(amount);
            items.add(new TaxResultVO.TaxItemVO(
                    type.name(), type.label(), price, rule.getRate(), percent(rule.getRate()),
                    rule.getQuickDeduction() == null ? BigDecimal.ZERO.setScale(2) : rule.getQuickDeduction(),
                    amount, rule.getSource(), rule.getEffectiveDate()));
        }

        List<String> assumptions = new ArrayList<>();
        assumptions.add("税费以成交价 " + plain(price) + " 元为基数，按「" + city + "」生效规则分档计算");
        assumptions.add("未考虑家庭住房套数、房屋面积、持有年限等减免或差别化政策");
        if (request.area() != null) {
            assumptions.add("建筑面积 " + plain(request.area()) + "㎡ 仅随结果展示，未参与当前规则匹配");
        }
        if (request.firstHome() != null) {
            assumptions.add("是否首套：" + (request.firstHome() ? "是" : "否") + "（仅随结果展示，未参与当前规则匹配）");
        }

        TaxResultVO vo = new TaxResultVO(city, price, request.area(), request.firstHome(),
                items, total, assumptions, Instant.now(), DISCLAIMER);
        log.info("税费测算 city={} price={} totalTax={} items={}", city, price, total, items.size());
        return vo;
    }

    @Override
    public CalculatorConfigVO config() {
        LocalDate today = LocalDate.now();
        List<CalculatorConfigVO.LoanRateVO> rates = loanRateMapper.selectActiveRates(today).stream()
                .map(r -> new CalculatorConfigVO.LoanRateVO(
                        r.getLoanType(), loanTypeLabel(r.getLoanType()), r.getTermYears(),
                        r.getRate(), percent(r.getRate()), r.getEffectiveDate(), r.getSource()))
                .toList();
        List<CalculatorConfigVO.TaxRuleVO> taxRules = taxRuleMapper.selectActiveRules(today).stream()
                .map(r -> new CalculatorConfigVO.TaxRuleVO(
                        r.getCity(), r.getRuleType(), taxTypeLabel(r.getRuleType()),
                        r.getMinAmount(), r.getMaxAmount(), r.getRate(), percent(r.getRate()),
                        r.getQuickDeduction(), r.getEffectiveDate(), r.getSource()))
                .toList();
        return new CalculatorConfigVO(rates, taxRules, Instant.now(), DISCLAIMER);
    }

    private String loanTypeLabel(String value) {
        LoanType type = LoanType.of(value);
        return type == null ? value : type.label();
    }

    private String taxTypeLabel(String value) {
        TaxType type = TaxType.of(value);
        return type == null ? value : type.label();
    }

    private BigDecimal percent(BigDecimal rate) {
        return rate.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
    }

    private String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
