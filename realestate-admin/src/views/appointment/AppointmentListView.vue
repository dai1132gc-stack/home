<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'

import {
  cancelAppointment,
  completeAppointment,
  confirmAppointment,
  listAppointments,
  noShowAppointment,
} from '@/api/appointment'
import type { AppointmentItem, AppointmentQueryParams } from '@/types/appointment'
import { appointmentStatusLabel, appointmentStatusTagType, APPOINTMENT_STATUS_OPTIONS } from '@/types/appointment'
import { formatDateTime } from '@/utils/format'
import { ApiError } from '@/utils/request'

const loading = ref(false)
const acting = ref(false)
const rows = ref<AppointmentItem[]>([])
const total = ref(0)
const dateRange = ref<[string, string] | null>(null)

const filters = reactive<AppointmentQueryParams>({
  page: 1,
  size: 10,
  status: '',
  houseId: undefined,
  fromTime: undefined,
  toTime: undefined,
})

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof ApiError ? error.message : fallback
}

/** 日期区间按本地时区转换为 ISO，后端按 Instant 接收 */
function applyDateRange(): void {
  if (!dateRange.value || dateRange.value.length !== 2) {
    filters.fromTime = undefined
    filters.toTime = undefined
    return
  }
  filters.fromTime = new Date(`${dateRange.value[0]}T00:00:00+08:00`).toISOString()
  filters.toTime = new Date(`${dateRange.value[1]}T23:59:59+08:00`).toISOString()
}

async function load(): Promise<void> {
  loading.value = true
  applyDateRange()
  try {
    const result = await listAppointments({ ...filters })
    rows.value = result.items
    total.value = result.total
  } catch (error) {
    ElMessage.error(errorMessage(error, '加载预约列表失败'))
  } finally {
    loading.value = false
  }
}

function search(): void {
  filters.page = 1
  load()
}

function reset(): void {
  filters.status = ''
  filters.houseId = undefined
  dateRange.value = null
  search()
}

type Action = 'confirm' | 'cancel' | 'complete' | 'no-show'

const ACTION_LABEL: Record<Action, string> = {
  confirm: '确认',
  cancel: '取消',
  complete: '标记完成',
  'no-show': '标记爽约',
}

async function act(row: AppointmentItem, action: Action): Promise<void> {
  const label = ACTION_LABEL[action]
  try {
    await ElMessageBox.confirm(`确认${label}该预约（${row.houseTitle ?? '房源'}）？`, `${label}确认`, {
      type: 'warning',
    })
  } catch {
    return
  }
  acting.value = true
  try {
    if (action === 'confirm') await confirmAppointment(row.id)
    else if (action === 'cancel') await cancelAppointment(row.id)
    else if (action === 'complete') await completeAppointment(row.id)
    else await noShowAppointment(row.id)
    ElMessage.success(`${label}成功`)
    load()
  } catch (error) {
    ElMessage.error(errorMessage(error, `${label}失败`))
  } finally {
    acting.value = false
  }
}

function canConfirm(row: AppointmentItem): boolean {
  return row.status === 'PENDING'
}

function canFinish(row: AppointmentItem): boolean {
  return row.status === 'CONFIRMED'
}

onMounted(load)
</script>

<template>
  <div class="page">
    <el-card shadow="never">
      <el-form :inline="true" :model="filters" @submit.prevent="search">
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="item in APPOINTMENT_STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="房源ID">
          <el-input-number v-model="filters.houseId" :min="1" :controls="false" placeholder="房源ID" style="width: 120px" />
        </el-form-item>
        <el-form-item label="看房日期">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            style="width: 240px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="search">查询</el-button>
          <el-button :icon="Refresh" @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>预约带看（共 {{ total }} 条）</span>
          <span class="hint">确认预约后房源自动置为「已预定」</span>
        </div>
      </template>

      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="房源" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <div>{{ row.houseTitle || `房源 #${row.houseId}` }}</div>
            <div class="muted">{{ row.houseRegion }} · {{ row.houseCommunity }}</div>
          </template>
        </el-table-column>
        <el-table-column label="客户" width="130">
          <template #default="{ row }">
            <div>{{ row.customerName || `客户 #${row.customerId}` }}</div>
            <div class="muted">{{ row.contactPhone }}</div>
          </template>
        </el-table-column>
        <el-table-column label="看房时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.preferredTime) }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="appointmentStatusTagType(row.status)">{{ appointmentStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="提交时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button v-if="canConfirm(row)" link type="primary" :disabled="acting" @click="act(row, 'confirm')">
              确认
            </el-button>
            <el-button v-if="canFinish(row)" link type="success" :disabled="acting" @click="act(row, 'complete')">
              完成
            </el-button>
            <el-button v-if="canFinish(row)" link type="warning" :disabled="acting" @click="act(row, 'no-show')">
              爽约
            </el-button>
            <el-button
              v-if="row.status === 'PENDING' || row.status === 'CONFIRMED'"
              link
              type="danger"
              :disabled="acting"
              @click="act(row, 'cancel')"
            >
              取消
            </el-button>
            <span v-if="row.status === 'COMPLETED' || row.status === 'CANCELLED' || row.status === 'NO_SHOW'" class="muted">
              已结束
            </span>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="filters.page"
        v-model:page-size="filters.size"
        class="pager"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="load"
        @size-change="search"
      />
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
.hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.muted {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
