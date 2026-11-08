import { describe, expect, it } from 'vitest'

import { contractStatusLabel, contractStatusTagType, PAYMENT_CYCLE_OPTIONS } from '@/types/contract'
import { canOffline, canPublish, statusLabel, statusTagType } from '@/utils/house'

describe('W3 合同状态与房源状态扩展', () => {
  it('合同状态中文标签与后端枚举一致', () => {
    expect(contractStatusLabel('DRAFT')).toBe('草稿')
    expect(contractStatusLabel('PENDING_SIGN')).toBe('待签署')
    expect(contractStatusLabel('ACTIVE')).toBe('生效中')
    expect(contractStatusLabel('EXPIRED')).toBe('已到期')
    expect(contractStatusLabel('TERMINATED')).toBe('已终止')
    expect(contractStatusLabel('CANCELLED')).toBe('已作废')
    expect(contractStatusLabel('UNKNOWN')).toBe('UNKNOWN')
  })

  it('合同状态配色区分待签与生效', () => {
    expect(contractStatusTagType('PENDING_SIGN')).toBe('warning')
    expect(contractStatusTagType('ACTIVE')).toBe('success')
    expect(contractStatusTagType('TERMINATED')).toBe('danger')
    expect(contractStatusTagType('DRAFT')).toBe('info')
  })

  it('付款周期选项与后端 PaymentCycle 一致', () => {
    expect(PAYMENT_CYCLE_OPTIONS.map((item) => item.value)).toEqual(['MONTHLY', 'QUARTERLY', 'YEARLY'])
    expect(PAYMENT_CYCLE_OPTIONS[0].label).toBe('月付')
  })

  it('房源新增 RESERVED / CONTRACTED 状态展示', () => {
    expect(statusLabel('RESERVED')).toBe('已预定')
    expect(statusLabel('CONTRACTED')).toBe('已签约')
    expect(statusTagType('RESERVED')).toBe('primary')
    expect(statusTagType('CONTRACTED')).toBe('danger')
  })

  it('预定/签约房源不可直接发布或下架', () => {
    expect(canPublish('RESERVED')).toBe(false)
    expect(canPublish('CONTRACTED')).toBe(false)
    expect(canOffline('RESERVED')).toBe(false)
    expect(canOffline('CONTRACTED')).toBe(false)
    expect(canPublish('PENDING')).toBe(true)
    expect(canOffline('PUBLISHED')).toBe(true)
  })
})
