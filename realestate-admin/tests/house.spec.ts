import { describe, expect, it } from 'vitest'

import {
  canOffline,
  canPublish,
  formatArea,
  formatPrice,
  houseTypeLabel,
  statusLabel,
  statusTagType,
} from '@/utils/house'

describe('房源展示工具', () => {
  it('状态标签与配色与后端枚举一致', () => {
    expect(statusLabel('PENDING')).toBe('待发布')
    expect(statusLabel('PUBLISHED')).toBe('已发布')
    expect(statusLabel('OFFLINE')).toBe('已下架')
    expect(statusLabel('UNKNOWN')).toBe('UNKNOWN')

    expect(statusTagType('PUBLISHED')).toBe('success')
    expect(statusTagType('PENDING')).toBe('warning')
    expect(statusTagType('OFFLINE')).toBe('info')
    expect(statusTagType('UNKNOWN')).toBe('info')
  })

  it('出租按月显示价格，出售显示总价', () => {
    expect(formatPrice(4500, 'RENT')).toBe('¥4,500/月')
    expect(formatPrice(1200000, 'SALE')).toBe('¥1,200,000')
  })

  it('面积格式化为两位小数以内并带单位', () => {
    expect(formatArea(89.5)).toBe('89.5 ㎡')
    expect(formatArea(0)).toBe('0 ㎡')
  })

  it('类型标签', () => {
    expect(houseTypeLabel('RENT')).toBe('出租')
    expect(houseTypeLabel('SALE')).toBe('出售')
  })

  it('发布/下架按钮可用性与状态机一致', () => {
    expect(canPublish('PENDING')).toBe(true)
    expect(canPublish('OFFLINE')).toBe(true)
    expect(canPublish('PUBLISHED')).toBe(false)

    expect(canOffline('PUBLISHED')).toBe(true)
    expect(canOffline('PENDING')).toBe(false)
    expect(canOffline('OFFLINE')).toBe(false)
  })
})
