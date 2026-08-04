/**
 * use-responsive 组合式函数 — 移动端适配基础
 *
 * @path comm\effects\shared-business\src\composables\use-responsive.ts
 * @author ydsz-team
 * @since 1.1.0
 *
 * @remarks
 * 在 useIsMobile 基础上补充常用移动端布局辅助：
 * - isMobile：是否移动端视口（< 768px）
 * - isTablet：是否平板视口（768-1024px）
 * - isDesktop：是否桌面视口（>= 1024px）
 * - responsiveTableColumns：按设备裁剪表格列（移动端只保留关键列）
 * - responsiveGridCols：移动端栅格列数（1 列）
 */
import { computed } from 'vue';

import { breakpointsTailwind, useBreakpoints } from '@vueuse/core';

/** 表格列裁剪配置 */
export interface ResponsiveColumn<T = any> {
  /** 列属性（对应 vxe-table column field） */
  key: string;
  /** 标题 */
  title: string;
  /** 桌面端是否显示，默认 true */
  desktop?: boolean;
  /** 平板端是否显示，默认 true */
  tablet?: boolean;
  /** 移动端是否显示，默认 false（移动端只保留核心列） */
  mobile?: boolean;
  /** 额外列配置（宽度等） */
  extra?: Record<string, any>;
}

/**
 * 移动端/响应式适配 Hook
 *
 * @example
 * ```ts
 * const { isMobile, responsiveColumns, gridCols } = useResponsive();
 * // vxe-table columns 直接用 responsiveColumns
 * ```
 */
export function useResponsive() {
  const breakpoints = useBreakpoints(breakpointsTailwind);

  const isMobile = breakpoints.smaller('md');
  const isTablet = breakpoints.between('md', 'lg');
  const isDesktop = breakpoints.greaterOrEqual('lg');

  /**
   * 根据设备裁剪表格列
   *
   * @param columns - 完整列配置
   * @returns 当前设备可见的列
   */
  function responsiveColumns<T = any>(
    columns: ResponsiveColumn<T>[],
  ): ResponsiveColumn<T>[] {
    if (isDesktop.value) {
      return columns.filter((col) => col.desktop !== false);
    }
    if (isTablet.value) {
      return columns.filter((col) => col.tablet !== false);
    }
    // 移动端：只保留标记了 mobile 的列
    return columns.filter((col) => col.mobile === true);
  }

  /** 移动端栅格列数（1 列），桌面端保留原列数 */
  const gridCols = computed(() => (isMobile.value ? 1 : undefined));

  /** 移动端是否隐藏某元素（配合 v-if） */
  const showDesktop = computed(() => !isMobile.value);

  return {
    gridCols,
    isDesktop,
    isMobile,
    isTablet,
    responsiveColumns,
    showDesktop,
  };
}
