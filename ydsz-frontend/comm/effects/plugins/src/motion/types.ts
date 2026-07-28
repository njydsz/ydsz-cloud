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

export type MotionPreset = (typeof MotionPresets)[number];
