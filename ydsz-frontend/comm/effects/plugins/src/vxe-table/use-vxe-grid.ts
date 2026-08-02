/**
 * use-vxe-grid 模块
 *
 * @path comm\effects\plugins\src\vxe-table\use-vxe-grid.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { VxeGridSlots, VxeGridSlotTypes } from 'vxe-table';

import type { SlotsType } from 'vue';

import type { BaseFormComponentType } from '@ydsz-core/form-ui';

import type { ExtendedVxeGridApi, VxeGridProps } from './types';

import { defineComponent, h, onBeforeUnmount } from 'vue';

import { useStore } from '@ydsz-core/shared/store';

import { VxeGridApi } from './api';
import VxeGrid from './use-vxe-grid.vue';

type FilteredSlots<T> = {
  [K in keyof VxeGridSlots<T> as K extends 'form'
    ? never
    : K]: VxeGridSlots<T>[K];
};

/**
 * 创建一对「表格组件 + 操作句柄」，以命令式方式驱动 vxe-grid。
 *
 * @remarks
 * 入参：`options` 为初始配置，会与默认配置合并后写入内部 store。
 * 注意它是**一次性快照**：直接修改传入的对象不会触发表格更新（源码中的响应式方案已注释掉），
 * 运行时变更请统一走 `api.setState` / `api.setGridOptions`。
 *
 * 返回值：`[Grid, api]` 元组（`as const`，可安全解构）。`Grid` 直接用于模板渲染，
 * `api` 用于查询、改配置、控制 loading，并额外提供 `useStore` 订阅状态。
 *
 * 副作用与生命周期：
 * - **应在组件 `setup` 中调用**：内部注册了 `onBeforeUnmount`，
 *   在 `Grid` 卸载时自动调用 `api.unmount()` 释放实例绑定，无需手动清理；
 * - 每次调用都会 new 一个独立的 `VxeGridApi`，同一页面多张表格互不干扰；
 * - `Grid` 声明了 `inheritAttrs: false`，透传的 attrs 会被合并进配置而非落到根元素上；
 * - 插槽中 `form` 被刻意剔除（搜索表单由内置实现接管），另外扩展了
 *   `table-title`、`toolbar-actions`、`toolbar-tools` 三个自定义插槽。
 *
 * @param options - 表格与搜索表单的初始配置
 * @returns `[Grid, api]`：可直接渲染的表格组件与其操作句柄
 *
 * @example
 * ```ts
 * const [Grid, gridApi] = useYDSZVxeGrid({
 *   formOptions,
 *   gridOptions: { columns, proxyConfig: { ajax: { query: fetchList } } },
 * });
 * ```
 */
export function useYDSZVxeGrid<
  T extends Record<string, any> = any,
  D extends BaseFormComponentType = BaseFormComponentType,
>(options: VxeGridProps<T, D>) {
  // const IS_REACTIVE = isReactive(options);
  const api = new VxeGridApi(options);
  const extendedApi: ExtendedVxeGridApi<T, D> = api as ExtendedVxeGridApi<T, D>;
  extendedApi.useStore = (selector) => {
    return useStore(api.store, selector);
  };

  const Grid = defineComponent(
    (props: VxeGridProps<T>, { attrs, slots }) => {
      onBeforeUnmount(() => {
        api.unmount();
      });
      api.setState({ ...props, ...attrs });
      return () => h(VxeGrid, { ...props, ...attrs, api: extendedApi }, slots);
    },
    {
      name: 'YDSZVxeGrid',
      inheritAttrs: false,
      slots: Object as SlotsType<
        {
          // 表格标题
          'table-title': undefined;
          // 工具栏左侧部分
          'toolbar-actions': VxeGridSlotTypes.DefaultSlotParams<T>;
          // 工具栏右侧部分
          'toolbar-tools': VxeGridSlotTypes.DefaultSlotParams<T>;
        } & FilteredSlots<T>
      >,
    },
  );
  // Add reactivity support
  // if (IS_REACTIVE) {
  //   watch(
  //     () => options,
  //     () => {
  //       api.setState(options);
  //     },
  //     { immediate: true },
  //   );
  // }

  return [Grid, extendedApi] as const;
}

/**
 * {@link useYDSZVxeGrid} 的函数类型别名。
 *
 * @remarks
 * 供应用层在依赖注入或适配层声明中引用该 Hook 的签名，避免直接依赖实现。
 */
export type UseYDSZVxeGrid = typeof useYDSZVxeGrid;
