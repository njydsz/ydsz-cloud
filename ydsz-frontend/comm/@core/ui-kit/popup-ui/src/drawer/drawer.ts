/**
 * drawer 模块
 *
 * @path comm\@core\ui-kit\popup-ui\src\drawer\drawer.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { Component, Ref } from 'vue';

import type { ClassType, MaybePromise } from '@ydsz-core/typings';

import type { DrawerApi } from './drawer-api';

/**
 * 抽屉从哪一侧滑出。
 *
 * @remarks
 * 方位不仅决定动画方向，也决定尺寸语义：左右侧滑出时宽度可调、高度铺满；
 * 上下滑出时则相反。切换方位后原先设置的宽/高可能不再生效，需一并调整。
 */
export type DrawerPlacement = 'bottom' | 'left' | 'right' | 'top';

/**
 * 关闭按钮位于头部的哪一侧。
 *
 * @remarks
 * 默认在右侧，符合桌面端习惯；置于左侧多用于模拟移动端「返回」的交互位置。
 */
export type CloseIconPlacement = 'left' | 'right';

/**
 * 抽屉组件的展示层配置。
 *
 * @remarks
 * 这里只描述「长什么样、能不能关」，不包含打开状态与业务数据——
 * 后者属于 {@link DrawerState}。这样拆分是为了让纯展示配置可以被静态复用。
 */
export interface DrawerProps {
  /**
   * 是否挂载到内容区域
   * @default false
   */
  appendToMain?: boolean;
  /**
   * 取消按钮文字
   */
  cancelText?: string;
  class?: ClassType;
  /**
   * 是否显示关闭按钮
   * @default true
   */
  closable?: boolean;
  /**
   * 关闭按钮的位置
   */
  closeIconPlacement?: CloseIconPlacement;
  /**
   * 点击弹窗遮罩是否关闭弹窗
   * @default true
   */
  closeOnClickModal?: boolean;
  /**
   * 按下 ESC 键是否关闭弹窗
   * @default true
   */
  closeOnPressEscape?: boolean;
  /**
   * 确定按钮 loading
   * @default false
   */
  confirmLoading?: boolean;
  /**
   * 确定按钮文字
   */
  confirmText?: string;
  contentClass?: string;
  /**
   * 弹窗描述
   */
  description?: string;
  /**
   * 在关闭时销毁抽屉
   */
  destroyOnClose?: boolean;
  /**
   * 是否显示底部
   * @default true
   */
  footer?: boolean;
  /**
   * 弹窗底部样式
   */
  footerClass?: ClassType;
  /**
   * 是否显示顶栏
   * @default true
   */
  header?: boolean;
  /**
   * 弹窗头部样式
   */
  headerClass?: ClassType;
  /**
   * 弹窗是否显示
   * @default false
   */
  loading?: boolean;
  /**
   * 是否显示遮罩
   * @default true
   */
  modal?: boolean;

  /**
   * 是否自动聚焦
   */
  openAutoFocus?: boolean;
  /**
   * 弹窗遮罩模糊效果
   */
  overlayBlur?: number;
  /**
   * 抽屉位置
   * @default right
   */
  placement?: DrawerPlacement;

  /**
   * 是否显示取消按钮
   * @default true
   */
  showCancelButton?: boolean;
  /**
   * 是否显示确认按钮
   * @default true
   */
  showConfirmButton?: boolean;
  /**
   * 提交中（锁定抽屉状态）
   */
  submitting?: boolean;
  /**
   * 弹窗标题
   */
  title?: string;
  /**
   * 弹窗标题提示
   */
  titleTooltip?: string;
  /**
   * 抽屉层级
   */
  zIndex?: number;
}

/**
 * 抽屉的运行时状态，即 DrawerApi 内部 store 所存储的完整数据。
 *
 * @remarks
 * 在展示配置 {@link DrawerProps} 之外补充了「开关状态」与「跨组件传值通道」，
 * 这两项都会随交互变化，因此归入 state 而非 props。
 */
export interface DrawerState extends DrawerProps {
  /**
   * 弹窗打开状态
   *
   * @remarks
   * 由 API 统一维护，业务不应直接改写 store 中的该值，
   * 应调用 `open()` / `close()`，否则会跳过 `onBeforeClose` 拦截与动画回调。
   */
  isOpen?: boolean;
  /**
   * 共享数据
   *
   * @remarks
   * 打开方与抽屉内容组件之间的传值通道，典型用途是把「当前编辑行」传进抽屉。
   * 注意其生命周期与抽屉实例一致，**关闭后不会自动清空**：
   * 若下次打开时未重新赋值，会读到上一次的残留数据，
   * 编辑/新增复用同一抽屉时尤其容易出现串数据。
   */
  sharedData?: Record<string, any>;
}

/**
 * 附加了响应式订阅能力的抽屉 API，是业务实际持有的句柄类型。
 *
 * @remarks
 * `DrawerApi` 本身为普通类实例，`useStore` 用于把内部状态桥接成 Vue 只读 ref，
 * 以便在模板中消费（如根据 `isOpen` 控制内容懒加载）。
 * 建议传入 selector 只订阅所需切片，避免任意状态变更都引发重渲染。
 */
export type ExtendedDrawerApi = DrawerApi & {
  useStore: <T = NoInfer<DrawerState>>(
    selector?: (state: NoInfer<DrawerState>) => T,
  ) => Readonly<Ref<T>>;
};

/**
 * 创建抽屉 API 时的初始化选项。
 *
 * @remarks
 * 在 {@link DrawerState} 基础上追加了生命周期回调与「独立抽屉组件」的连接配置。
 * 所有回调都是可选的，未提供时对应环节不做任何处理。
 */
export interface DrawerApiOptions extends DrawerState {
  /**
   * 独立的抽屉组件
   */
  connectedComponent?: Component;
  /**
   * 关闭前的回调，返回 false 可以阻止关闭
   * @returns
   */
  onBeforeClose?: () => MaybePromise<boolean | undefined>;
  /**
   * 点击取消按钮的回调
   */
  onCancel?: () => void;
  /**
   * 弹窗关闭动画结束的回调
   * @returns
   */
  onClosed?: () => void;
  /**
   * 点击确定按钮的回调
   */
  onConfirm?: () => void;
  /**
   * 弹窗状态变化回调
   * @param isOpen
   * @returns
   */
  onOpenChange?: (isOpen: boolean) => void;
  /**
   * 弹窗打开动画结束的回调
   * @returns
   */
  onOpened?: () => void;
}
