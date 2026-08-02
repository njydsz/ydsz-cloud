/**
 * 国际化配置入口
 *
 * @path apps\agent-web\src\locales\index.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { Language } from 'element-plus/es/locale';

import type { App } from 'vue';

import type { LocaleSetupOptions, SupportedLanguagesType } from '@ydsz/locales';

import { ref } from 'vue';

import {
  $t,
  setupI18n as coreSetup,
  loadLocalesMapFromDir,
} from '@ydsz/locales';
import { preferences } from '@ydsz/preferences';

import dayjs from 'dayjs';
import enLocale from 'element-plus/es/locale/lang/en';
import defaultLocale from 'element-plus/es/locale/lang/zh-cn';

const elementLocale = ref<Language>(defaultLocale);

const modules = import.meta.glob('./langs/**/*.json');

const localesMap = loadLocalesMapFromDir(
  /\.\/langs\/([^/]+)\/(.*)\.json$/,
  modules,
);
/**
 * 加载应用特有的语言包。
 *
 * 与第三方组件库语言包并行加载；此处也可改造为从服务端拉取翻译数据。
 *
 * @param lang - 目标语言（如 zh-CN / en-US）
 * @returns 应用级语言包模块（默认导出），未找到时返回 undefined
 */
async function loadMessages(lang: SupportedLanguagesType) {
  const [appLocaleMessages] = await Promise.all([
    localesMap[lang]?.(),
    loadThirdPartyMessage(lang),
  ]);
  return appLocaleMessages?.default;
}

/**
 * 加载第三方组件库（Element Plus / dayjs）的语言包。
 *
 * @param lang - 目标语言
 */
async function loadThirdPartyMessage(lang: SupportedLanguagesType) {
  await Promise.all([loadElementLocale(lang), loadDayjsLocale(lang)]);
}

/**
 * 加载 dayjs 的语言包并设置全局 locale。
 *
 * @param lang - 目标语言，未匹配时默认回退到英语
 */
async function loadDayjsLocale(lang: SupportedLanguagesType) {
  let locale;
  switch (lang) {
    case 'en-US': {
      locale = await import('dayjs/locale/en');
      break;
    }
    case 'zh-CN': {
      locale = await import('dayjs/locale/zh-cn');
      break;
    }
    // 默认使用英语
    default: {
      locale = await import('dayjs/locale/en');
    }
  }
  if (locale) {
    dayjs.locale(locale);
  } else {
    console.error(`Failed to load dayjs locale for ${lang}`);
  }
}

/**
 * 加载 Element Plus 的语言包并写入响应式 ref，供组件 locale 注入使用。
 *
 * @param lang - 目标语言
 */
async function loadElementLocale(lang: SupportedLanguagesType) {
  switch (lang) {
    case 'en-US': {
      elementLocale.value = enLocale;
      break;
    }
    case 'zh-CN': {
      elementLocale.value = defaultLocale;
      break;
    }
  }
}

/**
 * 初始化 i18n（封装 @ydsz/locales 的核心 setup）。
 *
 * 以应用偏好中的 locale 为默认语言，并以 {@link loadMessages} 作为按需加载器。
 *
 * @param app - Vue 应用实例
 * @param options - 额外 i18n 配置，会与默认配置合并
 */
async function setupI18n(app: App, options: LocaleSetupOptions = {}) {
  await coreSetup(app, {
    defaultLocale: preferences.app.locale,
    loadMessages,
    missingWarn: !import.meta.env.PROD,
    ...options,
  });
}

export { $t, elementLocale, setupI18n };
