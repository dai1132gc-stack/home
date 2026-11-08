import type { PageParams } from '@/types/api'

/** 客户账号（合同起草选择承租方） */
export interface CustomerItem {
  id: number
  username: string
  nickname: string | null
  phone: string | null
  status: string
  createdAt: string
}

export interface CustomerQueryParams extends PageParams {
  keyword?: string
}
