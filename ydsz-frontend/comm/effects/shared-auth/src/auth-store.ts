/**
 * 共享 Auth Store 工厂 — 完整登录/登出/token 刷新流程
 *
 * 子应用调用 createSharedAuthStore(router) 获得与主应用一致的 auth store。
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
 * 创建共享 Auth Store
 *
 * 子应用使用方式：
 * ```ts
 * import { createSharedAuthStore } from '@ydsz/shared-auth';
 * export const useAuthStore = createSharedAuthStore();
 * ```
 */
export function createSharedAuthStore() {
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

        if (accessToken) {
          tokenStore.setAccessToken(accessToken);
          if (refreshToken) {
            tokenStore.setRefreshToken(refreshToken);
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
