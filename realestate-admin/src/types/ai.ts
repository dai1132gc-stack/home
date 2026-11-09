// W6 智能服务：类型与后端 VO 对齐；不包含任何 Key/base-url/原始问答内容

export interface AiStatus {
  enabled: boolean
  chatModel: string | null
  embeddingModel: string | null
  indexedChunks: number
  disclaimer: string
  provider: string | null
}

export interface AiSceneMetrics {
  scene: string
  total: number
  success: number
  degraded: number
  failed: number
}

export interface AiRecentItem {
  scene: string
  status: string
  model: string | null
  latencyMs: number | null
  degradedReason: string | null
  clientType: string
  createdAt: string
}

export interface AiMetrics {
  total: number
  success: number
  degraded: number
  failed: number
  successRate: number | null
  avgLatencyMs: number | null
  p95LatencyMs: number | null
  promptTokens: number
  completionTokens: number
  byScene: AiSceneMetrics[]
  recent: AiRecentItem[]
  from: string
  to: string
}

export interface ReindexResult {
  policies?: number
  chunks: number
  failed?: number
}
