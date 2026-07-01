/**
 * @file 售后管理权限码 常量单元测试
 * @description 验证售后模块（质保期、运维工单、满意度）权限码符合
 *              module:resource:action 三段式命名规范，且所有权限码全局唯一。
 * @module constants/__tests__/aftersalesPermissionCodes
 */
import { describe, it, expect } from 'vitest'
import { PC } from '@/constants/permissionCodes'

describe('售后管理权限码 (aftersales)', () => {
  it('质保期权限码应符合 module:resource:action 三段式', () => {
    expect(PC.AFTERSALES_WARRANTY_LIST).toBe('aftersales:warranty:list')
    expect(PC.AFTERSALES_WARRANTY_CREATE).toBe('aftersales:warranty:create')
    expect(PC.AFTERSALES_WARRANTY_TERMINATE).toBe('aftersales:warranty:terminate')
    expect(PC.AFTERSALES_WARRANTY_SCAN).toBe('aftersales:warranty:scan')
  })

  it('运维工单权限码应符合 module:resource:action 三段式', () => {
    expect(PC.AFTERSALES_OPS_TICKET_LIST).toBe('aftersales:ops-ticket:list')
    expect(PC.AFTERSALES_OPS_TICKET_CREATE).toBe('aftersales:ops-ticket:create')
    expect(PC.AFTERSALES_OPS_TICKET_ASSIGN).toBe('aftersales:ops-ticket:assign')
    expect(PC.AFTERSALES_OPS_TICKET_STATUS).toBe('aftersales:ops-ticket:status')
    expect(PC.AFTERSALES_OPS_TICKET_EVALUATE).toBe('aftersales:ops-ticket:evaluate')
    expect(PC.AFTERSALES_OPS_TICKET_SCAN).toBe('aftersales:ops-ticket:scan')
  })

  it('满意度权限码应符合 module:resource:action 三段式', () => {
    expect(PC.AFTERSALES_SATISFACTION_LIST).toBe('aftersales:satisfaction:list')
    expect(PC.AFTERSALES_SATISFACTION_SUBMIT).toBe('aftersales:satisfaction:submit')
    expect(PC.AFTERSALES_SATISFACTION_FOLLOWUP).toBe('aftersales:satisfaction:follow-up')
  })

  it('所有售后权限码应唯一', () => {
    const codes = [
      PC.AFTERSALES_WARRANTY_LIST,
      PC.AFTERSALES_WARRANTY_CREATE,
      PC.AFTERSALES_WARRANTY_TERMINATE,
      PC.AFTERSALES_WARRANTY_SCAN,
      PC.AFTERSALES_OPS_TICKET_LIST,
      PC.AFTERSALES_OPS_TICKET_CREATE,
      PC.AFTERSALES_OPS_TICKET_ASSIGN,
      PC.AFTERSALES_OPS_TICKET_STATUS,
      PC.AFTERSALES_OPS_TICKET_EVALUATE,
      PC.AFTERSALES_OPS_TICKET_SCAN,
      PC.AFTERSALES_SATISFACTION_LIST,
      PC.AFTERSALES_SATISFACTION_SUBMIT,
      PC.AFTERSALES_SATISFACTION_FOLLOWUP,
    ]
    expect(new Set(codes).size).toBe(codes.length)
  })
})
