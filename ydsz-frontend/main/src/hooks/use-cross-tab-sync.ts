/**
 * 跨标签页状态同步集成
 *
 * 监听关键状态变更（登出/会话失效/token 刷新）并广播到同源其它标签页，
 * 同时订阅远端事件执行本地联动。
 *
 * 防回环：远端事件触发的本地操作不再广播（通过 isHandlingRemote 标志位）。
 *
 * D4: 新增 TOKEN_REFRESHED 事件订阅 — 其它标签页刷新 token 后，
 *     本标签页同步更新 tokenStore，避免各自独立刷新导致 refreshToken 竞态。
 *
 * @path main/src/hooks/use-cross-tab-sync.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import { getCurrentScope, onScopeDispose } from 'vue';

import { useCrossTabEvent } from '@ydsz/hooks';
import {
  type TokenRefreshedPayload,
  CROSS_TAB_CHANNEL,
  CROSS_TAB_EVENTS,
  notifyCrossTab,
} from '@ydsz/shared-auth';
import { useTokenStore } from '@ydsz/stores';

import { useAuthStore } from '#/store/auth';

/** 防回环标志：正在处理远端事件时为 true */
let isHandlingRemote = false;

/**
 * 广播跨标签页事件（供本地主动操作调用）。
 *
 * 在远端事件处理过程中调用为 no-op，防止回环。
 */
export { notifyCrossTab };

// Re-export 供主应用其它模块引用
export { CROSS_TAB_CHANNEL, CROSS_TAB_EVENTS };

/**
 * 安装跨标签页状态同步。
 *
 * 必须在 Pinia 初始化后调用（bootstrap 中 initStores 之后）。
 *
 * 当前集成：
 *   - 登出同步：任一标签页登出 → 所有标签页同步登出
 *   - 会话失效同步：401 触发重新认证时通知其它标签页
 *   - Token 刷新同步：任一标签页刷新 token → 其它标签页更新本地 tokenStore
 */
export function useCrossTabSync(): void {
  // 订阅远端登出事件
  useCrossTabEvent(CROSS_TAB_CHANNEL, CROSS_TAB_EVENTS.LOGOUT, () => {
    isHandlingRemote = true;
    try {
      const authStore = useAuthStore();
      // 远端登出不跳转（已在其它标签页完成跳转），仅清理本地状态
      void authStore.logout(false);
    } finally {
      isHandlingRemote = false;
    }
  });

  // 订阅远端会话失效事件
  useCrossTabEvent(
    CROSS_TAB_CHANNEL,
    CROSS_TAB_EVENTS.SESSION_EXPIRED,
    () => {
      isHandlingRemote = true;
      try {
        const authStore = useAuthStore();
        void authStore.logout(false);
      } finally {
        isHandlingRemote = false;
      }
    },
  );

  // D4: 订阅远端 Token 刷新事件 — 同步更新本地 tokenStore
  useCrossTabEvent(
    CROSS_TAB_CHANNEL,
    CROSS_TAB_EVENTS.TOKEN_REFRESHED,
    (payload: TokenRefreshedPayload) => {
      isHandlingRemote = true;
      try {
        const tokenStore = useTokenStore();
        if (payload.accessToken) {
          tokenStore.setAccessToken(payload.accessToken);
        }
        if (payload.expiresAt !== undefined) {
          tokenStore.setExpiresAt(payload.expiresAt);
        }
      } finally {
        isHandlingRemote = false;
      }
    },
  );

  // bootstrap 顶层无 active scope 时不注册 onScopeDispose，
  // 通道随应用生命周期常驻。
  if (getCurrentScope()) {
    onScopeDispose(() => {
      /* useCrossTabEvent 内部已注册 onScopeDispose */
    });
  }
}
