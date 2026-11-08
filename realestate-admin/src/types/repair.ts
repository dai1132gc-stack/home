import type { PageParams } from '@/types/api'

export type RepairStatus =
  | 'SUBMITTED'
  | 'ACCEPTED'
  | 'PROCESSING'
  | 'RESOLVED'
  | 'CLOSED'
  | 'CANCELLED'
  | 'REOPENED'

export type RepairCategory = 'PLUMBING' | 'ELECTRICAL' | 'APPLIANCE' | 'STRUCTURE' | 'OTHER'

export type RepairUrgency = 'NORMAL' | 'URGENT'

export interface RepairListItem {
  id: number
  ticketNo: string
  houseId: number
  houseTitle: string | null
  contractId: number | null
  contractNo: string | null
  customerId: number
  customerName: string | null
  category: RepairCategory
  categoryLabel: string
  urgency: RepairUrgency
  urgencyLabel: string
  title: string
  status: RepairStatus
  statusLabel: string
  contactPhone: string
  preferredVisitTime: string | null
  createdAt: string
  updatedAt: string
}

export interface RepairMedia {
  id: number
  previewUrl: string
  mediaType: string
  sort: number
  createdAt: string
}

export interface RepairStatusLog {
  id: number
  fromStatus: string | null
  fromStatusLabel: string | null
  toStatus: string
  toStatusLabel: string
  operatorId: number | null
  reason: string | null
  createdAt: string
}

export interface RepairDetail extends RepairListItem {
  houseAddress: string | null
  description: string
  version: number
  media: RepairMedia[]
  statusLogs: RepairStatusLog[]
}

export interface RepairQueryParams extends PageParams {
  status?: string
  category?: string
  keyword?: string
  fromTime?: string
  toTime?: string
}

export const REPAIR_STATUS_OPTIONS: Array<{ label: string; value: RepairStatus }> = [
  { label: '待受理', value: 'SUBMITTED' },
  { label: '已受理', value: 'ACCEPTED' },
  { label: '处理中', value: 'PROCESSING' },
  { label: '已解决', value: 'RESOLVED' },
  { label: '已关闭', value: 'CLOSED' },
  { label: '已撤销', value: 'CANCELLED' },
  { label: '已重开', value: 'REOPENED' },
]

export const REPAIR_CATEGORY_OPTIONS: Array<{ label: string; value: RepairCategory }> = [
  { label: '水暖管道', value: 'PLUMBING' },
  { label: '电路照明', value: 'ELECTRICAL' },
  { label: '家电设备', value: 'APPLIANCE' },
  { label: '房屋结构', value: 'STRUCTURE' },
  { label: '其他', value: 'OTHER' },
]

export function repairStatusLabel(status: string): string {
  return REPAIR_STATUS_OPTIONS.find((item) => item.value === status)?.label ?? status
}

export function repairStatusTagType(status: string): 'success' | 'info' | 'warning' | 'danger' | 'primary' {
  switch (status) {
    case 'SUBMITTED':
      return 'warning'
    case 'ACCEPTED':
    case 'REOPENED':
      return 'primary'
    case 'PROCESSING':
      return 'warning'
    case 'RESOLVED':
      return 'success'
    case 'CLOSED':
      return 'info'
    case 'CANCELLED':
      return 'danger'
    default:
      return 'info'
  }
}

export function repairCategoryLabel(category: string): string {
  return REPAIR_CATEGORY_OPTIONS.find((item) => item.value === category)?.label ?? category
}
