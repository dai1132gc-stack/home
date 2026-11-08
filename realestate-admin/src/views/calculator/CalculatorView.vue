<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { calculateMortgage, calculateTax, getCalculatorConfig } from '@/api/calculator'
import type {
  CalculatorConfig,
  LoanType,
  MortgageResult,
  RepaymentMethod,
  TaxResult,
} from '@/types/calculator'
import { LOAN_TYPE_OPTIONS, REPAYMENT_METHOD_OPTIONS } from '@/types/calculator'
import { formatDateTime, formatMoney } from '@/utils/format'
import { ApiError } from '@/utils/request'

const activeTab = ref<'mortgage' | 'tax'>('mortgage')
const config = ref<CalculatorConfig | null>(null)
const configLoading = ref(false)
const mortgageLoading = ref(false)
const taxLoading = ref(false)
const mortgageResult = ref<MortgageResult | null>(null)
const taxResult = ref<TaxResult | null>(null)

const mortgageForm = reactive({
  loanType: 'COMMERCIAL' as LoanType,
  repaymentMethod: 'EQUAL_INSTALLMENT' as RepaymentMethod,
  totalPrice: 1000000,
  downPayment: 300000,
  termYears: 30,
  annualRatePercent: undefined as number | undefined,
})

const taxForm = reactive({
  city: '上海',
  price: 1000000,
  area: undefined as number | undefined,
  firstHome: true,
})

const cities = computed(() => {
  const set = new Set((config.value?.taxRules ?? []).map((item) => item.city))
  return Array.from(set)
})

const loanAmountPreview = computed(() => Math.max(0, mortgageForm.totalPrice - mortgageForm.downPayment))

const configuredRate = computed(() => {
  const found = config.value?.rates.find(
    (item) => item.loanType === mortgageForm.loanType && item.termYears === mortgageForm.termYears,
  )
  return found ?? null
})

const taxRuleHints = computed(() =>
  (config.value?.taxRules ?? []).filter((item) => item.city === taxForm.city),
)

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof ApiError ? error.message : fallback
}

function percentText(value: number | null | undefined): string {
  return `${Number(value ?? 0).toFixed(2)}%`
}

async function loadConfig(): Promise<void> {
  configLoading.value = true
  try {
    config.value = await getCalculatorConfig()
    if (cities.value.length > 0 && !cities.value.includes(taxForm.city)) {
      taxForm.city = cities.value[0]
    }
  } catch (error) {
    ElMessage.error(errorMessage(error, '加载测算配置失败'))
  } finally {
    configLoading.value = false
  }
}

async function submitMortgage(): Promise<void> {
  if (mortgageForm.downPayment > mortgageForm.totalPrice) {
    ElMessage.warning('首付不能大于房屋总价')
    return
  }
  mortgageLoading.value = true
  try {
    mortgageResult.value = await calculateMortgage({
      loanType: mortgageForm.loanType,
      repaymentMethod: mortgageForm.repaymentMethod,
      totalPrice: mortgageForm.totalPrice,
      downPayment: mortgageForm.downPayment,
      termYears: mortgageForm.termYears,
      annualRate:
        mortgageForm.annualRatePercent == null
          ? undefined
          : Number((mortgageForm.annualRatePercent / 100).toFixed(6)),
    })
  } catch (error) {
    ElMessage.error(errorMessage(error, '房贷测算失败'))
  } finally {
    mortgageLoading.value = false
  }
}

async function submitTax(): Promise<void> {
  taxLoading.value = true
  try {
    taxResult.value = await calculateTax({
      city: taxForm.city,
      price: taxForm.price,
      area: taxForm.area,
      firstHome: taxForm.firstHome,
    })
  } catch (error) {
    ElMessage.error(errorMessage(error, '税费测算失败'))
  } finally {
    taxLoading.value = false
  }
}

onMounted(loadConfig)
</script>

<template>
  <div class="page">
    <el-alert
      type="warning"
      :closable="false"
      show-icon
      title="测算结果仅供参考，不构成贷款审批、报价或税务意见；示例费率与规则非实时政策。"
    />

    <el-card shadow="never" v-loading="configLoading">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="房贷测算" name="mortgage">
          <div class="pane">
            <el-card shadow="never" class="form-card">
              <template #header><span>贷款参数</span></template>
              <el-form label-width="100px" @submit.prevent="submitMortgage">
                <el-form-item label="贷款类型">
                  <el-radio-group v-model="mortgageForm.loanType">
                    <el-radio-button v-for="item in LOAN_TYPE_OPTIONS" :key="item.value" :value="item.value">
                      {{ item.label }}
                    </el-radio-button>
                  </el-radio-group>
                </el-form-item>
                <el-form-item label="还款方式">
                  <el-radio-group v-model="mortgageForm.repaymentMethod">
                    <el-radio-button v-for="item in REPAYMENT_METHOD_OPTIONS" :key="item.value" :value="item.value">
                      {{ item.label }}
                    </el-radio-button>
                  </el-radio-group>
                </el-form-item>
                <el-form-item label="房屋总价">
                  <el-input-number v-model="mortgageForm.totalPrice" :min="10000" :max="100000000" :step="10000" style="width: 100%" />
                </el-form-item>
                <el-form-item label="首付金额">
                  <el-input-number v-model="mortgageForm.downPayment" :min="0" :max="mortgageForm.totalPrice" :step="10000" style="width: 100%" />
                  <div class="muted">贷款金额约 {{ formatMoney(loanAmountPreview) }}</div>
                </el-form-item>
                <el-form-item label="贷款年限">
                  <el-select v-model="mortgageForm.termYears" style="width: 100%">
                    <el-option v-for="year in [5, 10, 15, 20, 25, 30]" :key="year" :label="`${year} 年（${year * 12} 期）`" :value="year" />
                  </el-select>
                </el-form-item>
                <el-form-item label="年利率">
                  <el-input-number
                    v-model="mortgageForm.annualRatePercent"
                    :min="0"
                    :max="24"
                    :precision="2"
                    :step="0.05"
                    placeholder="留空使用配置费率"
                    style="width: 100%"
                  />
                  <div class="muted">
                    <template v-if="configuredRate">
                      配置费率 {{ percentText(configuredRate.ratePercent) }}（{{ configuredRate.source }}，生效
                      {{ configuredRate.effectiveDate }}）
                    </template>
                    <template v-else>该类型与年限暂无配置费率，请手工输入</template>
                  </div>
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" :loading="mortgageLoading" @click="submitMortgage">开始测算</el-button>
                </el-form-item>
              </el-form>
            </el-card>

            <el-card v-if="mortgageResult" shadow="never" class="result-card">
              <template #header>
                <div class="card-header">
                  <span>测算结果</span>
                  <span class="muted">{{ formatDateTime(mortgageResult.calculatedAt) }}</span>
                </div>
              </template>

              <div class="stat-grid">
                <div class="stat">
                  <div class="stat-label">贷款金额</div>
                  <div class="stat-value">{{ formatMoney(mortgageResult.loanAmount) }}</div>
                </div>
                <div class="stat">
                  <div class="stat-label">首月还款</div>
                  <div class="stat-value primary">{{ formatMoney(mortgageResult.firstMonthPayment) }}</div>
                </div>
                <div class="stat">
                  <div class="stat-label">总利息</div>
                  <div class="stat-value">{{ formatMoney(mortgageResult.totalInterest) }}</div>
                </div>
                <div class="stat">
                  <div class="stat-label">总还款</div>
                  <div class="stat-value">{{ formatMoney(mortgageResult.totalRepayment) }}</div>
                </div>
              </div>

              <el-descriptions :column="3" border size="small" class="details">
                <el-descriptions-item label="贷款类型">{{ mortgageResult.loanTypeLabel }}</el-descriptions-item>
                <el-descriptions-item label="还款方式">{{ mortgageResult.repaymentMethodLabel }}</el-descriptions-item>
                <el-descriptions-item label="期限">{{ mortgageResult.termYears }} 年 / {{ mortgageResult.months }} 期</el-descriptions-item>
                <el-descriptions-item label="年利率">{{ percentText(mortgageResult.annualRatePercent) }}</el-descriptions-item>
                <el-descriptions-item label="首付比例">{{ percentText(mortgageResult.downPaymentRatio) }}</el-descriptions-item>
                <el-descriptions-item label="末月还款">{{ formatMoney(mortgageResult.lastMonthPayment) }}</el-descriptions-item>
                <el-descriptions-item label="费率来源" :span="2">{{ mortgageResult.rateSource }}</el-descriptions-item>
                <el-descriptions-item label="费率生效日期">{{ mortgageResult.rateEffectiveDate || '用户输入' }}</el-descriptions-item>
              </el-descriptions>

              <div class="block-title">按年汇总</div>
              <el-table :data="mortgageResult.yearlySummary" border size="small" max-height="300">
                <el-table-column prop="year" label="第 N 年" width="90" align="center" />
                <el-table-column label="年还本金" align="right">
                  <template #default="{ row }">{{ formatMoney(row.principal) }}</template>
                </el-table-column>
                <el-table-column label="年还利息" align="right">
                  <template #default="{ row }">{{ formatMoney(row.interest) }}</template>
                </el-table-column>
                <el-table-column label="年末剩余本金" align="right">
                  <template #default="{ row }">{{ formatMoney(row.endingBalance) }}</template>
                </el-table-column>
              </el-table>

              <div class="block-title">假设与说明</div>
              <ul class="assumptions">
                <li v-for="(item, index) in mortgageResult.assumptions" :key="index">{{ item }}</li>
              </ul>
              <div class="disclaimer">{{ mortgageResult.disclaimer }}</div>
            </el-card>
          </div>
        </el-tab-pane>

        <el-tab-pane label="税费测算" name="tax">
          <div class="pane">
            <el-card shadow="never" class="form-card">
              <template #header><span>交易信息</span></template>
              <el-form label-width="100px" @submit.prevent="submitTax">
                <el-form-item label="城市">
                  <el-select v-model="taxForm.city" filterable allow-create default-first-option style="width: 100%">
                    <el-option v-for="city in cities" :key="city" :label="city" :value="city" />
                  </el-select>
                  <div class="muted">仅展示配置中存在的城市规则；无匹配返回 400，不做猜测。</div>
                </el-form-item>
                <el-form-item label="成交价">
                  <el-input-number v-model="taxForm.price" :min="0.01" :max="1000000000" :step="10000" style="width: 100%" />
                </el-form-item>
                <el-form-item label="建筑面积">
                  <el-input-number v-model="taxForm.area" :min="1" :max="10000" :precision="2" placeholder="可选" style="width: 100%" />
                </el-form-item>
                <el-form-item label="是否首套">
                  <el-switch v-model="taxForm.firstHome" />
                  <span class="muted switch-hint">仅随结果展示，当前规则未按套数区分</span>
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" :loading="taxLoading" @click="submitTax">开始测算</el-button>
                </el-form-item>
              </el-form>

              <div v-if="taxRuleHints.length > 0" class="rule-hints">
                <div class="block-title">当前城市生效规则</div>
                <div v-for="rule in taxRuleHints" :key="`${rule.ruleType}-${rule.minAmount}`" class="rule-line">
                  {{ rule.ruleTypeLabel }}：{{ formatMoney(rule.minAmount) }} 起
                  {{ rule.maxAmount == null ? '（无上限）' : `至 ${formatMoney(rule.maxAmount)}` }}，税率
                  {{ percentText(rule.ratePercent) }}，扣除 {{ formatMoney(rule.quickDeduction) }}
                </div>
              </div>
            </el-card>

            <el-card v-if="taxResult" shadow="never" class="result-card">
              <template #header>
                <div class="card-header">
                  <span>税费明细（{{ taxResult.city }}）</span>
                  <span class="muted">{{ formatDateTime(taxResult.calculatedAt) }}</span>
                </div>
              </template>

              <div class="stat-grid">
                <div class="stat">
                  <div class="stat-label">成交价</div>
                  <div class="stat-value">{{ formatMoney(taxResult.price) }}</div>
                </div>
                <div class="stat">
                  <div class="stat-label">税费合计</div>
                  <div class="stat-value primary">{{ formatMoney(taxResult.totalTax) }}</div>
                </div>
              </div>

              <el-table :data="taxResult.items" border size="small">
                <el-table-column prop="taxTypeLabel" label="税种" width="100" />
                <el-table-column label="计税基数" align="right">
                  <template #default="{ row }">{{ formatMoney(row.base) }}</template>
                </el-table-column>
                <el-table-column label="税率" width="90" align="right">
                  <template #default="{ row }">{{ percentText(row.ratePercent) }}</template>
                </el-table-column>
                <el-table-column label="速算扣除" align="right">
                  <template #default="{ row }">{{ formatMoney(row.quickDeduction) }}</template>
                </el-table-column>
                <el-table-column label="金额" align="right">
                  <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
                </el-table-column>
                <el-table-column label="来源 / 生效日期" min-width="200">
                  <template #default="{ row }">{{ row.source }}（{{ row.effectiveDate }}）</template>
                </el-table-column>
              </el-table>

              <div class="block-title">假设与说明</div>
              <ul class="assumptions">
                <li v-for="(item, index) in taxResult.assumptions" :key="index">{{ item }}</li>
              </ul>
              <div class="disclaimer">{{ taxResult.disclaimer }}</div>
            </el-card>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<style scoped>
.page {
  display: grid;
  gap: 16px;
}
.pane {
  display: grid;
  grid-template-columns: 380px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}
.form-card :deep(.el-form-item) {
  margin-bottom: 18px;
}
.result-card {
  min-width: 0;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}
.stat {
  padding: 12px 14px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
}
.stat-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 6px;
}
.stat-value {
  font-size: 18px;
  font-weight: 600;
}
.stat-value.primary {
  color: var(--el-color-danger);
}
.details {
  margin-bottom: 4px;
}
.block-title {
  margin: 18px 0 8px;
  font-weight: 600;
  font-size: 14px;
}
.assumptions {
  margin: 0;
  padding-left: 18px;
  color: var(--el-text-color-regular);
  line-height: 1.8;
  font-size: 13px;
}
.disclaimer {
  margin-top: 12px;
  padding: 8px 12px;
  border-radius: 6px;
  background: var(--el-fill-color-lighter);
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.7;
}
.muted {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  line-height: 1.6;
}
.switch-hint {
  margin-left: 10px;
}
.rule-hints {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed var(--el-border-color-lighter);
}
.rule-line {
  font-size: 12px;
  color: var(--el-text-color-regular);
  line-height: 1.8;
}
@media (max-width: 1280px) {
  .pane {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
