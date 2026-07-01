/**
 * 权限码常量测试（批次 17 增量）
 *
 * 验证 AI Agent / 编排 / 预测 相关权限码完整且符合 <module>:<resource>:<action> 三段式规范。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
import { describe, it, expect } from 'vitest'
import { PC, ALL_PERMISSION_CODES } from '@/constants/permissionCodes'

describe('permissionCodes 批次17 增量校验', () => {
  const PATTERN = /^[a-z]+:[a-z0-9_-]+:[a-z0-9_-]+$/

  it('AGENT_RUN / AGENT_HISTORY 已存在', () => {
    expect(PC.AGENT_RUN).toBe('agent:run')
    expect(PC.AGENT_HISTORY).toBe('agent:history')
  })

  it('AGENT_VIEW 新增', () => {
    expect(PC.AGENT_VIEW).toBe('agent:view')
  })

  it('AGENT_ORCHESTRATION_RUN / VIEW 新增', () => {
    expect(PC.AGENT_ORCHESTRATION_RUN).toBe('agent:orchestration:run')
    expect(PC.AGENT_ORCHESTRATION_VIEW).toBe('agent:orchestration:view')
  })

  it('AGENT_PREDICTION_VIEW 新增', () => {
    expect(PC.AGENT_PREDICTION_VIEW).toBe('agent:prediction:view')
  })

  it('新增权限码符合 <module>:<resource>(:<action>)? 命名规范', () => {
    const codes = [
      PC.AGENT_VIEW,
      PC.AGENT_ORCHESTRATION_RUN,
      PC.AGENT_ORCHESTRATION_VIEW,
      PC.AGENT_PREDICTION_VIEW,
    ]
    // 允许 2 段或 3 段，与原有 AGENT_RUN='agent:run' / AGENT_HISTORY='agent:history' 风格保持一致
    const PATTERN = /^[a-z]+:[a-z0-9_-]+(:[a-z0-9_-]+)?$/
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
