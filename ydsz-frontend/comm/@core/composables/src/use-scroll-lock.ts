/**
 * use-scroll-lock 组合式函数
 *
 * @path comm\@core\composables\src\use-scroll-lock.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import { getScrollbarWidth, needsScrollbar } from '@ydsz-core/shared/utils';

import {
  useScrollLock as _useScrollLock,
  tryOnBeforeUnmount,
  tryOnMounted,
} from '@vueuse/core';

/** 滚动锁定时需同步宽度补偿的 fixed 定位元素标记类名，脱离文档流元素不吃 body 的 padding */
export const SCROLL_FIXED_CLASS = `_scroll__fixed_`;

/**
 * 在组件挂载期间锁定页面滚动，并补偿滚动条消失导致的横向抖动。
 *
 * @remarks
 * 典型用于 Modal / Drawer：打开时禁止背景滚动。难点不在锁滚动本身，
 * 而在于隐藏滚动条会让可用宽度突然增加约 15px，导致整页元素向右跳动。
 * 因此本函数额外做了两件事：
 * 1. 给 `document.body` 加上等于滚动条宽度的 `padding-right`；
 * 2. 对所有带 {@link SCROLL_FIXED_CLASS} 的 fixed 定位元素（顶栏、悬浮按钮等）
 *    同步补偿——它们脱离文档流，不吃 body 的 padding，必须单独处理。
 *
 * 补偿期间会临时把这些节点的 `transition` 置为 `none` 并把原值存入 `dataset.transition`，
 * 否则补偿量会以动画形式缓慢移动，视觉上仍是抖动；卸载时在 `requestAnimationFrame`
 * 中还原 transition，确保「清除 padding」这一帧不被动画捕捉。
 *
 * 重要约束：
 * - **生命周期绑定**：锁定发生在 mounted、解锁发生在 beforeUnmount，
 *   调用方只能通过销毁组件来解锁，无法手动控制；
 * - **无引用计数**：多个组件同时调用时，先卸载的那个会提前清除 padding，
 *   造成剩余弹窗抖动，应避免嵌套使用；
 * - 若挂载时页面本就没有滚动条，则整个补偿逻辑跳过，此时**连滚动锁定也不会生效**；
 * - 依赖 `document`，不可在 SSR 环境调用。
 */
export function useScrollLock() {
  const isLocked = _useScrollLock(document.body);
  const scrollbarWidth = getScrollbarWidth();

  tryOnMounted(() => {
    if (!needsScrollbar()) {
      return;
    }
    document.body.style.paddingRight = `${scrollbarWidth}px`;

    const layoutFixedNodes = document.querySelectorAll<HTMLElement>(
      `.${SCROLL_FIXED_CLASS}`,
    );
    const nodes = [...layoutFixedNodes];
    if (nodes.length > 0) {
      nodes.forEach((node) => {
        node.dataset.transition = node.style.transition;
        node.style.transition = 'none';
        node.style.paddingRight = `${scrollbarWidth}px`;
      });
    }
    isLocked.value = true;
  });

  tryOnBeforeUnmount(() => {
    if (!needsScrollbar()) {
      return;
    }
    isLocked.value = false;
    const layoutFixedNodes = document.querySelectorAll<HTMLElement>(
      `.${SCROLL_FIXED_CLASS}`,
    );
    const nodes = [...layoutFixedNodes];
    if (nodes.length > 0) {
      nodes.forEach((node) => {
        node.style.paddingRight = '';
        requestAnimationFrame(() => {
          node.style.transition = node.dataset.transition || '';
        });
      });
    }
    document.body.style.paddingRight = '';
  });
}
