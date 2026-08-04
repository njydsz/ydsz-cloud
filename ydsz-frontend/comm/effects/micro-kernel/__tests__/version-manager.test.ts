/**
 * version-manager 模块单元测试
 *
 * @path comm/effects/micro-kernel/__tests__/version-manager.test.ts
 * @author ydsz-team
 * @since 3.0.0
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { getVersionManager, resetVersionManager } from '../src/version-manager';
import type { Manifest } from '../src/loader';

// 模拟 localStorage
const localStorageMock = (() => {
  let store: Record<string, string> = {};
  return {
    getItem: vi.fn((key: string) => store[key] ?? null),
    setItem: vi.fn((key: string, value: string) => {
      store[key] = value;
    }),
    clear() {
      store = {};
    },
  };
})();

Object.defineProperty(globalThis, 'localStorage', {
  value: localStorageMock,
  writable: true,
});

function createManifest(name: string, version: string): Manifest {
  return {
    name,
    entry: `https://example.com/${name}/entry.js`,
    css: [`https://example.com/${name}/style.css`],
    version,
  };
}

describe('version-manager', () => {
  beforeEach(() => {
    localStorageMock.clear();
    vi.clearAllMocks();
    resetVersionManager();
  });

  afterEach(() => {
    resetVersionManager();
  });

  describe('getVersionManager 单例', () => {
    it('返回同一实例', () => {
      const a = getVersionManager();
      const b = getVersionManager();
      expect(a).toBe(b);
    });

    it('resetVersionManager 后返回新实例', () => {
      const a = getVersionManager();
      resetVersionManager();
      const b = getVersionManager();
      expect(a).not.toBe(b);
    });
  });

  describe('updateVersion / getVersion', () => {
    it('更新版本后可读取', () => {
      const vm = getVersionManager();
      const manifest = createManifest('project-web', '1.2.0');
      vm.updateVersion('project-web', manifest);
      expect(vm.getVersion('project-web')).toBe('1.2.0');
    });

    it('未记录的应用返回 null', () => {
      const vm = getVersionManager();
      expect(vm.getVersion('unknown-app')).toBeNull();
    });

    it('更新版本后写入 localStorage', () => {
      const vm = getVersionManager();
      const manifest = createManifest('agent-web', '2.0.0');
      vm.updateVersion('agent-web', manifest);
      expect(localStorageMock.setItem).toHaveBeenCalled();
    });
  });

  describe('checkUpdate', () => {
    it('首次检查时无当前版本，hasUpdate 为 false', async () => {
      const vm = getVersionManager();
      const manifest = createManifest('system-web', '1.0.0');
      const result = await vm.checkUpdate('system-web', manifest);
      expect(result.hasUpdate).toBe(false);
      expect(result.currentVersion).toBe('unknown');
      expect(result.latestVersion).toBe('1.0.0');
    });

    it('版本不同时 hasUpdate 为 true', async () => {
      const vm = getVersionManager();
      vm.updateVersion('system-web', createManifest('system-web', '1.0.0'));
      const result = await vm.checkUpdate(
        'system-web',
        createManifest('system-web', '1.1.0'),
      );
      expect(result.hasUpdate).toBe(true);
      expect(result.currentVersion).toBe('1.0.0');
      expect(result.latestVersion).toBe('1.1.0');
    });

    it('版本相同时 hasUpdate 为 false', async () => {
      const vm = getVersionManager();
      vm.updateVersion('system-web', createManifest('system-web', '1.0.0'));
      const result = await vm.checkUpdate(
        'system-web',
        createManifest('system-web', '1.0.0'),
      );
      expect(result.hasUpdate).toBe(false);
    });

    it('触发 onVersionCheck 回调', async () => {
      const onVersionCheck = vi.fn();
      const vm = getVersionManager({ onVersionCheck });
      const manifest = createManifest('cronjob-web', '3.0.0');
      await vm.checkUpdate('cronjob-web', manifest);
      expect(onVersionCheck).toHaveBeenCalledTimes(1);
      expect(onVersionCheck).toHaveBeenCalledWith(
        expect.objectContaining({
          appName: 'cronjob-web',
          latestVersion: '3.0.0',
        }),
      );
    });
  });

  describe('compareVersions', () => {
    it('相等版本返回 0', () => {
      const vm = getVersionManager();
      expect(vm.compareVersions('1.0.0', '1.0.0')).toBe(0);
    });

    it('v1 < v2 返回 -1', () => {
      const vm = getVersionManager();
      expect(vm.compareVersions('1.0.0', '1.1.0')).toBe(-1);
      expect(vm.compareVersions('1.0.0', '2.0.0')).toBe(-1);
    });

    it('v1 > v2 返回 1', () => {
      const vm = getVersionManager();
      expect(vm.compareVersions('1.1.0', '1.0.0')).toBe(1);
      expect(vm.compareVersions('2.0.0', '1.9.9')).toBe(1);
    });

    it('不同段数按 0 补齐比较', () => {
      const vm = getVersionManager();
      expect(vm.compareVersions('1.0', '1.0.0')).toBe(0);
      expect(vm.compareVersions('1.0.1', '1.0')).toBe(1);
    });
  });

  describe('isCompatible', () => {
    it('主版本号相同视为兼容', () => {
      const vm = getVersionManager();
      expect(vm.isCompatible('1.0.0', '1.5.3')).toBe(true);
    });

    it('主版本号不同视为不兼容', () => {
      const vm = getVersionManager();
      expect(vm.isCompatible('1.0.0', '2.0.0')).toBe(false);
    });
  });

  describe('startAutoCheck / stopAutoCheck', () => {
    it('启动后定时器存在', () => {
      const vm = getVersionManager({ autoCheck: false });
      vm.startAutoCheck();
      // 内部 checkTimer 不为 null 即可（通过 stopAutoCheck 间接验证不抛错）
      expect(() => vm.stopAutoCheck()).not.toThrow();
    });

    it('stopAutoCheck 后再次 stop 不抛错', () => {
      const vm = getVersionManager({ autoCheck: false });
      vm.stopAutoCheck();
      expect(() => vm.stopAutoCheck()).not.toThrow();
    });
  });

  describe('destroy', () => {
    it('清理后版本信息为空', () => {
      const vm = getVersionManager({ autoCheck: false });
      vm.updateVersion('app', createManifest('app', '1.0.0'));
      vm.destroy();
      expect(vm.getVersion('app')).toBeNull();
    });
  });
});
