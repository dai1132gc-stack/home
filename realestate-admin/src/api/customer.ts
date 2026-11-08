import { get } from '@/utils/request'

import type { PageResult } from '@/types/api'
import type { CustomerItem, CustomerQueryParams } from '@/types/customer'

/** 客户账号列表（ADMIN）：合同起草时选择承租方 */
export function listCustomers(params: CustomerQueryParams): Promise<PageResult<CustomerItem>> {
  return get<PageResult<CustomerItem>>('/customers', { params })
}
