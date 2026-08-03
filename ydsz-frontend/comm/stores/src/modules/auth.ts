/**
 * Token Store — 独立的认证令牌状态管理（accessToken / refreshToken / 锁屏）
 *
 * 从 accessStore 中拆分出纯令牌职责，与 main 应用的 useAuthStore（业务级登录/登出）
 * 明确区分：useTokenStore 只管令牌存取，业务 login/logout 逻辑在 useAuthStore 中。
 *
 * @path comm\stores\src\modules\auth.ts
 * @author ydsz-team
 * @since 2.0.0
 */
import { acceptHMRUpdate, defineStore } from 'pinia';

type AuthToken = null | string;

/**
 * 对密码进行简单哈希处理（SHA-256）
 */
async function hashPassword(password: string): Promise<string> {
  const encoder = new TextEncoder();
  const data = encoder.encode(password);
  const hashBuffer = await crypto.subtle.digest('SHA-256', data);
  const hashArray = Array.from(new Uint8Array(hashBuffer));
  return hashArray.map((b) => b.toString(16).padStart(2, '0')).join('');
}

async function verifyPassword(password: string, storedHash: string): Promise<boolean> {
  const hash = await hashPassword(password);
  return hash === storedHash;
}

interface TokenState {
  /** 登录 accessToken */
  accessToken: AuthToken;
  /** 刷新 token */
  refreshToken: AuthToken;
  /** 登录是否过期 */
  loginExpired: boolean;
  /** 是否锁屏状态 */
  isLockScreen: boolean;
  /** 锁屏密码哈希值 */
  lockScreenPassword?: string;
  /**
   * accessToken 绝对过期时间戳（ms）。
   *
   * 由 login / refreshToken 响应中的 expiresIn（秒）换算而来：
   * `expiresAt = Date.now() + expiresIn * 1000`。
   * 用于会话超时预警（到期前 5 分钟提示续期）。
   * null 表示未知（后端未返回 expiresIn），不触发预警。
   */
  expiresAt: null | number;
}

/** 认证令牌 Store：负责 accessToken / refreshToken / 锁屏状态的存取与持久化，登录登出业务在 useAuthStore 中 */
export const useTokenStore = defineStore('core-auth', {
  actions: {
    setAccessToken(token: AuthToken) {
      this.accessToken = token;
    },
    setRefreshToken(token: AuthToken) {
      this.refreshToken = token;
    },
    setLoginExpired(loginExpired: boolean) {
      this.loginExpired = loginExpired;
    },
    /** 设置 accessToken 绝对过期时间戳（ms），null 表示未知 */
    setExpiresAt(expiresAt: null | number) {
      this.expiresAt = expiresAt;
    },
    async lockScreen(password: string) {
      this.isLockScreen = true;
      this.lockScreenPassword = await hashPassword(password);
    },
    unlockScreen() {
      this.isLockScreen = false;
      this.lockScreenPassword = undefined;
    },
    async verifyLockScreenPassword(password: string): Promise<boolean> {
      if (!this.lockScreenPassword) return false;
      return verifyPassword(password, this.lockScreenPassword);
    },
  },
  persist: {
    pick: ['accessToken', 'refreshToken', 'isLockScreen', 'lockScreenPassword', 'expiresAt'],
  },
  state: (): TokenState => ({
    accessToken: null,
    refreshToken: null,
    loginExpired: false,
    isLockScreen: false,
    lockScreenPassword: undefined,
    expiresAt: null,
  }),
});

const hot = import.meta.hot;
if (hot) {
  hot.accept(acceptHMRUpdate(useTokenStore, hot));
}
