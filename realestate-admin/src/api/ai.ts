import { get, post } from '@/utils/request'

import type { AiMetrics, AiStatus, ReindexResult } from '@/types/ai'

// W6 智能服务（ADMIN）

export function fetchAiStatus(): Promise<AiStatus> {
  return get<AiStatus>('/ai/status')
}

export function fetchAiMetrics(params?: { from?: string; to?: string }): Promise<AiMetrics> {
  return get<AiMetrics>('/admin/ai/metrics', { params })
}

export function reindexPolicy(id: number): Promise<ReindexResult> {
  return post<ReindexResult>(`/admin/policies/${id}/reindex`, {})
}

export function reindexAllPolicies(): Promise<ReindexResult> {
  return post<ReindexResult>('/admin/ai/reindex', {})
}
