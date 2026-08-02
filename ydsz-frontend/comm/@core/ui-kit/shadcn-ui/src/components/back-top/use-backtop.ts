/**
 * use-backtop 模块
 *
 * @path comm\@core\ui-kit\shadcn-ui\src\components\back-top\use-backtop.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { BacktopProps } from './backtop';

import { onMounted, ref, shallowRef } from 'vue';

import { useEventListener, useThrottleFn } from '@vueuse/core';

/**
 * 驱动「回到顶部」按钮的显隐与滚动行为。
 *
 * @remarks
 * 滚动监听采用 300ms 节流（首次立即执行），在滚动流畅度与显隐及时性之间取平衡；
 * 挂载后还会**主动执行一次**判断，避免刷新页面时停留在中段却看不到按钮。
 *
 * 滚动目标的解析在 `onMounted` 中完成：未指定 `target` 时监听 `document`、
 * 滚动 `documentElement`；指定后则改为监听该元素自身。
 *
 * 需要注意的行为：
 * - **`target` 查询不到元素时会直接抛出 Error**，而非降级为监听全局。
 *   由于抛错发生在 `onMounted` 内，会中断当前组件的挂载流程，
 *   因此选择器必须确保在此时已存在于 DOM 中（异步渲染的容器需谨慎）；
 * - `target` 仅在挂载时读取一次，**运行中修改不会重新绑定**；
 * - 事件监听由 VueUse 管理，组件卸载时自动移除；
 * - 回到顶部使用平滑滚动，若用户系统开启了「减少动态效果」，浏览器可能忽略动画直接跳转。
 *
 * @param props - 回到顶部按钮的配置，主要使用其中的 `target` 与 `visibilityHeight`
 * @returns `visible` 按钮是否应显示；`handleClick` 触发滚动回顶部
 */
export const useBackTop = (props: BacktopProps) => {
  const el = shallowRef<HTMLElement>();
  const container = shallowRef<Document | HTMLElement>();
  const visible = ref(false);

  const handleScroll = () => {
    if (el.value) {
      visible.value = el.value.scrollTop >= (props?.visibilityHeight ?? 0);
    }
  };

  const handleClick = () => {
    el.value?.scrollTo({ behavior: 'smooth', top: 0 });
  };

  const handleScrollThrottled = useThrottleFn(handleScroll, 300, true);

  useEventListener(container, 'scroll', handleScrollThrottled);
  onMounted(() => {
    container.value = document;
    el.value = document.documentElement;

    if (props.target) {
      el.value = document.querySelector<HTMLElement>(props.target) ?? undefined;

      if (!el.value) {
        throw new Error(`target does not exist: ${props.target}`);
      }
      container.value = el.value;
    }
    // Give visible an initial value, fix #13066
    handleScroll();
  });

  return {
    handleClick,
    visible,
  };
};
