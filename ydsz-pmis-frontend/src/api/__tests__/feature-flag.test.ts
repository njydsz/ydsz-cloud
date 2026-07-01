/**
 * Feature Flag API 契约测试 (批次 20 P2-3)
 *
 * 验证 6 个 endpoint 的 method + path 命名符合后端实现.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/utils/request', () => ({
  request: vi.fn(),
}))

import { request } from '@/utils/request'
import {
  getFeatureFlagSnapshot,
  getFeatureFlagSnapshotGrouped,
  checkFeatureFlag,
  setFeatureFlagEnabled,
  setFeatureFlagRollout,
  refreshFeatureFlagCache,
} from '@/api/feature-flag'

const requestMock = request as unknown as ReturnType<typeof vi.fn>

describe('feature-flag API 契约', () => {
  beforeEach(() => {
    requestMock.mockReset()
  })

  it('getFeatureFlagSnapshot: GET /feature-flags/snapshot', () => {
    getFeatureFlagSnapshot()
    const call = requestMock.mock.calls[0][0]
    expect(call.method).toBe('GET')
    expect(call.url).toBe('/feature-flags/snapshot')
  })

  it('getFeatureFlagSnapshotGrouped: GET /feature-flags/snapshot/grouped', () => {
    getFeatureFlagSnapshotGrouped()
    const call = requestMock.mock.calls[0][0]
    expect(call.method).toBe('GET')
    expect(call.url).toBe('/feature-flags/snapshot/grouped')
  })

  it('checkFeatureFlag: GET /feature-flags/check 透传 key + userId', () => {
    checkFeatureFlag('COCKPIT_V2', 100)
    const call = requestMock.mock.calls[0][0]
    expect(call.method).toBe('GET')
    expect(call.url).toBe('/feature-flags/check')
    expect(call.params.key).toBe('COCKPIT_V2')
    expect(call.params.userId).toBe(100)
  })

  it('checkFeatureFlag 不传 userId 时不附加 userId', () => {
    checkFeatureFlag('AGENT_ORCHESTRATION')
    const call = requestMock.mock.calls[0][0]
    expect(call.params.userId).toBeUndefined()
  })

  it('setFeatureFlagEnabled: PUT /feature-flags/{key}/enabled 透传 enabled', () => {
    setFeatureFlagEnabled('COCKPIT_V2', true)
    const call = requestMock.mock.calls[0][0]
    expect(call.method).toBe('PUT')
    expect(call.url).toBe('/feature-flags/COCKPIT_V2/enabled')
    expect(call.params.enabled).toBe(true)
  })

  it('setFeatureFlagRollout: PUT /feature-flags/{key}/rollout 透传 percentage', () => {
    setFeatureFlagRollout('COCKPIT_V2', 50)
    const call = requestMock.mock.calls[0][0]
    expect(call.method).toBe('PUT')
    expect(call.url).toBe('/feature-flags/COCKPIT_V2/rollout')
    expect(call.params.percentage).toBe(50)
  })

  it('refreshFeatureFlagCache: POST /feature-flags/refresh', () => {
    refreshFeatureFlagCache()
    const call = requestMock.mock.calls[0][0]
    expect(call.method).toBe('POST')
    expect(call.url).toBe('/feature-flags/refresh')
  })
})
