/**
 * PMIS 子应用配置（qiankun 适配层）
 *
 * 所有应用清单由 {@link MICRO_APPS} 注册表单源驱动，不再硬编码。
 * 仅作 qiankun 专属的 entry/activeRule 转换。
 *
 * @path main/src/qiankun/index.ts
 * @author ydsz-team
 * @since 1.0.0
 */

import { MICRO_APPS } from '@ydsz/vite-config';

const isDev = import.meta.env.DEV;

/**
 * qiankun 子应用注册列表。
 *
 * 从注册表 MICRO_APPS 派生：dev 下 entry 指向 localhost 端口，
 * prod 下 entry 指向部署子路径 `/ydsz-{name}-web/`。
 */
export const microApps = MICRO_APPS.map((app) => ({
  name: app.name,
  entry: isDev
    ? `//localhost:${app.devPort}`
    : `/${app.name.replace('-web', '')}-web/`,
  container: '#subapp-container',
  activeRule: app.activeRule,
}));
