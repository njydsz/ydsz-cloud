/**
 * types 模块
 *
 * @path comm\effects\common-ui\src\components\count-to\types.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { CubicBezierPoints, EasingFunction } from '@vueuse/core';

import type { StyleValue } from 'vue';

import { TransitionPresets as TransitionPresetsData } from '@vueuse/core';

/**
 * VueUse 内置缓动预设的名称联合类型，如 `'linear'`、`'easeOutExpo'`。
 *
 * @remarks
 * 由 `@vueuse/core` 的 `TransitionPresets` 常量对象的键推导而来，
 * 用作 {@link CountToProps.transition} 的字符串形式取值；
 * 运行时可用的完整取值列表见 `TransitionPresetsKeys`。
 */
export type TransitionPresets = keyof typeof TransitionPresetsData;

export const TransitionPresetsKeys = Object.keys(
  TransitionPresetsData,
) as TransitionPresets[];

/**
 * 数字滚动动画组件 `CountTo` 的 Props。
 *
 * @remarks
 * 组件把数值拆成「前缀 / 整数部分 / 小数点 / 小数部分 / 后缀」五段分别渲染，
 * 因此可对各段单独设置 class 与 style。
 *
 * 行为约定：首次挂载时从 `startVal` 滚动到 `endVal`；之后每次 `endVal` 变化都会
 * 从**当前显示值**继续过渡到新值，而不会回到 `startVal`。
 * 动画开始 / 结束分别派发 `started` / `finished` 事件。
 */
export interface CountToProps {
  /**
   * 动画起始值，仅首次挂载时生效
   * @default 0
   */
  startVal?: number;
  /** 动画目标值（必填）；变化时自动从当前值过渡到新值 */
  endVal: number;
  /** 是否禁用动画；为 true 时直接跳变到 endVal，不派发过渡事件 */
  disabled?: boolean;
  /**
   * 动画开始前的延迟（毫秒）
   * @default 0
   */
  delay?: number;
  /**
   * 动画持续时长（毫秒）
   * @default 2000
   */
  duration?: number;
  /**
   * 保留的小数位数；为 0 时不渲染小数点与小数部分
   * @default 0
   */
  decimals?: number;
  /**
   * 小数点符号，用于本地化（如德语使用 `','`）
   * @default '.'
   */
  decimal?: string;
  /**
   * 整数部分的千分位分隔符，传空字符串可关闭分隔
   * @default ','
   */
  separator?: string;
  /** 数值前缀，如 `'￥'` */
  prefix?: string;
  /** 数值后缀，如 `'%'`、`'万'` */
  suffix?: string;
  /**
   * 缓动方式：可传预设名、三次贝塞尔控制点数组或自定义缓动函数
   * @default 'easeOutExpo'
   */
  transition?: CubicBezierPoints | EasingFunction | TransitionPresets;
  /** 整数部分的类名 */
  mainClass?: string;
  /** 小数部分的类名 */
  decimalClass?: string;
  /** 前缀部分的类名 */
  prefixClass?: string;
  /** 后缀部分的类名 */
  suffixClass?: string;

  /** 整数部分的样式 */
  mainStyle?: StyleValue;
  /** 小数部分的样式 */
  decimalStyle?: StyleValue;
  /** 前缀部分的样式 */
  prefixStyle?: StyleValue;
  /** 后缀部分的样式 */
  suffixStyle?: StyleValue;
}
