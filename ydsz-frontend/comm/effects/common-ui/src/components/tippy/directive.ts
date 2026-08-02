/**
 * directive 模块
 *
 * @path comm\effects\common-ui\src\components\tippy\directive.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { ComputedRef, Directive } from 'vue';

import { useTippy } from 'vue-tippy';

/**
 * 创建 `v-tippy` 文字提示指令，把 tippy.js 的实例管理封装为指令生命周期。
 *
 * @remarks
 * 指令用法与匹配规则：
 * - 指令值可传字符串（等价于 `{ content }`）或完整的 tippy 配置对象；
 * - 修饰符中**除 `arrow` 外的第一个**被当作 `placement`（如 `v-tippy.top`），
 *   显式写在配置对象里的 `placement` 优先级更高；`.arrow` 修饰符用于开启箭头；
 * - 未显式给出 `content` 时，依次回退读取元素的 `title`、`content` 属性；
 *   命中 `title` 时会**移除该属性**（副作用），以避免浏览器原生 tooltip 与 tippy 重叠；
 * - 组件上声明的 `onTippyShow` / `onTippyShown` / `onTippyHide` / `onTippyHidden` / `onTippyMount`
 *   会被桥接为 tippy 的同名生命周期回调。
 *
 * 副作用与清理：`mounted` 时创建 tippy 实例并挂在元素上，`unmounted` 时调用 `destroy()` 释放，
 * 无需调用方手动清理。`updated` 时通过 `setProps` 增量更新配置，
 * **但此时才会应用 `isDark` 推导出的主题**——挂载阶段的主题由全局 `setDefaultProps` 决定。
 *
 * @param isDark - 暗色模式响应式标记，用于在配置更新时切换 tippy 主题（暗色为默认主题，亮色为 `'light'`）
 * @returns 可直接注册到 Vue 应用的指令对象
 *
 * @example
 * ```ts
 * app.directive('tippy', useTippyDirective(isDark));
 * ```
 * ```html
 * <button v-tippy.top.arrow="'保存草稿'">保存</button>
 * ```
 */
export default function useTippyDirective(isDark: ComputedRef<boolean>) {
  const directive: Directive = {
    mounted(el, binding, vnode) {
      const opts =
        typeof binding.value === 'string'
          ? { content: binding.value }
          : binding.value || {};

      const modifiers = Object.keys(binding.modifiers || {});
      const placement = modifiers.find((modifier) => modifier !== 'arrow');
      const withArrow = modifiers.includes('arrow');

      if (placement) {
        opts.placement = opts.placement || placement;
      }

      if (withArrow) {
        opts.arrow = opts.arrow === undefined ? true : opts.arrow;
      }

      if (vnode.props && vnode.props.onTippyShow) {
        opts.onShow = function (...args: any[]) {
          return vnode.props?.onTippyShow(...args);
        };
      }

      if (vnode.props && vnode.props.onTippyShown) {
        opts.onShown = function (...args: any[]) {
          return vnode.props?.onTippyShown(...args);
        };
      }

      if (vnode.props && vnode.props.onTippyHidden) {
        opts.onHidden = function (...args: any[]) {
          return vnode.props?.onTippyHidden(...args);
        };
      }

      if (vnode.props && vnode.props.onTippyHide) {
        opts.onHide = function (...args: any[]) {
          return vnode.props?.onTippyHide(...args);
        };
      }

      if (vnode.props && vnode.props.onTippyMount) {
        opts.onMount = function (...args: any[]) {
          return vnode.props?.onTippyMount(...args);
        };
      }

      if (el.getAttribute('title') && !opts.content) {
        opts.content = el.getAttribute('title');
        el.removeAttribute('title');
      }

      if (el.getAttribute('content') && !opts.content) {
        opts.content = el.getAttribute('content');
      }

      useTippy(el, opts);
    },
    unmounted(el) {
      if (el.$tippy) {
        el.$tippy.destroy();
      } else if (el._tippy) {
        el._tippy.destroy();
      }
    },

    updated(el, binding) {
      const opts =
        typeof binding.value === 'string'
          ? { content: binding.value, theme: isDark.value ? '' : 'light' }
          : Object.assign(
              { theme: isDark.value ? '' : 'light' },
              binding.value,
            );

      if (el.getAttribute('title') && !opts.content) {
        opts.content = el.getAttribute('title');
        el.removeAttribute('title');
      }

      if (el.getAttribute('content') && !opts.content) {
        opts.content = el.getAttribute('content');
      }

      if (el.$tippy) {
        el.$tippy.setProps(opts || {});
      } else if (el._tippy) {
        el._tippy.setProps(opts || {});
      }
    },
  };
  return directive;
}
