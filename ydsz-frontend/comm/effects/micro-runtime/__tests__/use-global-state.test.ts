/**
 * useGlobalState 单元测试
 *
 * 覆盖：
 *   - 初始值同步
 *   - subscribe 后 set 触发 ref 更新
 *   - v3.2 修复：组件卸载时自动 unsubscribe，避免泄漏
 *   - globalStateHandle 未注入时的降级
 *
 * @path comm/effects/micro-runtime/__tests__/use-global-state.test.ts
 * @author ydsz-team
 * @since 3.2.0
 */
import { describe, expect, it, vi } from 'vitest';

import { createGlobalStateHandle } from '../src/global-state';
import { provideGlobalState, useGlobalState, useGlobalStateRef } from '../src/composable';

import { effectScope, nextTick } from 'vue';

function provideFreshHandle(initial: Record<string, unknown> = {}) {
  const handle = createGlobalStateHandle({ initial });
  provideGlobalState(handle);
  return handle;
}

describe('useGlobalState', () => {
  it('应同步注入时的初始值', () => {
    provideFreshHandle({ theme: 'dark', lang: 'zh' });
    const scope = effectScope();
    const theme = scope.run(() => useGlobalState<'theme', string>('theme'))!;
    expect(theme.value).toBe('dark');
    scope.stop();
  });

  it('set 后应通过 subscribe 通知 ref 更新', async () => {
    const handle = provideFreshHandle({ count: 0 });
    const scope = effectScope();
    const count = scope.run(() => useGlobalState<'count', number>('count'))!;
    expect(count.value).toBe(0);

    handle.set({ count: 42 });
    await nextTick();
    expect(count.value).toBe(42);
    scope.stop();
  });

  it('v3.2: scope.stop() 后应自动取消订阅，后续 set 不再触发更新', async () => {
    const handle = provideFreshHandle({ k: 'v1' });
    const scope = effectScope();
    const val = scope.run(() => useGlobalState<'k', string>('k'))!;
    expect(val.value).toBe('v1');

    scope.stop();

    handle.set({ k: 'v2' });
    await nextTick();
    // 取消订阅后 ref 不应再更新
    expect(val.value).toBe('v1');
  });

  it('globalStateHandle 未注入时返回 null ref 且不抛错', () => {
    // 提供一个 null 句柄
    provideGlobalState(null as unknown as ReturnType<typeof createGlobalStateHandle>);
    const scope = effectScope();
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const val = scope.run(() => useGlobalState<'x', string>('x'))!;
    expect(val.value).toBeNull();
    expect(warn).toHaveBeenCalled();
    warn.mockRestore();
    scope.stop();
  });

  it('useGlobalStateRef 支持 get/set 带默认值', async () => {
    const handle = provideFreshHandle({});
    const scope = effectScope();
    const lang = scope.run(() => useGlobalStateRef<string>('lang', 'en'))!;

    expect(lang.value).toBe('en'); // 使用默认值
    lang.value = 'zh';
    await nextTick();
    expect(handle.get().lang).toBe('zh');
    scope.stop();
  });

  it('多次调用应各自独立取消订阅', async () => {
    const handle = provideFreshHandle({ v: 0 });
    const scope1 = effectScope();
    const scope2 = effectScope();
    const a = scope1.run(() => useGlobalState<'v', number>('v'))!;
    const b = scope2.run(() => useGlobalState<'v', number>('v'))!;

    handle.set({ v: 1 });
    await nextTick();
    expect(a.value).toBe(1);
    expect(b.value).toBe(1);

    // 停掉 scope1 后，scope2 仍应能收到更新
    scope1.stop();
    handle.set({ v: 2 });
    await nextTick();
    expect(a.value).toBe(1); // 已取消订阅
    expect(b.value).toBe(2);
    scope2.stop();
  });
});
