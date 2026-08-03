/**
 * 跨标签页状态同步集成
 *
 * 监听关键状态变更（登出/会话失效）并广播到同源其它标签页，
 * 同时订阅远端事件执行本地联动。
 *
 * 防回环：远端事件触发的本地操作不再广播（通过 isHandlingRemote 标志位）。
 *
 * @path main/src/hooks/use-cross-tab-sync.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import { getCurrentScope, onScopeDispose } from 'vue';

import { broadcastCrossTabEvent, useCrossTabEvent } from '@ydsz/hooks';

import { useAuthStore } from '#/store/auth';

/** 主应用统一使用的跨标签页通道名 */
export const CROSS_TAB_CHANNEL = 'ydsz-pmis';

/** 跨标签页事件类型注册表（避免散写字符串 key） */
export const CROSS_TAB_EVENTS = {
  /** 用户登出 — 其它标签页应同步登出 */
  LOGOUT: 'logout',
  /** 会话失效 — 与 LOGOUT 类似但语义不同（被动 vs 主动） */
  SESSION_EXPIRED: 'session-expired',
  /** 主题变更 — 同步主题到其它标签页 */
  THEME_CHANGE: 'theme-change',
  /** 语言变更 — 同步语言到其它标签页 */
  LOCALE_CHANGE: 'locale-change',
} as const;

/** 防回环标志：正在处理远端事件时为 true */
let isHandlingRemote = false;

/**
 * 广播跨标签页事件（供本地主动操作调用）。
 *
 * 在远端事件处理过程中调用为 no-op，防止回环。
 */
export function notifyCrossTab<T = unknown>(
  eventType: string,
  payload: T,
): void {
  if (isHandlingRemote) return;
  broadcastCrossTabEvent(CROSS_TAB_CHANNEL, eventType, payload);
}

/**
 * 安装跨标签页状态同步。
 *
 * 必须在 Pinia 初始化后调用（bootstrap 中 initStores 之后）。
 *
 * 当前集成：
 *   - 登出同步：任一标签页登出 → 所有标签页同步登出
 *   - 会话失效同步：401 触发重新认证时通知其它标签页
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

  // bootstrap 顶层无 active scope 时不注册 onScopeDispose，
  // 通道随应用生命周期常驻。
  if (getCurrentScope()) {
    onScopeDispose(() => {
      /* useCrossTabEvent 内部已注册 onScopeDispose */
    });
  }
}
