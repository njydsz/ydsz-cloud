/**
 * i18n 国际化语言包
 *
 * @path comm\locales\src\i18n.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { App } from 'vue';
import type { Locale } from 'vue-i18n';

import type {
  ImportLocaleFn,
  ImportNamespaceFn,
  LoadMessageFn,
  LocaleSetupOptions,
  NamespacedLocalesMap,
  PreloadLocaleOptions,
  SupportedLanguagesType,
} from './typing';

import { unref } from 'vue';
import { createI18n } from 'vue-i18n';

import { useSimpleLocale } from '@ydsz-core/composables';

const i18n = createI18n({
  globalInjection: true,
  legacy: false,
  locale: '',
  messages: {},
});

const modules = import.meta.glob('./langs/**/*.json');

const { setSimpleLocale } = useSimpleLocale();

const localesMap = loadLocalesMapFromDir(
  /\.\/langs\/([^/]+)\/(.*)\.json$/,
  modules,
);
let loadMessages: LoadMessageFn;

/**
 * 已加载语种词条集合（避免重复 setLocaleMessage）。
 *
 * @remarks
 * F2：记录已通过 {@link loadLocaleMessages} / {@link preloadLocaleOnIdle}
 * 写入词条的语种，防止空闲预加载重复拉取。
 */
const loadedLocales = new Set<SupportedLanguagesType>();

/**
 * Load locale modules
 * @param modules
 */
function loadLocalesMap(modules: Record<string, () => Promise<unknown>>) {
  const localesMap: Record<Locale, ImportLocaleFn> = {};

  for (const [path, loadLocale] of Object.entries(modules)) {
    const key = path.match(/([\w-]*)\.(json)/)?.[1];
    if (key) {
      localesMap[key] = loadLocale as ImportLocaleFn;
    }
  }
  return localesMap;
}

/**
 * Load locale modules with directory structure
 * @param regexp - Regular expression to match language and file names
 * @param modules - The modules object containing paths and import functions
 * @returns A map of locales to their corresponding import functions
 */
function loadLocalesMapFromDir(
  regexp: RegExp,
  modules: Record<string, () => Promise<unknown>>,
): Record<Locale, ImportLocaleFn> {
  const localesRaw: Record<Locale, Record<string, () => Promise<unknown>>> = {};
  const localesMap: Record<Locale, ImportLocaleFn> = {};

  // Iterate over the modules to extract language and file names
  for (const path in modules) {
    const match = path.match(regexp);
    if (match) {
      const [_, locale, fileName] = match;
      if (locale && fileName) {
        if (!localesRaw[locale]) {
          localesRaw[locale] = {};
        }
        if (modules[path]) {
          localesRaw[locale][fileName] = modules[path];
        }
      }
    }
  }

  // Convert raw locale data into async import functions
  for (const [locale, files] of Object.entries(localesRaw)) {
    localesMap[locale] = async () => {
      const messages: Record<string, any> = {};
      for (const [fileName, importFn] of Object.entries(files)) {
        messages[fileName] = ((await importFn()) as any)?.default;
      }
      return { default: messages };
    };
  }

  return localesMap;
}

/**
 * Set i18n language
 * @param locale
 */
function setI18nLanguage(locale: Locale) {
  i18n.global.locale.value = locale;

  document?.querySelector('html')?.setAttribute('lang', locale);
}

async function setupI18n(app: App, options: LocaleSetupOptions = {}) {
  const { defaultLocale = 'zh-CN' } = options;
  // app可以自行扩展一些第三方库和组件库的国际化
  loadMessages = options.loadMessages || (async () => ({}));
  app.use(i18n);
  await loadLocaleMessages(defaultLocale);

  // 在控制台打印警告
  i18n.global.setMissingHandler((locale, key) => {
    if (options.missingWarn && key.includes('.')) {
      console.warn(
        `[intlify] Not found '${key}' key in '${locale}' locale messages.`,
      );
    }
  });
}

/**
 * Load locale messages
 * @param lang
 */
async function loadLocaleMessages(lang: SupportedLanguagesType) {
  if (unref(i18n.global.locale) === lang && loadedLocales.has(lang)) {
    return setI18nLanguage(lang);
  }
  setSimpleLocale(lang);

  const message = await localesMap[lang]?.();

  if (message?.default) {
    i18n.global.setLocaleMessage(lang, message.default);
  }

  const mergeMessage = await loadMessages(lang);
  i18n.global.mergeLocaleMessage(lang, mergeMessage);

  loadedLocales.add(lang);
  return setI18nLanguage(lang);
}

/**
 * 构建命名空间粒度的语言包映射表。
 *
 * @remarks
 * F2 国际化按需加载改造：与 {@link loadLocalesMapFromDir} 不同，
 * 此函数保留 `locale → namespace → 加载函数` 的两级结构，
 * 供 {@link loadNamespaceMessages} 按路由声明的命名空间按需加载。
 *
 * @param regexp - 匹配 `locale` 与 `fileName` 的正则（需两个捕获组）
 * @param modules - `import.meta.glob` 产出的模块映射
 * @returns 命名空间粒度语言包映射表
 */
function loadNamespacedLocalesMap(
  regexp: RegExp,
  modules: Record<string, () => Promise<unknown>>,
): NamespacedLocalesMap {
  const map: NamespacedLocalesMap = {} as NamespacedLocalesMap;
  for (const path in modules) {
    const match = path.match(regexp);
    if (match) {
      const [, locale, fileName] = match;
      if (locale && fileName) {
        if (!map[locale as SupportedLanguagesType]) {
          map[locale as SupportedLanguagesType] = {};
        }
        map[locale as SupportedLanguagesType][fileName] =
          modules[path] as ImportNamespaceFn;
      }
    }
  }
  return map;
}

/**
 * 按命名空间按需加载语言包并合并入 i18n。
 *
 * @remarks
 * F2：路由级按需加载入口。路由可在进入前声明所需命名空间
 * （如 `['page', 'business']`），仅拉取对应 JSON 分包，
 * 避免一次性加载全部命名空间。重复加载同一命名空间会被忽略。
 *
 * @param lang - 目标语种
 * @param namespaces - 需加载的命名空间列表（如 `['common', 'page']`）
 * @param map - 由 {@link loadNamespacedLocalesMap} 构建的映射表
 */
async function loadNamespaceMessages(
  lang: SupportedLanguagesType,
  namespaces: string[],
  map: NamespacedLocalesMap,
): Promise<void> {
  const localeMap = map[lang];
  if (!localeMap) return;

  const loadedKey = `${lang}::${namespaces.slice().sort().join(',')}`;
  if (loadedNamespaceSets.has(loadedKey)) return;

  const entries = await Promise.all(
    namespaces
      .filter((ns) => typeof localeMap[ns] === 'function')
      .map(async (ns) => {
        const mod = await localeMap[ns]();
        return [ns, mod?.default ?? {}] as const;
      }),
  );

  const merged: Record<string, unknown> = {};
  for (const [ns, messages] of entries) {
    merged[ns] = messages;
  }

  if (Object.keys(merged).length > 0) {
    i18n.global.mergeLocaleMessage(lang, merged);
  }
  loadedNamespaceSets.add(loadedKey);
}

/**
 * 已加载的「语种::命名空间集合」缓存键，避免路由重复进入时重复加载。
 */
const loadedNamespaceSets = new Set<string>();

/**
 * 浏览器空闲时预加载非默认语种词条。
 *
 * @remarks
 * F2：首屏完成后，利用 `requestIdleCallback` 在空闲时段预拉取
 * 另一语种的全部命名空间并写入 i18n（不切换当前语种），
 * 使后续用户切换语种近似瞬时完成。已加载的语种会被跳过。
 *
 * 不支持 `requestIdleCallback` 的环境回退为 `setTimeout`。
 *
 * @param lang - 待预加载的语种
 * @param targetLocalesMap - 语种 → 加载函数映射（通常即 `loadLocalesMapFromDir` 产出）
 * @param options - 预加载可选配置
 */
function preloadLocaleOnIdle(
  lang: SupportedLanguagesType,
  targetLocalesMap: Record<string, ImportLocaleFn>,
  options: PreloadLocaleOptions = {},
): void {
  const { timeout = 4000, onLoaded, onError } = options;

  if (loadedLocales.has(lang)) {
    onLoaded?.();
    return;
  }

  const run = (): void => {
    if (loadedLocales.has(lang)) {
      onLoaded?.();
      return;
    }
    const loader = targetLocalesMap[lang];
    if (typeof loader !== 'function') {
      onLoaded?.();
      return;
    }
    loader()
      .then((msg) => {
        if (msg?.default) {
          i18n.global.setLocaleMessage(lang, msg.default);
          loadedLocales.add(lang);
        }
        onLoaded?.();
      })
      .catch((err) => {
        console.warn(`[i18n] Failed to preload locale ${lang}:`, err);
        onError?.(err);
      });
  };

  // requestIdleCallback 类型在部分 TS lib 下可能缺失，做兼容判定
  const ric =
    typeof globalThis !== 'undefined' &&
    typeof (globalThis as { requestIdleCallback?: unknown }).requestIdleCallback ===
      'function'
      ? (globalThis as { requestIdleCallback: typeof requestIdleCallback })
          .requestIdleCallback
      : null;
  if (ric) {
    ric(run, { timeout });
  } else {
    setTimeout(run, timeout);
  }
}

export {
  i18n,
  loadLocaleMessages,
  loadLocalesMap,
  loadLocalesMapFromDir,
  loadNamespaceMessages,
  loadNamespacedLocalesMap,
  preloadLocaleOnIdle,
  setupI18n,
};
