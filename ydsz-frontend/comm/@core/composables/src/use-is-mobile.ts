/**
 * use-is-mobile 组合式函数
 *
 * @path comm\@core\composables\src\use-is-mobile.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import { breakpointsTailwind, useBreakpoints } from '@vueuse/core';

export function useIsMobile() {
  const breakpoints = useBreakpoints(breakpointsTailwind);
  const isMobile = breakpoints.smaller('md');
  return { isMobile };
}
