<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'

import { deleteHouse, listHouses, offlineHouse, publishHouse } from '@/api/house'
import type { HouseListItem, HouseQueryParams } from '@/types/house'
import { ApiError } from '@/utils/request'
import { canOffline, canPublish, formatArea, formatPrice, houseTypeLabel, statusLabel, statusTagType } from '@/utils/house'

const router = useRouter()

const loading = ref(false)
const rows = ref<HouseListItem[]>([])
const total = ref(0)

const filters = reactive<HouseQueryParams>({
  page: 1,
  size: 10,
  keyword: '',
  status: '',
  houseType: '',
  region: '',
  minPrice: undefined,
  maxPrice: undefined,
  sort: 'created_desc',
})

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof ApiError ? error.message : fallback
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const result = await listHouses({ ...filters })
    rows.value = result.items
    total.value = result.total
  } catch (error) {
    ElMessage.error(errorMessage(error, '加载房源列表失败'))
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
  filters.status = ''
  filters.houseType = ''
  filters.region = ''
  filters.minPrice = undefined
  filters.maxPrice = undefined
  filters.sort = 'created_desc'
  search()
}

async function changeStatus(row: HouseListItem, action: 'publish' | 'offline'): Promise<void> {
  const label = action === 'publish' ? '发布' : '下架'
  try {
    await ElMessageBox.confirm(`确认${label}房源「${row.title}」？`, `${label}确认`, {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    if (action === 'publish') {
      await publishHouse(row.id)
    } else {
      await offlineHouse(row.id)
    }
    ElMessage.success(`${label}成功`)
    load()
  } catch (error) {
    ElMessage.error(errorMessage(error, `${label}失败`))
  }
}

async function remove(row: HouseListItem): Promise<void> {
  try {
    await ElMessageBox.confirm(`删除后不可恢复（逻辑删除）：${row.title}`, '删除确认', {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await deleteHouse(row.id)
    ElMessage.success('已删除')
    if (rows.value.length === 1 && (filters.page ?? 1) > 1) {
      filters.page = (filters.page ?? 1) - 1
    }
    load()
  } catch (error) {
    ElMessage.error(errorMessage(error, '删除失败'))
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <el-card shadow="never">
      <el-form :inline="true" :model="filters" @submit.prevent="search">
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" placeholder="标题/小区前缀" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="待发布" value="PENDING" />
            <el-option label="已发布" value="PUBLISHED" />
            <el-option label="已下架" value="OFFLINE" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="filters.houseType" placeholder="全部" clearable style="width: 110px">
            <el-option label="出租" value="RENT" />
            <el-option label="出售" value="SALE" />
          </el-select>
        </el-form-item>
        <el-form-item label="区域">
          <el-input v-model="filters.region" placeholder="如 浦东" clearable style="width: 120px" />
        </el-form-item>
        <el-form-item label="价格">
          <el-input-number v-model="filters.minPrice" :min="0" :controls="false" placeholder="最低" style="width: 100px" />
          <span class="range-sep">-</span>
          <el-input-number v-model="filters.maxPrice" :min="0" :controls="false" placeholder="最高" style="width: 100px" />
        </el-form-item>
        <el-form-item label="排序">
          <el-select v-model="filters.sort" style="width: 140px">
            <el-option label="最新发布" value="created_desc" />
            <el-option label="价格升序" value="price_asc" />
            <el-option label="价格降序" value="price_desc" />
            <el-option label="面积升序" value="area_asc" />
            <el-option label="面积降序" value="area_desc" />
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
          <span>房源管理（共 {{ total }} 条）</span>
          <el-button type="primary" :icon="Plus" @click="router.push({ name: 'house-create' })">
            新建房源
          </el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column label="封面" width="96">
          <template #default="{ row }">
            <el-image
              v-if="row.coverUrl"
              :src="row.coverUrl"
              fit="cover"
              class="cover"
              :preview-src-list="[row.coverUrl]"
              preview-teleported
            />
            <span v-else class="muted">无图</span>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="170" show-overflow-tooltip />
        <el-table-column label="位置" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.region }} · {{ row.community }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="74">
          <template #default="{ row }">{{ houseTypeLabel(row.houseType) }}</template>
        </el-table-column>
        <el-table-column label="价格" width="118" align="right">
          <template #default="{ row }">{{ formatPrice(row.price, row.houseType) }}</template>
        </el-table-column>
        <el-table-column label="面积" width="96" align="right">
          <template #default="{ row }">{{ formatArea(row.area) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="218" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="router.push({ name: 'house-detail', params: { id: row.id } })">
              详情
            </el-button>
            <el-button link type="primary" @click="router.push({ name: 'house-edit', params: { id: row.id } })">
              编辑
            </el-button>
            <el-button
              v-if="canPublish(row.status)"
              link
              type="success"
              @click="changeStatus(row, 'publish')"
            >
              发布
            </el-button>
            <el-button
              v-if="canOffline(row.status)"
              link
              type="warning"
              @click="changeStatus(row, 'offline')"
            >
              下架
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
}
.cover {
  width: 64px;
  height: 48px;
  border-radius: 4px;
}
.muted {
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}
.range-sep {
  margin: 0 6px;
  color: var(--el-text-color-secondary);
}
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
