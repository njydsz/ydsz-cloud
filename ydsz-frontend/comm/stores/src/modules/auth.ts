/**
 * Auth Store — 独立的认证状态管理（token / refreshToken / 锁屏）。
 *
 * 从 accessStore 中拆分出纯认证职责，accessStore 仅负责 menus/routes/codes 的
 * 访问控制逻辑。两者职责清晰，消除跨 store 访问的 as any 类型断言。
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

interface AuthState {
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
}

export const useAuthStore = defineStore('core-auth', {
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
    pick: ['accessToken', 'refreshToken', 'isLockScreen', 'lockScreenPassword'],
  },
  state: (): AuthState => ({
    accessToken: null,
    refreshToken: null,
    loginExpired: false,
    isLockScreen: false,
    lockScreenPassword: undefined,
  }),
});

const hot = import.meta.hot;
if (hot) {
  hot.accept(acceptHMRUpdate(useAuthStore, hot));
}
