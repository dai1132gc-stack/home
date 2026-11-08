<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'

import {
  createGuide,
  deleteGuide,
  getGuide,
  listGuides,
  offlineGuide,
  publishGuide,
  updateGuide,
} from '@/api/content'
import type { ContentQueryParams, GuideCategory, GuideListItem } from '@/types/content'
import {
  CONTENT_STATUS_OPTIONS,
  GUIDE_CATEGORY_OPTIONS,
  contentStatusTagType,
  guideCategoryLabel,
} from '@/types/content'
import { formatDateTime } from '@/utils/format'
import { ApiError } from '@/utils/request'

const loading = ref(false)
const saving = ref(false)
const rows = ref<GuideListItem[]>([])
const total = ref(0)

const filters = reactive<ContentQueryParams>({
  page: 1,
  size: 10,
  category: '',
  status: '',
  keyword: '',
})

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const form = reactive({
  title: '',
  category: 'GUIDE' as GuideCategory,
  summary: '',
  content: '',
  sort: 0,
  version: 0,
})

const previewText = computed(() => form.content || '（正文预览）')

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof ApiError ? error.message : fallback
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const result = await listGuides({ ...filters })
    rows.value = result.items
    total.value = result.total
  } catch (error) {
    ElMessage.error(errorMessage(error, '加载内容列表失败'))
  } finally {
    loading.value = false
  }
}

function search(): void {
  filters.page = 1
  load()
}

function reset(): void {
  filters.category = ''
  filters.status = ''
  filters.keyword = ''
  search()
}

function resetForm(): void {
  editingId.value = null
  form.title = ''
  form.category = 'GUIDE'
  form.summary = ''
  form.content = ''
  form.sort = 0
  form.version = 0
}

function openCreate(): void {
  resetForm()
  dialogVisible.value = true
}

async function openEdit(row: GuideListItem): Promise<void> {
  try {
    const detail = await getGuide(row.id)
    editingId.value = detail.id
    form.title = detail.title
    form.category = detail.category
    form.summary = detail.summary ?? ''
    form.content = detail.content
    form.sort = detail.sort
    form.version = detail.version
    dialogVisible.value = true
  } catch (error) {
    ElMessage.error(errorMessage(error, '加载内容详情失败'))
  }
}

async function save(): Promise<void> {
  if (!form.title.trim() || !form.content.trim()) {
    ElMessage.warning('标题与正文不能为空')
    return
  }
  saving.value = true
  try {
    if (editingId.value == null) {
      await createGuide({
        title: form.title.trim(),
        category: form.category,
        summary: form.summary.trim() || undefined,
        content: form.content,
        sort: form.sort,
      })
      ElMessage.success('已保存为草稿')
    } else {
      await updateGuide(editingId.value, {
        title: form.title.trim(),
        category: form.category,
        summary: form.summary.trim(),
        content: form.content,
        sort: form.sort,
        version: form.version,
      })
      ElMessage.success('已保存')
    }
    dialogVisible.value = false
    load()
  } catch (error) {
    ElMessage.error(errorMessage(error, '保存失败'))
  } finally {
    saving.value = false
  }
}

async function publish(row: GuideListItem): Promise<void> {
  try {
    await ElMessageBox.confirm(`确认发布「${row.title}」？发布后公开可见。`, '发布确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await publishGuide(row.id)
    ElMessage.success('已发布')
    load()
  } catch (error) {
    ElMessage.error(errorMessage(error, '发布失败'))
  }
}

async function offline(row: GuideListItem): Promise<void> {
  try {
    await ElMessageBox.confirm(`确认下架「${row.title}」？下架后公开访问返回 404。`, '下架确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await offlineGuide(row.id)
    ElMessage.success('已下架')
    load()
  } catch (error) {
    ElMessage.error(errorMessage(error, '下架失败'))
  }
}

async function remove(row: GuideListItem): Promise<void> {
  try {
    await ElMessageBox.confirm(`确认删除「${row.title}」？该操作只做逻辑删除。`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteGuide(row.id)
    ElMessage.success('已删除')
    load()
  } catch (error) {
    ElMessage.error(errorMessage(error, '删除失败'))
  }
}

function editable(row: GuideListItem): boolean {
  return row.status === 'DRAFT' || row.status === 'OFFLINE'
}

onMounted(load)
</script>

<template>
  <div class="page">
    <el-card shadow="never">
      <el-form :inline="true" :model="filters" @submit.prevent="search">
        <el-form-item label="分类">
          <el-select v-model="filters.category" placeholder="全部" clearable style="width: 140px">
            <el-option v-for="item in GUIDE_CATEGORY_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="item in CONTENT_STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="filters.keyword" placeholder="标题前缀" clearable style="width: 180px" />
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
          <span>教程 / 网签指引 / 公告（共 {{ total }} 条）</span>
          <el-button type="primary" :icon="Plus" @click="openCreate">新建内容</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
        <el-table-column label="分类" width="110">
          <template #default="{ row }">{{ guideCategoryLabel(row.category) }}</template>
        </el-table-column>
        <el-table-column prop="summary" label="摘要" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.summary || '-' }}</template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="80" align="center" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="contentStatusTagType(row.status)">{{ row.statusLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button v-if="editable(row)" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="editable(row)" link type="success" @click="publish(row)">发布</el-button>
            <el-button v-if="row.status === 'PUBLISHED'" link type="warning" @click="offline(row)">下架</el-button>
            <el-button v-if="editable(row)" link type="danger" @click="remove(row)">删除</el-button>
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

    <el-dialog v-model="dialogVisible" :title="editingId == null ? '新建内容' : '编辑内容'" width="920px" top="6vh">
      <el-form label-width="80px">
        <el-row :gutter="16">
          <el-col :span="14">
            <el-form-item label="标题">
              <el-input v-model="form.title" maxlength="200" show-word-limit />
            </el-form-item>
          </el-col>
          <el-col :span="5">
            <el-form-item label="分类">
              <el-select v-model="form.category" style="width: 100%">
                <el-option v-for="item in GUIDE_CATEGORY_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="5">
            <el-form-item label="排序">
              <el-input-number v-model="form.sort" :min="0" :max="9999" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="摘要">
          <el-input v-model="form.summary" maxlength="500" show-word-limit />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="正文">
              <el-input
                v-model="form.content"
                type="textarea"
                :rows="16"
                maxlength="20000"
                show-word-limit
                placeholder="纯文本正文，支持换行；不支持富文本与 HTML"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="预览">
              <div class="preview">{{ previewText }}</div>
            </el-form-item>
          </el-col>
        </el-row>
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
}
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
.preview {
  width: 100%;
  height: 348px;
  overflow-y: auto;
  white-space: pre-wrap;
  line-height: 1.7;
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-fill-color-lighter);
}
</style>
