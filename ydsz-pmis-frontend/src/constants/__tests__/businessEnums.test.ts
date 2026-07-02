/**
 * @file businessEnums 业务枚举常量测试
 * @module constants/__tests__/businessEnums
 */
import { describe, it, expect } from 'vitest'
import {
  APPROVAL_STATUS,
  EXECUTION_STATUS,
  WBS_TASK_STATUS,
  PRIORITY_LEVEL,
  CHANGE_STATUS,
  INVOICE_STATUS,
  OPS_TICKET_STATUS,
  STATUS_TAG_TYPE,
  toOptions,
  getLabel,
} from '@/constants/businessEnums'

describe('businessEnums 业务枚举常量', () => {
  describe('枚举完整性', () => {
    it('APPROVAL_STATUS 包含 DRAFT/SUBMITTED/APPROVED/REJECTED/ARCHIVED', () => {
      expect(Object.keys(APPROVAL_STATUS)).toHaveLength(5)
      expect(APPROVAL_STATUS.DRAFT.value).toBe('DRAFT')
      expect(APPROVAL_STATUS.APPROVED.value).toBe('APPROVED')
    })

    it('WBS_TASK_STATUS 包含 6 种状态', () => {
      expect(Object.keys(WBS_TASK_STATUS)).toHaveLength(6)
      expect(WBS_TASK_STATUS.PLANNED.value).toBe('PLANNED')
      expect(WBS_TASK_STATUS.COMPLETED.value).toBe('COMPLETED')
    })

    it('PRIORITY_LEVEL 包含 LOW/NORMAL/HIGH/URGENT', () => {
      expect(Object.keys(PRIORITY_LEVEL)).toHaveLength(4)
      expect(PRIORITY_LEVEL.URGENT.value).toBe('URGENT')
    })

    it('CHANGE_STATUS 包含 7 种状态', () => {
      expect(Object.keys(CHANGE_STATUS)).toHaveLength(7)
      expect(CHANGE_STATUS.EXECUTING.value).toBe('EXECUTING')
    })

    it('INVOICE_STATUS 包含 DRAFT/ISSUED/RED_REVERSED/CANCELLED', () => {
      expect(Object.keys(INVOICE_STATUS)).toHaveLength(4)
      expect(INVOICE_STATUS.RED_REVERSED.value).toBe('RED_REVERSED')
    })

    it('OPS_TICKET_STATUS 包含 5 种状态', () => {
      expect(Object.keys(OPS_TICKET_STATUS)).toHaveLength(5)
      expect(OPS_TICKET_STATUS.REOPENED.value).toBe('REOPENED')
    })
  })

  describe('STATUS_TAG_TYPE 类型映射', () => {
    it('成功状态映射为 success', () => {
      expect(STATUS_TAG_TYPE.COMPLETED).toBe('success')
      expect(STATUS_TAG_TYPE.APPROVED).toBe('success')
      expect(STATUS_TAG_TYPE.RESOLVED).toBe('success')
    })

    it('危险状态映射为 danger', () => {
      expect(STATUS_TAG_TYPE.REJECTED).toBe('danger')
      expect(STATUS_TAG_TYPE.BLOCKED).toBe('danger')
      expect(STATUS_TAG_TYPE.RED).toBe('danger')
    })

    it('草稿状态映射为 info', () => {
      expect(STATUS_TAG_TYPE.DRAFT).toBe('info')
      expect(STATUS_TAG_TYPE.ARCHIVED).toBe('info')
    })
  })

  describe('toOptions 工具函数', () => {
    it('将枚举 Map 转换为 OptionVO 数组', () => {
      const options = toOptions(EXECUTION_STATUS)
      expect(options).toHaveLength(4)
      expect(options[0]).toHaveProperty('label')
      expect(options[0]).toHaveProperty('value')
    })

    it('空 Map 返回空数组', () => {
      expect(toOptions({})).toHaveLength(0)
    })
  })

  describe('getLabel 工具函数', () => {
    it('已知 value 返回 label', () => {
      expect(getLabel(WBS_TASK_STATUS, 'COMPLETED')).toBe('已完成')
      expect(getLabel(PRIORITY_LEVEL, 'URGENT')).toBe('紧急')
    })

    it('未知 value 返回回退值', () => {
      expect(getLabel(WBS_TASK_STATUS, 'UNKNOWN')).toBe('-')
      expect(getLabel(WBS_TASK_STATUS, 'UNKNOWN', '未知')).toBe('未知')
    })

    it('null/undefined 返回回退值', () => {
      expect(getLabel(WBS_TASK_STATUS, null)).toBe('-')
      expect(getLabel(WBS_TASK_STATUS, undefined)).toBe('-')
    })
  })

  describe('所有枚举的 label 非空', () => {
    const allEnums = [
      APPROVAL_STATUS, EXECUTION_STATUS, WBS_TASK_STATUS, PRIORITY_LEVEL,
      CHANGE_STATUS, INVOICE_STATUS, OPS_TICKET_STATUS,
    ]
    for (const enumMap of allEnums) {
      const name = Object.keys(enumMap)[0] || 'unknown'
      it(`${name} 所有的 label 和 value 都非空字符串`, () => {
        for (const item of Object.values(enumMap)) {
          expect(item.label).toBeTruthy()
          expect(item.value).toBeTruthy()
        }
      })
    }
  })
})
