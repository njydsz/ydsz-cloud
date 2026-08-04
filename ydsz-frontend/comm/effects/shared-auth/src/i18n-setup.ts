/**
 * 子应用 i18n 装配工厂 — 消除各子应用 locales/index.ts 中重复的样板代码。
 *
 * v3.5 (A6/B6): 将 dayjs / element-plus / app langs 的加载逻辑收敛至 shared-auth，
 *               子应用只需传入 `import.meta.glob` 产物即可获得完整 i18n 装配。
 *
 * 设计要点：
 *   - `import.meta.glob` 必须在子应用源码中执行（路径相对子应用），
 *     因此 factory 接收 modules 作为入参，而非在内部 glob
 *   - dayjs locale 与 element-plus locale 通过动态 import 按需加载，
 *     避免静态导入导致两个语种包同时进入主包
 *   - `elementLocale` 作为响应式 ref 暴露，供 `ElConfigProvider` 注入
 *
 * @path comm/effects/shared-auth/src/i18n-setup.ts
 * @author ydsz-team
 * @since 3.5.0
 */
import type { Language } from 'element-plus/es/locale';

import type { App, Ref } from 'vue';
import type { LocaleSetupOptions, SupportedLanguagesType } from '@ydsz/locales';

import { ref } from 'vue';

import {
  $t,
  loadLocalesMapFromDir,
  setupI18n as coreSetup,
} from '@ydsz/locales';
import { preferences } from '@ydsz/preferences';

import dayjs from 'dayjs';
import enLocale from 'element-plus/es/locale/lang/en';
import defaultLocale from 'element-plus/es/locale/lang/zh-cn';

/** 默认 locale → file 正则（与子应用既有约定一致） */
const DEFAULT_LANG_PATTERN = /\.\/langs\/([^/]+)\/(.*)\.json$/;

/** createSubAppI18n 入参 */
export interface CreateSubAppI18nOptions {
  /**
   * import.meta.glob 产物（如 `./langs` 目录下的 JSON 模块映射）。
   *
   * 必须由子应用源码中执行 glob（Vite 编译期相对路径解析），再传入本工厂。
   */
  modules: Record<string, () => Promise<unknown>>;
  /**
   * 匹配 locale 与 fileName 的正则，默认 `\.\/langs\/([^/]+)\/(.*)\.json$`。
   *
   * 若子应用目录结构不同（如使用 `./langs/*.json` 平铺结构），可自定义。
   */
  pattern?: RegExp;
  /**
   * 额外的 LocaleSetupOptions，与运行时传入的 options 合并（运行时优先）。
   */
  setupOptions?: LocaleSetupOptions;
}

/** createSubAppI18n 返回值 */
export interface SubAppI18nInstance {
  /** 翻译函数（绑定到全局 i18n） */
  $t: typeof $t;
  /** Element Plus 当前语种，供 `ElConfigProvider :locale` 使用 */
  elementLocale: Ref<Language>;
  /** 安装 i18n 到 Vue app（封装 coreSetup，注入默认 locale 与 missingWarn） */
  setupI18n: (app: App, options?: LocaleSetupOptions) => Promise<void>;
}

/**
 * 创建子应用 i18n 实例。
 *
 * 子应用 `locales/index.ts` 只需两行：用 `import.meta.glob` 扫描 `./langs`
 * 目录下的 JSON，再传入本工厂即可获得 `{ $t, elementLocale, setupI18n }`。
 *
 * @example
 * ```ts
 * import { createSubAppI18n } from '@ydsz/shared-auth';
 * // modules = import.meta.glob 扫描 ./langs 下的所有 JSON
 * export const { $t, elementLocale, setupI18n } = createSubAppI18n({ modules });
 * ```
 */
export function createSubAppI18n(
  options: CreateSubAppI18nOptions,
): SubAppI18nInstance {
  const {
    modules,
    pattern = DEFAULT_LANG_PATTERN,
    setupOptions,
  } = options;

  const elementLocale = ref<Language>(defaultLocale);
  const localesMap = loadLocalesMapFromDir(pattern, modules);

  /**
   * 加载应用特有的语言包。
   *
   * 与第三方组件库语言包并行加载；此处也可改造为从服务端拉取翻译数据。
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
   */
  async function loadThirdPartyMessage(lang: SupportedLanguagesType) {
    await Promise.all([loadElementLocale(lang), loadDayjsLocale(lang)]);
  }

  /**
   * 加载 dayjs 的语言包并设置全局 locale。
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
   */
  async function setupI18n(app: App, runtimeOptions: LocaleSetupOptions = {}) {
    await coreSetup(app, {
      defaultLocale: preferences.app.locale,
      loadMessages,
      missingWarn: !import.meta.env.PROD,
      ...setupOptions,
      ...runtimeOptions,
    });
  }

  return { $t, elementLocale, setupI18n };
}
