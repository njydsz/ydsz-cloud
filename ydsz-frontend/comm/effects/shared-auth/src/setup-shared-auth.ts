/**
 * setup-shared-auth — 统一的 initSharedAuth 实现
 *
 * 消除 9 个子应用 main.ts 中完全重复的 initSharedAuth() 函数（约 40 行 × 9 = 360 行重复代码）。
 * 子应用调用 setupSharedAuth(appName) 即可完成共享请求客户端的初始化。
 *
 * 行为与原各子应用内联实现完全一致：
 * - doReAuthenticate: token 失效时清除 token，按 loginExpiredMode 决定弹窗或跳转
 * - doRefreshToken: 使用 refreshToken 刷新 accessToken
 *
 * P0-F2: HttpOnly Cookie 模式下，doRefreshToken 直接返回 null（后端通过 Cookie 续期），
 *        doReAuthenticate 不再清空本地 token（前端无 token 可清）。
 */
import { CROSS_TAB_EVENTS, notifyCrossTab } from './cross-tab';
import { initSharedRequest, refreshTokenApi } from './request-setup';

/**
 * P0-F2: 认证令牌存储模式（构建期常量，与 request.ts 保持一致）。
 *
 * @see comm/effects/shared-auth/src/request.ts 中的 isHttpOnlyCookieMode
 */
const isHttpOnlyCookieMode: boolean =
  import.meta.env.VITE_APP_AUTH_TOKEN_STORAGE === 'httpOnlyCookie';

/**
 * 初始化共享请求客户端（注入 reAuthenticate / refreshToken 回调）
 *
 * @param appName 子应用名称，用于日志标识（如 'message-web'、'nextwiki-web'）
 */
export async function setupSharedAuth(appName: string): Promise<void> {
  const { preferences } = await import('@ydsz/preferences');
  const { resetAllStores, useAccessStore, useTokenStore } = await import('@ydsz/stores');

  initSharedRequest(
    // doReAuthenticate: token 失效时退出登录
    async () => {
      console.warn(`[${appName}] Access token expired, re-authenticating...`);
      const tokenStore = useTokenStore();
      const accessStore = useAccessStore();
      // P0-F2: HttpOnly Cookie 模式下前端无 token 可清，跳过 setAccessToken(null)
      if (!isHttpOnlyCookieMode) {
        tokenStore.setAccessToken(null);
        // 同步清空过期时间戳，避免登出后仍触发会话超时预警
        tokenStore.setExpiresAt(null);
      }
      if (
        preferences.app.loginExpiredMode === 'modal' &&
        accessStore.isAccessChecked
      ) {
        tokenStore.setLoginExpired(true);
      } else {
        resetAllStores();
        tokenStore.setLoginExpired(false);
        window.location.href = '/';
      }
    },
    // doRefreshToken: 刷新 accessToken
    // P0-F2: HttpOnly Cookie 模式下后端通过 Cookie 自动续期，前端不主动刷新
    async () => {
      if (isHttpOnlyCookieMode) return null;

      const tokenStore = useTokenStore();
      const refreshToken = tokenStore.refreshToken;
      if (!refreshToken) return null;
      try {
        const resp = await refreshTokenApi(refreshToken);
        const newToken =
          resp.data?.accessToken || (resp.data as unknown as string);
        let newExpiresAt: null | number = null;
        if (typeof newToken === 'string') {
          tokenStore.setAccessToken(newToken);
        }
        // 续期后同步刷新绝对过期时间戳（供会话超时预警使用）
        const expiresIn = (resp.data as { expiresIn?: number } | undefined)?.expiresIn;
        if (typeof expiresIn === 'number' && expiresIn > 0) {
          newExpiresAt = Date.now() + expiresIn * 1000;
          tokenStore.setExpiresAt(newExpiresAt);
        }
        // D4: 广播 token 刷新成功事件到其它标签页，
        //     避免其它标签页同时 401 时重复刷新导致 refreshToken 竞态
        if (typeof newToken === 'string') {
          notifyCrossTab(CROSS_TAB_EVENTS.TOKEN_REFRESHED, {
            accessToken: newToken,
            expiresAt: newExpiresAt,
          });
        }
        return newToken;
      } catch {
        return null;
      }
    },
  );
}
