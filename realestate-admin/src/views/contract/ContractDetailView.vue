<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Download, Document, CircleCheck, Refresh } from '@element-plus/icons-vue'

import {
  doneTransaction,
  fetchContractPdf,
  getContract,
  submitContract,
  terminateContract,
} from '@/api/contract'
import type { ContractDetail, TransactionItem } from '@/types/contract'
import { contractStatusLabel, contractStatusTagType } from '@/types/contract'
import { formatDate, formatDateTime, formatMoney, openBlobInNewTab, saveBlob } from '@/utils/format'
import { ApiError } from '@/utils/request'

const route = useRoute()
const router = useRouter()

const id = computed(() => Number(route.params.id))
const loading = ref(false)
const acting = ref(false)
const detail = ref<ContractDetail | null>(null)

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof ApiError ? error.message : fallback
}

async function load(): Promise<void> {
  loading.value = true
  try {
    detail.value = await getContract(id.value)
  } catch (error) {
    ElMessage.error(errorMessage(error, '加载合同详情失败'))
  } finally {
    loading.value = false
  }
}

async function submit(): Promise<void> {
  if (!detail.value) return
  try {
    await ElMessageBox.confirm('提交后合同进入待签署状态，将占用该房源，确认提交？', '提交待签', {
      type: 'warning',
    })
  } catch {
    return
  }
  acting.value = true
  try {
    detail.value = await submitContract(detail.value.id)
    ElMessage.success('已提交待签')
  } catch (error) {
    ElMessage.error(errorMessage(error, '提交失败'))
  } finally {
    acting.value = false
  }
}

async function terminate(): Promise<void> {
  if (!detail.value) return
  const isDraft = detail.value.status === 'DRAFT'
  try {
    const { value } = await ElMessageBox.prompt(
      isDraft ? '作废后不可恢复，请填写作废原因' : '终止后房源将释放为「已发布」，请填写终止原因',
      isDraft ? '作废合同' : '终止合同',
      { inputPattern: /\S+/, inputErrorMessage: '原因不能为空', type: 'warning' },
    )
    acting.value = true
    detail.value = await terminateContract(detail.value.id, value)
    ElMessage.success(isDraft ? '已作废' : '已终止')
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    if (error instanceof ApiError) ElMessage.error(error.message)
  } finally {
    acting.value = false
  }
}

async function completeNode(row: TransactionItem): Promise<void> {
  if (!detail.value) return
  try {
    const { value } = await ElMessageBox.prompt(`确认完成「${row.nodeLabel}」节点？可填写备注。`, '完成节点', {
      inputPlaceholder: '备注（选填）',
      type: 'warning',
    })
    acting.value = true
    await doneTransaction(detail.value.id, row.node, value || undefined)
    ElMessage.success('节点已完成')
    await load()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    if (error instanceof ApiError) ElMessage.error(error.message)
  } finally {
    acting.value = false
  }
}

async function previewPdf(): Promise<void> {
  if (!detail.value) return
  acting.value = true
  try {
    const blob = await fetchContractPdf(detail.value.id)
    openBlobInNewTab(blob)
  } catch (error) {
    ElMessage.error(errorMessage(error, 'PDF 预览失败'))
  } finally {
    acting.value = false
  }
}

async function downloadPdf(): Promise<void> {
  if (!detail.value) return
  acting.value = true
  try {
    const blob = await fetchContractPdf(detail.value.id)
    saveBlob(blob, `${detail.value.contractNo}.pdf`)
  } catch (error) {
    ElMessage.error(errorMessage(error, 'PDF 下载失败'))
  } finally {
    acting.value = false
  }
}

const canSubmit = computed(() => detail.value?.status === 'DRAFT')
const canTerminate = computed(() =>
  detail.value ? !['EXPIRED', 'TERMINATED', 'CANCELLED'].includes(detail.value.status) : false,
)
const canFinishNode = computed(() => detail.value?.status === 'ACTIVE')

function nodeStatusType(status: string): 'success' | 'info' | 'warning' {
  if (status === 'DONE') return 'success'
  if (status === 'CANCELLED') return 'info'
  return 'warning'
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="page">
    <el-card v-if="detail" shadow="never">
      <template #header>
        <div class="card-header">
          <div class="title">
            <el-button link :icon="ArrowLeft" @click="router.push({ name: 'contract-list' })">返回</el-button>
            <span class="no">{{ detail.contractNo }}</span>
            <el-tag :type="contractStatusTagType(detail.status)">{{ contractStatusLabel(detail.status) }}</el-tag>
          </div>
          <div class="actions">
            <el-button :icon="Refresh" :loading="acting" @click="load">刷新</el-button>
            <el-button :icon="Document" :disabled="!detail.pdfAvailable" @click="previewPdf">预览 PDF</el-button>
            <el-button :icon="Download" :disabled="!detail.pdfAvailable" @click="downloadPdf">下载 PDF</el-button>
            <el-button v-if="canSubmit" type="warning" @click="submit">提交待签</el-button>
            <el-button v-if="canTerminate" type="danger" plain @click="terminate">
              {{ detail.status === 'DRAFT' ? '作废' : '终止' }}
            </el-button>
          </div>
        </div>
      </template>

      <el-descriptions title="合同信息" :column="3" border>
        <el-descriptions-item label="标题" :span="3">{{ detail.title }}</el-descriptions-item>
        <el-descriptions-item label="房源">
          {{ detail.houseTitle || `房源 #${detail.houseId}` }}
        </el-descriptions-item>
        <el-descriptions-item label="小区/区域">{{ detail.community }} / {{ detail.region }}</el-descriptions-item>
        <el-descriptions-item label="户型">
          {{ detail.rooms ?? '-' }} 室 {{ detail.bathrooms ?? '-' }} 卫 · {{ detail.area ?? '-' }} ㎡
        </el-descriptions-item>
        <el-descriptions-item label="房屋地址" :span="3">{{ detail.address }}</el-descriptions-item>
        <el-descriptions-item label="租金">{{ formatMoney(detail.amount) }}</el-descriptions-item>
        <el-descriptions-item label="押金">{{ formatMoney(detail.deposit) }}</el-descriptions-item>
        <el-descriptions-item label="付款周期">{{ detail.paymentCycleLabel }}</el-descriptions-item>
        <el-descriptions-item label="租金大写" :span="3">{{ detail.amountInWords }}</el-descriptions-item>
        <el-descriptions-item label="租期" :span="2">
          {{ formatDate(detail.startDate) }} 至 {{ formatDate(detail.endDate) }}
        </el-descriptions-item>
        <el-descriptions-item label="模板版本">{{ detail.templateVersion }}</el-descriptions-item>
        <el-descriptions-item label="客户">{{ detail.customerName || `客户 #${detail.customerId}` }}</el-descriptions-item>
        <el-descriptions-item label="签署时间">{{ formatDateTime(detail.signedAt) }}</el-descriptions-item>
        <el-descriptions-item label="版本">v{{ detail.version }}</el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">签署方</el-divider>
      <el-table :data="detail.parties" border size="small">
        <el-table-column prop="partyTypeLabel" label="身份" width="110" />
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column label="联系电话" width="160">
          <template #default="{ row }">{{ row.phone || '—' }}</template>
        </el-table-column>
        <el-table-column label="证件号码（脱敏）" width="200">
          <template #default="{ row }">{{ row.idCardMasked || '—' }}</template>
        </el-table-column>
      </el-table>

      <el-divider content-position="left">交易节点</el-divider>
      <el-table :data="detail.transactions" border size="small">
        <el-table-column prop="nodeLabel" label="节点" width="110" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="nodeStatusType(row.status)" size="small">{{ row.statusLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="完成时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.doneAt) }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="180" />
        <el-table-column label="操作" width="110">
          <template #default="{ row }">
            <el-button
              v-if="canFinishNode && row.status === 'PENDING'"
              link
              type="primary"
              :icon="CircleCheck"
              :disabled="acting"
              @click="completeNode(row)"
            >
              完成
            </el-button>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
      </el-table>

      <el-divider content-position="left">状态日志</el-divider>
      <el-table :data="detail.statusLogs" border size="small">
        <el-table-column label="变更" min-width="180">
          <template #default="{ row }">
            {{ row.fromStatusLabel || '创建' }} → {{ row.toStatusLabel || row.toStatus }}
          </template>
        </el-table-column>
        <el-table-column prop="reason" label="原因/备注" min-width="200" />
        <el-table-column prop="operatorId" label="操作人ID" width="100" />
        <el-table-column label="时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.page {
  display: grid;
  gap: 16px;
  min-height: 200px;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.title {
  display: flex;
  align-items: center;
  gap: 10px;
}
.no {
  font-weight: 600;
}
.actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.muted {
  color: var(--el-text-color-placeholder);
}
</style>
