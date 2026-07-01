import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useReAuth, withReAuthHeader } from '@/composables/useReAuth'

// Mock reauth API
const mockIssue = vi.fn()
vi.mock('@/api/user/reauth', () => ({
  issueReAuthToken: (...args: unknown[]) => mockIssue(...args),
}))

// Mock ElMessage
vi.mock('element-plus', () => ({
  ElMessage: { error: vi.fn(), success: vi.fn() },
}))

describe('useReAuth 二次认证 composable', () => {
  beforeEach(() => {
    mockIssue.mockReset()
  })

  it('初始化时 dialog 不可见', () => {
    const { dialog } = useReAuth({
      operationCode: 'OP-1',
      operationName: 'test',
    })
    expect(dialog.visible).toBe(false)
    expect(dialog.method).toBe('PASSWORD')
    expect(dialog.password).toBe('')
  })

  it('默认凭据类型为 PASSWORD', () => {
    const { dialog } = useReAuth({
      operationCode: 'OP-1',
      operationName: 'test',
      defaultMethod: 'TOTP',
    })
    expect(dialog.method).toBe('TOTP')
  })

  it('submit 在 PASSWORD 模式下空密码应设置错误信息', async () => {
    const { dialog, submit } = useReAuth({
      operationCode: 'OP-1',
      operationName: 'test',
    })
    dialog.password = ''
    const result = await submit()
    expect(result).toBe('')
    expect(dialog.errorMessage).toBe('请输入当前密码')
  })

  it('submit 在 PASSWORD 模式调通 API 后返回 token', async () => {
    mockIssue.mockResolvedValue({
      code: 0,
      data: { token: 'tok-1', ttlSeconds: 300, method: 'PASSWORD', operationCode: 'OP-1' },
    })
    const { dialog, submit } = useReAuth({
      operationCode: 'OP-1',
      operationName: 'test',
    })
    dialog.password = 'secret'
    const token = await submit()
    expect(token).toBe('tok-1')
    expect(mockIssue).toHaveBeenCalledWith(
      expect.objectContaining({ operationCode: 'OP-1', method: 'PASSWORD', password: 'secret' }),
    )
  })

  it('submit 在 TOTP 模式校验 6 位数字', async () => {
    const { dialog, submit } = useReAuth({
      operationCode: 'OP-1',
      operationName: 'test',
    })
    dialog.method = 'TOTP'
    dialog.otp = '12'
    const r = await submit()
    expect(r).toBe('')
    expect(dialog.errorMessage).toBe('请输入 6 位 TOTP 动态码')
  })

  it('submit 在 BACKUP_CODE 模式空值抛错', async () => {
    const { dialog, submit } = useReAuth({
      operationCode: 'OP-1',
      operationName: 'test',
    })
    dialog.method = 'BACKUP_CODE'
    dialog.backupCode = ''
    const r = await submit()
    expect(r).toBe('')
    expect(dialog.errorMessage).toBe('请输入备份码')
  })

  it('close 拒绝 pending 等待', async () => {
    const { dialog, requestToken, close } = useReAuth({
      operationCode: 'OP-1',
      operationName: 'test',
    })
    const p = requestToken()
    expect(dialog.visible).toBe(true)
    close()
    await expect(p).rejects.toThrow('用户取消二次认证')
    expect(dialog.visible).toBe(false)
  })

  it('withReAuth 串起 弹窗 -> 颁发 -> 业务', async () => {
    mockIssue.mockResolvedValue({
      code: 0,
      data: { token: 'tok-2', ttlSeconds: 300, method: 'PASSWORD', operationCode: 'OP-1' },
    })
    const { withReAuth, submit, dialog, close } = useReAuth({
      operationCode: 'OP-1',
      operationName: 'test',
    })

    // 直接调用 submit（不走 UI）拿到 token
    dialog.password = 'secret'
    const token = await submit()
    expect(token).toBe('tok-2')
    expect(dialog.loading).toBe(false)

    // withReAuth 装饰器：close 后 reject，biz 不会被调用
    const biz = vi.fn().mockResolvedValue('OK')
    const p = withReAuth(biz)
    close()
    await expect(p).resolves.toBeUndefined()
    expect(biz).not.toHaveBeenCalled()
  })

  it('withReAuthHeader 注入 X-Re-Auth-Token', () => {
    const out = withReAuthHeader({ 'X-Foo': 'bar' }, 'tok-xyz')
    expect(out['X-Re-Auth-Token']).toBe('tok-xyz')
    expect(out['X-Foo']).toBe('bar')
  })

  it('withReAuthHeader 不传 base 也能工作', () => {
    const out = withReAuthHeader(undefined, 'tok-xyz')
    expect(out['X-Re-Auth-Token']).toBe('tok-xyz')
  })
})
