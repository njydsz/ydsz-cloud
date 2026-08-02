/**
 * button 模块
 *
 * @path comm\@core\ui-kit\shadcn-ui\src\components\button\button.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { AsTag } from 'radix-vue';

import type { Component } from 'vue';

import type { ButtonVariants, ButtonVariantSize } from '../../ui';

/**
 * 基础按钮的 props。
 *
 * @remarks
 * 在 shadcn 按钮样式变体之上补充了 `loading` 与多态渲染能力。
 * 注意 `loading` 与 `disabled` 是两个独立开关：置为 loading 并**不会自动禁用点击**，
 * 需要防重复提交时应同时设置二者。
 */
export interface YDSZButtonProps {
  /**
   * The element or component this component should render as. Can be overwrite by `asChild`
   * @defaultValue "div"
   */
  as?: AsTag | Component;
  /**
   * Change the default rendered element for the one passed as a child, merging their props and behavior.
   *
   * Read our [Composition](https://www.radix-vue.com/guides/composition.html) guide for more details.
   */
  asChild?: boolean;
  class?: any;
  disabled?: boolean;
  loading?: boolean;
  size?: ButtonVariantSize;
  variant?: ButtonVariants;
}

/**
 * 按钮文本的自定义渲染类型。
 *
 * @remarks
 * 传字符串按纯文本渲染；传函数则每次重渲染都会调用，可返回组件以实现图文混排，
 * 函数内应保持轻量且无副作用。
 */
export type CustomRenderType = (() => Component | string) | string;

/**
 * 按钮组选项的取值类型。
 *
 * @remarks
 * 限定为原始类型，因为选中态比较使用的是值相等而非引用相等；
 * 若传入对象将无法正确匹配选中项。同一组内应保持类型一致，
 * 混用数字 `1` 与字符串 `'1'` 会被视为不同的值。
 */
export type ValueType = boolean | number | string;

/**
 * 按钮组的 props，用于以按钮形态实现单选/多选。
 *
 * @remarks
 * 相比原生 Radio/Checkbox，按钮组在筛选栏等场景下点击热区更大、视觉更紧凑。
 * 仅从 {@link YDSZButtonProps} 继承 `disabled`（作用于整组），
 * 其余按钮样式通过 `btnClass` 统一定制。
 */
export interface YDSZButtonGroupProps extends Pick<
  YDSZButtonProps,
  'disabled'
> {
  /** 单选模式下允许清除选中 */
  allowClear?: boolean;
  /** 值改变前的回调 */
  beforeChange?: (
    value: ValueType,
    isChecked: boolean,
  ) => boolean | PromiseLike<boolean | undefined> | undefined;
  /** 按钮样式 */
  btnClass?: any;
  /** 按钮间隔距离 */
  gap?: number;
  /** 多选模式下限制最多选择的数量。0表示不限制 */
  maxCount?: number;
  /** 是否允许多选 */
  multiple?: boolean;
  /** 选项 */
  options?: { [key: string]: any; label: CustomRenderType; value: ValueType }[];
  /** 显示图标 */
  showIcon?: boolean;
  /** 尺寸 */
  size?: 'large' | 'middle' | 'small';
}
