/**
 * use-sortable 组合式函数
 *
 * @path comm\@core\composables\src\use-sortable.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { SortableOptions } from 'sortablejs';
import type Sortable from 'sortablejs';

import { tryOnUnmounted } from '@vueuse/core';

function useSortable<T extends HTMLElement>(
  sortableContainer: T,
  options: SortableOptions = {},
) {
  let sortableInstance: Sortable | null = null;

  const initializeSortable = async () => {
    const Sortable = await import(
      // @ts-expect-error - 这是一个动态导入
      'sortablejs/modular/sortable.complete.esm.js'
    );
    sortableInstance = Sortable?.default?.create?.(sortableContainer, {
      animation: 300,
      delay: 400,
      delayOnTouchOnly: true,
      ...options,
    }) as Sortable;

    tryOnUnmounted(() => {
      sortableInstance?.destroy();
      sortableInstance = null;
    });

    return sortableInstance;
  };

  return {
    initializeSortable,
  };
}

export { useSortable };

export type { Sortable };
