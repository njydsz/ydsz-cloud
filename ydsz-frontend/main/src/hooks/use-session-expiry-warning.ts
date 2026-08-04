/**
 * 会话超时预警 — 到期前 5 分钟提示续期
 *
 * 监听 tokenStore.expiresAt（绝对过期时间戳），在到期前 5 分钟弹出
 * ElMessageBox 询问用户是否立即续期：
 *   - 用户确认 → 调用 refreshTokenApi 续期，更新 accessToken + expiresAt
 *   - 用户取消 → 不打扰，等真正过期时由 401 拦截器走 doReAuthenticate
 *
 * 同一过期周期只提示一次；expiresAt 变化（登录/续期成功）后重新计时。
 * 仅在主应用安装一次（bootstrap 中调用），子应用共享同一 tokenStore，
 * 无需各自重复安装。
 *
 * @path main/src/hooks/use-session-expiry-warning.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import { getCurrentScope, onScopeDispose, watch } from 'vue';

import { useTokenStore } from '@ydsz/stores';

import { ElMessageBox, ElMessage } from 'element-plus';

import { refreshTokenApi } from '#/api/core/auth';
import {
  CROSS_TAB_EVENTS,
  notifyCrossTab,
} from '#/hooks/use-cross-tab-sync';
import { $t } from '#/locales';

/** 提前多久开始预警（5 分钟） */
const WARNING_BEFORE_MS = 5 * 60 * 1000;
/** 轮询间隔（30 秒； setInterval 不精确，但配合 watch 已够用） */
const CHECK_INTERVAL_MS = 30 * 1000;

/**
 * 启动会话超时预警。
 *
 * 必须在 Pinia 初始化后调用（bootstrap 中 initStores 之后）。
 */
export function useSessionExpiryWarning(): void {
  const tokenStore = useTokenStore();

  let timer: null | ReturnType<typeof setInterval> = null;
  /** 当前 expiresAt 周期是否已提示过，避免重复打扰 */
  let warnedFor: null | number = null;
  /** 是否正在续期，防止并发 */
  let renewing = false;

  /** 调用刷新令牌接口并更新 tokenStore */
  async function renewSession(): Promise<boolean> {
    if (renewing) return false;
    const refreshToken = tokenStore.refreshToken;
    if (!refreshToken) {
      ElMessage.warning($t('authentication.renewFailed'));
      return false;
    }
    renewing = true;
    try {
      const resp = await refreshTokenApi(refreshToken);
      const data = resp.data as
        | { accessToken?: string; expiresIn?: number }
        | undefined;
      const newToken = data?.accessToken;
      let newExpiresAt: null | number = null;
      if (typeof newToken === 'string') {
        tokenStore.setAccessToken(newToken);
      }
      // doRefreshToken 回调中也会更新 expiresAt，这里同步兜底
      if (typeof data?.expiresIn === 'number' && data.expiresIn > 0) {
        newExpiresAt = Date.now() + data.expiresIn * 1000;
        tokenStore.setExpiresAt(newExpiresAt);
      }
      // D4: 广播 token 刷新成功事件到其它标签页
      if (typeof newToken === 'string') {
        notifyCrossTab(CROSS_TAB_EVENTS.TOKEN_REFRESHED, {
          accessToken: newToken,
          expiresAt: newExpiresAt,
        });
      }
      ElMessage.success($t('authentication.renewSuccess'));
      return true;
    } catch {
      ElMessage.warning($t('authentication.renewFailed'));
      return false;
    } finally {
      renewing = false;
    }
  }

  /** 弹出续期确认框 */
  async function showRenewDialog(): Promise<void> {
    if (warnedFor === tokenStore.expiresAt) return;
    warnedFor = tokenStore.expiresAt;
    try {
      await ElMessageBox.confirm(
        $t('authentication.sessionExpiringSoon'),
        $t('authentication.sessionExpiryTitle'),
        {
          type: 'warning',
          confirmButtonText: $t('authentication.renew'),
          cancelButtonText: $t('common.cancel'),
        },
      );
      await renewSession();
    } catch {
      // 用户取消 — 不打扰，等真正过期由 401 拦截器处理
    }
  }

  /** 单次检查：是否进入预警窗口 */
  function check(): void {
    const expiresAt = tokenStore.expiresAt;
    if (!expiresAt) {
      warnedFor = null;
      return;
    }
    const remaining = expiresAt - Date.now();
    if (remaining <= 0) {
      // 已过期 — 由 401 拦截器处理，这里不再提示
      warnedFor = expiresAt;
      return;
    }
    if (remaining <= WARNING_BEFORE_MS) {
      void showRenewDialog();
    }
  }

  /** 启动 / 重置定时器 */
  function startTimer(): void {
    if (timer) return;
    timer = setInterval(check, CHECK_INTERVAL_MS);
  }

  function stopTimer(): void {
    if (timer) {
      clearInterval(timer);
      timer = null;
    }
  }

  // 监听 expiresAt 变化（登录 / 续期 / 登出都会改）
  watch(
    () => tokenStore.expiresAt,
    (expiresAt) => {
      // expiresAt 变化视为新一轮周期，重置提示标志
      warnedFor = null;
      if (!expiresAt) {
        stopTimer();
        return;
      }
      startTimer();
      check(); // 立即检查一次（处理已临近过期的场景）
    },
    { immediate: true },
  );

  // 组件 / effect scope 销毁时清理定时器。
  // 若调用方处于 bootstrap 顶层（无 active scope），不注册 onScopeDispose 以避免 dev 警告，
  // 定时器随应用生命周期常驻（与 bootstrap 中 watchEffect 同模式）。
  if (getCurrentScope()) {
    onScopeDispose(() => {
      stopTimer();
    });
  }
}
