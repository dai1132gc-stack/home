import type { HouseStatus, HouseType } from '@/types/house'

const STATUS_LABEL: Record<HouseStatus, string> = {
  PENDING: '待发布',
  PUBLISHED: '已发布',
  OFFLINE: '已下架',
}

const TYPE_LABEL: Record<HouseType, string> = {
  RENT: '出租',
  SALE: '出售',
}

export type TagType = 'success' | 'info' | 'warning' | 'danger'

export function statusLabel(status: string): string {
  return STATUS_LABEL[status as HouseStatus] ?? status
}

export function statusTagType(status: string): TagType {
  switch (status) {
    case 'PUBLISHED':
      return 'success'
    case 'PENDING':
      return 'warning'
    case 'OFFLINE':
      return 'info'
    default:
      return 'info'
  }
}

export function houseTypeLabel(type: string): string {
  return TYPE_LABEL[type as HouseType] ?? type
}

/** 出租按月显示，出售显示总价 */
export function formatPrice(price: number, type: string): string {
  const amount = Number(price ?? 0).toLocaleString('zh-CN', { maximumFractionDigits: 2 })
  return type === 'RENT' ? `¥${amount}/月` : `¥${amount}`
}

export function formatArea(area: number): string {
  return `${Number(area ?? 0).toLocaleString('zh-CN', { maximumFractionDigits: 2 })} ㎡`
}

/** 发布/下架按钮的可用状态判断，避免前端发出必然 409 的请求 */
export function canPublish(status: string): boolean {
  return status === 'PENDING' || status === 'OFFLINE'
}

export function canOffline(status: string): boolean {
  return status === 'PUBLISHED'
}
