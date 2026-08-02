/**
 * use-layout-style 组合式函数
 *
 * @path comm\@core\composables\src\use-layout-style.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { CSSProperties } from 'vue';

import type { VisibleDomRect } from '@ydsz-core/shared/utils';

import { computed, onMounted, onUnmounted, ref } from 'vue';

import {
  CSS_VARIABLE_LAYOUT_CONTENT_HEIGHT,
  CSS_VARIABLE_LAYOUT_CONTENT_WIDTH,
  CSS_VARIABLE_LAYOUT_FOOTER_HEIGHT,
  CSS_VARIABLE_LAYOUT_HEADER_HEIGHT,
} from '@ydsz-core/shared/constants';
import { getElementVisibleRect } from '@ydsz-core/shared/utils';

import { useCssVar, useDebounceFn } from '@vueuse/core';

/**
 * @zh_CN content style
 */
export function useLayoutContentStyle() {
  let resizeObserver: null | ResizeObserver = null;
  const contentElement = ref<HTMLDivElement | null>(null);
  const visibleDomRect = ref<null | VisibleDomRect>(null);
  const contentHeight = useCssVar(CSS_VARIABLE_LAYOUT_CONTENT_HEIGHT);
  const contentWidth = useCssVar(CSS_VARIABLE_LAYOUT_CONTENT_WIDTH);

  const overlayStyle = computed((): CSSProperties => {
    const { height, left, top, width } = visibleDomRect.value ?? {};
    return {
      height: `${height}px`,
      left: `${left}px`,
      position: 'fixed',
      top: `${top}px`,
      width: `${width}px`,
      zIndex: 150,
    };
  });

  const debouncedCalcHeight = useDebounceFn(
    (_entries: ResizeObserverEntry[]) => {
      visibleDomRect.value = getElementVisibleRect(contentElement.value);
      contentHeight.value = `${visibleDomRect.value.height}px`;
      contentWidth.value = `${visibleDomRect.value.width}px`;
    },
    16,
  );

  onMounted(() => {
    if (contentElement.value && !resizeObserver) {
      resizeObserver = new ResizeObserver(debouncedCalcHeight);
      resizeObserver.observe(contentElement.value);
    }
  });

  onUnmounted(() => {
    resizeObserver?.disconnect();
    resizeObserver = null;
  });

  return { contentElement, overlayStyle, visibleDomRect };
}

/**
 * 读写布局顶栏高度的 CSS 变量，实现 JS 与样式层共享同一份高度数据。
 *
 * @remarks
 * 顶栏高度既要参与 CSS 计算（如内容区 `calc(100vh - var(--header-height))`），
 * 又要被 JS 用于滚动定位偏移。此处以 CSS 变量为**唯一数据源**，
 * 避免在 JS 常量和 SCSS 变量里各维护一份而出现不同步。
 *
 * 注意读写并不对称：写入的是带 `px` 单位的字符串，读取时用 `Number.parseInt` 剥离单位，
 * 因此**只支持 px**；若外部把该变量设置成 `rem`/`%`，getter 会返回错误的数值
 * （如 `'4rem'` 得到 `4`）而不会报错。变量尚未初始化时读取会得到 `NaN`，
 * 调用方需自行兜底。
 *
 * 副作用：setter 直接修改 `:root` 上的 CSS 变量，影响全局所有引用该变量的元素。
 *
 * @returns `getLayoutHeaderHeight` 返回当前高度像素数；`setLayoutHeaderHeight` 写入新的高度
 */
export function useLayoutHeaderStyle() {
  const headerHeight = useCssVar(CSS_VARIABLE_LAYOUT_HEADER_HEIGHT);

  return {
    getLayoutHeaderHeight: () => {
      return Number.parseInt(`${headerHeight.value}`, 10);
    },
    setLayoutHeaderHeight: (height: number) => {
      headerHeight.value = `${height}px`;
    },
  };
}

/**
 * 读写布局底栏高度的 CSS 变量，与 {@link useLayoutHeaderStyle} 对称。
 *
 * @remarks
 * 约束与顶栏版本完全一致：仅支持 px 单位、未初始化时读取为 `NaN`、setter 为全局副作用。
 * 底栏在多数页面是隐藏的，此时变量通常为 `0px`；若忘记在隐藏时归零，
 * 内容区会凭空多出一段空白，排查布局问题时可优先确认该变量值。
 *
 * @returns `getLayoutFooterHeight` 返回当前高度像素数；`setLayoutFooterHeight` 写入新的高度
 */
export function useLayoutFooterStyle() {
  const footerHeight = useCssVar(CSS_VARIABLE_LAYOUT_FOOTER_HEIGHT);

  return {
    getLayoutFooterHeight: () => {
      return Number.parseInt(`${footerHeight.value}`, 10);
    },
    setLayoutFooterHeight: (height: number) => {
      footerHeight.value = `${height}px`;
    },
  };
}
