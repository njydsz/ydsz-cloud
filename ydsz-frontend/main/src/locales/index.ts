/**
 * 国际化配置入口
 *
 * @path main\src\locales\index.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { Language } from 'element-plus/es/locale';

import type { App } from 'vue';

import type { LocaleSetupOptions, SupportedLanguagesType } from '@ydsz/locales';

import { ref } from 'vue';

import {
  $t,
  loadLocalesMapFromDir,
  preloadLocaleOnIdle,
  setupI18n as coreSetup,
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

async function loadMessages(lang: SupportedLanguagesType) {
  const [appLocaleMessages] = await Promise.all([
    localesMap[lang]?.(),
    loadThirdPartyMessage(lang),
  ]);
  return appLocaleMessages?.default;
}

async function loadThirdPartyMessage(lang: SupportedLanguagesType) {
  await Promise.all([loadElementLocale(lang), loadDayjsLocale(lang)]);
}

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
 * 初始化应用国际化（i18n）。
 *
 * @param app - Vue 应用实例
 * @param options - 额外的 i18n 配置项，会覆盖默认值（如默认语言、消息加载器）
 */
async function setupI18n(app: App, options: LocaleSetupOptions = {}) {
  const defaultLocale = options.defaultLocale ?? preferences.app.locale;
  await coreSetup(app, {
    defaultLocale,
    loadMessages,
    missingWarn: !import.meta.env.PROD,
    ...options,
  });

  // F2: 首屏完成后空闲预加载另一语种，使切换近似瞬时
  const alternateLocale: SupportedLanguagesType =
    defaultLocale === 'zh-CN' ? 'en-US' : 'zh-CN';
  preloadLocaleOnIdle(alternateLocale, localesMap);
}

export { $t, elementLocale, setupI18n };
