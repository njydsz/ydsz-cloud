/**
 * global.d 类型定义模块
 *
 * @path comm\types\global.d.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { RouteMeta as IRouteMeta } from '@ydsz-core/typings';

import 'vue-router';

declare module 'vue-router' {
  // eslint-disable-next-line @typescript-eslint/no-empty-object-type
  interface RouteMeta extends IRouteMeta {}
}

/**
 * 运行时应用配置的**原始**结构，对应挂载在 `window._YDSZ_ADMIN_PRO_APP_CONF_` 上的对象。
 *
 * @remarks
 * 为支持「一次构建、多环境部署」，这些配置不打进产物，而是在构建后由部署脚本
 * 写入 `_app.config.js` 并在 index.html 中先于应用加载。
 * 因此字段名与 `.env` 中的 `VITE_GLOB_*` 变量保持一一对应，全部为字符串；
 * 消费前应先经 {@link ApplicationConfig} 归一化，不要在业务代码里直读 window。
 */
export interface YDSZAdminProAppConfigRaw {
  /** 后端接口基础地址，如 `https://api.example.com/api` */
  VITE_GLOB_API_URL: string;
  /** 钉钉扫码登录的应用 ClientId；未接入钉钉登录时为空串 */
  VITE_GLOB_AUTH_DINGDING_CLIENT_ID: string;
  /** 钉钉企业 CorpId；未接入钉钉登录时为空串 */
  VITE_GLOB_AUTH_DINGDING_CORP_ID: string;
}

/** 第三方登录相关配置；对应渠道未配置时字段整体缺省 */
interface AuthConfig {
  /** 钉钉扫码登录配置，缺省表示不启用钉钉登录入口 */
  dingding?: {
    /** 钉钉应用 ClientId */
    clientId: string;
    /** 钉钉企业 CorpId */
    corpId: string;
  };
}

/**
 * 归一化后的应用配置，业务代码统一消费该结构。
 *
 * @remarks
 * 由 {@link YDSZAdminProAppConfigRaw} 转换而来：把扁平的环境变量整理成语义化的嵌套结构，
 * 并按「值是否为空」决定可选渠道配置是否存在，从而让业务侧只需判断 `auth.dingding` 是否有值。
 */
export interface ApplicationConfig {
  /** 后端接口基础地址，请求客户端的 baseURL */
  apiURL: string;
  /** 第三方登录配置 */
  auth: AuthConfig;
}

declare global {
  interface Window {
    _YDSZ_ADMIN_PRO_APP_CONF_: YDSZAdminProAppConfigRaw;
  }
}
