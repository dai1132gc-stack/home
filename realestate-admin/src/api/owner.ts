import { del, get, patch, post } from '@/utils/request'

import type { PageResult } from '@/types/api'
import type {
  CustomerFollow,
  CustomerFollowPayload,
  OwnerFormPayload,
  OwnerQueryParams,
  OwnerVO,
} from '@/types/owner'

/** 客源分页列表（ADMIN） */
export function listOwners(params: OwnerQueryParams): Promise<PageResult<OwnerVO>> {
  return get<PageResult<OwnerVO>>('/owners', { params })
}

/** 客源详情（ADMIN，手机号明文） */
export function getOwner(id: number): Promise<OwnerVO> {
  return get<OwnerVO>(`/owners/${id}`)
}

export function createOwner(payload: OwnerFormPayload): Promise<OwnerVO> {
  return post<OwnerVO>('/owners', payload)
}

export function updateOwner(id: number, payload: OwnerFormPayload): Promise<OwnerVO> {
  return patch<OwnerVO>(`/owners/${id}`, payload)
}

export function deleteOwner(id: number): Promise<void> {
  return del<void>(`/owners/${id}`)
}

/** 客源跟进（ADMIN） */
export function listFollows(
  ownerId: number,
  params: { page?: number; size?: number },
): Promise<PageResult<CustomerFollow>> {
  return get<PageResult<CustomerFollow>>(`/owners/${ownerId}/follows`, { params })
}

export function createFollow(ownerId: number, payload: CustomerFollowPayload): Promise<CustomerFollow> {
  return post<CustomerFollow>(`/owners/${ownerId}/follows`, payload)
}
