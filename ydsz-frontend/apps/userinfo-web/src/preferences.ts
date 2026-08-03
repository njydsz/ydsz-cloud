/**
 * 用户中心应用偏好配置 — 定义侧边栏、主题、水印等默认设置
 *
 * @path apps\userinfo-web\src\preferences.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import { defineOverridesPreferences } from '@ydsz/preferences';

/**
 * 用户中心子应用偏好覆盖配置。
 *
 * 在 @ydsz/preferences 默认值基础上按 userinfo-web 诉求定制：
 * 应用名取自构建期标题、默认首页指向 /ydsz-user、隐藏侧边栏，并固定 deep-blue 主题。
 */
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
