/**
 * 应用偏好配置覆盖
 * <p>覆盖 @ydsz/preferences 框架默认值，设置 ydsz-literule 子应用的默认首页路径、侧边栏等配置。
 *
 * @path apps\literule-web\src\preferences.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import { defineOverridesPreferences } from '@ydsz/preferences';

/**
 * 规则引擎子应用偏好覆盖配置。
 *
 * 在 @ydsz/preferences 默认值基础上按 literule-web 诉求定制：
 * 应用名取自构建期标题、默认首页指向 /ydsz-rule、隐藏侧边栏，并固定 deep-blue 主题。
 */
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
