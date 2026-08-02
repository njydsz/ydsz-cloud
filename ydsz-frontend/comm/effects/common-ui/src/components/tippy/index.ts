/**
 * index 模块
 *
 * @path comm\effects\common-ui\src\components\tippy\index.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { DefaultProps, Props } from 'tippy.js';

import type { App, SetupContext } from 'vue';

import { h, watchEffect } from 'vue';
import { setDefaultProps, Tippy as TippyComponent } from 'vue-tippy';

import { usePreferences } from '@ydsz-core/preferences';

import useTippyDirective from './directive';

import 'tippy.js/dist/tippy.css';
import 'tippy.js/dist/backdrop.css';
import 'tippy.js/themes/light.css';
import 'tippy.js/animations/scale.css';
import 'tippy.js/animations/shift-toward.css';
import 'tippy.js/animations/shift-away.css';
import 'tippy.js/animations/perspective.css';

const { isDark } = usePreferences();

/**
 * Tippy 提示组件 / 指令的配置项类型。
 *
 * @remarks
 * 在 tippy.js 原生 `Props` 的基础上做了两处收窄：
 * - `animation` 仅保留项目内已引入 CSS 的几种动画（传其他值会因缺少样式而失效）；
 * - `theme` 扩展出 `'auto'`，表示跟随系统 / 应用的暗色偏好自动切换。
 *
 * 所有字段均为可选，未指定的部分回退到 {@link initTippy} 设置的全局默认值。
 */
export type TippyProps = Partial<
  Props & {
    animation?:
      | 'fade'
      | 'perspective'
      | 'scale'
      | 'shift-away'
      | 'shift-toward'
      | boolean;
    theme?: 'auto' | 'dark' | 'light';
  }
>;

/**
 * 初始化 Tippy：设置全局默认配置并注册 `v-tippy` 指令。
 *
 * @remarks
 * 应在应用启动阶段调用一次；重复调用会覆盖此前的全局默认配置。
 *
 * 主题策略：仅当调用方**未传 `theme`** 或显式传入 `'auto'` 时，才建立 `watchEffect`
 * 跟随暗色偏好自动切换主题（暗色使用 tippy 默认主题，亮色使用 `'light'`）；
 * 一旦传入固定主题则不再自动跟随。该 `watchEffect` 在应用整个生命周期内常驻，不会被回收。
 *
 * 默认开启 `allowHTML: true`，因此 `content` 中的 HTML 会被解析——
 * 渲染用户输入时务必先做转义，避免 XSS。
 *
 * @param app - Vue 应用实例
 * @param options - tippy 全局默认配置，会与内置默认值浅合并并覆盖同名项
 */
export function initTippy(app: App<Element>, options?: DefaultProps) {
  setDefaultProps({
    allowHTML: true,
    delay: [500, 200],
    theme: isDark.value ? '' : 'light',
    ...options,
  });
  if (!options || !Reflect.has(options, 'theme') || options.theme === 'auto') {
    watchEffect(() => {
      setDefaultProps({ theme: isDark.value ? '' : 'light' });
    });
  }

  app.directive('tippy', useTippyDirective(isDark));
}

/**
 * 对 `vue-tippy` 组件的函数式包装，补充 `theme='auto'` 的暗色自适应能力。
 *
 * @remarks
 * 主题映射规则：`'auto'`（默认）根据暗色偏好解析为 `''` 或 `'light'`；
 * `'dark'` 映射为 `''`（tippy 的默认主题即暗色）；其余值原样透传给底层组件。
 *
 * 这是一个无状态函数式组件，`attrs` 会覆盖同名 `props`，插槽全部透传。
 *
 * @param props - 透传给底层 Tippy 组件的配置，见 {@link TippyProps}
 * @param ctx - 组件上下文，其中 `attrs.theme` 参与主题解析，`slots` 原样透传
 * @returns 底层 Tippy 组件的 VNode
 */
export const Tippy = (props: any, { attrs, slots }: SetupContext) => {
  let theme: string = (attrs.theme as string) ?? 'auto';
  if (theme === 'auto') {
    theme = isDark.value ? '' : 'light';
  }
  if (theme === 'dark') {
    theme = '';
  }
  return h(
    TippyComponent,
    {
      ...props,
      ...attrs,
      theme,
    },
    slots,
  );
};
