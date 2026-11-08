import type { PageParams } from '@/types/api'

export type HouseType = 'RENT' | 'SALE'
export type HouseStatus = 'PENDING' | 'PUBLISHED' | 'OFFLINE'

export interface HouseMedia {
  id: number
  mediaType: 'IMAGE' | 'VIDEO'
  url: string
  sort: number
}

export interface HouseListItem {
  id: number
  title: string
  houseType: HouseType
  status: HouseStatus
  community: string
  region: string
  price: number
  area: number
  rooms: number
  bathrooms: number
  floor: string | null
  coverUrl: string | null
  ownerId: number | null
  ownerName: string | null
  createdAt: string
}

export interface HouseDetail extends HouseListItem {
  address: string
  description: string | null
  media: HouseMedia[]
  ownerPhone: string | null
  version: number
  updatedAt: string
  favorited: boolean
}

export interface HouseQueryParams extends PageParams {
  status?: string
  houseType?: string
  region?: string
  minPrice?: number
  maxPrice?: number
  minArea?: number
  maxArea?: number
  keyword?: string
  sort?: string
}

export interface HouseFormPayload {
  title: string
  houseType: HouseType
  community: string
  region: string
  address: string
  price: number
  area: number
  rooms: number
  bathrooms: number
  floor?: string
  description?: string
  ownerId?: number
  version?: number
}

export interface HouseMediaPayload {
  url: string
  mediaType: 'IMAGE' | 'VIDEO'
  sort?: number
}

/** 排序白名单（与后端 HouseQuery 保持一致） */
export const HOUSE_SORT_OPTIONS: Array<{ label: string; value: string }> = [
  { label: '最新发布', value: 'created_desc' },
  { label: '价格从低到高', value: 'price_asc' },
  { label: '价格从高到低', value: 'price_desc' },
  { label: '面积从小到大', value: 'area_asc' },
  { label: '面积从大到小', value: 'area_desc' },
]
