/**
 * use-refresh 组合式函数
 *
 * @path comm\effects\hooks\src\use-refresh.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import { useRouter } from 'vue-router';

import { useTabbarStore } from '@ydsz/stores';

/**
 * 提供刷新当前标签页内容的能力。
 *
 * @remarks
 * 入参：无。
 *
 * 副作用：`refresh()` 会委托 `tabbarStore.refresh(router)`，
 * 内部通过临时禁用 keep-alive 缓存并重新挂载路由视图来实现「局部刷新」，
 * **不会触发浏览器整页重载**，因此 Pinia 状态与已登录态均得以保留；
 * 但当前页面组件会被销毁重建，组件内部的临时状态（表单草稿、滚动位置等）会丢失。
 *
 * 生命周期依赖：内部调用了 `useRouter()`，**必须在组件 `setup` 期间调用**，
 * 否则拿不到路由实例。无需手动清理。
 *
 * @returns `refresh` —— 异步刷新方法，Promise resolve 时视图已重新渲染
 *
 * @example
 * ```ts
 * const { refresh } = useRefresh();
 * await refresh();
 * ```
 */
export function useRefresh() {
  const router = useRouter();
  const tabbarStore = useTabbarStore();

  async function refresh() {
    await tabbarStore.refresh(router);
  }

  return {
    refresh,
  };
}
