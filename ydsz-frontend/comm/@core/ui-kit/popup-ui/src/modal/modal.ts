/**
 * modal 模块
 *
 * @path comm\@core\ui-kit\popup-ui\src\modal\modal.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { Component, Ref } from 'vue';

import type { MaybePromise } from '@ydsz-core/typings';

import type { ModalApi } from './modal-api';

/**
 * 模态弹窗的展示层配置。
 *
 * @remarks
 * 只描述外观与交互开关，不含打开状态与业务数据（见 {@link ModalState}）。
 *
 * 与抽屉（Drawer）的配置高度相似，差异在于弹窗额外支持居中、全屏、拖拽等能力；
 * 两者刻意不做类型复用，以免任一侧新增能力时被迫污染另一侧。
 */
export interface ModalProps {
  /**
   * 动画类型
   * @default 'slide'
   */
  animationType?: 'scale' | 'slide';
  /**
   * 是否要挂载到内容区域
   * @default false
   */
  appendToMain?: boolean;
  /**
   * 是否显示边框
   * @default false
   */
  bordered?: boolean;
  /**
   * 取消按钮文字
   */
  cancelText?: string;
  /**
   * 是否居中
   * @default false
   */
  centered?: boolean;

  class?: string;

  /**
   * 是否显示右上角的关闭按钮
   * @default true
   */
  closable?: boolean;
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
   * 禁用确认按钮
   */
  confirmDisabled?: boolean;
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
   * 在关闭时销毁弹窗
   */
  destroyOnClose?: boolean;
  /**
   * 是否可拖拽
   * @default false
   */
  draggable?: boolean;
  /**
   * 是否显示底部
   * @default true
   */
  footer?: boolean;
  footerClass?: string;
  /**
   * 是否全屏
   * @default false
   */
  fullscreen?: boolean;
  /**
   * 是否显示全屏按钮
   * @default true
   */
  fullscreenButton?: boolean;
  /**
   * 是否显示顶栏
   * @default true
   */
  header?: boolean;
  headerClass?: string;
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
   * 提交中（锁定弹窗状态）
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
   * 弹窗层级
   */
  zIndex?: number;
}

/**
 * 弹窗的运行时状态，即 ModalApi 内部 store 所存储的完整数据。
 */
export interface ModalState extends ModalProps {
  /**
   * 弹窗打开状态
   *
   * @remarks
   * 应通过 API 的 `open()` / `close()` 变更，直接改写会绕过 `onBeforeClose` 拦截与动画回调。
   */
  isOpen?: boolean;
  /**
   * 共享数据
   *
   * @remarks
   * 打开方向弹窗内容组件传值的通道。**关闭后不会自动清空**，
   * 新增/编辑复用同一弹窗时必须每次显式设置，否则会读到上一次的残留值。
   */
  sharedData?: Record<string, any>;
}

/**
 * 附加了响应式订阅能力的弹窗 API，是业务实际持有的句柄类型。
 *
 * @remarks
 * `useStore` 把内部状态桥接为 Vue 只读 ref；建议传 selector 只订阅所需切片，
 * 否则任意状态变更都会触发重渲染。
 */
export type ExtendedModalApi = ModalApi & {
  useStore: <T = NoInfer<ModalState>>(
    selector?: (state: NoInfer<ModalState>) => T,
  ) => Readonly<Ref<T>>;
};

/**
 * 创建弹窗 API 时的初始化选项。
 *
 * @remarks
 * 在 {@link ModalState} 基础上追加了生命周期回调与独立弹窗组件的连接配置。
 * 其中 `onBeforeClose` 是唯一可以阻断关闭流程的钩子——返回 `false` 即中止关闭，
 * 返回 `undefined` 或其他值均视为放行，常用于「有未保存内容时二次确认」。
 */
export interface ModalApiOptions extends ModalState {
  /**
   * 独立的弹窗组件
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
