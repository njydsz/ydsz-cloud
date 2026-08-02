/**
 * alert 模块
 *
 * @path comm\@core\ui-kit\popup-ui\src\alert\alert.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { Component, VNode, VNodeArrayChildren } from 'vue';

import type { Recordable } from '@ydsz-core/typings';

import { createContext } from '@ydsz-core/shadcn-ui';

/**
 * 弹窗内置图标的语义类型。
 *
 * @remarks
 * 仅表达语义，具体图形与配色由弹窗组件按主题映射，业务无需关心图标资源。
 * `question` 专用于需要用户抉择的确认场景（区别于纯告知的 `info`），
 * 需要自定义图形时可直接给 `icon` 传组件而非此处的枚举值。
 */
export type IconType = 'error' | 'info' | 'question' | 'success' | 'warning';

/**
 * 关闭前回调的入参上下文。
 *
 * @remarks
 * 通过 `isConfirm` 区分本次关闭是「点了确认」还是「取消/遮罩/关闭按钮」，
 * 使同一个 `beforeClose` 能对两条路径做差异处理（例如仅在确认时提交数据）。
 */
export type BeforeCloseScope = {
  /** 是否由确认按钮触发；取消、点击遮罩、按 Esc 等路径均为 `false` */
  isConfirm: boolean;
};

/**
 * 通用提示弹窗的配置项。
 *
 * @remarks
 * 是 `ydszAlert` / `ydszConfirm` 共用的参数结构，二者差异仅在于默认是否显示取消按钮。
 * 除 `content` 外均为可选，未指定项使用组件默认值或全局默认配置。
 */
export type AlertProps = {
  /** 关闭前的回调，如果返回false，则终止关闭 */
  beforeClose?: (
    scope: BeforeCloseScope,
  ) => boolean | Promise<boolean | undefined> | undefined;
  /** 边框 */
  bordered?: boolean;
  /**
   * 按钮对齐方式
   * @default 'end'
   */
  buttonAlign?: 'center' | 'end' | 'start';
  /** 取消按钮的标题 */
  cancelText?: string;
  /** 是否居中显示 */
  centered?: boolean;
  /** 确认按钮的标题 */
  confirmText?: string;
  /** 弹窗容器的额外样式 */
  containerClass?: string;
  /** 弹窗提示内容 */
  content: Component | string;
  /** 弹窗内容的额外样式 */
  contentClass?: string;
  /** 执行beforeClose回调期间，在内容区域显示一个loading遮罩*/
  contentMasking?: boolean;
  /** 弹窗底部内容（与按钮在同一个容器中） */
  footer?: Component | string;
  /** 弹窗的图标（在标题的前面） */
  icon?: Component | IconType;
  /**
   * 弹窗遮罩模糊效果
   */
  overlayBlur?: number;
  /** 是否显示取消按钮 */
  showCancel?: boolean;
  /** 弹窗标题 */
  title?: string;
};

/** Prompt属性 */
export type PromptProps<T = any> = {
  /** 关闭前的回调，如果返回false，则终止关闭 */
  beforeClose?: (scope: {
    isConfirm: boolean;
    value: T | undefined;
  }) => boolean | Promise<boolean | undefined> | undefined;
  /** 用于接受用户输入的组件 */
  component?: Component;
  /** 输入组件的属性 */
  componentProps?: Recordable<any>;
  /** 输入组件的插槽 */
  componentSlots?:
    | (() => any)
    | Recordable<unknown>
    | VNode
    | VNodeArrayChildren;
  /** 默认值 */
  defaultValue?: T;
  /** 输入组件的值属性名 */
  modelPropName?: string;
} & Omit<AlertProps, 'beforeClose'>;

/**
 * Alert上下文
 */
export type AlertContext = {
  /** 执行取消操作 */
  doCancel: () => void;
  /** 执行确认操作 */
  doConfirm: () => void;
};

export const [injectAlertContext, provideAlertContext] =
  createContext<AlertContext>('YDSZAlertContext');

/**
 * 获取Alert上下文
 * @returns AlertContext
 */
export function useAlertContext() {
  const context = injectAlertContext();
  if (!context) {
    throw new Error('useAlertContext must be used within an AlertProvider');
  }
  return context;
}
