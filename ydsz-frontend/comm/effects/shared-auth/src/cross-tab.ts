/**
 * 跨标签页事件常量与广播工具 — 供主应用与子应用共享。
 *
 * D4 会话预警 + 跨标签页续期：
 *   当任一标签页刷新 token 成功后，广播 TOKEN_REFRESHED 事件，
 *   其它标签页收到后更新本地 tokenStore，避免各自独立刷新导致 refreshToken 竞态。
 *
 * 底层使用 BroadcastChannel（via @ydsz/hooks），同源标签页自动收发。
 *
 * @path comm/effects/shared-auth/src/cross-tab.ts
 * @author ydsz-team
 * @since 3.5.0
 */
import { broadcastCrossTabEvent } from '@ydsz/hooks';

/** 主应用统一使用的跨标签页通道名 */
export const CROSS_TAB_CHANNEL = 'ydsz-pmis';

/** 跨标签页事件类型注册表（避免散写字符串 key） */
export const CROSS_TAB_EVENTS = {
  /** 用户登出 — 其它标签页应同步登出 */
  LOGOUT: 'logout',
  /** 会话失效 — 与 LOGOUT 类似但语义不同（被动 vs 主动） */
  SESSION_EXPIRED: 'session-expired',
  /** Token 刷新成功 — 其它标签页应更新本地 tokenStore */
  TOKEN_REFRESHED: 'token-refreshed',
  /** 主题变更 — 同步主题到其它标签页 */
  THEME_CHANGE: 'theme-change',
  /** 语言变更 — 同步语言到其它标签页 */
  LOCALE_CHANGE: 'locale-change',
} as const;

/** TOKEN_REFRESHED 事件负载 */
export interface TokenRefreshedPayload {
  /** 新的 accessToken */
  accessToken: string;
  /** 新的绝对过期时间戳（ms），null 表示后端未返回 expiresIn */
  expiresAt: null | number;
}

/**
 * 广播跨标签页事件。
 *
 * 供主应用与子应用共享的广播入口，确保通道名一致。
 * 内部直接调用 @ydsz/hooks 的 broadcastCrossTabEvent。
 *
 * @param eventType - 事件类型（建议使用 CROSS_TAB_EVENTS 常量）
 * @param payload - 事件负载
 */
export function notifyCrossTab<T = unknown>(
  eventType: string,
  payload: T,
): void {
  broadcastCrossTabEvent(CROSS_TAB_CHANNEL, eventType, payload);
}
