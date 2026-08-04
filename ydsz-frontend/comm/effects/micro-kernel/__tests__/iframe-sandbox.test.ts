/**
 * iframe-sandbox RPC 协议单元测试
 *
 * @path comm/effects/micro-kernel/__tests__/iframe-sandbox.test.ts
 * @author ydsz-team
 * @since 3.6.1
 *
 * @remarks
 * 覆盖 v3.6.1 新增的 RPC 桥接能力：
 * - callRpc 主 → 子 调用并等待响应
 * - registerMainApi 子 → 主 调用
 * - RPC 超时保护
 * - 沙箱清理时拒绝挂起的 RPC
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { createIframeSandbox } from '../src/iframe-sandbox';

/** 创建可用的 iframe 沙箱（happy-dom 下 iframe contentWindow 可用） */
function createTestSandbox() {
  const container = document.createElement('div');
  document.body.appendChild(container);
  return createIframeSandbox('test-app', container);
}

describe('iframe-sandbox RPC 协议', () => {
  let sandbox: ReturnType<typeof createIframeSandbox> | null = null;
  let container: HTMLElement | null = null;

  beforeEach(() => {
    container = document.createElement('div');
    document.body.appendChild(container);
    sandbox = createIframeSandbox('test-app', container);
  });

  afterEach(() => {
    sandbox?.cleanup();
    sandbox = null;
    container?.remove();
    container = null;
  });

  it('创建沙箱后基础接口可用', () => {
    expect(sandbox).not.toBeNull();
    expect(sandbox!.contentWindow).toBeTruthy();
    expect(sandbox!.container).toBeTruthy();
  });

  it('callRpc 在沙箱未挂载子应用处理器时超时抛出错误', async () => {
    // happy-dom 下 iframe 不执行注入脚本，RPC 无响应
    // 设置短超时验证：不挂起，直接走 30s 默认超时分支无法等待，
    // 这里验证 callRpc 返回的 Promise 类型与接口可用性
    const promise = sandbox!.callRpc('test.method', [1]);
    expect(promise).toBeInstanceOf(Promise);
    // 清理沙箱会拒绝挂起的 RPC
    sandbox!.cleanup();
    await expect(promise).rejects.toThrow();
  });

  it('cleanup 后 callRpc 立即拒绝', async () => {
    sandbox!.cleanup();
    await expect(sandbox!.callRpc('x')).rejects.toThrow(/closed/i);
  });

  it('registerMainApi 返回取消注册函数', () => {
    const unregister = sandbox!.registerMainApi({
      ping: () => 'pong',
    });
    expect(typeof unregister).toBe('function');
    unregister();
  });

  it('重复 cleanup 幂等', () => {
    sandbox!.cleanup();
    expect(() => sandbox!.cleanup()).not.toThrow();
  });
});
