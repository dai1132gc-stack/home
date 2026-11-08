import request, { del, get, patch, post, upload } from '@/utils/request'

import type { PageResult } from '@/types/api'
import type {
  ContentQueryParams,
  GuideDetail,
  GuideListItem,
  GuidePayload,
  PolicyDetail,
  PolicyListItem,
  PolicyPayload,
} from '@/types/content'

// ---------- 教程 / 网签指引 / 公告 ----------

export function listGuides(params: ContentQueryParams): Promise<PageResult<GuideListItem>> {
  return get<PageResult<GuideListItem>>('/guides', { params })
}

export function getGuide(id: number): Promise<GuideDetail> {
  return get<GuideDetail>(`/guides/${id}`)
}

export function createGuide(payload: GuidePayload): Promise<GuideDetail> {
  return post<GuideDetail>('/guides', payload)
}

export function updateGuide(
  id: number,
  payload: Partial<GuidePayload> & { version: number },
): Promise<GuideDetail> {
  return patch<GuideDetail>(`/guides/${id}`, payload)
}

export function publishGuide(id: number): Promise<GuideDetail> {
  return post<GuideDetail>(`/guides/${id}/publish`, {})
}

export function offlineGuide(id: number): Promise<GuideDetail> {
  return post<GuideDetail>(`/guides/${id}/offline`, {})
}

export function deleteGuide(id: number): Promise<void> {
  return del<void>(`/guides/${id}`)
}

// ---------- 政策文档 ----------

export function listPolicies(params: ContentQueryParams): Promise<PageResult<PolicyListItem>> {
  return get<PageResult<PolicyListItem>>('/policies', { params })
}

export function getPolicy(id: number): Promise<PolicyDetail> {
  return get<PolicyDetail>(`/policies/${id}`)
}

export function createPolicy(payload: PolicyPayload): Promise<PolicyDetail> {
  return post<PolicyDetail>('/policies', payload)
}

export function updatePolicy(
  id: number,
  payload: Partial<PolicyPayload> & { version: number },
): Promise<PolicyDetail> {
  return patch<PolicyDetail>(`/policies/${id}`, payload)
}

export function publishPolicy(id: number): Promise<PolicyDetail> {
  return post<PolicyDetail>(`/policies/${id}/publish`, {})
}

export function offlinePolicy(id: number): Promise<PolicyDetail> {
  return post<PolicyDetail>(`/policies/${id}/offline`, {})
}

export function deletePolicy(id: number): Promise<void> {
  return del<void>(`/policies/${id}`)
}

export function uploadPolicyAttachment(id: number, file: File): Promise<PolicyDetail> {
  return upload<PolicyDetail>(`/policies/${id}/attachment`, file)
}

/** 政策附件为私有存储，需登录鉴权后取 Blob 下载 */
export async function fetchPolicyAttachment(id: number): Promise<Blob> {
  const response = await request.get(`/policies/${id}/attachment`, { responseType: 'blob' })
  return response.data as Blob
}
