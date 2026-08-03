/**
 * 工作流子应用偏好设置
 * <p>定义当前子应用的 preferences 覆盖项，包括布局、菜单、水印等。
 *
 * @path apps\workflow-web\src\preferences.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import { defineOverridesPreferences } from '@ydsz/preferences';

/**
 * 工作流子应用偏好覆盖配置。
 *
 * 在 @ydsz/preferences 默认值基础上按 workflow-web 诉求定制：
 * 应用名取自构建期标题、默认首页指向 /ydsz-flow、隐藏侧边栏，并固定 deep-blue 主题。
 */
export const overridesPreferences = defineOverridesPreferences({
  app: {
    name: import.meta.env.VITE_APP_TITLE,
    defaultHomePath: '/ydsz-flow',
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
