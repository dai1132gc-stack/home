import { get, post } from '@/utils/request'

import type {
  CalculatorConfig,
  MortgagePayload,
  MortgageResult,
  TaxPayload,
  TaxResult,
} from '@/types/calculator'

export function getCalculatorConfig(): Promise<CalculatorConfig> {
  return get<CalculatorConfig>('/calculators/config')
}

export function calculateMortgage(payload: MortgagePayload): Promise<MortgageResult> {
  return post<MortgageResult>('/calculators/mortgage', payload)
}

export function calculateTax(payload: TaxPayload): Promise<TaxResult> {
  return post<TaxResult>('/calculators/tax', payload)
}
