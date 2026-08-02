/**
 * color 模块
 *
 * @path comm\@core\base\shared\src\color\color.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import { TinyColor } from '@ctrl/tinycolor';

/**
 * 判断给定颜色是否属于「暗色」，用于自动决定其上层文字应使用亮色还是暗色。
 *
 * @remarks
 * 底层由 TinyColor 按 YIQ 亮度模型计算，亮度阈值为 128（含），即亮度 &lt; 128 判定为暗色。
 * 入参解析失败（非法颜色字符串）时 TinyColor **不会抛异常**，而是退化为无效颜色，
 * 此时结果恒为 `true`，调用方若需区分「非法输入」与「真的是暗色」必须自行前置校验。
 * 该函数为纯计算、无副作用，但每次调用都会新建 TinyColor 实例，高频场景（如列表逐行着色）
 * 建议在外层缓存结果。
 *
 * @param color - 任意 TinyColor 可解析的颜色，支持 hex、rgb()、hsl()、颜色关键字等
 * @returns 判定为暗色返回 `true`；亮色或无法解析时返回 `false` / `true`（见 remarks）
 *
 * @example
 * ```ts
 * isDarkColor('#000000'); // true
 * isDarkColor('#ffffff'); // false
 * ```
 */
export function isDarkColor(color: string) {
  return new TinyColor(color).isDark();
}

/**
 * 判断给定颜色是否属于「亮色」，语义上是 {@link isDarkColor} 的取反。
 *
 * @remarks
 * 与 {@link isDarkColor} 共用同一套 YIQ 亮度阈值，二者对同一入参的结果互斥，
 * 因此不要同时调用两次做互补判断，直接取反即可，可省去一次颜色解析开销。
 * 无效颜色的处理方式同样是静默退化而非抛错。
 *
 * @param color - 任意 TinyColor 可解析的颜色字符串
 * @returns 判定为亮色返回 `true`，否则返回 `false`
 */
export function isLightColor(color: string) {
  return new TinyColor(color).isLight();
}
