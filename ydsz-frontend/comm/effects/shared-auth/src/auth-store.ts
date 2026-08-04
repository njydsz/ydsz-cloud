/**
 * 共享 Auth Store 工厂 — 完整登录/登出/token 刷新流程
 *
 * 子应用调用 createSharedAuthStore(router) 获得与主应用一致的 auth store。
 *
 * P0-F2: 支持 HttpOnly Cookie 模式。当 `VITE_APP_AUTH_TOKEN_STORAGE=httpOnlyCookie` 时：
 * - 登录成功后不从响应体读取 accessToken/refreshToken（后端通过 Set-Cookie 下发）
 * - 仍读取 userInfo 和 accessCodes（非敏感数据，正常通过响应体返回）
 * - 登出时调用 logoutApi 让后端清除 Cookie，前端仅清理本地 UI 状态
 */
import type { Recordable, UserInfo } from '@ydsz/types';

import { ref } from 'vue';
import { useRouter } from 'vue-router';

import { LOGIN_PATH } from '@ydsz/constants';
import { preferences } from '@ydsz/preferences';
import { resetAllStores, useAccessStore, useTokenStore, useUserStore } from '@ydsz/stores';

import { ElNotification } from 'element-plus';
import { defineStore } from 'pinia';

import { getAccessCodesApi, getUserInfoApi, loginApi, logoutApi } from './auth-api';
import { $t } from './i18n-setup';

/**
 * P0-F2: 认证令牌存储模式（构建期常量，与 request.ts 保持一致）。
 *
 * @see comm/effects/shared-auth/src/request.ts 中的 isHttpOnlyCookieMode
 */
const isHttpOnlyCookieMode: boolean =
  import.meta.env.VITE_APP_AUTH_TOKEN_STORAGE === 'httpOnlyCookie';

/**
 * 创建共享 Auth Store
 *
 * 子应用使用方式：
 * ```ts
 * import { createSharedAuthStore } from '@ydsz/shared-auth';
 * export const useAuthStore = createSharedAuthStore();
 * ```
 *
 * 主应用可传入回调以扩展行为（如跨标签页广播）：
 * ```ts
 * export const useAuthStore = createSharedAuthStore({
 *   onLogout: (redirect) => notifyCrossTab(CROSS_TAB_EVENTS.LOGOUT, { redirect }),
 * });
 * ```
 *
 * @param options - 可选回调，允许宿主在登录/登出等关键节点注入自定义逻辑
 */
export function createSharedAuthStore(options: {
  /** 登出时回调（在 resetAllStores 之后、路由跳转之前触发） */
  onLogout?: (redirect: boolean) => void;
} = {}) {
  return defineStore('auth', () => {
    const accessStore = useAccessStore();
    const tokenStore = useTokenStore();
    const userStore = useUserStore();
    const router = useRouter();

    const loginLoading = ref(false);

    async function authLogin(
      params: Recordable<any>,
      onSuccess?: () => Promise<void> | void,
    ) {
      let userInfo: null | UserInfo = null;
      try {
        loginLoading.value = true;
        const loginResult = await loginApi(params);
        const {
          accessToken,
          refreshToken,
          userInfo: loginUserInfo,
        } = loginResult;

        // P0-F2: HttpOnly Cookie 模式下，令牌由后端通过 Set-Cookie 下发，
        //        前端不从响应体读取/存储 accessToken/refreshToken。
        //        判定登录成功的条件从 "有 accessToken" 改为 "请求成功无异常"。
        const isLoginSuccess = isHttpOnlyCookieMode || Boolean(accessToken);

        if (isLoginSuccess) {
          if (!isHttpOnlyCookieMode) {
            tokenStore.setAccessToken(accessToken);
            if (refreshToken) {
              tokenStore.setRefreshToken(refreshToken);
            }
            // 记录绝对过期时间戳，供会话超时预警使用（expiresIn 单位：秒）
            if (typeof loginResult.expiresIn === 'number' && loginResult.expiresIn > 0) {
              tokenStore.setExpiresAt(Date.now() + loginResult.expiresIn * 1000);
            }
          }

          if (loginUserInfo) {
            userInfo = loginUserInfo as unknown as UserInfo;
            userStore.setUserInfo(userInfo);
          } else {
            userInfo = await fetchUserInfo();
          }

          try {
            const accessCodes = await getAccessCodesApi();
            accessStore.setAccessCodes(accessCodes);
          } catch {
            accessStore.setAccessCodes([]);
          }

          if (tokenStore.loginExpired) {
            tokenStore.setLoginExpired(false);
          } else {
            onSuccess
              ? await onSuccess?.()
              : await router.push(
                  userInfo.homePath || preferences.app.defaultHomePath,
                );
          }

          if (userInfo?.realName) {
            ElNotification.success({
              title: $t('authentication.loginSuccess'),
              message: `${$t('authentication.loginSuccessDesc')}: ${userInfo.realName}`,
              duration: 3000,
            });
          }
        }
      } finally {
        loginLoading.value = false;
      }

      return { userInfo };
    }

    async function logout(redirect: boolean = true) {
      try {
        await logoutApi();
      } catch {
        // 静默
      }
      resetAllStores();
      tokenStore.setLoginExpired(false);

      // 宿主回调（如跨标签页广播登出事件）
      options.onLogout?.(redirect);

      await router.replace({
        path: LOGIN_PATH,
        query: redirect
          ? {
              redirect: encodeURIComponent(router.currentRoute.value.fullPath),
            }
          : {},
      });
    }

    async function fetchUserInfo() {
      const userInfo = await getUserInfoApi();
      userStore.setUserInfo(userInfo);
      return userInfo;
    }

    function $reset() {
      loginLoading.value = false;
    }

    return {
      $reset,
      authLogin,
      fetchUserInfo,
      loginLoading,
      logout,
    };
  });
}
