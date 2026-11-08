<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'

import { listContracts } from '@/api/contract'
import type { ContractListItem, ContractQueryParams } from '@/types/contract'
import { CONTRACT_STATUS_OPTIONS, contractStatusLabel, contractStatusTagType } from '@/types/contract'
import { formatDate, formatDateTime, formatMoney } from '@/utils/format'
import { ApiError } from '@/utils/request'

const router = useRouter()

const loading = ref(false)
const rows = ref<ContractListItem[]>([])
const total = ref(0)

const filters = reactive<ContractQueryParams>({
  page: 1,
  size: 10,
  status: '',
  keyword: '',
})

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof ApiError ? error.message : fallback
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const result = await listContracts({ ...filters })
    rows.value = result.items
    total.value = result.total
  } catch (error) {
    ElMessage.error(errorMessage(error, '加载合同列表失败'))
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
  filters.keyword = ''
  search()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <el-card shadow="never">
      <el-form :inline="true" :model="filters" @submit.prevent="search">
        <el-form-item label="关键字">
          <el-input v-model="filters.keyword" placeholder="合同编号/标题前缀" clearable style="width: 190px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="item in CONTRACT_STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
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
          <span>合同管理（共 {{ total }} 条）</span>
          <el-button type="primary" :icon="Plus" @click="router.push({ name: 'contract-create' })">
            起草合同
          </el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="contractNo" label="合同编号" width="180" />
        <el-table-column label="标题 / 房源" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <div>{{ row.title }}</div>
            <div class="muted">{{ row.houseTitle || `房源 #${row.houseId}` }}</div>
          </template>
        </el-table-column>
        <el-table-column label="客户" width="110">
          <template #default="{ row }">{{ row.customerName || `客户 #${row.customerId}` }}</template>
        </el-table-column>
        <el-table-column label="租金" width="120" align="right">
          <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
        </el-table-column>
        <el-table-column label="押金" width="120" align="right">
          <template #default="{ row }">{{ formatMoney(row.deposit) }}</template>
        </el-table-column>
        <el-table-column label="租期" width="190">
          <template #default="{ row }">{{ formatDate(row.startDate) }} ~ {{ formatDate(row.endDate) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="contractStatusTagType(row.status)">{{ contractStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="PDF" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.pdfReady" type="success" size="small">已生成</el-tag>
            <span v-else class="muted">未生成</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="router.push({ name: 'contract-detail', params: { id: row.id } })">
              详情
            </el-button>
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
.muted {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
