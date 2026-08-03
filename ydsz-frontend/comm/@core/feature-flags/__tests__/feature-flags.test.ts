/**
 * 功能开关管理器测试
 *
 * @path comm/@core/feature-flags/__tests__/feature-flags.test.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { FeatureFlagsManager } from '../src/feature-flags';
import { useFeatureFlag } from '../src/use-feature-flag';

describe('feature-flags', () => {
  let mgr: FeatureFlagsManager;

  beforeEach(() => {
    localStorage.clear();
    mgr = new FeatureFlagsManager();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  /** dev 环境 env 快照 */
  const DEV_ENV = { DEV: true, MODE: 'development' };
  /** prod 环境 env 快照 */
  const PROD_ENV = { DEV: false, MODE: 'production' };

  describe('注册与默认值', () => {
    it('register 后 isEnabled 应返回 defaultValue', () => {
      mgr.register([
        { name: 'new-dashboard', defaultValue: true },
        { name: 'legacy-export', defaultValue: false },
      ]);
      expect(mgr.isEnabled('new-dashboard')).toBe(true);
      expect(mgr.isEnabled('legacy-export')).toBe(false);
    });

    it('未注册的开关 isEnabled 返回 false', () => {
      mgr.register([{ name: 'a', defaultValue: true }]);
      expect(mgr.isEnabled('not-registered')).toBe(false);
    });

    it('getDef 返回已注册定义', () => {
      mgr.register([
        { name: 'x', defaultValue: false, description: 'test', allowLocalOverride: false },
      ]);
      expect(mgr.getDef('x')?.description).toBe('test');
      expect(mgr.getDef('missing')).toBeUndefined();
    });
  });

  describe('本地覆盖（dev 环境）', () => {
    it('setEnabled 在 dev 环境应生效并覆盖默认值', async () => {
      mgr.register([{ name: 'flag-a', defaultValue: false }]);
      await mgr.init({ env: DEV_ENV, namespace: 'ydsz' });
      expect(mgr.isEnabled('flag-a')).toBe(false);

      mgr.setEnabled('flag-a', true);
      expect(mgr.isEnabled('flag-a')).toBe(true);
    });

    it('resetFlag 应回退到默认值', async () => {
      mgr.register([{ name: 'flag-b', defaultValue: true }]);
      await mgr.init({ env: DEV_ENV, namespace: 'ydsz' });
      mgr.setEnabled('flag-b', false);
      expect(mgr.isEnabled('flag-b')).toBe(false);

      mgr.resetFlag('flag-b');
      expect(mgr.isEnabled('flag-b')).toBe(true);
    });

    it('未注册开关调用 setEnabled 应被拒绝并警告', async () => {
      await mgr.init({ env: DEV_ENV, namespace: 'ydsz' });
      const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
      mgr.setEnabled('unknown', true);
      expect(warn).toHaveBeenCalledWith(
        expect.stringContaining('Unknown flag "unknown"'),
      );
    });

    it('allowLocalOverride=false 的开关应拒绝 setEnabled', async () => {
      mgr.register([
        { name: 'billing-gated', defaultValue: false, allowLocalOverride: false },
      ]);
      await mgr.init({ env: DEV_ENV, namespace: 'ydsz' });
      const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
      mgr.setEnabled('billing-gated', true);
      expect(warn).toHaveBeenCalledWith(
        expect.stringContaining('not allowed'),
      );
      expect(mgr.isEnabled('billing-gated')).toBe(false);
    });
  });

  describe('生产环境本地覆盖控制', () => {
    it('生产环境且 enableLocalOverrideInProd=false 时 setEnabled 应被拒绝', async () => {
      mgr.register([{ name: 'prod-flag', defaultValue: false }]);
      await mgr.init({ env: PROD_ENV, namespace: 'ydsz' });

      const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
      mgr.setEnabled('prod-flag', true);
      expect(warn).toHaveBeenCalledWith(
        expect.stringContaining('not allowed'),
      );
      expect(mgr.isEnabled('prod-flag')).toBe(false);
    });

    it('生产环境且 enableLocalOverrideInProd=true 时 setEnabled 应生效', async () => {
      mgr.register([{ name: 'prod-override', defaultValue: false }]);
      await mgr.init({
        env: PROD_ENV,
        namespace: 'ydsz',
        enableLocalOverrideInProd: true,
      });

      mgr.setEnabled('prod-override', true);
      expect(mgr.isEnabled('prod-override')).toBe(true);
    });
  });

  describe('构建期环境变量', () => {
    it('VITE_FEATURE_<NAME> 应覆盖 defaultValue', async () => {
      mgr.register([
        { name: 'new-dashboard', defaultValue: false },
        { name: 'legacy-export', defaultValue: true },
        { name: 'no-env', defaultValue: true },
      ]);
      await mgr.init({
        env: {
          ...DEV_ENV,
          VITE_FEATURE_NEW_DASHBOARD: 'true',
          VITE_FEATURE_LEGACY_EXPORT: 'false',
        },
        namespace: 'ydsz',
      });
      expect(mgr.isEnabled('new-dashboard')).toBe(true);
      expect(mgr.isEnabled('legacy-export')).toBe(false);
      expect(mgr.isEnabled('no-env')).toBe(true); // 无 env，回退默认
    });

    it('连字符开关名应转为大写下划线 envKey', async () => {
      mgr.register([{ name: 'ai-assistant', defaultValue: false }]);
      await mgr.init({
        env: { ...DEV_ENV, VITE_FEATURE_AI_ASSISTANT: 'true' },
        namespace: 'ydsz',
      });
      expect(mgr.isEnabled('ai-assistant')).toBe(true);
    });

    it('boolean 型 env 值（true/false）也应识别', async () => {
      mgr.register([{ name: 'bool-on', defaultValue: false }]);
      await mgr.init({
        env: { ...DEV_ENV, VITE_FEATURE_BOOL_ON: true },
        namespace: 'ydsz',
      });
      expect(mgr.isEnabled('bool-on')).toBe(true);
    });
  });

  describe('远程配置', () => {
    it('loadRemote 合并远程值，优先级高于默认值但低于本地覆盖', async () => {
      const remoteLoader = vi.fn().mockResolvedValue({
        'remote-on': 'on',
        'remote-off': 'off',
      });
      mgr.register([
        { name: 'remote-on', defaultValue: false },
        { name: 'remote-off', defaultValue: true },
        { name: 'no-remote', defaultValue: true },
      ]);
      await mgr.init({ env: DEV_ENV, namespace: 'ydsz', remoteLoader });
      await mgr.loadRemote();

      expect(mgr.isEnabled('remote-on')).toBe(true);
      expect(mgr.isEnabled('remote-off')).toBe(false);
      expect(mgr.isEnabled('no-remote')).toBe(true);
    });

    it('本地覆盖优先级高于远程', async () => {
      const remoteLoader = vi.fn().mockResolvedValue({ overridable: true });
      mgr.register([{ name: 'overridable', defaultValue: false }]);
      await mgr.init({ env: DEV_ENV, namespace: 'ydsz', remoteLoader });
      await mgr.loadRemote();

      // 远程设为 true，本地覆盖设为 false → 最终 false
      mgr.setEnabled('overridable', false);
      expect(mgr.isEnabled('overridable')).toBe(false);
    });

    it('远程加载失败不抛错且保留既有状态', async () => {
      const remoteLoader = vi.fn().mockRejectedValue(new Error('network'));
      const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
      mgr.register([{ name: 'stable', defaultValue: true }]);
      await mgr.init({ env: DEV_ENV, namespace: 'ydsz', remoteLoader });
      await mgr.loadRemote();

      expect(warn).toHaveBeenCalledWith(
        expect.stringContaining('Remote loader failed'),
        expect.anything(),
      );
      expect(mgr.isEnabled('stable')).toBe(true);
    });
  });

  describe('localStorage 持久化', () => {
    it('init 应恢复 localStorage 中已注册且允许本地覆盖的开关', async () => {
      const ns = 'ydsz-persist';
      const key = `${ns}-feature-flags`;
      localStorage.setItem(
        key,
        JSON.stringify({
          value: { 'persisted-on': true, blocked: true },
        }),
      );
      mgr.register([
        { name: 'persisted-on', defaultValue: false },
        { name: 'blocked', defaultValue: false, allowLocalOverride: false },
      ]);
      await mgr.init({ env: DEV_ENV, namespace: ns });

      expect(mgr.isEnabled('persisted-on')).toBe(true);
      // blocked 不允许本地覆盖 → 持久化值应被忽略，回退默认
      expect(mgr.isEnabled('blocked')).toBe(false);
    });

    it('setEnabled 应持久化到 localStorage', async () => {
      const ns = 'ydsz-write';
      mgr.register([{ name: 'w', defaultValue: false }]);
      await mgr.init({ env: DEV_ENV, namespace: ns });
      mgr.setEnabled('w', true);

      const stored = JSON.parse(
        localStorage.getItem(`${ns}-feature-flags`) ?? '{}',
      );
      expect(stored.value.w).toBe(true);
    });
  });

  describe('onChange 监听器', () => {
    it('setEnabled 触发监听器并传入新值', async () => {
      mgr.register([{ name: 'listened', defaultValue: false }]);
      await mgr.init({ env: DEV_ENV, namespace: 'ydsz' });
      const listener = vi.fn();
      mgr.onChange(listener);

      mgr.setEnabled('listened', true);
      expect(listener).toHaveBeenCalledWith('listened', true);
    });

    it('取消订阅后不再触发', async () => {
      mgr.register([{ name: 'off', defaultValue: false }]);
      await mgr.init({ env: DEV_ENV, namespace: 'ydsz' });
      const listener = vi.fn();
      const unsubscribe = mgr.onChange(listener);

      unsubscribe();
      mgr.setEnabled('off', true);
      expect(listener).not.toHaveBeenCalled();
    });
  });

  describe('useFeatureFlag 组合式', () => {
    it('返回响应式 ComputedRef，状态变化时更新', async () => {
      const { effectScope } = await import('vue');
      const { featureFlagsManager } = await import('../src/feature-flags');
      featureFlagsManager.reset();
      featureFlagsManager.register([{ name: 'reactive', defaultValue: false }]);
      await featureFlagsManager.init({ env: DEV_ENV, namespace: 'ydsz-composable' });

      const scope = effectScope();
      let value = false;
      const flag = scope.run(() => useFeatureFlag('reactive'))!;
      value = flag.value;
      expect(value).toBe(false);

      featureFlagsManager.setEnabled('reactive', true);
      value = flag.value;
      expect(value).toBe(true);

      featureFlagsManager.resetFlag('reactive');
      value = flag.value;
      expect(value).toBe(false);

      scope.stop();
      featureFlagsManager.reset();
    });
  });

  describe('reset', () => {
    it('清空所有注册、覆盖与监听器', async () => {
      mgr.register([{ name: 'to-clear', defaultValue: true }]);
      await mgr.init({ env: DEV_ENV, namespace: 'ydsz' });
      const listener = vi.fn();
      mgr.onChange(listener);

      mgr.setEnabled('to-clear', false);
      expect(listener).toHaveBeenCalledTimes(1);

      mgr.reset();

      expect(mgr.isEnabled('to-clear')).toBe(false);
      expect(mgr.getDef('to-clear')).toBeUndefined();

      // reset 后重置 mock 计数，再触发未注册开关的 setEnabled
      listener.mockClear();
      const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
      mgr.setEnabled('to-clear', true); // reset 后未注册 → 拒绝，不触发监听器
      expect(listener).not.toHaveBeenCalled();
      expect(warn).toHaveBeenCalledWith(
        expect.stringContaining('Unknown flag "to-clear"'),
      );
    });
  });
});
