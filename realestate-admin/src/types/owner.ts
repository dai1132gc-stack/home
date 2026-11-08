import type { PageParams } from '@/types/api'

export type OwnerType = 'LANDLORD' | 'CUSTOMER_LEAD'

export interface OwnerVO {
  id: number
  name: string
  phone: string
  type: OwnerType
  typeLabel: string
  remark: string | null
  version: number
  createdAt: string
  updatedAt: string
}

export interface OwnerQueryParams extends PageParams {
  keyword?: string
  type?: string
}

export interface OwnerFormPayload {
  name: string
  phone: string
  type: OwnerType
  remark?: string
  version?: number
}

export type FollowType = 'PHONE' | 'WECHAT' | 'VISIT' | 'OTHER'

export interface CustomerFollow {
  id: number
  ownerId: number
  content: string
  followType: FollowType
  followTypeLabel: string
  nextFollowAt: string | null
  createdBy: number | null
  createdByName: string | null
  createdAt: string
}

export interface CustomerFollowPayload {
  content: string
  followType: FollowType
  nextFollowAt?: string
}

export const FOLLOW_TYPE_OPTIONS: Array<{ label: string; value: FollowType }> = [
  { label: '电话', value: 'PHONE' },
  { label: '微信', value: 'WECHAT' },
  { label: '到访', value: 'VISIT' },
  { label: '其他', value: 'OTHER' },
]
