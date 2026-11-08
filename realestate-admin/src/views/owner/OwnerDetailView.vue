<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Plus } from '@element-plus/icons-vue'

import { createFollow, getOwner, listFollows } from '@/api/owner'
import type { CustomerFollow, FollowType, OwnerVO } from '@/types/owner'
import { FOLLOW_TYPE_OPTIONS } from '@/types/owner'
import { formatDateTime } from '@/utils/format'
import { ApiError } from '@/utils/request'

const route = useRoute()
const router = useRouter()
const ownerId = Number(route.params.id)

const loading = ref(false)
const owner = ref<OwnerVO | null>(null)
const follows = ref<CustomerFollow[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)

const dialogVisible = ref(false)
const saving = ref(false)
const form = reactive({
  content: '',
  followType: 'PHONE' as FollowType,
  nextFollowAt: '',
})

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof ApiError ? error.message : fallback
}

async function loadOwner(): Promise<void> {
  try {
    owner.value = await getOwner(ownerId)
  } catch (error) {
    ElMessage.error(errorMessage(error, '加载客源详情失败'))
  }
}

async function loadFollows(): Promise<void> {
  loading.value = true
  try {
    const result = await listFollows(ownerId, { page: page.value, size: size.value })
    follows.value = result.items
    total.value = result.total
  } catch (error) {
    ElMessage.error(errorMessage(error, '加载跟进记录失败'))
  } finally {
    loading.value = false
  }
}

function openCreate(): void {
  form.content = ''
  form.followType = 'PHONE'
  form.nextFollowAt = ''
  dialogVisible.value = true
}

/** 日期选择器输出本地时间字符串，转成 UTC ISO 提交 */
function toIso(value: string): string | undefined {
  if (!value) return undefined
  return new Date(`${value.replace(' ', 'T')}+08:00`).toISOString()
}

async function save(): Promise<void> {
  if (!form.content.trim()) {
    ElMessage.warning('请填写跟进内容')
    return
  }
  saving.value = true
  try {
    await createFollow(ownerId, {
      content: form.content.trim(),
      followType: form.followType,
      nextFollowAt: toIso(form.nextFollowAt),
    })
    ElMessage.success('跟进记录已保存')
    dialogVisible.value = false
    page.value = 1
    loadFollows()
  } catch (error) {
    ElMessage.error(errorMessage(error, '保存跟进失败'))
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await loadOwner()
  await loadFollows()
})
</script>

<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <div class="title">
            <el-button link :icon="ArrowLeft" @click="router.push({ name: 'owner-list' })">返回</el-button>
            <span>客源详情{{ owner ? `：${owner.name}` : '' }}</span>
          </div>
          <el-button v-if="owner" type="primary" @click="router.push({ name: 'owner-edit', params: { id: owner.id } })">
            编辑资料
          </el-button>
        </div>
      </template>

      <el-descriptions v-if="owner" :column="3" border size="small">
        <el-descriptions-item label="姓名">{{ owner.name }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ owner.phone }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ owner.typeLabel }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="3">{{ owner.remark || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatDateTime(owner.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ formatDateTime(owner.updatedAt) }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>跟进记录（共 {{ total }} 条）</span>
          <el-button type="primary" :icon="Plus" @click="openCreate">新增跟进</el-button>
        </div>
      </template>

      <div v-loading="loading">
        <el-empty v-if="follows.length === 0" description="暂无跟进记录" />
        <el-timeline v-else>
          <el-timeline-item
            v-for="item in follows"
            :key="item.id"
            :timestamp="formatDateTime(item.createdAt)"
            placement="top"
          >
            <div class="follow-head">
              <el-tag size="small" effect="plain">{{ item.followTypeLabel }}</el-tag>
              <span class="muted">跟进人：{{ item.createdByName || '-' }}</span>
            </div>
            <div class="follow-content">{{ item.content }}</div>
            <div v-if="item.nextFollowAt" class="next">
              下次跟进：{{ formatDateTime(item.nextFollowAt) }}
            </div>
          </el-timeline-item>
        </el-timeline>

        <el-pagination
          v-if="total > size"
          v-model:current-page="page"
          v-model:page-size="size"
          class="pager"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="loadFollows"
          @size-change="loadFollows"
        />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" title="新增跟进" width="520px">
      <el-form label-width="90px">
        <el-form-item label="跟进方式">
          <el-select v-model="form.followType" style="width: 100%">
            <el-option v-for="item in FOLLOW_TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="跟进内容">
          <el-input v-model="form.content" type="textarea" :rows="5" maxlength="1000" show-word-limit />
        </el-form-item>
        <el-form-item label="下次跟进">
          <el-date-picker
            v-model="form.nextFollowAt"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="可选"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
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
.title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}
.follow-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 4px;
}
.follow-content {
  white-space: pre-wrap;
  line-height: 1.7;
}
.next {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-color-warning);
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
