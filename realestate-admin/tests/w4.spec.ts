import { describe, expect, it } from 'vitest'

import { LOAN_TYPE_OPTIONS, REPAYMENT_METHOD_OPTIONS } from '@/types/calculator'
import {
  contentStatusLabel,
  contentStatusTagType,
  GUIDE_CATEGORY_OPTIONS,
  guideCategoryLabel,
  POLICY_TYPE_OPTIONS,
} from '@/types/content'
import { FOLLOW_TYPE_OPTIONS } from '@/types/owner'
import {
  REPAIR_CATEGORY_OPTIONS,
  REPAIR_STATUS_OPTIONS,
  repairCategoryLabel,
  repairStatusLabel,
  repairStatusTagType,
} from '@/types/repair'

describe('W4 报修/内容/测算前端字典', () => {
  it('报修状态标签覆盖七态', () => {
    expect(REPAIR_STATUS_OPTIONS).toHaveLength(7)
    expect(repairStatusLabel('SUBMITTED')).toBe('待受理')
    expect(repairStatusLabel('REOPENED')).toBe('已重开')
    expect(repairStatusLabel('CANCELLED')).toBe('已撤销')
    expect(repairStatusLabel('UNKNOWN')).toBe('UNKNOWN')
  })

  it('报修状态配色区分处理中与已解决', () => {
    expect(repairStatusTagType('SUBMITTED')).toBe('warning')
    expect(repairStatusTagType('PROCESSING')).toBe('warning')
    expect(repairStatusTagType('RESOLVED')).toBe('success')
    expect(repairStatusTagType('CANCELLED')).toBe('danger')
  })

  it('报修分类与后端枚举一致', () => {
    expect(REPAIR_CATEGORY_OPTIONS.map((item) => item.value)).toEqual([
      'PLUMBING',
      'ELECTRICAL',
      'APPLIANCE',
      'STRUCTURE',
      'OTHER',
    ])
    expect(repairCategoryLabel('PLUMBING')).toBe('水暖管道')
  })

  it('内容状态与分类标签', () => {
    expect(contentStatusLabel('DRAFT')).toBe('草稿')
    expect(contentStatusLabel('OFFLINE')).toBe('已下架')
    expect(contentStatusTagType('PUBLISHED')).toBe('success')
    expect(GUIDE_CATEGORY_OPTIONS.map((item) => item.value)).toEqual(['GUIDE', 'NETWORK_GUIDE', 'NOTICE'])
    expect(guideCategoryLabel('NETWORK_GUIDE')).toBe('网签指引')
    expect(POLICY_TYPE_OPTIONS[0].label).toBe('政策')
  })

  it('测算与跟进选项', () => {
    expect(LOAN_TYPE_OPTIONS.map((item) => item.value)).toEqual(['COMMERCIAL', 'FUND'])
    expect(REPAYMENT_METHOD_OPTIONS.map((item) => item.label)).toEqual(['等额本息', '等额本金'])
    expect(FOLLOW_TYPE_OPTIONS.map((item) => item.value)).toEqual(['PHONE', 'WECHAT', 'VISIT', 'OTHER'])
  })
})
