/**
 * proxy-sandbox 模块单元测试
 *
 * @path comm/effects/micro-kernel/__tests__/proxy-sandbox.test.ts
 * @author ydsz-team
 * @since 3.2.0
 */
import { describe, expect, it, vi } from 'vitest';

import { createProxySandbox } from '../src/proxy-sandbox';

describe('proxy-sandbox', () => {
  describe('createProxySandbox 基础行为', () => {
    it('创建实例包含 fakeWindow / activate / deactivate / cleanup', () => {
      const sb = createProxySandbox('test-app');
      expect(sb.fakeWindow).toBeDefined();
      expect(typeof sb.activate).toBe('function');
      expect(typeof sb.deactivate).toBe('function');
      expect(typeof sb.cleanup).toBe('function');
    });

    it('activate 幂等：重复激活不抛错', () => {
      const sb = createProxySandbox('test-app');
      expect(() => sb.activate()).not.toThrow();
      expect(() => sb.activate()).not.toThrow();
    });

    it('deactivate 幂等：未激活时停用不抛错', () => {
      const sb = createProxySandbox('test-app');
      expect(() => sb.deactivate()).not.toThrow();
    });
  });

  describe('fakeWindow 数据隔离', () => {
    it('子应用写入的属性可读取', () => {
      const sb = createProxySandbox('test-app');
      sb.activate();
      const fw = sb.fakeWindow as Record<string, unknown>;
      fw.__customProp = 'hello';
      expect(fw.__customProp).toBe('hello');
      sb.cleanup();
    });

    it('cleanup 后写入的属性被清除', () => {
      const sb = createProxySandbox('test-app');
      sb.activate();
      const fw = sb.fakeWindow as Record<string, unknown>;
      fw.__tempProp = 'temp';
      sb.cleanup();
      expect(fw.__tempProp).toBeUndefined();
    });

    it('读取未设置的属性返回真实 window 的值', () => {
      const sb = createProxySandbox('test-app');
      sb.activate();
      // window.console 是 immutableProps 之一，应能读取真实值
      expect(sb.fakeWindow.console).toBe(window.console);
      sb.cleanup();
    });
  });

  describe('immutableProps 不可修改', () => {
    const immutableKeys = [
      'document',
      'location',
      'navigator',
      'history',
      'localStorage',
      'sessionStorage',
      'console',
      'window',
      'self',
      'globalThis',
    ];

    for (const key of immutableKeys) {
      it(`不可修改 ${key}`, () => {
        const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
        const sb = createProxySandbox('test-app');
        sb.activate();
        const fw = sb.fakeWindow as Record<string, unknown>;
        // 赋值不抛错（被 set 拦截忽略），但原值不变
        fw[key] = 'tampered';
        expect(window[key as keyof Window]).not.toBe('tampered');
        sb.cleanup();
        warnSpy.mockRestore();
      });
    }
  });

  describe('has / deleteProperty', () => {
    it('has 检查 fakeWindow 与真实 window', () => {
      const sb = createProxySandbox('test-app');
      sb.activate();
      const fw = sb.fakeWindow as Record<string, unknown>;
      fw.__customHas = 1;
      // 自定义属性存在
      expect('__customHas' in fw).toBe(true);
      // 真实 window 属性也存在
      expect('console' in fw).toBe(true);
      sb.cleanup();
    });

    it('deleteProperty 删除 fakeWindow 中的属性', () => {
      const sb = createProxySandbox('test-app');
      sb.activate();
      const fw = sb.fakeWindow as Record<string, unknown>;
      fw.__deletable = 'yes';
      expect(fw.__deletable).toBe('yes');
      // eslint-disable-next-line @typescript-eslint/no-dynamic-delete
      delete fw.__deletable;
      expect(fw.__deletable).toBeUndefined();
      sb.cleanup();
    });
  });

  describe('defineProperty', () => {
    it('在 fakeWindow 上定义属性', () => {
      const sb = createProxySandbox('test-app');
      sb.activate();
      const fw = sb.fakeWindow as Record<string, unknown>;
      Object.defineProperty(fw, '__definedProp', {
        value: 42,
        writable: false,
        enumerable: true,
        configurable: false,
      });
      expect(fw.__definedProp).toBe(42);
      sb.cleanup();
    });
  });

  describe('getPrototypeOf', () => {
    it('返回真实 window 的原型', () => {
      const sb = createProxySandbox('test-app');
      const proto = Object.getPrototypeOf(sb.fakeWindow);
      expect(proto).toBe(Object.getPrototypeOf(window));
      sb.cleanup();
    });
  });

  describe('生命周期', () => {
    it('activate → deactivate → cleanup 完整流程', () => {
      const sb = createProxySandbox('lifecycle-app');
      expect(() => {
        sb.activate();
        const fw = sb.fakeWindow as Record<string, unknown>;
        fw.__lifecycle = 'data';
        sb.deactivate();
        sb.cleanup();
      }).not.toThrow();
    });

    it('cleanup 后可重新 activate', () => {
      const sb = createProxySandbox('reuse-app');
      sb.activate();
      sb.cleanup();
      expect(() => sb.activate()).not.toThrow();
      sb.cleanup();
    });
  });
});
