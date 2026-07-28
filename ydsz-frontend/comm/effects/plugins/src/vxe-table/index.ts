/**
 * index 模块
 *
 * @path comm\effects\plugins\src\vxe-table\index.ts
 * @author ydsz-team
 * @since 1.0.0
 */
export { setupYDSZVxeTable } from './init';
export type { VxeTableGridOptions } from './types';
export * from './use-vxe-grid';

export { default as YDSZVxeGrid } from './use-vxe-grid.vue';
export type {
  VxeGridListeners,
  VxeGridProps,
  VxeGridPropTypes,
} from 'vxe-table';
