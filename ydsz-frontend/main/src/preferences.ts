import { defineOverridesPreferences } from '@ydsz/preferences';

/**
 * PMIS 主应用偏好设置
 * 只需要覆盖项目中的一部分配置，不需要的配置不用覆盖，会自动使用默认配置
 * !!! 更改配置后请清空缓存，否则可能不生效
 */
export const overridesPreferences = defineOverridesPreferences({
  app: {
    name: import.meta.env.VITE_APP_TITLE,
    defaultHomePath: '/dashboard/analytics',
    // 启用 refreshToken
    enableRefreshToken: true,
  },
  theme: {
    builtinType: 'deep-blue',
    colorPrimary: 'hsl(211 98% 52%)',
    // auto 模式：跟随系统暗黑模式偏好
    mode: 'auto',
    radius: '0.5',
    semiDarkHeader: false,
    semiDarkSidebar: false,
  },
  widget: {
    // 启用主题切换按钮
    themeToggle: true,
    // 启用全局搜索
    globalSearch: true,
    // 启用通知
    notification: true,
    // 启用锁屏
    lockScreen: true,
    // 启用全屏
    fullscreen: true,
    // 启用语言切换
    languageToggle: true,
  },
  shortcutKeys: {
    enable: true,
    globalSearch: true,
    globalLockScreen: true,
    globalLogout: true,
    globalPreferences: true,
  },
});
