/**
 * scheduler 模块单元测试
 *
 * @path comm/effects/micro-kernel-lite/__tests__/scheduler.test.ts
 * @author ydsz-team
 * @since 3.0.0
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  type AppInstance,
  activateApp,
  createAppInstance,
  deactivateApp,
  getAppInstance,
  getAllInstances,
  setKeepAlive,
} from '../src/scheduler';

import type { MicroAppConfig } from '@ydsz/micro-runtime';

// Mock dynamic import
vi.mock('../src/loader', () => ({
  loadApp: vi.fn(async (_config: MicroAppConfig) => ({
    exports: {
      mount: vi.fn(async (_props: Record<string, unknown>) => {
        const container = _props.container as HTMLElement;
        const div = document.createElement('div');
        div.id = `app-${_config.name}`;
        container.appendChild(div);
      }),
      unmount: vi.fn(async () => {
        document.querySelector(`#app-${_config.name}`)?.remove();
      }),
    },
    manifest: { name: 'test-app', entry: '/test.js', css: [], version: '1.0.0' },
    duration: 42,
    fromCache: false,
  })),
  removeStylesheets: vi.fn(),
}));

describe('scheduler', () => {
  const baseConfig: MicroAppConfig = {
    name: 'test-app',
    entry: '/test-app/',
    container: '#container',
    activeRule: '/test',
  };

  let container: HTMLElement;

  beforeEach(() => {
    document.body.innerHTML = '<div id="container"></div>';
    container = document.getElementById('container')!;
  });

  describe('createAppInstance', () => {
    it('应创建 NOT_LOADED 状态的实例', () => {
      const instance = createAppInstance(baseConfig);
      expect(instance.status).toBe('NOT_LOADED');
      expect(instance.exports).toBeNull();
      expect(instance.keepAlive).toBe(false);
      expect(instance.error).toBeNull();
    });

    it('应注册到全局实例表', () => {
      createAppInstance(baseConfig);
      expect(getAppInstance('test-app')).toBeDefined();
    });
  });

  describe('activateApp', () => {
    it('应加载并挂载子应用', async () => {
      const instance = createAppInstance(baseConfig);
      await activateApp(instance, container);

      expect(instance.status).toBe('MOUNTED');
      expect(instance.exports).not.toBeNull();
      expect(document.querySelector('#app-test-app')).not.toBeNull();
    });

    it('已挂载的应用不应重复挂载', async () => {
      const instance = createAppInstance(baseConfig);
      await activateApp(instance, container);
      const firstExports = instance.exports;

      await activateApp(instance, container);
      expect(instance.exports).toBe(firstExports); // 未重新加载
    });
  });

  describe('deactivateApp', () => {
    it('应卸载已挂载的应用', async () => {
      const instance = createAppInstance(baseConfig);
      await activateApp(instance, container);

      const result = await deactivateApp(instance);
      expect(result.success).toBe(true);
      expect(instance.status).toBe('NOT_LOADED');
      expect(instance.exports).toBeNull();
    });

    it('未挂载的应用不应失败', async () => {
      const instance = createAppInstance(baseConfig);
      const result = await deactivateApp(instance);
      expect(result.success).toBe(true);
    });
  });

  describe('keepAlive', () => {
    it('保活模式下应摘除 DOM 而不销毁组件', async () => {
      const instance = createAppInstance(baseConfig);
      setKeepAlive('test-app', true);
      await activateApp(instance, container);

      expect(instance.keepAlive).toBe(true);

      const result = await deactivateApp(instance);
      expect(result.success).toBe(true);
      expect(instance.cachedRoot).not.toBeNull();
      // 容器不再有子元素
      expect(container.childElementCount).toBe(0);
    });

    it('保活复用时应直接放回 DOM', async () => {
      const instance = createAppInstance(baseConfig);
      setKeepAlive('test-app', true);
      await activateApp(instance, container);
      await deactivateApp(instance);

      // 重新激活
      await activateApp(instance, container);
      expect(instance.status).toBe('MOUNTED');
      expect(container.childElementCount).toBe(1);
      expect(instance.cachedParent).toBeNull(); // 每次激活后清空
    });
  });

  describe('getAllInstances', () => {
    it('应返回所有已注册实例', () => {
      createAppInstance({ ...baseConfig, name: 'app-a', activeRule: '/a' });
      createAppInstance({ ...baseConfig, name: 'app-b', activeRule: '/b' });
      expect(getAllInstances().length).toBe(3); // 2 new + 1 from previous
    });
  });
});
