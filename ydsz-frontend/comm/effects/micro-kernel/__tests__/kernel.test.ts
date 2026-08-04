/**
 * kernel 模块集成测试
 *
 * 覆盖 globalState 通信、路由同步、switchToken 竞态、生命周期钩子、_stop 清理。
 * 因 kernel 使用模块级状态，每个用例后调用 _stop() 重置。
 *
 * @path comm/effects/micro-kernel/__tests__/kernel.test.ts
 * @author ydsz-team
 * @since 3.1.0
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { MicroAppConfig } from '@ydsz/micro-runtime';

// Mock loader：可控延迟 + 缓存标记
const loadAppMock = vi.fn();
vi.mock('../src/loader', () => ({
  loadApp: (...args: unknown[]) => loadAppMock(...args),
  removeStylesheets: vi.fn(),
}));

import { createKernel } from '../src/kernel';

function makeAppConfig(name: string, activeRule: string): MicroAppConfig {
  return {
    name,
    entry: `/${name}/`,
    container: '#subapp-container',
    activeRule,
  };
}

function setupLoader(delay = 0) {
  loadAppMock.mockImplementation(async (config: MicroAppConfig) => {
    if (delay) await new Promise((r) => setTimeout(r, delay));
    return {
      exports: {
        mount: vi.fn(async (props: Record<string, unknown>) => {
          const container = props.container as HTMLElement;
          const div = document.createElement('div');
          div.id = `mounted-${config.name}`;
          container.appendChild(div);
        }),
        unmount: vi.fn(async () => {
          document.querySelector(`#mounted-${config.name}`)?.remove();
        }),
      },
      manifest: { name: config.name, entry: '/entry.js', css: [], version: '1.0.0' },
      duration: 10,
      fromCache: false,
    };
  });
}

describe('kernel', () => {
  let kernel: ReturnType<typeof createKernel>;

  beforeEach(() => {
    document.body.innerHTML = '<div id="subapp-container"></div>';
    window.history.replaceState(null, '', '/');
    loadAppMock.mockReset();
    setupLoader();
    kernel = createKernel();
  });

  afterEach(async () => {
    await kernel._stop();
  });

  // ==================== globalState ====================
  describe('globalState 通信', () => {
    it('setGlobalState 应通知已订阅的 listener', async () => {
      kernel.registerApps([makeAppConfig('app-a', '/a')]);
      kernel.start();

      // 通过 mountProps 注入的 _globalState 间接测试
      // 先激活 app-a 以触发 mount，获取 props
      window.history.replaceState(null, '', '/a');
      window.dispatchEvent(new PopStateEvent('popstate'));

      await vi.waitFor(() => {
        expect(kernel.getActiveAppName()).toBe('app-a');
      });

      // 直接通过 kernel 内部 globalState 验证：mountProps 中含 _globalState
      const callArgs = loadAppMock.mock.calls[0]?.[0] as MicroAppConfig | undefined;
      expect(callArgs?.props?._globalState).toBeDefined();

      const stateAPI = callArgs!.props!._globalState as {
        onGlobalStateChange: (cb: (s: Record<string, unknown>) => void, fire?: boolean) => void;
        setGlobalState: (patch: Record<string, unknown>) => void;
        getGlobalState: () => Record<string, unknown>;
      };

      const listener = vi.fn();
      stateAPI.onGlobalStateChange(listener, false);
      stateAPI.setGlobalState({ theme: 'dark' });

      expect(listener).toHaveBeenCalledWith({ theme: 'dark' });
    });

    it('fireImmediately=true 时应立即以当前状态触发', async () => {
      kernel.registerApps([makeAppConfig('app-b', '/b')]);
      kernel.start();

      window.history.replaceState(null, '', '/b');
      window.dispatchEvent(new PopStateEvent('popstate'));

      await vi.waitFor(() => {
        expect(kernel.getActiveAppName()).toBe('app-b');
      });

      const callArgs = loadAppMock.mock.calls[0]?.[0] as MicroAppConfig | undefined;
      const stateAPI = callArgs!.props!._globalState as {
        onGlobalStateChange: (cb: (s: Record<string, unknown>) => void, fire?: boolean) => void;
        setGlobalState: (patch: Record<string, unknown>) => void;
      };

      stateAPI.setGlobalState({ lang: 'zh' });

      const listener = vi.fn();
      stateAPI.onGlobalStateChange(listener, true);
      expect(listener).toHaveBeenCalledWith({ lang: 'zh' });
    });

    it('getGlobalState 应返回快照而非引用', async () => {
      kernel.registerApps([makeAppConfig('app-c', '/c')]);
      kernel.start();

      window.history.replaceState(null, '', '/c');
      window.dispatchEvent(new PopStateEvent('popstate'));

      await vi.waitFor(() => {
        expect(kernel.getActiveAppName()).toBe('app-c');
      });

      const callArgs = loadAppMock.mock.calls[0]?.[0] as MicroAppConfig | undefined;
      const stateAPI = callArgs!.props!._globalState as {
        setGlobalState: (patch: Record<string, unknown>) => void;
        getGlobalState: () => Record<string, unknown>;
      };

      stateAPI.setGlobalState({ k: 'v' });
      const snap = stateAPI.getGlobalState();
      snap.k = 'mutated';

      expect(stateAPI.getGlobalState().k).toBe('v');
    });
  });

  // ==================== 路由同步 ====================
  describe('路由同步', () => {
    it('navigateTo 应激活匹配 activeRule 的子应用', async () => {
      kernel.registerApps([
        makeAppConfig('alpha', '/alpha'),
        makeAppConfig('beta', '/beta'),
      ]);
      kernel.start();

      kernel.navigateTo('/beta');

      await vi.waitFor(() => {
        expect(kernel.getActiveAppName()).toBe('beta');
      });
    });

    it('路径不匹配任何子应用时应卸载当前活跃应用', async () => {
      kernel.registerApps([makeAppConfig('alpha', '/alpha')]);
      kernel.start();

      kernel.navigateTo('/alpha');
      await vi.waitFor(() => {
        expect(kernel.getActiveAppName()).toBe('alpha');
      });

      // 导航到无匹配路径
      kernel.navigateTo('/unknown');
      await vi.waitFor(() => {
        expect(kernel.getActiveAppName()).toBeNull();
      });
    });

    it('history.pushState 补丁应派发路由变更事件', async () => {
      kernel.registerApps([makeAppConfig('alpha', '/alpha')]);
      kernel.start();

      history.pushState(null, '', '/alpha');

      await vi.waitFor(() => {
        expect(kernel.getActiveAppName()).toBe('alpha');
      });
    });
  });

  // ==================== switchToken 竞态 ====================
  describe('switchToken 竞态防护', () => {
    it('快速连续切换时，后到的切换应胜出', async () => {
      // 慢加载：50ms
      setupLoader(50);
      kernel.registerApps([
        makeAppConfig('slow-a', '/slow-a'),
        makeAppConfig('slow-b', '/slow-b'),
      ]);
      kernel.start();

      // 快速连续导航
      kernel.navigateTo('/slow-a');
      kernel.navigateTo('/slow-b');

      await vi.waitFor(() => {
        expect(kernel.getActiveAppName()).toBe('slow-b');
      }, { timeout: 2000 });
    });
  });

  // ==================== 生命周期钩子 ====================
  describe('生命周期钩子', () => {
    it('beforeLoad / afterMount / afterUnmount 应按序触发', async () => {
      const order: string[] = [];
      kernel.addLifecycleHook('beforeLoad', () => {
        order.push('beforeLoad');
      });
      kernel.addLifecycleHook('afterMount', () => {
        order.push('afterMount');
      });
      kernel.addLifecycleHook('afterUnmount', () => {
        order.push('afterUnmount');
      });

      kernel.registerApps([makeAppConfig('hooked', '/hooked')]);
      kernel.start();

      kernel.navigateTo('/hooked');
      await vi.waitFor(() => {
        expect(kernel.getActiveAppName()).toBe('hooked');
      });

      await kernel.unmountApp('hooked');

      expect(order).toEqual(['beforeLoad', 'afterMount', 'afterUnmount']);
    });

    it('afterLoad / beforeMount 细化钩子应在加载与挂载之间触发（v3.3）', async () => {
      const order: string[] = [];
      kernel.addLifecycleHook('beforeLoad', () => {
        order.push('beforeLoad');
      });
      kernel.addLifecycleHook('afterLoad', () => {
        order.push('afterLoad');
      });
      kernel.addLifecycleHook('beforeMount', () => {
        order.push('beforeMount');
      });
      kernel.addLifecycleHook('afterMount', () => {
        order.push('afterMount');
      });

      kernel.registerApps([makeAppConfig('refined', '/refined')]);
      kernel.start();

      kernel.navigateTo('/refined');
      await vi.waitFor(() => {
        expect(kernel.getActiveAppName()).toBe('refined');
      });

      // 等待所有异步钩子（afterLoad / beforeMount 通过 void runHooks 异步触发）
      await vi.waitFor(() => {
        expect(order).toEqual(['beforeLoad', 'afterLoad', 'beforeMount', 'afterMount']);
      });
    });

    it('addLifecycleHook 返回的取消订阅函数应移除钩子', async () => {
      const calls: string[] = [];
      const off = kernel.addLifecycleHook('beforeLoad', (app) => {
        calls.push(app.name);
      });

      kernel.registerApps([makeAppConfig('unsub', '/unsub')]);
      kernel.start();

      kernel.navigateTo('/unsub');
      await vi.waitFor(() => {
        expect(kernel.getActiveAppName()).toBe('unsub');
      });
      expect(calls).toEqual(['unsub']);

      // 取消订阅后再次切换不应触发
      off();
      await kernel.unmountApp('unsub');
      // 重新激活 — beforeLoad 不应再被调用
      loadAppMock.mockClear();
      kernel.navigateTo('/unsub');
      await vi.waitFor(() => {
        expect(kernel.getActiveAppName()).toBe('unsub');
      });
      expect(calls).toEqual(['unsub']); // 仍只有第一次
    });

    it('error 钩子应在加载/挂载失败时被触发', async () => {
      // 让 loader 抛错以触发 error 路径
      loadAppMock.mockReset();
      loadAppMock.mockRejectedValueOnce(new Error('boom: load failed'));

      const errors: Array<{ app: string; err: unknown }> = [];
      kernel.addLifecycleHook('error', (app, err) => {
        errors.push({ app: app.name, err });
      });

      kernel.registerApps([makeAppConfig('boom', '/boom')]);
      kernel.start();

      kernel.navigateTo('/boom');
      await vi.waitFor(() => {
        expect(errors.length).toBeGreaterThan(0);
      });

      expect(errors[0].app).toBe('boom');
      expect(String(errors[0].err)).toContain('boom: load failed');
    });
  });

  // ==================== _stop 清理 ====================
  describe('_stop', () => {
    it('应清理路由监听并卸载所有子应用', async () => {
      kernel.registerApps([makeAppConfig('stopper', '/stopper')]);
      kernel.start();

      kernel.navigateTo('/stopper');
      await vi.waitFor(() => {
        expect(kernel.getActiveAppName()).toBe('stopper');
      });

      await kernel._stop();

      expect(kernel.getActiveAppName()).toBeNull();

      // stop 后 navigateTo 不应再激活（路由监听已移除）
      kernel.navigateTo('/stopper');
      await new Promise((r) => setTimeout(r, 50));
      expect(kernel.getActiveAppName()).toBeNull();
    });
  });

  // ==================== 预加载 ====================
  describe('prefetch 预加载', () => {
    it('start 时应按 prefetch 过滤器预加载指定应用', async () => {
      const apps = [
        makeAppConfig('pre-a', '/pre-a'),
        makeAppConfig('pre-b', '/pre-b'),
      ];
      kernel.registerApps(apps);
      kernel.start({
        prefetch: (app) => app.name === 'pre-a',
      });

      // requestIdleCallback 在 happy-dom 下可能同步/异步，等待一下
      await new Promise((r) => setTimeout(r, 50));

      // pre-a 应被 loadApp 调用（预加载），pre-b 不应
      const loadedNames = loadAppMock.mock.calls.map((c) => (c[0] as MicroAppConfig).name);
      expect(loadedNames).toContain('pre-a');
      expect(loadedNames).not.toContain('pre-b');
    });

    // P2: 网络条件感知 — 慢速网络/省流量模式下应跳过自动预加载
    describe('网络条件感知', () => {
      type ConnShape = { effectiveType?: string; saveData?: boolean };
      const originalConn = (navigator as Navigator & { connection?: ConnShape }).connection;

      function setConnection(conn: ConnShape): void {
        Object.defineProperty(navigator, 'connection', {
          value: conn,
          configurable: true,
          writable: true,
        });
      }

      afterEach(() => {
        // 恢复原始 connection（可能是 undefined）
        if (originalConn === undefined) {
          // happy-dom 默认没有该属性，删除以还原
          delete (navigator as Navigator & { connection?: ConnShape }).connection;
        } else {
          setConnection(originalConn);
        }
      });

      it('saveData=true 时应跳过自动预加载', async () => {
        setConnection({ saveData: true, effectiveType: '4g' });
        kernel.registerApps([makeAppConfig('save-data', '/save-data')]);
        kernel.start({ prefetch: () => true });

        await new Promise((r) => setTimeout(r, 50));

        const loadedNames = loadAppMock.mock.calls.map((c) => (c[0] as MicroAppConfig).name);
        expect(loadedNames).not.toContain('save-data');
      });

      it('effectiveType=2g 时应跳过自动预加载', async () => {
        setConnection({ effectiveType: '2g', saveData: false });
        kernel.registerApps([makeAppConfig('slow-2g', '/slow-2g')]);
        kernel.start({ prefetch: () => true });

        await new Promise((r) => setTimeout(r, 50));

        const loadedNames = loadAppMock.mock.calls.map((c) => (c[0] as MicroAppConfig).name);
        expect(loadedNames).not.toContain('slow-2g');
      });

      it('effectiveType=3g 时应跳过自动预加载', async () => {
        setConnection({ effectiveType: '3g' });
        kernel.registerApps([makeAppConfig('slow-3g', '/slow-3g')]);
        kernel.start({ prefetch: () => true });

        await new Promise((r) => setTimeout(r, 50));

        const loadedNames = loadAppMock.mock.calls.map((c) => (c[0] as MicroAppConfig).name);
        expect(loadedNames).not.toContain('slow-3g');
      });

      it('effectiveType=4g 时应正常预加载', async () => {
        setConnection({ effectiveType: '4g', saveData: false });
        kernel.registerApps([makeAppConfig('fast-4g', '/fast-4g')]);
        kernel.start({ prefetch: () => true });

        await new Promise((r) => setTimeout(r, 50));

        const loadedNames = loadAppMock.mock.calls.map((c) => (c[0] as MicroAppConfig).name);
        expect(loadedNames).toContain('fast-4g');
      });

      it('浏览器不支持 Network Information API 时应正常预加载', async () => {
        // 确保没有 connection 属性
        delete (navigator as Navigator & { connection?: ConnShape }).connection;
        kernel.registerApps([makeAppConfig('no-conn-api', '/no-conn-api')]);
        kernel.start({ prefetch: () => true });

        await new Promise((r) => setTimeout(r, 50));

        const loadedNames = loadAppMock.mock.calls.map((c) => (c[0] as MicroAppConfig).name);
        expect(loadedNames).toContain('no-conn-api');
      });

      it('prefetchApp 手动预加载应忽略网络条件（即使 saveData=true）', async () => {
        setConnection({ saveData: true, effectiveType: '2g' });
        kernel.registerApps([makeAppConfig('manual-prefetch', '/manual-prefetch')]);
        kernel.start();

        await kernel.prefetchApp('manual-prefetch');

        const loadedNames = loadAppMock.mock.calls.map((c) => (c[0] as MicroAppConfig).name);
        expect(loadedNames).toContain('manual-prefetch');
      });

      it('prefetchApp 未注册应用时应静默返回不抛错', async () => {
        kernel.registerApps([]);
        kernel.start();

        await expect(kernel.prefetchApp('not-registered')).resolves.toBeUndefined();
      });
    });
  });
});
