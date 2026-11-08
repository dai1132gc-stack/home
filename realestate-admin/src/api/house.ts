import { del, get, patch, post } from '@/utils/request'

import type { PageResult } from '@/types/api'
import type {
  HouseDetail,
  HouseFormPayload,
  HouseListItem,
  HouseMedia,
  HouseMediaPayload,
  HouseQueryParams,
} from '@/types/house'

/** 房源分页列表（ADMIN 可见全部状态） */
export function listHouses(params: HouseQueryParams): Promise<PageResult<HouseListItem>> {
  return get<PageResult<HouseListItem>>('/houses', { params })
}

/** 房源详情 */
export function getHouse(id: number): Promise<HouseDetail> {
  return get<HouseDetail>(`/houses/${id}`)
}

/** 新建房源（后端固定 PENDING） */
export function createHouse(payload: HouseFormPayload): Promise<HouseDetail> {
  return post<HouseDetail>('/houses', payload)
}

/** 编辑房源（PATCH，携带 version 触发乐观锁） */
export function updateHouse(id: number, payload: HouseFormPayload): Promise<HouseDetail> {
  return patch<HouseDetail>(`/houses/${id}`, payload)
}

/** 发布（PENDING/OFFLINE → PUBLISHED） */
export function publishHouse(id: number): Promise<HouseDetail> {
  return post<HouseDetail>(`/houses/${id}/publish`, {})
}

/** 下架（PUBLISHED → OFFLINE） */
export function offlineHouse(id: number): Promise<HouseDetail> {
  return post<HouseDetail>(`/houses/${id}/offline`, {})
}

/** 逻辑删除 */
export function deleteHouse(id: number): Promise<void> {
  return del<void>(`/houses/${id}`)
}

/** 关联媒体到房源 */
export function addHouseMedia(id: number, payload: HouseMediaPayload): Promise<HouseMedia> {
  return post<HouseMedia>(`/houses/${id}/media`, payload)
}

/** 移除房源媒体 */
export function removeHouseMedia(id: number, mediaId: number): Promise<void> {
  return del<void>(`/houses/${id}/media/${mediaId}`)
}
