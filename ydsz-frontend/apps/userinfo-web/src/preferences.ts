/**
 * 用户中心应用偏好配置 — 定义侧边栏、主题、水印等默认设置
 *
 * @path apps\userinfo-web\src\preferences.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import { defineOverridesPreferences } from '@ydsz/preferences';

export const overridesPreferences = defineOverridesPreferences({
  app: {
    name: import.meta.env.VITE_APP_TITLE,
    defaultHomePath: '/ydsz-user',
  },
  sidebar: {
    hidden: true,
  },
  theme: {
    builtinType: 'deep-blue',
    colorPrimary: 'hsl(211 98% 52%)',
    mode: 'light',
    radius: '0.5',
    semiDarkHeader: false,
    semiDarkSidebar: false,
  },
});
