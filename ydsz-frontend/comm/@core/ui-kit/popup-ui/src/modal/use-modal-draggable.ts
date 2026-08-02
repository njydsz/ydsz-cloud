/**
 * @copy https://github.com/element-plus/element-plus/blob/dev/comm/hooks/use-draggable/index.ts
 * 调整部分细节
 */

import type { ComputedRef, Ref } from 'vue';

import { onBeforeUnmount, onMounted, reactive, ref, watchEffect } from 'vue';

import { unrefElement } from '@vueuse/core';

/**
 * 让弹窗可通过拖拽标题栏移动位置，并把移动范围限制在容器（默认视口）之内。
 *
 * @remarks
 * 位移通过 CSS `transform: translate()` 实现而非修改 `left/top`，
 * 因为前者不触发重排、由合成器处理，拖拽过程更流畅，也不会破坏弹窗原有的居中定位方式。
 *
 * 边界计算在 **mousedown 时一次性完成**：按下瞬间记录目标元素与容器的矩形，
 * 推导出位移的上下限，拖拽过程中只做钳制。这样避免了每次 mousemove 都读取布局信息，
 * 但也意味着**拖拽期间若窗口尺寸或弹窗大小发生变化，边界不会更新**，
 * 可能出现拖出可视区域的情况。
 *
 * 事件监听策略：`mousedown` 绑在拖拽把手上，而 `mousemove` / `mouseup` 绑在 `document` 上，
 * 这样鼠标移出弹窗范围甚至移动过快时仍能持续跟随，松开即自动解绑，不会长期驻留监听器。
 *
 * 其他注意点：
 * - 仅支持鼠标事件，**未处理 touch**，移动端无法拖拽；
 * - `draggable` 的开关通过 `watchEffect` 动态响应，关闭时会移除监听但**不会复位已产生的位移**，
 *   需要复位请显式调用返回的 `resetPosition`（通常在弹窗关闭时调用，
 *   否则下次打开仍停留在上次拖动的位置）；
 * - 组件卸载时自动解绑把手上的监听，无内存泄漏；
 * - 该实现改写自 Element Plus 的 `use-draggable`。
 *
 * @param targetRef - 被移动的弹窗根元素
 * @param dragRef - 触发拖拽的把手元素，通常是标题栏
 * @param draggable - 是否启用拖拽，变化时自动绑定/解绑监听
 * @param containerSelector - 限位容器的 CSS 选择器；省略或查询不到元素时退化为以视口为界
 * @returns `dragging` 是否正在拖拽（可用于拖拽时禁用文本选中等）；
 *          `resetPosition` 复位到初始位置；`transform` 当前的 x/y 偏移量
 */
export function useModalDraggable(
  targetRef: Ref<HTMLElement | undefined>,
  dragRef: Ref<HTMLElement | undefined>,
  draggable: ComputedRef<boolean>,
  containerSelector?: ComputedRef<string | undefined>,
) {
  const transform = reactive({
    offsetX: 0,
    offsetY: 0,
  });

  const dragging = ref(false);

  const onMousedown = (e: MouseEvent) => {
    const downX = e.clientX;
    const downY = e.clientY;

    if (!targetRef.value) {
      return;
    }

    const targetRect = targetRef.value.getBoundingClientRect();
    const { offsetX, offsetY } = transform;
    const targetLeft = targetRect.left;
    const targetTop = targetRect.top;
    const targetWidth = targetRect.width;
    const targetHeight = targetRect.height;

    let containerRect: DOMRect | null = null;

    if (containerSelector?.value) {
      const container = document.querySelector(containerSelector.value);
      if (container) {
        containerRect = container.getBoundingClientRect();
      }
    }

    let maxLeft, maxTop, minLeft, minTop;
    if (containerRect) {
      minLeft = containerRect.left - targetLeft + offsetX;
      maxLeft = containerRect.right - targetLeft - targetWidth + offsetX;
      minTop = containerRect.top - targetTop + offsetY;
      maxTop = containerRect.bottom - targetTop - targetHeight + offsetY;
    } else {
      const docElement = document.documentElement;
      const clientWidth = docElement.clientWidth;
      const clientHeight = docElement.clientHeight;
      minLeft = -targetLeft + offsetX;
      minTop = -targetTop + offsetY;
      maxLeft = clientWidth - targetLeft - targetWidth + offsetX;
      maxTop = clientHeight - targetTop - targetHeight + offsetY;
    }

    const onMousemove = (e: MouseEvent) => {
      let moveX = offsetX + e.clientX - downX;
      let moveY = offsetY + e.clientY - downY;

      moveX = Math.min(Math.max(moveX, minLeft), maxLeft);
      moveY = Math.min(Math.max(moveY, minTop), maxTop);

      transform.offsetX = moveX;
      transform.offsetY = moveY;

      if (targetRef.value) {
        targetRef.value.style.transform = `translate(${moveX}px, ${moveY}px)`;
        dragging.value = true;
      }
    };

    const onMouseup = () => {
      dragging.value = false;
      document.removeEventListener('mousemove', onMousemove);
      document.removeEventListener('mouseup', onMouseup);
    };

    document.addEventListener('mousemove', onMousemove);
    document.addEventListener('mouseup', onMouseup);
  };

  const onDraggable = () => {
    const dragDom = unrefElement(dragRef);
    if (dragDom && targetRef.value) {
      dragDom.addEventListener('mousedown', onMousedown);
    }
  };

  const offDraggable = () => {
    const dragDom = unrefElement(dragRef);
    if (dragDom && targetRef.value) {
      dragDom.removeEventListener('mousedown', onMousedown);
    }
  };

  const resetPosition = () => {
    transform.offsetX = 0;
    transform.offsetY = 0;

    const target = unrefElement(targetRef);
    if (target) {
      target.style.transform = 'none';
    }
  };

  onMounted(() => {
    watchEffect(() => {
      if (draggable.value) {
        onDraggable();
      } else {
        offDraggable();
      }
    });
  });

  onBeforeUnmount(() => {
    offDraggable();
  });

  return {
    dragging,
    resetPosition,
    transform,
  };
}
