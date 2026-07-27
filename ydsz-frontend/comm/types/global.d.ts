import type { RouteMeta as IRouteMeta } from '@ydsz-core/typings';

import 'vue-router';

declare module 'vue-router' {
  // eslint-disable-next-line @typescript-eslint/no-empty-object-type
  interface RouteMeta extends IRouteMeta {}
}

export interface YDSZAdminProAppConfigRaw {
  VITE_GLOB_API_URL: string;
  VITE_GLOB_AUTH_DINGDING_CLIENT_ID: string;
  VITE_GLOB_AUTH_DINGDING_CORP_ID: string;
}

interface AuthConfig {
  dingding?: {
    clientId: string;
    corpId: string;
  };
}

export interface ApplicationConfig {
  apiURL: string;
  auth: AuthConfig;
}

declare global {
  interface Window {
    _YDSZ_ADMIN_PRO_APP_CONF_: YDSZAdminProAppConfigRaw;
    /**
     * Qiankun 微前端标识
     * 当应用运行在 Qiankun 微前端环境中时，此值为 true
     */
    __POWERED_BY_QIANKUN__?: boolean;
    /**
     * Qiankun 全局生命周期钩子
     */
    __QIANKUN__?: {
      afterMount: (() => void)[];
      afterUnmount: (() => void)[];
      beforeMount: (() => void)[];
      beforeUnmount: (() => void)[];
    };
  }
}
