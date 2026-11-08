<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search, View } from '@element-plus/icons-vue'

import {
  acceptRepair,
  closeRepair,
  fetchRepairMedia,
  getRepair,
  listRepairs,
  resolveRepair,
  startRepair,
} from '@/api/repair'
import type { RepairDetail, RepairListItem, RepairQueryParams } from '@/types/repair'
import {
  REPAIR_CATEGORY_OPTIONS,
  REPAIR_STATUS_OPTIONS,
  repairStatusLabel,
  repairStatusTagType,
} from '@/types/repair'
import { formatDateTime } from '@/utils/format'
import { ApiError } from '@/utils/request'

const loading = ref(false)
const acting = ref(false)
const rows = ref<RepairListItem[]>([])
const total = ref(0)
const dateRange = ref<[string, string] | null>(null)

const filters = reactive<RepairQueryParams>({
  page: 1,
  size: 10,
  status: '',
  category: '',
  keyword: '',
  fromTime: undefined,
  toTime: undefined,
})

const drawerVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<RepairDetail | null>(null)
const mediaUrls = ref<Record<number, string>>({})

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof ApiError ? error.message : fallback
}

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
    const result = await listRepairs({ ...filters })
    rows.value = result.items
    total.value = result.total
  } catch (error) {
    ElMessage.error(errorMessage(error, '加载报修列表失败'))
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
  filters.category = ''
  filters.keyword = ''
  dateRange.value = null
  search()
}

function releaseMediaUrls(): void {
  Object.values(mediaUrls.value).forEach((url) => URL.revokeObjectURL(url))
  mediaUrls.value = {}
}

async function loadMedia(target: RepairDetail): Promise<void> {
  releaseMediaUrls()
  for (const media of target.media) {
    try {
      const blob = await fetchRepairMedia(target.id, media.id)
      mediaUrls.value = { ...mediaUrls.value, [media.id]: URL.createObjectURL(blob) }
    } catch {
      // 单张图片失败不影响其他内容展示
    }
  }
}

async function openDetail(row: RepairListItem): Promise<void> {
  drawerVisible.value = true
  detailLoading.value = true
  detail.value = null
  releaseMediaUrls()
  try {
    const data = await getRepair(row.id)
    detail.value = data
    await loadMedia(data)
  } catch (error) {
    ElMessage.error(errorMessage(error, '加载报修详情失败'))
  } finally {
    detailLoading.value = false
  }
}

async function refreshDetail(): Promise<void> {
  if (!detail.value) return
  await openDetail(detail.value)
  load()
}

function closeDrawer(): void {
  drawerVisible.value = false
  detail.value = null
  releaseMediaUrls()
}

type Action = 'accept' | 'start' | 'resolve' | 'close'

const ACTION_LABEL: Record<Action, string> = {
  accept: '受理',
  start: '开始处理',
  resolve: '完成处理',
  close: '关闭工单',
}

async function act(row: RepairListItem, action: Action): Promise<void> {
  const label = ACTION_LABEL[action]
  try {
    await ElMessageBox.confirm(`确认${label}工单 ${row.ticketNo}？`, `${label}确认`, { type: 'warning' })
  } catch {
    return
  }
  acting.value = true
  try {
    if (action === 'accept') await acceptRepair(row.id)
    else if (action === 'start') await startRepair(row.id)
    else if (action === 'resolve') await resolveRepair(row.id)
    else await closeRepair(row.id)
    ElMessage.success(`${label}成功`)
    if (drawerVisible.value && detail.value?.id === row.id) {
      await refreshDetail()
    } else {
      load()
    }
  } catch (error) {
    ElMessage.error(errorMessage(error, `${label}失败`))
  } finally {
    acting.value = false
  }
}

function canAccept(row: RepairListItem): boolean {
  return row.status === 'SUBMITTED'
}

function canStart(row: RepairListItem): boolean {
  return row.status === 'ACCEPTED' || row.status === 'REOPENED'
}

function canResolve(row: RepairListItem): boolean {
  return row.status === 'PROCESSING'
}

function canClose(row: RepairListItem): boolean {
  return row.status === 'RESOLVED'
}

onMounted(load)
onBeforeUnmount(releaseMediaUrls)
</script>

<template>
  <div class="page">
    <el-card shadow="never">
      <el-form :inline="true" :model="filters" @submit.prevent="search">
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="item in REPAIR_STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="filters.category" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="item in REPAIR_CATEGORY_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键字">
          <el-input v-model="filters.keyword" placeholder="单号或标题前缀" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="提交日期">
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
          <span>报修管理（共 {{ total }} 条）</span>
          <span class="hint">状态迁移由后端状态机校验，非法操作返回 409</span>
        </div>
      </template>

      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="ticketNo" label="单号" width="165" />
        <el-table-column label="房源 / 客户" min-width="200">
          <template #default="{ row }">
            <div>{{ row.houseTitle || `房源 #${row.houseId}` }}</div>
            <div class="muted">{{ row.customerName || `客户 #${row.customerId}` }} · {{ row.contactPhone }}</div>
          </template>
        </el-table-column>
        <el-table-column label="分类" width="120">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.categoryLabel }}</el-tag>
            <el-tag v-if="row.urgency === 'URGENT'" size="small" type="danger" class="urgency">紧急</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="repairStatusTagType(row.status)">{{ repairStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="期望上门" width="170">
          <template #default="{ row }">{{ formatDateTime(row.preferredVisitTime) }}</template>
        </el-table-column>
        <el-table-column label="提交时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button v-if="canAccept(row)" link type="primary" :disabled="acting" @click="act(row, 'accept')">受理</el-button>
            <el-button v-if="canStart(row)" link type="primary" :disabled="acting" @click="act(row, 'start')">开始处理</el-button>
            <el-button v-if="canResolve(row)" link type="success" :disabled="acting" @click="act(row, 'resolve')">完成处理</el-button>
            <el-button v-if="canClose(row)" link type="warning" :disabled="acting" @click="act(row, 'close')">关闭</el-button>
            <el-button link type="primary" :icon="View" @click="openDetail(row)">详情</el-button>
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

    <el-drawer v-model="drawerVisible" title="报修详情" size="560px" @closed="closeDrawer">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="单号">{{ detail.ticketNo }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="repairStatusTagType(detail.status)">{{ detail.statusLabel }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="房源">{{ detail.houseTitle || `#${detail.houseId}` }}</el-descriptions-item>
            <el-descriptions-item label="合同">{{ detail.contractNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="客户">{{ detail.customerName || `#${detail.customerId}` }}</el-descriptions-item>
            <el-descriptions-item label="联系电话">{{ detail.contactPhone }}</el-descriptions-item>
            <el-descriptions-item label="分类">{{ detail.categoryLabel }}</el-descriptions-item>
            <el-descriptions-item label="紧急度">{{ detail.urgencyLabel }}</el-descriptions-item>
            <el-descriptions-item label="期望上门">{{ formatDateTime(detail.preferredVisitTime) }}</el-descriptions-item>
            <el-descriptions-item label="提交时间">{{ formatDateTime(detail.createdAt) }}</el-descriptions-item>
          </el-descriptions>

          <div class="block-title">问题描述</div>
          <div class="text-block">{{ detail.description }}</div>

          <div class="block-title">现场图片（私有存储，鉴权预览）</div>
          <div v-if="detail.media.length === 0" class="muted">客户未上传图片</div>
          <div v-else class="media-grid">
            <el-image
              v-for="media in detail.media"
              :key="media.id"
              class="media-item"
              fit="cover"
              :src="mediaUrls[media.id] || ''"
              :preview-src-list="mediaUrls[media.id] ? [mediaUrls[media.id]] : []"
              preview-teleported
            >
              <template #error>
                <div class="media-error">加载中…</div>
              </template>
            </el-image>
          </div>

          <div class="block-title">状态时间线</div>
          <el-timeline>
            <el-timeline-item
              v-for="log in detail.statusLogs"
              :key="log.id"
              :timestamp="formatDateTime(log.createdAt)"
              placement="top"
            >
              <div class="timeline-title">
                {{ log.fromStatusLabel || '创建' }} → {{ log.toStatusLabel }}
              </div>
              <div class="muted">{{ log.reason || '-' }}</div>
            </el-timeline-item>
          </el-timeline>
        </template>
      </div>

      <template #footer>
        <div v-if="detail" class="drawer-actions">
          <el-button v-if="canAccept(detail)" type="primary" :disabled="acting" @click="act(detail, 'accept')">受理</el-button>
          <el-button v-if="canStart(detail)" type="primary" :disabled="acting" @click="act(detail, 'start')">开始处理</el-button>
          <el-button v-if="canResolve(detail)" type="success" :disabled="acting" @click="act(detail, 'resolve')">完成处理</el-button>
          <el-button v-if="canClose(detail)" type="warning" :disabled="acting" @click="act(detail, 'close')">关闭工单</el-button>
          <el-button @click="closeDrawer">关闭</el-button>
        </div>
      </template>
    </el-drawer>
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
  gap: 12px;
}
.hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.muted {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}
.urgency {
  margin-left: 4px;
}
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
.block-title {
  margin: 18px 0 8px;
  font-weight: 600;
  font-size: 14px;
}
.text-block {
  white-space: pre-wrap;
  line-height: 1.7;
  padding: 10px 12px;
  border-radius: 6px;
  background: var(--el-fill-color-light);
}
.media-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.media-item {
  width: 96px;
  height: 96px;
  border-radius: 6px;
  border: 1px solid var(--el-border-color-lighter);
}
.media-error {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}
.timeline-title {
  font-weight: 600;
  margin-bottom: 2px;
}
.drawer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
