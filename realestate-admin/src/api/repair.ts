import request, { del, get, post, upload } from '@/utils/request'

import type { PageResult } from '@/types/api'
import type { RepairDetail, RepairListItem, RepairMedia, RepairQueryParams } from '@/types/repair'

/** 报修分页列表（ADMIN 全部） */
export function listRepairs(params: RepairQueryParams): Promise<PageResult<RepairListItem>> {
  return get<PageResult<RepairListItem>>('/repairs', { params })
}

export function getRepair(id: number): Promise<RepairDetail> {
  return get<RepairDetail>(`/repairs/${id}`)
}

export function acceptRepair(id: number, reason?: string): Promise<RepairDetail> {
  return post<RepairDetail>(`/repairs/${id}/accept`, { reason })
}

export function startRepair(id: number, reason?: string): Promise<RepairDetail> {
  return post<RepairDetail>(`/repairs/${id}/start`, { reason })
}

export function resolveRepair(id: number, reason?: string): Promise<RepairDetail> {
  return post<RepairDetail>(`/repairs/${id}/resolve`, { reason })
}

export function closeRepair(id: number, reason?: string): Promise<RepairDetail> {
  return post<RepairDetail>(`/repairs/${id}/close`, { reason })
}

export function uploadRepairMedia(id: number, file: File): Promise<RepairMedia> {
  return upload<RepairMedia>(`/repairs/${id}/media`, file)
}

/** 报修图片受鉴权保护，必须带 Token 取 Blob 后本地预览，不能直接使用 img src */
export async function fetchRepairMedia(id: number, mediaId: number): Promise<Blob> {
  const response = await request.get(`/repairs/${id}/media/${mediaId}`, { responseType: 'blob' })
  return response.data as Blob
}

export function deleteRepairMedia(id: number, mediaId: number): Promise<void> {
  return del<void>(`/repairs/${id}/media/${mediaId}`)
}
