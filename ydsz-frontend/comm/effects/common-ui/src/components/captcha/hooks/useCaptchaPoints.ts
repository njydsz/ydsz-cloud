/**
 * useCaptchaPoints 组合式函数
 *
 * @path comm\effects\common-ui\src\components\captcha\hooks\useCaptchaPoints.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { CaptchaPoint } from '../types';

import { reactive } from 'vue';

export function useCaptchaPoints() {
  const points = reactive<CaptchaPoint[]>([]);
  function addPoint(point: CaptchaPoint) {
    points.push(point);
  }

  function clearPoints() {
    points.splice(0);
  }
  return {
    addPoint,
    clearPoints,
    points,
  };
}
