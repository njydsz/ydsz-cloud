/**
 * use-layout 组合式函数
 *
 * @path comm\@core\ui-kit\layout-ui\src\hooks\use-layout.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { LayoutType } from '@ydsz-core/typings';

import type { YDSZLayoutProps } from '../ydsz-layout';

import { computed } from 'vue';

/**
 * 把布局模式枚举展开为一组语义化的布尔标记，供布局组件按需渲染各区域。
 *
 * @remarks
 * 抽出该 hook 是为了把「模式判断」集中到一处：布局模板中若散落
 * `layout === 'xxx'` 的字符串比较，新增模式时极易漏改。
 *
 * 关键行为：**移动端会强制覆盖布局模式**。当 `props.isMobile` 为真时，
 * 无论外部传入什么 `layout`，`currentLayout` 一律返回 `'sidebar-nav'`，
 * 因为其余模式在窄屏下无法正常排布。这也意味着移动端下所有其他模式标记恒为 `false`，
 * 调试时若发现设置的布局不生效，应先确认是否被判定为移动端。
 *
 * 另外 `isMixedNav` 同时覆盖 `mixed-nav` 与 `header-sidebar-nav` 两种模式，
 * 二者在渲染结构上一致，仅样式细节不同，故合并为同一标记。
 *
 * 全部返回值均为 computed，随 props 变化自动更新；本 hook 无副作用。
 *
 * @param props - 布局组件的 props，需包含 `layout` 与 `isMobile`
 * @returns `currentLayout` 为生效中的布局模式，其余为各模式的布尔判定
 */
export function useLayout(props: YDSZLayoutProps) {
  const currentLayout = computed(() =>
    props.isMobile ? 'sidebar-nav' : (props.layout as LayoutType),
  );

  /**
   * 是否全屏显示content，不需要侧边、底部、顶部、tab区域
   */
  const isFullContent = computed(() => currentLayout.value === 'full-content');

  /**
   * 是否侧边混合模式
   */
  const isSidebarMixedNav = computed(
    () => currentLayout.value === 'sidebar-mixed-nav',
  );

  /**
   * 是否为头部导航模式
   */
  const isHeaderNav = computed(() => currentLayout.value === 'header-nav');

  /**
   * 是否为混合导航模式
   */
  const isMixedNav = computed(
    () =>
      currentLayout.value === 'mixed-nav' ||
      currentLayout.value === 'header-sidebar-nav',
  );

  /**
   * 是否为头部混合模式
   */
  const isHeaderMixedNav = computed(
    () => currentLayout.value === 'header-mixed-nav',
  );

  return {
    currentLayout,
    isFullContent,
    isHeaderMixedNav,
    isHeaderNav,
    isMixedNav,
    isSidebarMixedNav,
  };
}
