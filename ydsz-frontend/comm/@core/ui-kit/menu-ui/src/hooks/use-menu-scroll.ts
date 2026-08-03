/**
 * use-menu-scroll 组合式函数
 *
 * @path comm\@core\ui-kit\menu-ui\src\hooks\use-menu-scroll.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { Ref } from 'vue';

import { watch } from 'vue';

import { useDebounceFn } from '@vueuse/core';

interface UseMenuScrollOptions {
  delay?: number;
  enable?: boolean | Ref<boolean>;
}

/**
 * 在菜单选中项变化时，自动把该项滚动到侧边栏可视区域中央。
 *
 * @remarks
 * 解决长菜单场景下的定位问题：刷新页面或跨模块跳转后，当前路由对应的菜单项
 * 可能位于滚动区域之外，用户看不到自己身处何处。
 *
 * 实现上有几个需要留意的点：
 * - **依赖 DOM 结构与类名**：通过选择器 `aside li[role=menuitem].is-active` 全局查找，
 *   属于跨组件的隐式耦合。若菜单容器不是 `aside`、或选中态类名变更，滚动会静默失效；
 *   页面存在多个 `aside` 时只会命中第一个匹配元素；
 * - **默认 320ms 防抖**：该延迟是为了等待菜单展开/折叠动画结束——过早滚动时元素位置
 *   仍在变化，会滚到错误位置。若项目调整了动画时长，需同步调整 `delay`；
 * - **watch 未设 `immediate`**：首次挂载不会自动滚动，需要首屏定位请在
 *   `onMounted` 中手动调用返回的 `scrollToActiveItem`（且需自行确保 DOM 已渲染完成）；
 * - `scrollIntoView` 使用平滑滚动，会影响祖先滚动容器，页面存在嵌套滚动时可能出现连带位移。
 *
 * @param activePath - 当前选中菜单项的路径，值变化即触发滚动
 * @param options - `enable` 控制是否启用，支持传 ref 以动态开关（如侧边栏折叠时关闭），
 *                  默认 `true`；`delay` 为防抖毫秒数，默认 `320`
 * @returns `scrollToActiveItem` 立即执行一次滚动（不经防抖），用于手动触发
 */
export function useMenuScroll(
  activePath: Ref<string | undefined>,
  options: UseMenuScrollOptions = {},
) {
  const { enable = true, delay = 320 } = options;

  function scrollToActiveItem() {
    const isEnabled = typeof enable === 'boolean' ? enable : enable.value;
    if (!isEnabled) return;

    const activeElement = document.querySelector(
      `aside li[role=menuitem].is-active`,
    );
    if (activeElement) {
      activeElement.scrollIntoView({
        behavior: 'smooth',
        block: 'center',
        inline: 'center',
      });
      // 无障碍优化：滚动后聚焦到活动菜单项
      (activeElement as HTMLElement).focus();
    }
  }

  const debouncedScroll = useDebounceFn(scrollToActiveItem, delay);

  watch(activePath, () => {
    const isEnabled = typeof enable === 'boolean' ? enable : enable.value;
    if (!isEnabled) return;

    debouncedScroll();
  });

  return {
    scrollToActiveItem,
  };
}
