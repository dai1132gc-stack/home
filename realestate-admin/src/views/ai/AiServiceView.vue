<script setup lang="ts">
// W6 智能服务面板：AI 状态、调用统计、场景分布、最近记录（不含原始内容）
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { fetchAiMetrics, fetchAiStatus, reindexAllPolicies } from '@/api/ai'
import type { AiMetrics, AiStatus } from '@/types/ai'
import { ApiError } from '@/utils/request'

const status = ref<AiStatus | null>(null)
const metrics = ref<AiMetrics | null>(null)
const loading = ref(false)
const reindexing = ref(false)
const error = ref('')

const sceneLabels: Record<string, string> = {
  SMART_SEARCH: '智能选房',
  POLICY_QA: '政策问答',
  INDEX: '政策索引',
}

const statusTypes: Record<string, string> = {
  SUCCESS: 'success',
  DEGRADED: 'warning',
  FAILED: 'danger',
}

function percent(value: number | null): string {
  return value == null ? '-' : `${(value * 100).toFixed(1)}%`
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [s, m] = await Promise.all([fetchAiStatus(), fetchAiMetrics()])
    status.value = s
    metrics.value = m
  } catch (e) {
    error.value = e instanceof ApiError ? `${e.message}（traceId: ${e.traceId}）` : '加载失败'
  } finally {
    loading.value = false
  }
}

async function handleReindexAll() {
  reindexing.value = true
  try {
    const result = await reindexAllPolicies()
    ElMessage.success(`重建完成：政策 ${result.policies ?? 0} 条，分块 ${result.chunks} 个，失败 ${result.failed ?? 0} 条`)
    await load()
  } catch (e) {
    ElMessage.error(e instanceof ApiError ? `${e.message}（traceId: ${e.traceId}）` : '重建失败')
  } finally {
    reindexing.value = false
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="ai-page">
    <el-alert
      v-if="error"
      :title="error"
      type="error"
      show-icon
      class="error-bar"
      @close="error = ''"
    />

    <el-card class="status-card">
      <template #header>
        <div class="card-header">
          <span>AI 服务状态</span>
          <el-button size="small" :loading="reindexing" @click="handleReindexAll">全量重建索引</el-button>
        </div>
      </template>
      <template v-if="status">
        <el-descriptions :column="3" border>
          <el-descriptions-item label="启用状态">
            <el-tag :type="status.enabled ? 'success' : 'info'">
              {{ status.enabled ? '已启用' : '未启用（自动降级）' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="对话模型">{{ status.chatModel || '-' }}</el-descriptions-item>
          <el-descriptions-item label="向量模型">{{ status.embeddingModel || '-' }}</el-descriptions-item>
          <el-descriptions-item label="已索引分块">{{ status.indexedChunks }}</el-descriptions-item>
          <el-descriptions-item label="提供方">{{ status.provider || '-' }}</el-descriptions-item>
          <el-descriptions-item label="免责声明">{{ status.disclaimer }}</el-descriptions-item>
        </el-descriptions>
      </template>
      <el-empty v-else-if="!loading" description="暂无状态数据" />
    </el-card>

    <el-row :gutter="12" class="metric-cards">
      <el-col :span="4"><el-card><div class="metric">{{ metrics?.total ?? '-' }}</div><div class="label">调用量</div></el-card></el-col>
      <el-col :span="4"><el-card><div class="metric">{{ percent(metrics?.successRate ?? null) }}</div><div class="label">成功率</div></el-card></el-col>
      <el-col :span="4"><el-card><div class="metric">{{ metrics?.degraded ?? '-' }}</div><div class="label">降级数</div></el-card></el-col>
      <el-col :span="4"><el-card><div class="metric">{{ metrics?.failed ?? '-' }}</div><div class="label">失败数</div></el-card></el-col>
      <el-col :span="4"><el-card><div class="metric">{{ metrics?.avgLatencyMs != null ? `${metrics.avgLatencyMs.toFixed(0)}ms` : '-' }}</div><div class="label">平均耗时</div></el-card></el-col>
      <el-col :span="4"><el-card><div class="metric">{{ metrics?.p95LatencyMs != null ? `${metrics.p95LatencyMs}ms` : '-' }}</div><div class="label">P95 耗时</div></el-card></el-col>
    </el-row>
    <el-card class="token-card">
      <span class="label">Token 估算：</span>
      输入 {{ metrics?.promptTokens ?? 0 }} / 输出 {{ metrics?.completionTokens ?? 0 }}
      <span class="range">（统计区间 {{ metrics?.from?.slice(0, 10) }} ~ {{ metrics?.to?.slice(0, 10) }}）</span>
    </el-card>

    <el-card>
      <template #header>按场景分布</template>
      <el-table :data="metrics?.byScene ?? []" border stripe>
        <el-table-column label="场景">
          <template #default="{ row }">{{ sceneLabels[row.scene] ?? row.scene }}</template>
        </el-table-column>
        <el-table-column prop="total" label="调用量" width="100" />
        <el-table-column prop="success" label="成功" width="100" />
        <el-table-column prop="degraded" label="降级" width="100" />
        <el-table-column prop="failed" label="失败" width="100" />
        <template #empty><el-empty description="暂无数据" /></template>
      </el-table>
    </el-card>

    <el-card>
      <template #header>最近 20 条记录（不含内容）</template>
      <el-table :data="metrics?.recent ?? []" border stripe>
        <el-table-column label="场景" width="110">
          <template #default="{ row }">{{ sceneLabels[row.scene] ?? row.scene }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusTypes[row.status] ?? 'info'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="model" label="模型" width="140">
          <template #default="{ row }">{{ row.model || '-' }}</template>
        </el-table-column>
        <el-table-column label="耗时" width="90">
          <template #default="{ row }">{{ row.latencyMs != null ? `${row.latencyMs}ms` : '-' }}</template>
        </el-table-column>
        <el-table-column prop="degradedReason" label="降级原因" width="150">
          <template #default="{ row }">{{ row.degradedReason || '-' }}</template>
        </el-table-column>
        <el-table-column prop="clientType" label="客户端" width="80" />
        <el-table-column label="时间">
          <template #default="{ row }">{{ row.createdAt?.replace('T', ' ').slice(0, 19) }}</template>
        </el-table-column>
        <template #empty><el-empty description="暂无记录" /></template>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.ai-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.error-bar {
  margin-bottom: 4px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.metric-cards .metric {
  font-size: 22px;
  font-weight: 600;
}
.metric-cards .label,
.token-card .label {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.token-card .range {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
