/**
 * types 模块
 *
 * @path comm\effects\plugins\src\motion\types.ts
 * @author ydsz-team
 * @since 1.0.0
 */
export const MotionPresets = [
  'fade',
  'fadeVisible',
  'fadeVisibleOnce',
  'rollBottom',
  'rollLeft',
  'rollRight',
  'rollTop',
  'rollVisibleBottom',
  'rollVisibleLeft',
  'rollVisibleRight',
  'rollVisibleTop',
  'pop',
  'popVisible',
  'popVisibleOnce',
  'slideBottom',
  'slideLeft',
  'slideRight',
  'slideTop',
  'slideVisibleBottom',
  'slideVisibleLeft',
  'slideVisibleRight',
  'slideVisibleTop',
] as const;

/**
 * 入场动画预设名称，取值来源于 `MotionPresets` 常量数组。
 *
 * @remarks
 * 命名规则（可据此推断行为，无需逐个记忆）：
 * - 前缀表示动画形式：`fade` 淡入、`roll` 翻滚、`pop` 弹出、`slide` 滑入；
 * - 含 `Visible` 表示**滚动进入视口时**才触发，否则挂载即触发；
 * - 含 `Once` 表示只播放一次，不含则每次进入视口都会重播；
 * - 末尾方位（`Top` / `Bottom` / `Left` / `Right`）为动画的起始方向。
 */
export type MotionPreset = (typeof MotionPresets)[number];
