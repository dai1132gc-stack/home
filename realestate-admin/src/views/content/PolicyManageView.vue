<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type UploadFile } from 'element-plus'
import { Download, Plus, Refresh, Search, Upload } from '@element-plus/icons-vue'

import {
  createPolicy,
  deletePolicy,
  fetchPolicyAttachment,
  getPolicy,
  listPolicies,
  offlinePolicy,
  publishPolicy,
  updatePolicy,
  uploadPolicyAttachment,
} from '@/api/content'
import { reindexPolicy } from '@/api/ai'
import type { ContentQueryParams, PolicyListItem, PolicyType } from '@/types/content'
import { CONTENT_STATUS_OPTIONS, POLICY_TYPE_OPTIONS, contentStatusTagType } from '@/types/content'
import { formatDate, formatDateTime, saveBlob } from '@/utils/format'
import { ApiError } from '@/utils/request'

const loading = ref(false)
const saving = ref(false)
const rows = ref<PolicyListItem[]>([])
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
const currentAttachment = ref(false)
const form = reactive({
  title: '',
  docNo: '',
  docType: 'POLICY' as PolicyType,
  source: '',
  publishDate: '',
  effectiveDate: '',
  content: '',
  version: 0,
})

const previewText = computed(() => form.content || '（正文预览）')

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof ApiError ? error.message : fallback
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const result = await listPolicies({ ...filters })
    rows.value = result.items
    total.value = result.total
  } catch (error) {
    ElMessage.error(errorMessage(error, '加载政策列表失败'))
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
  currentAttachment.value = false
  form.title = ''
  form.docNo = ''
  form.docType = 'POLICY'
  form.source = ''
  form.publishDate = ''
  form.effectiveDate = ''
  form.content = ''
  form.version = 0
}

function openCreate(): void {
  resetForm()
  dialogVisible.value = true
}

async function openEdit(row: PolicyListItem): Promise<void> {
  try {
    const detail = await getPolicy(row.id)
    editingId.value = detail.id
    currentAttachment.value = detail.hasAttachment
    form.title = detail.title
    form.docNo = detail.docNo ?? ''
    form.docType = detail.docType
    form.source = detail.source ?? ''
    form.publishDate = detail.publishDate ?? ''
    form.effectiveDate = detail.effectiveDate ?? ''
    form.content = detail.content
    form.version = detail.version
    dialogVisible.value = true
  } catch (error) {
    ElMessage.error(errorMessage(error, '加载政策详情失败'))
  }
}

async function save(): Promise<void> {
  if (!form.title.trim() || !form.content.trim()) {
    ElMessage.warning('标题与正文不能为空')
    return
  }
  saving.value = true
  try {
    const payload = {
      title: form.title.trim(),
      docNo: form.docNo.trim() || undefined,
      docType: form.docType,
      source: form.source.trim() || undefined,
      publishDate: form.publishDate || undefined,
      effectiveDate: form.effectiveDate || undefined,
      content: form.content,
    }
    if (editingId.value == null) {
      const created = await createPolicy(payload)
      editingId.value = created.id
      form.version = created.version
      ElMessage.success('已保存为草稿，可继续上传附件')
      load()
    } else {
      const updated = await updatePolicy(editingId.value, { ...payload, version: form.version })
      form.version = updated.version
      ElMessage.success('已保存')
      dialogVisible.value = false
      load()
    }
  } catch (error) {
    ElMessage.error(errorMessage(error, '保存失败'))
  } finally {
    saving.value = false
  }
}

async function handleAttachment(file: UploadFile): Promise<void> {
  if (editingId.value == null) {
    ElMessage.warning('请先保存政策草稿，再上传附件')
    return
  }
  if (!file.raw) return
  try {
    const updated = await uploadPolicyAttachment(editingId.value, file.raw)
    currentAttachment.value = updated.hasAttachment
    form.version = updated.version
    ElMessage.success('附件已上传（私有存储）')
    load()
  } catch (error) {
    ElMessage.error(errorMessage(error, '附件上传失败（仅支持 PDF，最大 10MB）'))
  }
}

async function downloadAttachment(row: PolicyListItem): Promise<void> {
  try {
    const blob = await fetchPolicyAttachment(row.id)
    saveBlob(blob, `policy-${row.id}.pdf`)
  } catch (error) {
    ElMessage.error(errorMessage(error, '附件下载失败'))
  }
}

async function publish(row: PolicyListItem): Promise<void> {
  try {
    await ElMessageBox.confirm(`确认发布「${row.title}」？发布后公开可见。`, '发布确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await publishPolicy(row.id)
    ElMessage.success('已发布')
    load()
  } catch (error) {
    ElMessage.error(errorMessage(error, '发布失败'))
  }
}

async function offline(row: PolicyListItem): Promise<void> {
  try {
    await ElMessageBox.confirm(`确认下架「${row.title}」？下架后公开访问返回 404。`, '下架确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await offlinePolicy(row.id)
    ElMessage.success('已下架')
    load()
  } catch (error) {
    ElMessage.error(errorMessage(error, '下架失败'))
  }
}

const reindexingId = ref<number | null>(null)

/** W6：重建单条政策向量索引（幂等），发布后自动索引失败时手工补偿 */
async function reindex(row: PolicyListItem): Promise<void> {
  reindexingId.value = row.id
  try {
    const result = await reindexPolicy(row.id)
    ElMessage.success(`索引已重建：${result.chunks} 个分块`)
  } catch (error) {
    ElMessage.error(errorMessage(error, '索引重建失败'))
  } finally {
    reindexingId.value = null
  }
}

async function remove(row: PolicyListItem): Promise<void> {
  try {
    await ElMessageBox.confirm(`确认删除「${row.title}」？该操作只做逻辑删除。`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deletePolicy(row.id)
    ElMessage.success('已删除')
    load()
  } catch (error) {
    ElMessage.error(errorMessage(error, '删除失败'))
  }
}

function editable(row: PolicyListItem): boolean {
  return row.status === 'DRAFT' || row.status === 'OFFLINE'
}

onMounted(load)
</script>

<template>
  <div class="page">
    <el-card shadow="never">
      <el-form :inline="true" :model="filters" @submit.prevent="search">
        <el-form-item label="类型">
          <el-select v-model="filters.category" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="item in POLICY_TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
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
          <span>政策文件（共 {{ total }} 条，公开仅展示已发布）</span>
          <el-button type="primary" :icon="Plus" @click="openCreate">新建政策</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
        <el-table-column prop="docNo" label="文号" width="140">
          <template #default="{ row }">{{ row.docNo || '-' }}</template>
        </el-table-column>
        <el-table-column label="类型" width="80" align="center">
          <template #default="{ row }">{{ row.docTypeLabel }}</template>
        </el-table-column>
        <el-table-column prop="source" label="来源" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.source || '-' }}</template>
        </el-table-column>
        <el-table-column label="发布日期" width="110">
          <template #default="{ row }">{{ formatDate(row.publishDate) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="contentStatusTagType(row.status)">{{ row.statusLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="附件" width="80" align="center">
          <template #default="{ row }">
            <el-button v-if="row.hasAttachment" link type="primary" :icon="Download" @click="downloadAttachment(row)">
              下载
            </el-button>
            <span v-else class="muted">无</span>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="165">
          <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button v-if="editable(row)" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="editable(row)" link type="success" @click="publish(row)">发布</el-button>
            <el-button v-if="row.status === 'PUBLISHED'" link type="warning" @click="offline(row)">下架</el-button>
            <el-button v-if="editable(row)" link type="danger" @click="remove(row)">删除</el-button>
            <el-button
              link
              type="info"
              :loading="reindexingId === row.id"
              @click="reindex(row)"
            >重建索引</el-button>
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

    <el-dialog v-model="dialogVisible" :title="editingId == null ? '新建政策' : '编辑政策'" width="920px" top="6vh">
      <el-form label-width="80px">
        <el-row :gutter="16">
          <el-col :span="14">
            <el-form-item label="标题">
              <el-input v-model="form.title" maxlength="200" show-word-limit />
            </el-form-item>
          </el-col>
          <el-col :span="5">
            <el-form-item label="文号">
              <el-input v-model="form.docNo" maxlength="80" />
            </el-form-item>
          </el-col>
          <el-col :span="5">
            <el-form-item label="类型">
              <el-select v-model="form.docType" style="width: 100%">
                <el-option v-for="item in POLICY_TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="来源">
          <el-input v-model="form.source" maxlength="200" placeholder="示例数据请标注：非实时政策 / 非官方口径" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="发布日期">
              <el-date-picker v-model="form.publishDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="生效日期">
              <el-date-picker v-model="form.effectiveDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="附件">
              <el-upload :auto-upload="false" :show-file-list="false" accept="application/pdf" :on-change="handleAttachment">
                <el-button :icon="Upload">上传 PDF</el-button>
              </el-upload>
              <span class="muted attachment-hint">{{ currentAttachment ? '已有附件' : '暂无附件' }}</span>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="正文">
              <el-input
                v-model="form.content"
                type="textarea"
                :rows="16"
                maxlength="40000"
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
.muted {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}
.attachment-hint {
  margin-left: 8px;
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
