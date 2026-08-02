/**
 * use-is-mobile 组合式函数
 *
 * @path comm\@core\composables\src\use-is-mobile.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import { breakpointsTailwind, useBreakpoints } from '@vueuse/core';

/**
 * 提供响应式的「是否处于移动端视口」标记，用于驱动布局在窄屏下切换形态。
 *
 * @remarks
 * 判定依据是**视口宽度**而非 UA，因此 PC 端把窗口拖窄同样会被判为移动端，
 * 这正是响应式布局所需的语义；如果业务真正要识别设备类型，请勿使用本函数。
 *
 * 断点沿用 Tailwind 预设，`md` 对应 768px，即视口宽度 &lt; 768px 时 `isMobile` 为 `true`，
 * 与项目 CSS 中的 `md:` 前缀保持同一套阈值，避免 JS 与样式判断不一致导致布局撕裂。
 *
 * 副作用：内部会注册 `matchMedia` 监听，组件卸载时由 VueUse 自动清理，无需手动释放。
 * 因依赖 `window`，**只能在组件 setup 作用域内调用**，SSR 首屏渲染阶段取值可能不准，
 * 需要精确值的场景应在 `onMounted` 之后读取。
 *
 * @returns 包含 `isMobile` 只读响应式布尔值的对象；可直接用于模板或 `watch`
 *
 * @example
 * ```ts
 * const { isMobile } = useIsMobile();
 * watch(isMobile, (v) => v && closeSidebar());
 * ```
 */
export function useIsMobile() {
  const breakpoints = useBreakpoints(breakpointsTailwind);
  const isMobile = breakpoints.smaller('md');
  return { isMobile };
}
