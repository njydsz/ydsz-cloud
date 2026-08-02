/**
 * use-tabs-drag 模块
 *
 * @path comm\@core\ui-kit\tabs-ui\src\use-tabs-drag.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { Sortable } from '@ydsz-core/composables';
import type { EmitType } from '@ydsz-core/typings';

import type { TabsProps } from './types';

import { nextTick, onMounted, onUnmounted, ref, watch } from 'vue';

import { useIsMobile, useSortable } from '@ydsz-core/composables';

// 可能会找到拖拽的子元素，这里需要确保拖拽的dom时tab元素
function findParentElement(element: HTMLElement) {
  const parentCls = 'group';
  return element.classList.contains(parentCls)
    ? element
    : element.closest(`.${parentCls}`);
}

/**
 * 为标签页视图接入基于 SortableJS 的拖拽排序能力。
 *
 * @param props - 标签页组件 props，主要消费 `contentClass`、`draggable`、
 * `styleType`；其中 `contentClass` 同时承担“定位可排序 DOM 容器”的选择器角色。
 * @param emit - 组件 `emit` 函数，排序结束后抛出 `sortTabs(oldIndex, newIndex)`。
 *
 * @remarks
 * 仅负责“拖拽交互 → 抛出排序事件”，不维护 tab 的顺序状态，真正的数组重排由
 * 父组件监听 `sortTabs` 完成，避免组件与数据源双向耦合。
 *
 * 几个隐式约定（改动模板或 props 默认值易踩坑）：
 * - 移动端（`useIsMobile`）直接跳过初始化，因为触屏拖拽体验由原生滚动替代；
 * - 通过 `.group` / `.draggable` / `.affix-tab` 等 className 定位与判定节点，
 *   这些类名散落在模板与 `findParentElement` 中，重命名需同步；
 * - `onMove` 禁止在“固定页 ↔ 普通页”之间互拖，保证 affix 区顺序稳定；
 * - `styleType` 变化会销毁并重建 Sortable 实例，以适配不同风格的容器结构；
 * - 会直接改写容器的 `cursor` 样式并在拖拽中给条目加 `.dragging`，属副作用，
 *   卸载时通过 `onUnmounted` 销毁实例，但若初始化前未找到容器仅 `warn` 后跳过。
 */
export function useTabsDrag(props: TabsProps, emit: EmitType) {
  const sortableInstance = ref<null | Sortable>(null);

  async function initTabsSortable() {
    await nextTick();

    const el = document.querySelectorAll(
      `.${props.contentClass}`,
    )?.[0] as HTMLElement;

    if (!el) {
      console.warn('Element not found for sortable initialization');
      return;
    }

    const resetElState = async () => {
      el.style.cursor = 'default';
      // el.classList.remove('dragging');
      el.querySelector('.draggable')?.classList.remove('dragging');
    };

    const { initializeSortable } = useSortable(el, {
      filter: (_evt, target: HTMLElement) => {
        const parent = findParentElement(target);
        const draggable = parent?.classList.contains('draggable');
        return !draggable || !props.draggable;
      },
      onEnd(evt) {
        const { newIndex, oldIndex } = evt;
        // const fromElement = evt.item;
        const { srcElement } = (evt as any).originalEvent;

        if (!srcElement) {
          resetElState();
          return;
        }

        const srcParent = findParentElement(srcElement);

        if (!srcParent) {
          resetElState();
          return;
        }

        if (!srcParent.classList.contains('draggable')) {
          resetElState();

          return;
        }

        if (
          oldIndex !== undefined &&
          newIndex !== undefined &&
          !Number.isNaN(oldIndex) &&
          !Number.isNaN(newIndex) &&
          oldIndex !== newIndex
        ) {
          emit('sortTabs', oldIndex, newIndex);
        }
        resetElState();
      },
      onMove(evt) {
        const parent = findParentElement(evt.related);
        if (parent?.classList.contains('draggable') && props.draggable) {
          const isCurrentAffix = evt.dragged.classList.contains('affix-tab');
          const isRelatedAffix = evt.related.classList.contains('affix-tab');
          // 不允许在固定的tab和非固定的tab之间互相拖拽
          return isCurrentAffix === isRelatedAffix;
        } else {
          return false;
        }
      },
      onStart: () => {
        el.style.cursor = 'grabbing';
        el.querySelector('.draggable')?.classList.add('dragging');
        // el.classList.add('dragging');
      },
    });

    sortableInstance.value = await initializeSortable();
  }

  async function init() {
    const { isMobile } = useIsMobile();

    // 移动端下tab不需要拖拽
    if (isMobile.value) {
      return;
    }
    await nextTick();
    initTabsSortable();
  }

  onMounted(init);

  watch(
    () => props.styleType,
    () => {
      sortableInstance.value?.destroy();
      init();
    },
  );

  onUnmounted(() => {
    sortableInstance.value?.destroy();
  });
}
