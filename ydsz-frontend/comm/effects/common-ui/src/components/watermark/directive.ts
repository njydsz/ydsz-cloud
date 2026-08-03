/**
 * watermark 指令模块
 *
 * @remarks
 * E3 敏感页面水印指令集成：通过 `v-watermark` 指令在任意元素上声明式叠加水印，
 * 无需在布局层手动调用 `useWatermark`。适合敏感页面（用户详情、财务报表等）
 * 按需打标，指令卸载时自动销毁水印实例。
 *
 * 用法：
 * ```vue
 * <!-- 纯文本水印 -->
 * <div v-watermark="userName">敏感内容</div>
 *
 * <!-- 完整选项 -->
 * <div v-watermark="{ content: '机密', globalAlpha: 0.2 }">财务报表</div>
 * ```
 *
 * @path comm/effects/common-ui/src/components/watermark/directive.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { WatermarkOptions } from 'watermark-js-plus';

import type { App, Directive, DirectiveBinding } from 'vue';

const WATERMARK_INSTANCE_KEY = Symbol('ydsz-watermark');

/** 指令绑定的值：字符串（仅内容）或完整水印选项 */
type WatermarkBinding = Partial<WatermarkOptions> | string;

/** 默认水印样式，与 useWatermark 保持一致 */
const DEFAULT_WATERMARK_OPTIONS: Partial<WatermarkOptions> = {
  advancedStyle: {
    colorStops: [
      { color: 'gray', offset: 0 },
      { color: 'gray', offset: 1 },
    ],
    type: 'linear',
  },
  contentType: 'multi-line-text',
  globalAlpha: 0.25,
  gridLayoutOptions: {
    cols: 2,
    gap: [20, 20],
    matrix: [
      [1, 0],
      [0, 1],
    ],
    rows: 2,
  },
  height: 200,
  layout: 'grid',
  rotate: 30,
  width: 160,
};

/** 将指令绑定值规范化为 Partial<WatermarkOptions> */
function normalizeBinding(
  binding: DirectiveBinding<WatermarkBinding>,
): Partial<WatermarkOptions> {
  const value = binding.value;
  if (typeof value === 'string') {
    return { content: value };
  }
  return { ...value };
}

/** 存储在元素上的水印实例句柄 */
interface WatermarkElement extends HTMLElement {
  [WATERMARK_INSTANCE_KEY]?: {
    destroy: () => void;
    changeOptions: (opts: Partial<WatermarkOptions>) => Promise<void>;
  } | null;
}

const watermarkDirective: Directive<WatermarkElement, WatermarkBinding> = {
  async mounted(el, binding) {
    const options = normalizeBinding(binding);
    if (!options.content) return;

    const { Watermark } = await import('watermark-js-plus');
    const merged: Partial<WatermarkOptions> = {
      ...DEFAULT_WATERMARK_OPTIONS,
      ...options,
      parent: el,
    };

    const instance = new Watermark(merged as WatermarkOptions);
    await instance.create();
    el[WATERMARK_INSTANCE_KEY] = instance;
  },

  async updated(el, binding) {
    // 值未变化时跳过
    if (binding.value === binding.oldValue) return;

    const options = normalizeBinding(binding);
    const instance = el[WATERMARK_INSTANCE_KEY];

    if (!options.content) {
      // 内容清空 → 销毁
      if (instance) {
        instance.destroy();
        el[WATERMARK_INSTANCE_KEY] = null;
      }
      return;
    }

    if (instance) {
      await instance.changeOptions({
        ...DEFAULT_WATERMARK_OPTIONS,
        ...options,
        parent: el,
      });
    } else {
      // 之前无实例（之前 content 为空），重新创建
      const { Watermark } = await import('watermark-js-plus');
      const merged: Partial<WatermarkOptions> = {
        ...DEFAULT_WATERMARK_OPTIONS,
        ...options,
        parent: el,
      };
      const newInstance = new Watermark(merged as WatermarkOptions);
      await newInstance.create();
      el[WATERMARK_INSTANCE_KEY] = newInstance;
    }
  },

  unmounted(el) {
    const instance = el[WATERMARK_INSTANCE_KEY];
    if (instance) {
      instance.destroy();
      el[WATERMARK_INSTANCE_KEY] = null;
    }
  },
};

type WatermarkDirectiveParams = {
  /** 是否注册水印指令。提供 string 则注册为指定名称 */
  watermark?: boolean | string;
};

/**
 * 注册水印指令。
 *
 * @param app - Vue 应用实例
 * @param params - 注册参数
 */
export function registerWatermarkDirective(
  app: App,
  params?: WatermarkDirectiveParams,
): void {
  if (params?.watermark !== false) {
    const name =
      typeof params?.watermark === 'string' ? params.watermark : 'watermark';
    app.directive(name, watermarkDirective);
  }
}

export { watermarkDirective };
export type { WatermarkBinding };
