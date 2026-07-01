/**
 * @file AI Agent 权限码常量 单元测试
 * @description 验证 AI Agent / 编排 / 预测 相关权限码完整且符合 <module>:<resource>:<action> 三段式规范，
 *              覆盖新增权限码、ALL_PERMISSION_CODES 包含性及全局唯一性校验。
 * @module constants/__tests__/agentPermissionCodes
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
import { describe, it, expect } from 'vitest'
import { PC, ALL_PERMISSION_CODES } from '@/constants/permissionCodes'

describe('permissionCodes 批次17 增量校验', () => {
  it('AGENT_RUN / AGENT_HISTORY 已存在', () => {
    expect(PC.AGENT_RUN).toBe('agent:task:run')
    expect(PC.AGENT_HISTORY).toBe('agent:task:list')
  })

  it('AGENT_VIEW 新增', () => {
    expect(PC.AGENT_VIEW).toBe('agent:task:view')
  })

  it('AGENT_ORCHESTRATION_RUN / VIEW 新增', () => {
    expect(PC.AGENT_ORCHESTRATION_RUN).toBe('agent:orchestration:run')
    expect(PC.AGENT_ORCHESTRATION_VIEW).toBe('agent:orchestration:view')
  })

  it('AGENT_PREDICTION_VIEW 新增', () => {
    expect(PC.AGENT_PREDICTION_VIEW).toBe('agent:prediction:view')
  })

  it('新增权限码符合 <module>:<resource>:<action> 三段式命名规范', () => {
    const codes = [
      PC.AGENT_RUN,
      PC.AGENT_HISTORY,
      PC.AGENT_VIEW,
      PC.AGENT_ORCHESTRATION_RUN,
      PC.AGENT_ORCHESTRATION_VIEW,
      PC.AGENT_PREDICTION_VIEW,
    ]
    // 统一三段式: module:resource:action
    const PATTERN = /^[a-z][a-z0-9-]*:[a-z][a-z0-9-]*:[a-z][a-z0-9-]+$/
    for (const c of codes) {
      expect(c).toMatch(PATTERN)
    }
  })

  it('ALL_PERMISSION_CODES 包含新增项', () => {
    expect(ALL_PERMISSION_CODES).toContain(PC.AGENT_ORCHESTRATION_RUN)
    expect(ALL_PERMISSION_CODES).toContain(PC.AGENT_PREDICTION_VIEW)
  })

  it('权限码唯一性', () => {
    const set = new Set<string>(ALL_PERMISSION_CODES)
    expect(set.size).toBe(ALL_PERMISSION_CODES.length)
  })
})
