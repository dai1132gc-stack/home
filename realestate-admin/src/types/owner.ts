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
