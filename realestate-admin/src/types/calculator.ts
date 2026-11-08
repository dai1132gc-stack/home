export type LoanType = 'COMMERCIAL' | 'FUND'

export type RepaymentMethod = 'EQUAL_INSTALLMENT' | 'EQUAL_PRINCIPAL'

export type TaxType = 'DEED_TAX' | 'VAT' | 'INCOME_TAX'

export interface LoanRateItem {
  loanType: LoanType
  loanTypeLabel: string
  termYears: number
  rate: number
  ratePercent: number
  effectiveDate: string
  source: string
}

export interface TaxRuleItem {
  city: string
  ruleType: TaxType
  ruleTypeLabel: string
  minAmount: number
  maxAmount: number | null
  rate: number
  ratePercent: number
  quickDeduction: number
  effectiveDate: string
  source: string
}

export interface CalculatorConfig {
  rates: LoanRateItem[]
  taxRules: TaxRuleItem[]
  generatedAt: string
  disclaimer: string
}

export interface MortgagePayload {
  loanType: LoanType
  repaymentMethod: RepaymentMethod
  totalPrice: number
  downPayment: number
  termYears: number
  annualRate?: number
}

export interface YearSummary {
  year: number
  principal: number
  interest: number
  endingBalance: number
}

export interface MortgageResult {
  loanType: LoanType
  loanTypeLabel: string
  repaymentMethod: RepaymentMethod
  repaymentMethodLabel: string
  totalPrice: number
  downPayment: number
  downPaymentRatio: number
  loanAmount: number
  termYears: number
  months: number
  annualRate: number
  annualRatePercent: number
  monthlyRate: number
  firstMonthPayment: number
  lastMonthPayment: number
  totalInterest: number
  totalRepayment: number
  yearlySummary: YearSummary[]
  assumptions: string[]
  rateSource: string
  rateEffectiveDate: string | null
  calculatedAt: string
  disclaimer: string
}

export interface TaxPayload {
  city: string
  price: number
  area?: number
  firstHome?: boolean
}

export interface TaxItem {
  taxType: TaxType
  taxTypeLabel: string
  base: number
  rate: number
  ratePercent: number
  quickDeduction: number
  amount: number
  source: string
  effectiveDate: string
}

export interface TaxResult {
  city: string
  price: number
  area: number | null
  firstHome: boolean | null
  items: TaxItem[]
  totalTax: number
  assumptions: string[]
  calculatedAt: string
  disclaimer: string
}

export const LOAN_TYPE_OPTIONS: Array<{ label: string; value: LoanType }> = [
  { label: '商业贷款', value: 'COMMERCIAL' },
  { label: '公积金贷款', value: 'FUND' },
]

export const REPAYMENT_METHOD_OPTIONS: Array<{ label: string; value: RepaymentMethod }> = [
  { label: '等额本息', value: 'EQUAL_INSTALLMENT' },
  { label: '等额本金', value: 'EQUAL_PRINCIPAL' },
]
