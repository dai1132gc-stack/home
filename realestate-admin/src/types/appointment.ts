import type { PageParams } from '@/types/api'

export type AppointmentStatus = 'PENDING' | 'CONFIRMED' | 'COMPLETED' | 'CANCELLED' | 'NO_SHOW'

export interface AppointmentItem {
  id: number
  houseId: number
  houseTitle: string | null
  houseCommunity: string | null
  houseRegion: string | null
  customerId: number
  customerName: string | null
  contactPhone: string
  preferredTime: string
  remark: string | null
  status: AppointmentStatus
  statusLabel: string
  createdAt: string
  updatedAt: string
}

export interface AppointmentQueryParams extends PageParams {
  status?: string
  houseId?: number
  customerId?: number
  fromTime?: string
  toTime?: string
}

export const APPOINTMENT_STATUS_OPTIONS: Array<{ label: string; value: AppointmentStatus }> = [
  { label: '待确认', value: 'PENDING' },
  { label: '已确认', value: 'CONFIRMED' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '已取消', value: 'CANCELLED' },
  { label: '已爽约', value: 'NO_SHOW' },
]

export function appointmentStatusLabel(status: string): string {
  return APPOINTMENT_STATUS_OPTIONS.find((item) => item.value === status)?.label ?? status
}

export function appointmentStatusTagType(status: string): 'success' | 'info' | 'warning' | 'danger' {
  switch (status) {
    case 'CONFIRMED':
      return 'warning'
    case 'COMPLETED':
      return 'success'
    case 'CANCELLED':
      return 'info'
    case 'NO_SHOW':
      return 'danger'
    default:
      return 'info'
  }
}
