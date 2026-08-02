/**
 * useCaptchaPoints 组合式函数
 *
 * @path comm\effects\common-ui\src\components\captcha\hooks\useCaptchaPoints.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { CaptchaPoint } from '../types';

import { reactive } from 'vue';

/**
 * 管理点选验证码已点击点位的组合式函数。
 *
 * @remarks
 * 入参：无。
 *
 * 副作用与生命周期：内部仅用 `reactive` 维护一个数组，**不注册任何事件监听或定时器**，
 * 因此不依赖组件生命周期，可在 `setup` 之外调用，也无需手动清理。
 *
 * 注意点：
 * - 返回的 `points` 是 `reactive` 数组本身（非只读），直接改写会绕过 `addPoint`/`clearPoints`，
 *   导致 `CaptchaPoint.i` 序号与实际下标不一致，请始终通过返回的方法操作；
 * - `clearPoints` 用 `splice(0)` 原地清空，保持引用不变，模板中的 `v-for` 与外部持有的引用均可继续使用；
 * - 每次调用都会创建**独立**的状态，多个验证码实例互不影响。
 *
 * @returns 点位状态与操作方法：`points` 为按点击顺序排列的点位数组，
 * `addPoint` 追加一个点位，`clearPoints` 原地清空全部点位
 *
 * @example
 * ```ts
 * const { addPoint, clearPoints, points } = useCaptchaPoints();
 * addPoint({ i: points.length, t: Date.now(), x: 12, y: 34 });
 * clearPoints();
 * ```
 */
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
