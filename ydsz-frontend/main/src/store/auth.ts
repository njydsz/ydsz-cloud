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
   * 异步处理登录操作。
   *
   * 调用登录接口获取令牌与用户信息，写入访问/刷新令牌与权限码，成功后跳转首页或执行回调。
   *
   * @param params - 登录参数（用户名、密码、验证码等）
   * @param onSuccess - 登录成功后的可选回调；不传则跳转首页
   * @returns 登录得到的用户信息（失败时为 null）
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

        if (authStore.loginExpired) {
          authStore.setLoginExpired(false);
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

  /**
   * 退出登录。
   *
   * 调用登出接口（失败不阻断），重置所有状态仓库并跳转登录页。
   *
   * @param redirect - 是否携带当前路径作为 redirect 参数跳转登录页
   */
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

  /**
   * 拉取并设置当前登录用户信息。
   *
   * @returns 当前用户的基础信息
   */
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
