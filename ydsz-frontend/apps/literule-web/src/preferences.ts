/**
 * 应用偏好配置覆盖
 * <p>覆盖 @ydsz/preferences 框架默认值，设置 ydsz-literule 子应用的默认首页路径、侧边栏等配置。
 *
 * @path apps\literule-web\src\preferences.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import { defineOverridesPreferences } from '@ydsz/preferences';

export const overridesPreferences = defineOverridesPreferences({
  app: {
    name: import.meta.env.VITE_APP_TITLE,
    defaultHomePath: '/ydsz-rule',
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
