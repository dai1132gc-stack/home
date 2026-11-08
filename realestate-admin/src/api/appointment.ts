import { get, post } from '@/utils/request'

import type { PageResult } from '@/types/api'
import type { AppointmentItem, AppointmentQueryParams } from '@/types/appointment'

/** 预约分页列表（ADMIN 全部） */
export function listAppointments(params: AppointmentQueryParams): Promise<PageResult<AppointmentItem>> {
  return get<PageResult<AppointmentItem>>('/appointments', { params })
}

/** 预约详情 */
export function getAppointment(id: number): Promise<AppointmentItem> {
  return get<AppointmentItem>(`/appointments/${id}`)
}

/** 确认预约（PENDING → CONFIRMED，房源置 RESERVED） */
export function confirmAppointment(id: number): Promise<AppointmentItem> {
  return post<AppointmentItem>(`/appointments/${id}/confirm`, {})
}

/** 取消预约 */
export function cancelAppointment(id: number): Promise<AppointmentItem> {
  return post<AppointmentItem>(`/appointments/${id}/cancel`, {})
}

/** 标记带看完成 */
export function completeAppointment(id: number): Promise<AppointmentItem> {
  return post<AppointmentItem>(`/appointments/${id}/complete`, {})
}

/** 标记爽约 */
export function noShowAppointment(id: number): Promise<AppointmentItem> {
  return post<AppointmentItem>(`/appointments/${id}/no-show`, {})
}
