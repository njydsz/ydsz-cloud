/**
 * setup-shared-auth — 统一的 initSharedAuth 实现
 *
 * 消除 9 个子应用 main.ts 中完全重复的 initSharedAuth() 函数（约 40 行 × 9 = 360 行重复代码）。
 * 子应用调用 setupSharedAuth(appName) 即可完成共享请求客户端的初始化。
 *
 * 行为与原各子应用内联实现完全一致：
 * - doReAuthenticate: token 失效时清除 token，按 loginExpiredMode 决定弹窗或跳转
 * - doRefreshToken: 使用 refreshToken 刷新 accessToken
 */
import { initSharedRequest, refreshTokenApi } from './request-setup';

/**
 * 初始化共享请求客户端（注入 reAuthenticate / refreshToken 回调）
 *
 * @param appName 子应用名称，用于日志标识（如 'message-web'、'nextwiki-web'）
 */
export async function setupSharedAuth(appName: string): Promise<void> {
  const { preferences } = await import('@ydsz/preferences');
  const { resetAllStores, useAccessStore } = await import('@ydsz/stores');

  initSharedRequest(
    // doReAuthenticate: token 失效时退出登录
    async () => {
      console.warn(`[${appName}] Access token expired, re-authenticating...`);
      const accessStore = useAccessStore();
      accessStore.setAccessToken(null);
      if (
        preferences.app.loginExpiredMode === 'modal' &&
        accessStore.isAccessChecked
      ) {
        accessStore.setLoginExpired(true);
      } else {
        resetAllStores();
        accessStore.setLoginExpired(false);
        window.location.href = '/';
      }
    },
    // doRefreshToken: 刷新 accessToken
    async () => {
      const accessStore = useAccessStore();
      const refreshToken = (accessStore as any).refreshToken;
      if (!refreshToken) return null;
      try {
        const resp = await refreshTokenApi(refreshToken);
        const newToken =
          resp.data?.accessToken || (resp.data as unknown as string);
        if (typeof newToken === 'string') {
          accessStore.setAccessToken(newToken);
        }
        return newToken;
      } catch {
        return null;
      }
    },
  );
}
