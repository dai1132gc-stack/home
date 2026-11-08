<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Edit } from '@element-plus/icons-vue'

import { deleteHouse, getHouse, offlineHouse, publishHouse } from '@/api/house'
import type { HouseDetail } from '@/types/house'
import { ApiError } from '@/utils/request'
import { canOffline, canPublish, formatArea, formatPrice, houseTypeLabel, statusLabel, statusTagType } from '@/utils/house'

const route = useRoute()
const router = useRouter()

const id = computed(() => Number(route.params.id))
const loading = ref(false)
const acting = ref(false)
const detail = ref<HouseDetail | null>(null)

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof ApiError ? error.message : fallback
}

async function load(): Promise<void> {
  loading.value = true
  try {
    detail.value = await getHouse(id.value)
  } catch (error) {
    ElMessage.error(errorMessage(error, '加载房源详情失败'))
  } finally {
    loading.value = false
  }
}

async function changeStatus(action: 'publish' | 'offline'): Promise<void> {
  if (!detail.value) return
  const label = action === 'publish' ? '发布' : '下架'
  try {
    await ElMessageBox.confirm(`确认${label}该房源？`, `${label}确认`, { type: 'warning' })
  } catch {
    return
  }
  acting.value = true
  try {
    detail.value = action === 'publish'
      ? await publishHouse(detail.value.id)
      : await offlineHouse(detail.value.id)
    ElMessage.success(`${label}成功`)
  } catch (error) {
    ElMessage.error(errorMessage(error, `${label}失败`))
  } finally {
    acting.value = false
  }
}

async function remove(): Promise<void> {
  if (!detail.value) return
  try {
    await ElMessageBox.confirm('删除后不可恢复（逻辑删除），确认删除？', '删除确认', { type: 'warning' })
  } catch {
    return
  }
  acting.value = true
  try {
    await deleteHouse(detail.value.id)
    ElMessage.success('已删除')
    router.push({ name: 'house-list' })
  } catch (error) {
    ElMessage.error(errorMessage(error, '删除失败'))
  } finally {
    acting.value = false
  }
}

function formatTime(value: string): string {
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

onMounted(load)
</script>

<template>
  <div class="page" v-loading="loading">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <div class="left">
            <el-button :icon="ArrowLeft" text @click="router.push({ name: 'house-list' })">返回列表</el-button>
            <span class="title">{{ detail?.title || '房源详情' }}</span>
            <el-tag v-if="detail" :type="statusTagType(detail.status)">{{ statusLabel(detail.status) }}</el-tag>
          </div>
          <el-space v-if="detail">
            <el-button
              v-if="canPublish(detail.status)"
              type="success"
              :loading="acting"
              @click="changeStatus('publish')"
            >
              发布
            </el-button>
            <el-button
              v-if="canOffline(detail.status)"
              type="warning"
              :loading="acting"
              @click="changeStatus('offline')"
            >
              下架
            </el-button>
            <el-button :icon="Edit" type="primary" @click="router.push({ name: 'house-edit', params: { id: detail.id } })">
              编辑
            </el-button>
            <el-button type="danger" :loading="acting" @click="remove">删除</el-button>
          </el-space>
        </div>
      </template>

      <template v-if="detail">
        <el-descriptions :column="3" border>
          <el-descriptions-item label="房源编号">{{ detail.id }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ houseTypeLabel(detail.houseType) }}</el-descriptions-item>
          <el-descriptions-item label="价格">{{ formatPrice(detail.price, detail.houseType) }}</el-descriptions-item>
          <el-descriptions-item label="小区">{{ detail.community }}</el-descriptions-item>
          <el-descriptions-item label="区域">{{ detail.region }}</el-descriptions-item>
          <el-descriptions-item label="楼层">{{ detail.floor || '-' }}</el-descriptions-item>
          <el-descriptions-item label="地址" :span="2">{{ detail.address }}</el-descriptions-item>
          <el-descriptions-item label="面积">{{ formatArea(detail.area) }}</el-descriptions-item>
          <el-descriptions-item label="户型">{{ detail.rooms }} 室 {{ detail.bathrooms }} 卫</el-descriptions-item>
          <el-descriptions-item label="房东">{{ detail.ownerName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="联系电话">{{ detail.ownerPhone || '-' }}</el-descriptions-item>
          <el-descriptions-item label="版本">{{ detail.version }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatTime(detail.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ formatTime(detail.updatedAt) }}</el-descriptions-item>
          <el-descriptions-item label="描述" :span="3">
            <span class="description">{{ detail.description || '暂无描述' }}</span>
          </el-descriptions-item>
        </el-descriptions>

        <div class="section-title">房源图片（{{ detail.media.length }}）</div>
        <div v-if="detail.media.length" class="media">
          <el-image
            v-for="item in detail.media"
            :key="item.id"
            :src="item.url"
            fit="cover"
            class="media-img"
            :preview-src-list="detail.media.map((m) => m.url)"
            preview-teleported
          />
        </div>
        <el-empty v-else description="暂无图片" :image-size="80" />
      </template>
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
.left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.title {
  font-weight: 600;
  font-size: 16px;
}
.description {
  white-space: pre-wrap;
}
.section-title {
  margin: 20px 0 12px;
  font-weight: 600;
}
.media {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}
.media-img {
  width: 160px;
  height: 120px;
  border-radius: 6px;
  border: 1px solid var(--el-border-color-light);
}
</style>
