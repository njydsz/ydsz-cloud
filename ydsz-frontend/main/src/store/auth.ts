/**
 * auth Pinia 状态管理
 *
 * 主应用 auth store：复用 @ydsz/shared-auth 的 createSharedAuthStore 工厂，
 * 仅通过 onLogout 回调注入跨标签页广播能力，消除重复代码。
 *
 * @path main\src\store\auth.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import { createSharedAuthStore } from '@ydsz/shared-auth';

import { CROSS_TAB_EVENTS, notifyCrossTab } from '#/hooks/use-cross-tab-sync';

export const useAuthStore = createSharedAuthStore({
  onLogout: (redirect) => notifyCrossTab(CROSS_TAB_EVENTS.LOGOUT, { redirect }),
});
