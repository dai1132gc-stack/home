import type { PageParams } from '@/types/api'

export type ContractStatus =
  | 'DRAFT'
  | 'PENDING_SIGN'
  | 'ACTIVE'
  | 'EXPIRED'
  | 'TERMINATED'
  | 'CANCELLED'

export type PaymentCycle = 'MONTHLY' | 'QUARTERLY' | 'YEARLY'

export type TransactionStatus = 'PENDING' | 'DONE' | 'CANCELLED'

export interface ContractListItem {
  id: number
  contractNo: string
  title: string
  houseId: number
  houseTitle: string | null
  customerId: number
  customerName: string | null
  amount: number
  deposit: number
  paymentCycle: PaymentCycle
  paymentCycleLabel: string | null
  startDate: string
  endDate: string
  status: ContractStatus
  statusLabel: string
  pdfReady: boolean
  signedAt: string | null
  createdAt: string
}

export interface ContractParty {
  partyType: 'LESSOR' | 'LESSEE' | 'AGENT'
  partyTypeLabel: string
  name: string
  phone: string | null
  idCardMasked: string | null
}

export interface TransactionItem {
  id: number
  node: 'DEPOSIT' | 'SIGN' | 'HANDOVER'
  nodeLabel: string
  status: TransactionStatus
  statusLabel: string
  doneAt: string | null
  remark: string | null
  updatedAt: string
}

export interface ContractStatusLog {
  id: number
  fromStatus: string | null
  fromStatusLabel: string | null
  toStatus: string
  toStatusLabel: string | null
  operatorId: number | null
  reason: string | null
  createdAt: string
}

export interface ContractDetail extends ContractListItem {
  houseType: string | null
  community: string | null
  region: string | null
  address: string | null
  area: number | null
  rooms: number | null
  bathrooms: number | null
  floor: string | null
  ownerId: number | null
  amountInWords: string
  templateVersion: string
  pdfAvailable: boolean
  version: number
  updatedAt: string
  parties: ContractParty[]
  transactions: TransactionItem[]
  statusLogs: ContractStatusLog[]
}

export interface ContractQueryParams extends PageParams {
  status?: string
  houseId?: number
  customerId?: number
  keyword?: string
}

export interface ContractCreatePayload {
  houseId: number
  customerId: number
  title: string
  amount: number
  deposit: number
  paymentCycle: PaymentCycle
  startDate: string
  endDate: string
  lessorName?: string
  lessorPhone?: string
  customerName?: string
  customerPhone?: string
  lessorIdCard?: string
  customerIdCard?: string
}

export interface ContractUpdatePayload {
  title?: string
  amount?: number
  deposit?: number
  paymentCycle?: PaymentCycle
  startDate?: string
  endDate?: string
  version: number
}

export const CONTRACT_STATUS_OPTIONS: Array<{ label: string; value: ContractStatus }> = [
  { label: '草稿', value: 'DRAFT' },
  { label: '待签署', value: 'PENDING_SIGN' },
  { label: '生效中', value: 'ACTIVE' },
  { label: '已到期', value: 'EXPIRED' },
  { label: '已终止', value: 'TERMINATED' },
  { label: '已作废', value: 'CANCELLED' },
]

export const PAYMENT_CYCLE_OPTIONS: Array<{ label: string; value: PaymentCycle }> = [
  { label: '月付', value: 'MONTHLY' },
  { label: '季付', value: 'QUARTERLY' },
  { label: '年付', value: 'YEARLY' },
]

export function contractStatusLabel(status: string): string {
  return CONTRACT_STATUS_OPTIONS.find((item) => item.value === status)?.label ?? status
}

export function contractStatusTagType(status: string): 'success' | 'info' | 'warning' | 'danger' {
  switch (status) {
    case 'ACTIVE':
      return 'success'
    case 'PENDING_SIGN':
      return 'warning'
    case 'TERMINATED':
    case 'CANCELLED':
      return 'danger'
    case 'EXPIRED':
      return 'info'
    default:
      return 'info'
  }
}
