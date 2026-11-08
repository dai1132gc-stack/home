import request, { get, patch, post } from '@/utils/request'

import type { PageResult } from '@/types/api'
import type {
  ContractCreatePayload,
  ContractDetail,
  ContractListItem,
  ContractQueryParams,
  ContractUpdatePayload,
  TransactionItem,
} from '@/types/contract'

/** 合同分页列表 */
export function listContracts(params: ContractQueryParams): Promise<PageResult<ContractListItem>> {
  return get<PageResult<ContractListItem>>('/contracts', { params })
}

/** 合同详情 */
export function getContract(id: number): Promise<ContractDetail> {
  return get<ContractDetail>(`/contracts/${id}`)
}

/** 起草合同（ADMIN） */
export function createContract(payload: ContractCreatePayload): Promise<ContractDetail> {
  return post<ContractDetail>('/contracts', payload)
}

/** 编辑草稿（PATCH，携带 version） */
export function updateContract(id: number, payload: ContractUpdatePayload): Promise<ContractDetail> {
  return patch<ContractDetail>(`/contracts/${id}`, payload)
}

/** 提交待签 */
export function submitContract(id: number): Promise<ContractDetail> {
  return post<ContractDetail>(`/contracts/${id}/submit`, {})
}

/** 终止/作废 */
export function terminateContract(id: number, reason: string): Promise<ContractDetail> {
  return post<ContractDetail>(`/contracts/${id}/terminate`, { reason })
}

/** 交易节点列表 */
export function listTransactions(id: number): Promise<TransactionItem[]> {
  return get<TransactionItem[]>(`/contracts/${id}/transactions`)
}

/** 完成交易节点（ADMIN） */
export function doneTransaction(id: number, node: string, remark?: string): Promise<TransactionItem> {
  return post<TransactionItem>(`/contracts/${id}/transactions/${node}/done`, { remark })
}

/**
 * 合同 PDF：鉴权接口返回二进制，浏览器不能直接打开带 Authorization 的链接，
 * 因此先取 Blob 再预览/下载，避免产生公开 URL。
 */
export async function fetchContractPdf(id: number): Promise<Blob> {
  const response = await request.get(`/contracts/${id}/pdf`, { responseType: 'blob' })
  return response.data as Blob
}
