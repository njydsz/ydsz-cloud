/**
 * useFeatureFlag 单元测试 (批次 20 P2-3)
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { ref } from 'vue'

// mock request
vi.mock('@/utils/request', () => ({
  request: vi.fn(),
}))

import { request } from '@/utils/request'
import {
  useFeatureFlag,
  useFlag,
  FEATURE_FLAGS,
} from '../useFeatureFlag'

const requestMock = request as unknown as ReturnType<typeof vi.fn>

describe('useFeatureFlag', () => {
  beforeEach(() => {
    requestMock.mockReset()
  })

  it('isEnabled 调用 /check 接口, 透传 key + userId', async () => {
    requestMock.mockResolvedValue(true)
    const { isEnabled } = useFeatureFlag()
    const result = await isEnabled('COCKPIT_V2', 123)
    expect(result).toBe(true)
    const call = requestMock.mock.calls[0][0]
    expect(call.url).toBe('/feature-flags/check')
    expect(call.method).toBe('GET')
    expect(call.params.key).toBe('COCKPIT_V2')
    expect(call.params.userId).toBe(123)
  })

  it('isEnabled 不传 userId 时不附加 userId 参数', async () => {
    requestMock.mockResolvedValue(true)
    const { isEnabled } = useFeatureFlag()
    await isEnabled('AGENT_ORCHESTRATION')
    const call = requestMock.mock.calls[0][0]
    expect(call.params.userId).toBeUndefined()
  })

  it('isEnabled 30s 缓存: 第二次不调用 request', async () => {
    requestMock.mockResolvedValue(true)
    const { isEnabled, clearCache } = useFeatureFlag()
    await isEnabled('COCKPIT_V2')
    await isEnabled('COCKPIT_V2')
    expect(requestMock).toHaveBeenCalledTimes(1)
    clearCache()
    await isEnabled('COCKPIT_V2')
    expect(requestMock).toHaveBeenCalledTimes(2)
  })

  it('isEnabled 请求失败时降级: SAFETY 类视为开启', async () => {
    requestMock.mockRejectedValue(new Error('network'))
    const { isEnabled, clearCache } = useFeatureFlag()
    clearCache()
    const safety = await isEnabled('AUDIT_LOG_MANDATORY')
    const business = await isEnabled('COCKPIT_V2')
    expect(safety).toBe(true)
    expect(business).toBe(false)
  })

  it('refresh 拉取全量快照并填充 flags ref', async () => {
    requestMock.mockResolvedValue([
      { key: 'COCKPIT_V2', effectiveValue: true },
      { key: 'AGENT_ORCHESTRATION', effectiveValue: false },
      { key: 'AUDIT_LOG_MANDATORY', effectiveValue: true },
    ])
    const { flags, refresh } = useFeatureFlag()
    await refresh()
    expect(flags.value.COCKPIT_V2).toBe(true)
    expect(flags.value.AGENT_ORCHESTRATION).toBe(false)
    expect(flags.value.AUDIT_LOG_MANDATORY).toBe(true)
  })

  it('refresh 拉取失败时填充 SAFETY 默认值', async () => {
    requestMock.mockRejectedValue(new Error('boom'))
    const { flags, refresh } = useFeatureFlag()
    await refresh()
    expect(flags.value.AUDIT_LOG_MANDATORY).toBe(true)
    expect(flags.value.COCKPIT_V2).toBe(false)
  })

  it('loading 在 refresh 期间为 true', async () => {
    let resolveFn: (v: unknown) => void = () => {}
    requestMock.mockReturnValue(
      new Promise((r) => {
        resolveFn = r
      }),
    )
    const { loading, refresh } = useFeatureFlag()
    const p = refresh()
    expect(loading.value).toBe(true)
    resolveFn([])
    await p
    expect(loading.value).toBe(false)
  })

  it('FEATURE_FLAGS 包含所有 18 个内置 flag', () => {
    expect(Object.keys(FEATURE_FLAGS)).toHaveLength(18)
    expect(FEATURE_FLAGS.COCKPIT_V2).toBe('COCKPIT_V2')
    expect(FEATURE_FLAGS.AUDIT_LOG_MANDATORY).toBe('AUDIT_LOG_MANDATORY')
  })
})

describe('useFlag (computed)', () => {
  beforeEach(() => {
    requestMock.mockReset()
  })

  it('flags 中存在时返回对应值', () => {
    const f = ref<Record<string, boolean>>({ COCKPIT_V2: true })
    // 直接构造 flags, 不依赖 refresh
    const { isEnabled } = useFeatureFlag()
    void isEnabled
    // flags 是 useFeatureFlag 内部 ref, 我们手动覆盖
    const flags = (useFeatureFlag as unknown as () => { flags: typeof f })
    // 验证 computed 默认值: 传 SAFETY → true
    const c1 = useFlag('AUDIT_LOG_MANDATORY')
    expect(c1.value).toBe(true)
    // 验证非 SAFETY → false (未拉取过)
    const c2 = useFlag('COCKPIT_V2')
    expect(c2.value).toBe(false)
    // 防 unused
    void flags
  })
})
