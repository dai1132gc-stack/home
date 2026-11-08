<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'

import { createContract } from '@/api/contract'
import { listCustomers } from '@/api/customer'
import { getHouse, listHouses } from '@/api/house'
import type { HouseListItem } from '@/types/house'
import type { ContractCreatePayload, PaymentCycle } from '@/types/contract'
import { PAYMENT_CYCLE_OPTIONS } from '@/types/contract'
import type { CustomerItem } from '@/types/customer'
import { formatMoney } from '@/utils/format'
import { ApiError } from '@/utils/request'

const router = useRouter()
const formRef = ref<FormInstance>()
const submitting = ref(false)
const houses = ref<HouseListItem[]>([])
const customers = ref<CustomerItem[]>([])
const customerLoading = ref(false)

function isoDate(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

const start = new Date()
start.setDate(start.getDate() + 7)
const end = new Date(start)
end.setFullYear(end.getFullYear() + 1)
end.setDate(end.getDate() - 1)

type ContractFormModel = Omit<ContractCreatePayload, 'houseId' | 'customerId'> & {
  houseId?: number
  customerId?: number
}

const form = reactive<ContractFormModel>({
  houseId: undefined,
  customerId: undefined,
  title: '',
  amount: 0,
  deposit: 0,
  paymentCycle: 'MONTHLY' as PaymentCycle,
  startDate: isoDate(start),
  endDate: isoDate(end),
  lessorName: '',
  lessorPhone: '',
  customerName: '',
  customerPhone: '',
  lessorIdCard: '',
  customerIdCard: '',
})

const rules: FormRules = {
  houseId: [{ required: true, message: '请选择房源', trigger: 'change' }],
  customerId: [{ required: true, message: '请选择承租客户', trigger: 'change' }],
  title: [{ required: true, message: '请填写合同标题', trigger: 'blur' }],
  amount: [
    {
      validator: (_rule: unknown, value: number, callback: (error?: Error) => void) => {
        if (!value || value <= 0) callback(new Error('租金必须大于 0'))
        else callback()
      },
      trigger: 'blur',
    },
  ],
  deposit: [{ required: true, message: '请填写押金', trigger: 'blur' }],
  startDate: [{ required: true, message: '请选择租期开始日期', trigger: 'change' }],
  endDate: [{ required: true, message: '请选择租期结束日期', trigger: 'change' }],
}

const selectedHouse = computed(() => houses.value.find((item) => item.id === form.houseId) ?? null)

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof ApiError ? error.message : fallback
}

async function loadHouses(): Promise<void> {
  try {
    // 只有已发布/已预定的房源可起草合同，两种状态分别查询后合并
    const [published, reserved] = await Promise.all([
      listHouses({ page: 1, size: 100, status: 'PUBLISHED' }),
      listHouses({ page: 1, size: 100, status: 'RESERVED' }),
    ])
    houses.value = [...published.items, ...reserved.items]
  } catch (error) {
    ElMessage.error(errorMessage(error, '加载房源失败'))
  }
}

async function searchCustomers(keyword: string): Promise<void> {
  customerLoading.value = true
  try {
    const result = await listCustomers({ page: 1, size: 50, keyword: keyword || undefined })
    customers.value = result.items
  } catch (error) {
    ElMessage.error(errorMessage(error, '加载客户失败'))
  } finally {
    customerLoading.value = false
  }
}

async function onHouseChange(): Promise<void> {
  const house = selectedHouse.value
  if (!house) return
  if (!form.title) {
    form.title = `${house.title} 租赁合同`
  }
  form.amount = Number(house.price)
  try {
    const detail = await getHouse(house.id)
    if (detail.ownerName) form.lessorName = detail.ownerName
    if (detail.ownerPhone) form.lessorPhone = detail.ownerPhone
  } catch {
    // 房东信息缺失不阻塞起草，可在表单中手工补充
  }
}

function onCustomerChange(): void {
  const customer = customers.value.find((item) => item.id === form.customerId)
  if (!customer) return
  form.customerName = customer.nickname || customer.username
  if (customer.phone) form.customerPhone = customer.phone
}

async function submit(): Promise<void> {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (!form.houseId || !form.customerId) {
      ElMessage.warning('请选择房源与承租客户')
      return
    }
    const payload: ContractCreatePayload = {
      ...form,
      houseId: form.houseId,
      customerId: form.customerId,
    }
    if (!payload.lessorPhone) delete payload.lessorPhone
    if (!payload.customerPhone) delete payload.customerPhone
    if (!payload.lessorIdCard) delete payload.lessorIdCard
    if (!payload.customerIdCard) delete payload.customerIdCard
    const created = await createContract(payload)
    ElMessage.success('合同草稿已创建，PDF 已生成')
    router.push({ name: 'contract-detail', params: { id: created.id } })
  } catch (error) {
    ElMessage.error(errorMessage(error, '创建合同失败'))
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadHouses()
  searchCustomers('')
})
</script>

<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <div class="title">
            <el-button link :icon="ArrowLeft" @click="router.back()">返回</el-button>
            <span>起草合同</span>
          </div>
          <el-button type="primary" :loading="submitting" @click="submit">保存草稿</el-button>
        </div>
      </template>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px" class="form">
        <el-divider content-position="left">房源与客户</el-divider>
        <el-form-item label="房源" prop="houseId">
          <el-select
            v-model="form.houseId"
            filterable
            placeholder="选择已发布或已预定房源"
            style="width: 420px"
            @change="onHouseChange"
          >
            <el-option v-for="house in houses" :key="house.id" :value="house.id"
                       :label="`#${house.id} ${house.title}（${house.community} · ${formatMoney(house.price)}）`" />
          </el-select>
          <span v-if="selectedHouse" class="hint">
            当前状态：{{ selectedHouse.status === 'RESERVED' ? '已预定' : '已发布' }}
          </span>
        </el-form-item>
        <el-form-item label="承租客户" prop="customerId">
          <el-select
            v-model="form.customerId"
            filterable
            remote
            :remote-method="searchCustomers"
            :loading="customerLoading"
            placeholder="按账号/昵称搜索客户"
            style="width: 420px"
            @change="onCustomerChange"
          >
            <el-option v-for="customer in customers" :key="customer.id" :value="customer.id"
                       :label="`${customer.nickname || customer.username}（${customer.username}）`" />
          </el-select>
        </el-form-item>

        <el-divider content-position="left">合同条款</el-divider>
        <el-form-item label="合同标题" prop="title">
          <el-input v-model="form.title" maxlength="200" show-word-limit style="width: 420px" />
        </el-form-item>
        <el-form-item label="租金" prop="amount">
          <el-input-number v-model="form.amount" :min="0" :precision="2" :step="100" style="width: 200px" />
          <span class="hint">元 / 期</span>
        </el-form-item>
        <el-form-item label="押金" prop="deposit">
          <el-input-number v-model="form.deposit" :min="0" :precision="2" :step="100" style="width: 200px" />
          <span class="hint">元</span>
        </el-form-item>
        <el-form-item label="付款周期">
          <el-select v-model="form.paymentCycle" style="width: 200px">
            <el-option v-for="item in PAYMENT_CYCLE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="租期" prop="startDate">
          <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" style="width: 170px" />
          <span class="range-sep">至</span>
          <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" style="width: 170px" />
        </el-form-item>

        <el-divider content-position="left">双方信息（写入合同快照，后续修改不影响历史合同）</el-divider>
        <el-form-item label="出租方姓名">
          <el-input v-model="form.lessorName" maxlength="64" style="width: 240px" />
        </el-form-item>
        <el-form-item label="出租方电话">
          <el-input v-model="form.lessorPhone" maxlength="11" style="width: 240px" />
        </el-form-item>
        <el-form-item label="出租方证件">
          <el-input v-model="form.lessorIdCard" maxlength="32" placeholder="选填，仅保存脱敏值" style="width: 320px" />
        </el-form-item>
        <el-form-item label="承租方姓名">
          <el-input v-model="form.customerName" maxlength="64" style="width: 240px" />
        </el-form-item>
        <el-form-item label="承租方电话">
          <el-input v-model="form.customerPhone" maxlength="11" placeholder="客户未留电话时填写" style="width: 240px" />
        </el-form-item>
        <el-form-item label="承租方证件">
          <el-input v-model="form.customerIdCard" maxlength="32" placeholder="选填，仅保存脱敏值" style="width: 320px" />
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.page {
  display: grid;
  gap: 16px;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.title {
  display: flex;
  align-items: center;
  gap: 8px;
}
.form {
  max-width: 860px;
}
.hint {
  margin-left: 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.range-sep {
  margin: 0 8px;
  color: var(--el-text-color-secondary);
}
</style>
