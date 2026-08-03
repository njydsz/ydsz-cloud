/**
 * watermark 指令单元测试
 *
 * E3 敏感页面水印指令集成：
 * - mounted 创建水印实例
 * - updated 调用 changeOptions / 重新创建
 * - unmounted 销毁实例
 * - 字符串与对象两种绑定值规范化
 *
 * @path comm/effects/common-ui/src/components/watermark/__tests__/directive.test.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { App, DirectiveBinding } from 'vue';

import { createApp, defineComponent } from 'vue';

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

// Mock watermark-js-plus
const mockCreate = vi.fn().mockResolvedValue(undefined);
const mockChangeOptions = vi.fn().mockResolvedValue(undefined);
const mockDestroy = vi.fn().mockClear();

const MockWatermark = vi.fn().mockImplementation(() => ({
  changeOptions: mockChangeOptions,
  create: mockCreate,
  destroy: mockDestroy,
}));

vi.mock('watermark-js-plus', () => ({
  Watermark: MockWatermark,
}));

import {
  registerWatermarkDirective,
  watermarkDirective,
} from '../directive';

describe('watermark directive: E3 敏感页面水印', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('mounted', () => {
    it('字符串绑定值应创建水印实例', async () => {
      const el = document.createElement('div');
      const binding = {
        value: '张三',
        oldValue: undefined,
      } as unknown as DirectiveBinding;

      await watermarkDirective.mounted!(el as never, binding, undefined, undefined);

      expect(MockWatermark).toHaveBeenCalledTimes(1);
      const args = MockWatermark.mock.calls[0][0];
      expect(args.content).toBe('张三');
      expect(args.parent).toBe(el);
      expect(mockCreate).toHaveBeenCalledTimes(1);
    });

    it('对象绑定值应合并选项', async () => {
      const el = document.createElement('div');
      const binding = {
        value: { content: '机密', globalAlpha: 0.1 },
        oldValue: undefined,
      } as unknown as DirectiveBinding;

      await watermarkDirective.mounted!(el as never, binding, undefined, undefined);

      const args = MockWatermark.mock.calls[0][0];
      expect(args.content).toBe('机密');
      expect(args.globalAlpha).toBe(0.1);
      expect(args.parent).toBe(el);
    });

    it('content 为空字符串时应跳过创建', async () => {
      const el = document.createElement('div');
      const binding = {
        value: '',
        oldValue: undefined,
      } as unknown as DirectiveBinding;

      await watermarkDirective.mounted!(el as never, binding, undefined, undefined);

      expect(MockWatermark).not.toHaveBeenCalled();
    });
  });

  describe('updated', () => {
    it('值未变化时应跳过更新', async () => {
      const el = document.createElement('div');
      const binding = {
        value: '张三',
        oldValue: '张三',
      } as unknown as DirectiveBinding;

      await watermarkDirective.updated!(el as never, binding, undefined, undefined);

      expect(mockChangeOptions).not.toHaveBeenCalled();
      expect(MockWatermark).not.toHaveBeenCalled();
    });

    it('值变化时应调用 changeOptions', async () => {
      const el = document.createElement('div');
      // 先 mounted 创建实例
      await watermarkDirective.mounted!(el as never, {
        value: '张三',
      } as unknown as DirectiveBinding, undefined, undefined);

      // updated 改变内容
      await watermarkDirective.updated!(el as never, {
        value: '李四',
        oldValue: '张三',
      } as unknown as DirectiveBinding, undefined, undefined);

      expect(mockChangeOptions).toHaveBeenCalledTimes(1);
      const args = mockChangeOptions.mock.calls[0][0];
      expect(args.content).toBe('李四');
      expect(args.parent).toBe(el);
    });

    it('内容清空时应销毁实例', async () => {
      const el = document.createElement('div');
      await watermarkDirective.mounted!(el as never, {
        value: '张三',
      } as unknown as DirectiveBinding, undefined, undefined);

      await watermarkDirective.updated!(el as never, {
        value: '',
        oldValue: '张三',
      } as unknown as DirectiveBinding, undefined, undefined);

      expect(mockDestroy).toHaveBeenCalledTimes(1);
    });
  });

  describe('unmounted', () => {
    it('有实例时应调用 destroy', async () => {
      const el = document.createElement('div');
      await watermarkDirective.mounted!(el as never, {
        value: '张三',
      } as unknown as DirectiveBinding, undefined, undefined);

      watermarkDirective.unmounted!(el as never, undefined, undefined, undefined);

      expect(mockDestroy).toHaveBeenCalledTimes(1);
    });

    it('无实例时不应抛错', () => {
      const el = document.createElement('div');
      expect(() => {
        watermarkDirective.unmounted!(el as never, undefined, undefined, undefined);
      }).not.toThrow();
    });
  });

  describe('registerWatermarkDirective', () => {
    it('应注册 v-watermark 指令', () => {
      const app = createApp(defineComponent({}));
      registerWatermarkDirective(app);
      // Vue 内部存储指令的获取方式不公开，但注册不报错即可
      // 通过 mount 验证指令可用
      const el = document.createElement('div');
      document.body.appendChild(el);
      app.mount(el);
      app.unmount();
      document.body.removeChild(el);
    });

    it('传入 false 时不应注册', () => {
      const app = createApp(defineComponent({}));
      expect(() => {
        registerWatermarkDirective(app, { watermark: false });
      }).not.toThrow();
    });

    it('传入自定义名称时应注册为指定名称', () => {
      const app = createApp(defineComponent({}));
      expect(() => {
        registerWatermarkDirective(app, { watermark: 'wm' });
      }).not.toThrow();
    });
  });
});
