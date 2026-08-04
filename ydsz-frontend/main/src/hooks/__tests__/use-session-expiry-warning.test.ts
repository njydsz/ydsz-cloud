/**
 * useSessionExpiryWarning 单元测试
 *
 * 通过 mock element-plus / refreshTokenApi / $t 验证：
 *   - expiresAt 为 null 时不弹窗
 *   - expiresAt 距今 > 5 分钟不弹窗
 *   - expiresAt 距今 ≤ 5 分钟弹窗一次
 *   - 同一 expiresAt 周期内不重复弹窗
 *   - 用户确认后调用 refreshTokenApi 续期
 *
 * @path main/src/hooks/__tests__/use-session-expiry-warning.test.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { createPinia, setActivePinia } from 'pinia';

// === mock element-plus ===
const elMessageBoxConfirm = vi.fn();
const elMessageWarning = vi.fn();
const elMessageSuccess = vi.fn();
vi.mock('element-plus', () => ({
  ElMessageBox: { confirm: (...args: unknown[]) => elMessageBoxConfirm(...args) },
  ElMessage: {
    warning: (...args: unknown[]) => elMessageWarning(...args),
    success: (...args: unknown[]) => elMessageSuccess(...args),
  },
}));

// === mock #/api/core/auth.refreshTokenApi ===
const refreshTokenApiMock = vi.fn();
vi.mock('#/api/core/auth', () => ({
  refreshTokenApi: (...args: unknown[]) => refreshTokenApiMock(...args),
}));

// === mock #/locales.$t — 直接回传 key 便于断言 ===
vi.mock('#/locales', () => ({
  $t: (key: string) => key,
}));

// === mock #/hooks/use-cross-tab-sync — D4 跨标签页广播 ===
vi.mock('#/hooks/use-cross-tab-sync', () => ({
  CROSS_TAB_EVENTS: { TOKEN_REFRESHED: 'token-refreshed' },
  notifyCrossTab: vi.fn(),
}));

import { useTokenStore } from '@ydsz/stores';

import { useSessionExpiryWarning } from '../use-session-expiry-warning';

describe('useSessionExpiryWarning', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    elMessageBoxConfirm.mockReset();
    elMessageWarning.mockReset();
    elMessageSuccess.mockReset();
    refreshTokenApiMock.mockReset();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('expiresAt 为 null 时不应弹窗', () => {
    vi.useFakeTimers();
    const tokenStore = useTokenStore();
    tokenStore.setExpiresAt(null);
    useSessionExpiryWarning();

    // 推进 1 分钟
    vi.advanceTimersByTime(60_000);
    expect(elMessageBoxConfirm).not.toHaveBeenCalled();
  });

  it('expiresAt 距今 > 5 分钟时不应弹窗', () => {
    vi.useFakeTimers();
    const tokenStore = useTokenStore();
    // 10 分钟后过期
    tokenStore.setExpiresAt(Date.now() + 10 * 60 * 1000);
    useSessionExpiryWarning();

    // 推进 1 分钟（仍在 9 分钟外）
    vi.advanceTimersByTime(60_000);
    expect(elMessageBoxConfirm).not.toHaveBeenCalled();
  });

  it('expiresAt 距今 ≤ 5 分钟时应弹窗一次', () => {
    vi.useFakeTimers();
    const tokenStore = useTokenStore();
    // 3 分钟后过期
    tokenStore.setExpiresAt(Date.now() + 3 * 60 * 1000);
    useSessionExpiryWarning();

    // immediate watch 立即检查一次
    expect(elMessageBoxConfirm).toHaveBeenCalledTimes(1);

    // 推进 1 分钟，不应再次弹窗（warnedFor 去重）
    vi.advanceTimersByTime(60_000);
    expect(elMessageBoxConfirm).toHaveBeenCalledTimes(1);
  });

  it('expiresAt 变化（续期）后应重新允许弹窗', async () => {
    vi.useFakeTimers();
    const tokenStore = useTokenStore();
    tokenStore.setExpiresAt(Date.now() + 3 * 60 * 1000);
    useSessionExpiryWarning();
    expect(elMessageBoxConfirm).toHaveBeenCalledTimes(1);

    // 续期：用不同的时间戳确保 watch 触发
    tokenStore.setExpiresAt(Date.now() + 4 * 60 * 1000);
    // Vue watch 默认为异步 flush，需刷新 microtask 队列
    await vi.advanceTimersByTimeAsync(0);
    expect(elMessageBoxConfirm).toHaveBeenCalledTimes(2);
  });

  it('用户确认后应调用 refreshTokenApi 续期', async () => {
    vi.useFakeTimers();
    const tokenStore = useTokenStore();
    tokenStore.setExpiresAt(Date.now() + 3 * 60 * 1000);
    tokenStore.setRefreshToken('old-refresh');
    // 模拟用户点确认
    elMessageBoxConfirm.mockResolvedValue(undefined);
    refreshTokenApiMock.mockResolvedValue({
      data: { accessToken: 'new-token', expiresIn: 3600 },
    });

    useSessionExpiryWarning();

    // 多次推进以串联 ElMessageBox.confirm → renewSession → refreshTokenApi 的 await 链
    for (let i = 0; i < 5; i++) {
      await vi.advanceTimersByTimeAsync(0);
    }

    expect(refreshTokenApiMock).toHaveBeenCalledTimes(1);
    expect(tokenStore.accessToken).toBe('new-token');
    expect(tokenStore.expiresAt).not.toBeNull();
  });

  it('用户取消后不应调用 refreshTokenApi', async () => {
    vi.useFakeTimers();
    const tokenStore = useTokenStore();
    tokenStore.setExpiresAt(Date.now() + 3 * 60 * 1000);
    // 模拟用户点取消
    elMessageBoxConfirm.mockRejectedValue(new Error('cancel'));

    useSessionExpiryWarning();

    for (let i = 0; i < 5; i++) {
      await vi.advanceTimersByTimeAsync(0);
    }

    expect(refreshTokenApiMock).not.toHaveBeenCalled();
  });
});
