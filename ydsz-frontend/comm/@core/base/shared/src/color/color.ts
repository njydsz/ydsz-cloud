/**
 * color 模块
 *
 * @path comm\@core\base\shared\src\color\color.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import { TinyColor } from '@ctrl/tinycolor';

export function isDarkColor(color: string) {
  return new TinyColor(color).isDark();
}

export function isLightColor(color: string) {
  return new TinyColor(color).isLight();
}
