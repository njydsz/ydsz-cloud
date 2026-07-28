/**
 * use-refresh 组合式函数
 *
 * @path comm\effects\hooks\src\use-refresh.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import { useRouter } from 'vue-router';

import { useTabbarStore } from '@ydsz/stores';

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
