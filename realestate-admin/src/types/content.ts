import type { PageParams } from '@/types/api'

export type GuideCategory = 'GUIDE' | 'NETWORK_GUIDE' | 'NOTICE'

export type ContentStatus = 'DRAFT' | 'PUBLISHED' | 'OFFLINE'

export type PolicyType = 'POLICY' | 'NOTICE'

export interface GuideListItem {
  id: number
  title: string
  category: GuideCategory
  categoryLabel: string
  summary: string | null
  status: ContentStatus
  statusLabel: string
  sort: number
  version: number
  createdAt: string
  updatedAt: string
}

export interface GuideDetail extends GuideListItem {
  content: string
}

export interface PolicyListItem {
  id: number
  title: string
  docNo: string | null
  docType: PolicyType
  docTypeLabel: string
  source: string | null
  publishDate: string | null
  effectiveDate: string | null
  status: ContentStatus
  statusLabel: string
  hasAttachment: boolean
  version: number
  createdAt: string
  updatedAt: string
}

export interface PolicyDetail extends PolicyListItem {
  content: string
}

export interface ContentQueryParams extends PageParams {
  category?: string
  status?: string
  keyword?: string
}

export interface GuidePayload {
  title: string
  category: GuideCategory
  summary?: string
  content: string
  sort?: number
}

export interface PolicyPayload {
  title: string
  docNo?: string
  docType: PolicyType
  source?: string
  publishDate?: string
  effectiveDate?: string
  content: string
}

export const GUIDE_CATEGORY_OPTIONS: Array<{ label: string; value: GuideCategory }> = [
  { label: '租房教程', value: 'GUIDE' },
  { label: '网签指引', value: 'NETWORK_GUIDE' },
  { label: '公告', value: 'NOTICE' },
]

export const POLICY_TYPE_OPTIONS: Array<{ label: string; value: PolicyType }> = [
  { label: '政策', value: 'POLICY' },
  { label: '通知', value: 'NOTICE' },
]

export const CONTENT_STATUS_OPTIONS: Array<{ label: string; value: ContentStatus }> = [
  { label: '草稿', value: 'DRAFT' },
  { label: '已发布', value: 'PUBLISHED' },
  { label: '已下架', value: 'OFFLINE' },
]

export function contentStatusLabel(status: string): string {
  return CONTENT_STATUS_OPTIONS.find((item) => item.value === status)?.label ?? status
}

export function contentStatusTagType(status: string): 'success' | 'info' | 'warning' {
  switch (status) {
    case 'PUBLISHED':
      return 'success'
    case 'DRAFT':
      return 'warning'
    default:
      return 'info'
  }
}

export function guideCategoryLabel(category: string): string {
  return GUIDE_CATEGORY_OPTIONS.find((item) => item.value === category)?.label ?? category
}

export function policyTypeLabel(type: string): string {
  return POLICY_TYPE_OPTIONS.find((item) => item.value === type)?.label ?? type
}
