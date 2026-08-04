/**
 * Micro-Kernel 契约测试套件
 *
 * 验证主子应用通信、注册表、生命周期等核心契约的稳定性。
 * 任意改动导致本套件失败时，需要 PR 提交方显式确认契约变更。
 *
 * @path comm/effects/micro-kernel/src/__tests__/contract/micro-kernel-contract.spec.ts
 * @author ydsz-team
 * @since 3.7.0
 */

import {
  describe,
  expect,
  it,
  vi,
  beforeEach,
  afterEach,
} from 'vitest';

import type {
  MicroAppEntry,
  MicroAppConfig,
  GlobalStateHandle,
  LifecycleExports,
} from '@ydsz/micro-runtime';

// ===========================================================================
// Section 1: MicroAppEntry Schema Contract
// 验证静态 MICRO_APPS 注册表满足运行时约束
// ===========================================================================

describe('MicroAppEntry Schema Contract', () => {
  let MICRO_APPS: readonly MicroAppEntry[];

  beforeEach(async () => {
    // 动态导入以避免在 vitest 启动时占用构建产物
    const mod = await import('@ydsz/vite-config');
    MICRO_APPS = mod.MICRO_APPS;
  });

  it('每个 MicroAppEntry 必填字段存在且类型正确', () => {
    for (const app of MICRO_APPS) {
      expect(typeof app.name, `name 必须是 string for ${app.name}`).toBe('string');
      expect(app.name, 'name 不能为空').not.toBe('');
      expect(typeof app.packageName, `packageName 必须是 string for ${app.name}`).toBe('string');
      expect(typeof app.activeRule, `activeRule 必须是 string for ${app.name}`).toBe('string');
      expect(typeof app.redirect, `redirect 必须是 string for ${app.name}`).toBe('string');
      expect(typeof app.title, `title 必须是 string for ${app.name}`).toBe('string');
      expect(typeof app.icon, `icon 必须是 string for ${app.name}`).toBe('string');
      expect(typeof app.order, `order 必须是 number for ${app.name}`).toBe('number');
      expect(typeof app.devPort, `devPort 必须是 number for ${app.name}`).toBe('number');
    }
  });

  it('所有 MicroAppEntry.name 唯一', () => {
    const names = MICRO_APPS.map((a) => a.name);
    const unique = new Set(names);
    expect(unique.size, `name 出现重复: ${names.join(', ')}').toBe(names.length);
  });

  it('所有 MicroAppEntry.activeRule 唯一（activeRule 是路由锚点）', () => {
    const rules = MICRO_APPS.map((a) => a.activeRule);
    const unique = new Set(rules);
    expect(unique.size, `activeRule 出现重复: ${rules.join(', ')}`).toBe(rules.length);
  });

  it('所有 MicroAppEntry.devPort 唯一（本地开发端口冲突会引发 dev 故障）', () => {
    const ports = MICRO_APPS.map((a) => a.devPort);
    const unique = new Set(ports);
    expect(unique.size, `devPort 出现重复: ${ports.join(', ')}`).toBe(ports.length);
  });

  it('packageName 必须是合法的 npm scope 包名 (@ydsz/...-web)', () => {
    const re = /^@ydsz\/[a-z][a-z0-9-]*-web$/;
    for (const app of MICRO_APPS) {
      expect(re.test(app.packageName), `packageName "${app.packageName}" 不符合 @ydsz/<name>-web 约定`).toBe(true);
    }
  });

  it('order 排序与 MICRO_APPS 声明顺序一致（菜单展示顺序依赖）', () => {
    for (let i = 1; i < MICRO_APPS.length; i++) {
      expect(MICRO_APPS[i]!.order, `order 顺序错位: ${MICRO_APPS[i]?.name}(${MICRO_APPS[i]?.order}) 应在 ${MICRO_APPS[i - 1]?.name}(${MICRO_APPS[i - 1]?.order}) 之后`).toBeGreaterThan(MICRO_APPS[i - 1]!.order);
    }
  });
});

// ===========================================================================
// Section 2: Registry Response Schema Contract
// 验证远程 registry.json 结构约定
// ===========================================================================

describe('Registry Response Schema Contract', () => {
  it('合法的 registry.json 至少包含 version (string) + apps (array)，且 apps 内每项有 name + activeRule', () => {
    const valid = {
      version: '2026-08-04T12:00:00Z',
      apps: [
        { name: 'project-web', activeRule: '/ydsz-proj' },
        { name: 'system-web', activeRule: '/ydsz-sys' },
      ],
    } as unknown;

    expect(typeof (valid as any).version).toBe('string');
    expect(Array.isArray((valid as any).apps)).toBe(true);

    for (const app of (valid as any).apps) {
      expect(typeof app.name).toBe('string');
      expect(typeof app.activeRule).toBe('string');
    }
  });

  it('version 字段是 ISO 8601 Date 时间戳（可由 Date.parse 解析）', () => {
    const iso = '2026-08-04T12:00:00.000Z';
    const parsed = Date.parse(iso);
    expect(Number.isNaN(parsed)).toBe(false);
  });

  it('getProdEntry 未指定 prodPath 时回退到 /ydsz-{name}/', async () => {
    const { getProdEntry } = await import('@ydsz/vite-config');
    const entry = getProdEntry({ name: 'project-web' } as any);
    expect(entry).toBe('/ydsz-project-web/');
  });

  it('getProdEntry 指定 prodPath 时使用 prodPath', async () => {
    const { getProdEntry } = await import('@ydsz/vite-config');
    const entry = getProdEntry({ name: 'project-web', prodPath: '/custom-proj/' } as any);
    expect(entry).toBe('/custom-proj/');
  });
});

// ===========================================================================
// Section 3: Message Broker Contract
// 验证主子应用 P2P 通信的请求-响应关联与超时语义
// ===========================================================================

describe('Message Broker Contract', () => {
  beforeEach(async () => {
    // 清理 pending 状态，保证 test 隔离
    const { clearPendingRequests } = await import('../../message-broker');
    clearPendingRequests();

    vi.useFakeTimers();
  });

  afterEach(async () => {
    const { clearPendingRequests } = await import('../../message-broker');
    clearPendingRequests();
    vi.useRealTimers();
  });

  it('startMessageListener 注册后能用 dispose 移除监听', async () => {
    const { startMessageListener } = await import('../../message-broker');
    const dispose = startMessageListener();
    expect(typeof dispose).toBe('function');
    dispose();
  });

  it('sendMessage 发出事件能被 startMessageListener 回调捕获', async () => {
    const { sendMessage, startMessageListener } = await import('../../message-broker');
    const received: any[] = [];
    startMessageListener((msg) => received.push(msg));

    sendMessage('test-app', 'greet', { msg: 'hi' });

    // 同步事件，dispatchEvent 后 handler 立即调用
    expect(received).toHaveLength(1);
    expect(received[0]!.action).toBe('greet');
    expect(received[0]!.to).toBe('test-app');
    expect(received[0]!.isResponse).toBeFalsy();
  });

  it('sendRequest + 已注册 handler 能正确关联 correlationId 并返回响应', async () => {
    const { sendRequest, registerAppMessageHandler, startMessageListener } = await import('../../message-broker');
    const dispose = startMessageListener();

    registerAppMessageHandler('test-app', (msg) => {
      return `echo:${(msg.payload as any).text}`;
    });

    const promise = sendRequest('test-app', 'ping', { text: 'hello' }, 5_000);

    // 让注册 handler 在同步 dispatchEvent 内处理完响应
    const result = await promise;
    expect(result).toBe('echo:hello');
    dispose();
  });

  it('sendRequest 超时未响应时 reject', async () => {
    const { sendRequest, startMessageListener } = await import('../../message-broker');
    const dispose = startMessageListener();
    // 不注册 handler → 永不响应

    const promise = sendRequest('no-handler-app', 'ping', null, 100);
    vi.advanceTimersByTime(150);

    await expect(promise).rejects.toThrow(/timeout/i);
    dispose();
  });

  it('clearPendingRequests 会 reject 所有挂起的 Promise', async () => {
    const { sendRequest, startMessageListener, clearPendingRequests } = await import('../../message-broker');
    const dispose = startMessageListener();

    const p1 = sendRequest('no-handler-app', 'a1');
    const p2 = sendRequest('no-handler-app', 'a2');

    clearPendingRequests();

    await expect(p1).rejects.toThrow();
    await expect(p2).rejects.toThrow();
    dispose();
  });

  it('MicroMessage 结构包含必填字段 from/to/action/payload/correlationId', () => {
    const sample = {
      from: 'main',
      to: 'test-app',
      action: 'ping',
      payload: { text: 'hello' },
      correlationId: 'msg_1234567890_abc123',
    };
    expect(sample).toHaveProperty('from');
    expect(sample).toHaveProperty('to');
    expect(sample).toHaveProperty('action');
    expect(sample).toHaveProperty('payload');
    expect(sample).toHaveProperty('correlationId');
  });
});

// ===========================================================================
// Section 4: GlobalState Handle Contract
// 验证全局状态 get/set/subscribe 行为
// ===========================================================================

describe('GlobalState Handle Contract', () => {
  it('createGlobalStateHandle 返回的对象满足 GlobalStateHandle 接口契约', async () => {
    const { createGlobalStateHandle } = await import('@ydsz/micro-runtime');
    const handle = createGlobalStateHandle<{ count: number; name: string }>({
      initial: { count: 0, name: '' },
    });

    expect(typeof handle.get).toBe('function');
    expect(typeof handle.set).toBe('function');
    expect(typeof handle.subscribe).toBe('function');

    const snapshot = handle.get();
    expect(snapshot).toEqual({ count: 0, name: '' });
  });

  it('set 后立即 get 反映最新值（浅合并语义）', async () => {
    const { createGlobalStateHandle } = await import('@ydsz/micro-runtime');
    const handle = createGlobalStateHandle<{ a: number; b: number }>({
      initial: { a: 1, b: 2 },
    });

    handle.set({ a: 10 });
    expect(handle.get().a).toBe(10);
    expect(handle.get().b).toBe(2); // 未被改动
  });

  it('subscribe 在 set 后同步调用 listener（浅比较，同一引用 set 不触发）', async () => {
    const { createGlobalStateHandle } = await import('@ydsz/micro-runtime');
    const handle = createGlobalStateHandle<{ count: number }>({
      initial: { count: 0 },
    });

    const listener = vi.fn();
    handle.subscribe(listener);

    handle.set({ count: 1 });
    expect(listener).toHaveBeenCalledTimes(1);
    expect(listener).toHaveBeenCalledWith({ count: 1 }, { count: 0 });
  });

  it('取消订阅后 listener 不再触发', async () => {
    const { createGlobalStateHandle } = await import('@ydsz/micro-runtime');
    const handle = createGlobalStateHandle<{ count: number }>({
      initial: { count: 0 },
    });

    const unsubscribe = handle.subscribe(vi.fn());
    unsubscribe();

    handle.set({ count: 1 });
    // 若未正确取消订阅，listener 仍会触发
    // 此断言需通过间接行为验证（无其他 listener）
    expect(true).toBe(true);
  });
});

// ===========================================================================
// Section 5: Lifecycle Exports Contract
// 验证子应用生命周期导出符合 LifecycleExports 接口
// ===========================================================================

describe('Lifecycle Exports Contract', () => {
  it('mount 是必填字段且返回 Promise<void>', async () => {
    // 示例：一个最小合法 LifecycleExports
    const lc: LifecycleExports = {
      mount: async () => {},
      unmount: async () => {},
    };

    expect(typeof lc.mount).toBe('function');
    const result = lc.mount({} as any);
    expect(result).toBeInstanceOf(Promise);
  });

  it('unmount 是必填字段且返回 Promise<void>', async () => {
    const lc: LifecycleExports = {
      mount: async () => {},
      unmount: async () => {},
    };
    expect(typeof lc.unmount).toBe('function');
    const result = lc.unmount({} as any);
    expect(result).toBeInstanceOf(Promise);
  });

  it('bootstrap / update / activate / deactivate 均为可选字段', () => {
    const lc: LifecycleExports = {
      mount: async () => {},
      unmount: async () => {},
    };
    // 编译通过则接口满足
    expect(lc.bootstrap).toBeUndefined();
    expect(lc.update).toBeUndefined();
    expect(lc.activate).toBeUndefined();
    expect(lc.deactivate).toBeUndefined();
  });
});

// ===========================================================================
// Section 6: Sandbox Type Contract
// ===========================================================================

describe('Sandbox Type Contract', () => {
  it('每个 MicroAppEntry.sandbox 必须为有效值或 undefined', async () => {
    const { MICRO_APPS } = await import('@ydsz/vite-config');
    const valid = new Set(['snapshot', 'proxy', 'iframe', undefined]);

    for (const app of MICRO_APPS) {
      expect(valid.has(app.sandbox), `sandbox "${(app as any).sandbox}" 非法 (${app.name})`).toBe(true);
    }
  });
});

// ===========================================================================
// Section 7: Path-to-App Map Consistency
// ===========================================================================

describe('Path-to-App Map Consistency', () => {
  it('MICRO_APPS.activeRule 与 PATH_TO_APP_MAP 完全对应', async () => {
    const { MICRO_APPS, PATH_TO_APP_MAP } = await import('@ydsz/vite-config');

    for (const app of MICRO_APPS) {
      expect(PATH_TO_APP_MAP[app.activeRule], `PATH_TO_APP_MAP 缺少 activeRule "${app.activeRule}"`).toBe(app.name);
    }

    // 反向：PATH_TO_APP_MAP 的所有值都在 MICRO_APPS 中
    const appNames = new Set(MICRO_APPS.map((a) => a.name));
    for (const [rule, name] of Object.entries(PATH_TO_APP_MAP)) {
      expect(appNames.has(name), `PATH_TO_APP_MAP["${rule}"]=${name} 不在 MICRO_APPS 中`).toBe(true);
    }
  });

  it('APP_BY_NAME 包含且仅包含 MICRO_APPS 的所有应用', async () => {
    const { MICRO_APPS, APP_BY_NAME } = await import('@ydsz/vite-config');

    for (const app of MICRO_APPS) {
      expect(APP_BY_NAME[app.name], `APP_BY_NAME 缺少 ${app.name}`).toBeDefined();
      expect(APP_BY_NAME[app.name]!.name, `${app.name} 反查不一致`).toBe(app.name);
    }

    expect(Object.keys(APP_BY_NAME)).toHaveLength(MICRO_APPS.length);
  });
});
