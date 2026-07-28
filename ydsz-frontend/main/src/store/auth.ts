/**
 * auth Pinia 状态管理
 *
 * @path main\src\store\auth.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { Recordable, UserInfo } from '@ydsz/types';

import { ref } from 'vue';
import { useRouter } from 'vue-router';

import { LOGIN_PATH } from '@ydsz/constants';
import { preferences } from '@ydsz/preferences';
import { resetAllStores, useAccessStore, useUserStore } from '@ydsz/stores';

import { ElNotification } from 'element-plus';
import { defineStore } from 'pinia';

import { getAccessCodesApi, getUserInfoApi, loginApi, logoutApi } from '#/api';
import { $t } from '#/locales';

export const useAuthStore = defineStore('auth', () => {
  const accessStore = useAccessStore();
  const userStore = useUserStore();
  const router = useRouter();

  const loginLoading = ref(false);

  /**
   * 异步处理登录操作
   */
  async function authLogin(
    params: Recordable<any>,
    onSuccess?: () => Promise<void> | void,
  ) {
    let userInfo: null | UserInfo = null;
    try {
      loginLoading.value = true;
      const loginResult = await loginApi(params);
      const { accessToken, refreshToken, userInfo: loginUserInfo } = loginResult;

      if (accessToken) {
        accessStore.setAccessToken(accessToken);
        // 存储 refreshToken 用于后续刷新
        if (refreshToken) {
          (accessStore as any).refreshToken = refreshToken;
        }

        // 如果登录接口已返回用户信息，直接使用；否则调接口获取
        if (loginUserInfo) {
          userInfo = loginUserInfo as unknown as UserInfo;
          userStore.setUserInfo(userInfo);
        } else {
          userInfo = await fetchUserInfo();
        }

        // 获取权限码
        try {
          const accessCodes = await getAccessCodesApi();
          accessStore.setAccessCodes(accessCodes);
        } catch {
          // 权限码获取失败不阻塞登录
          accessStore.setAccessCodes([]);
        }

        if (accessStore.loginExpired) {
          accessStore.setLoginExpired(false);
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

    return {
      userInfo,
    };
  }

  async function logout(redirect: boolean = true) {
    try {
      await logoutApi();
    } catch {
      // 不做任何处理
    }
    resetAllStores();
    accessStore.setLoginExpired(false);

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
