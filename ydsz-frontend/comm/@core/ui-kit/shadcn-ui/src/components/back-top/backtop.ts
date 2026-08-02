/**
 * backtop 模块
 *
 * @path comm\@core\ui-kit\shadcn-ui\src\components\back-top\backtop.ts
 * @author ydsz-team
 * @since 1.0.0
 */
export const backtopProps = {
  /**
   * @zh_CN bottom distance.
   */
  bottom: {
    default: 40,
    type: Number,
  },
  /**
   * @zh_CN right distance.
   */
  right: {
    default: 40,
    type: Number,
  },
  /**
   * @zh_CN the target to trigger scroll.
   */
  target: {
    default: '',
    type: String,
  },
  /**
   * @zh_CN the button will not show until the scroll height reaches this value.
   */
  visibilityHeight: {
    default: 200,
    type: Number,
  },
} as const;

/**
 * 回到顶部按钮的 props 类型定义。
 *
 * @remarks
 * 与运行时的 `backtopProps` 是两份独立声明：后者供 Vue 做运行时校验与默认值，
 * 前者供 TS 与 hook 使用。二者需**手动保持同步**——注意本接口多出 `isGroup` 一项，
 * 该项不在运行时声明中，只作为组合使用时的类型标记。
 */
export interface BacktopProps {
  /** 距视口底部的距离（px），默认 40 */
  bottom?: number;
  /** 是否处于悬浮按钮组中；为真时由外层容器统一定位，自身的 bottom/right 不再单独生效 */
  isGroup?: boolean;
  /** 距视口右侧的距离（px），默认 40 */
  right?: number;
  /**
   * 滚动容器的 CSS 选择器；省略时监听整个文档。
   *
   * @remarks
   * 页面内容位于自定义滚动容器（而非 body）时必须指定，否则按钮永远不出现。
   * 注意选择器必须能查询到元素，否则组件挂载时会抛错。
   */
  target?: string;
  /** 滚动距离超过该值（px）后按钮才显示，默认 200，避免短页面出现无意义的按钮 */
  visibilityHeight?: number;
}
