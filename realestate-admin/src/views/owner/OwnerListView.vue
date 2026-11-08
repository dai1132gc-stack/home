<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'

import { deleteOwner, listOwners } from '@/api/owner'
import type { OwnerQueryParams, OwnerVO } from '@/types/owner'
import { ApiError } from '@/utils/request'

const router = useRouter()

const loading = ref(false)
const rows = ref<OwnerVO[]>([])
const total = ref(0)

const filters = reactive<OwnerQueryParams>({
  page: 1,
  size: 10,
  keyword: '',
  type: '',
})

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof ApiError ? error.message : fallback
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const result = await listOwners({ ...filters })
    rows.value = result.items
    total.value = result.total
  } catch (error) {
    ElMessage.error(errorMessage(error, '加载客源列表失败'))
  } finally {
    loading.value = false
  }
}

function search(): void {
  filters.page = 1
  load()
}

function reset(): void {
  filters.keyword = ''
  filters.type = ''
  search()
}

async function remove(row: OwnerVO): Promise<void> {
  try {
    await ElMessageBox.confirm(`确认删除「${row.name}」？该操作只做逻辑删除。`, '删除确认', {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await deleteOwner(row.id)
    ElMessage.success('已删除')
    if (rows.value.length === 1 && (filters.page ?? 1) > 1) {
      filters.page = (filters.page ?? 1) - 1
    }
    load()
  } catch (error) {
    ElMessage.error(errorMessage(error, '删除失败'))
  }
}

function formatTime(value: string): string {
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

onMounted(load)
</script>

<template>
  <div class="page">
    <el-card shadow="never">
      <el-form :inline="true" :model="filters" @submit.prevent="search">
        <el-form-item label="搜索">
          <el-input
            v-model="filters.keyword"
            placeholder="姓名或手机号前缀"
            clearable
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="filters.type" placeholder="全部" clearable style="width: 140px">
            <el-option label="房东" value="LANDLORD" />
            <el-option label="客源" value="CUSTOMER_LEAD" />
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
          <span>客源管理（共 {{ total }} 条，手机号仅管理员可见）</span>
          <el-button type="primary" :icon="Plus" @click="router.push({ name: 'owner-create' })">
            新建房东/客源
          </el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="name" label="姓名" min-width="120" />
        <el-table-column prop="phone" label="手机号" width="140" />
        <el-table-column label="类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.type === 'LANDLORD' ? 'success' : 'warning'">{{ row.typeLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '-' }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="更新时间" width="170">
          <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="router.push({ name: 'owner-edit', params: { id: row.id } })">
              编辑
            </el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
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
  gap: 12px;
  flex-wrap: wrap;
}
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
