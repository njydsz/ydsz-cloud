/**
 * typing 国际化语言包
 *
 * @path comm\locales\src\typing.ts
 * @author ydsz-team
 * @since 1.0.0
 */
export type SupportedLanguagesType = 'en-US' | 'zh-CN';

/**
 * 语言包的动态导入函数。
 *
 * @remarks
 * 通常由 `import.meta.glob` 生成，返回值必须是带 `default` 导出的模块对象，
 * 因此语言包文件需以 `export default` 的形式导出扁平化的 key-value 词条表。
 * 采用函数（而非直接导入）是为了让各语种独立分包、按需加载。
 */
export type ImportLocaleFn = () => Promise<{ default: Record<string, string> }>;

/**
 * 单个命名空间语言包的动态导入函数。
 *
 * @remarks
 * F2 国际化按需加载改造：以命名空间（如 `common`/`authentication`/`page`）
 * 为粒度拆分语言包，使路由可声明所需命名空间并按需加载，
 * 避免切换语种时一次性拉取全部命名空间。
 */
export type ImportNamespaceFn = () => Promise<{ default: Record<string, unknown> }>;

/**
 * 命名空间粒度的语言包映射表：`locale → namespace → 加载函数`。
 *
 * @remarks
 * 由 {@link loadNamespacedLocalesMap} 构建，配合 {@link loadNamespaceMessages}
 * 实现路由级按需加载。
 */
export type NamespacedLocalesMap = Record<
  SupportedLanguagesType,
  Record<string, ImportNamespaceFn>
>;

/**
 * 空闲预加载语言包的可选配置。
 *
 * @remarks
 * 供 {@link preloadLocaleOnIdle} 使用；浏览器空闲时预拉取非默认语种词条，
 * 使后续切换语种近似瞬时完成。
 */
export interface PreloadLocaleOptions {
  /** requestIdleCallback 的 timeout 上限（毫秒），默认 4000 */
  timeout?: number;
  /** 预加载完成回调（无论是否命中缓存） */
  onLoaded?: () => void;
  /** 预加载失败回调 */
  onError?: (err: unknown) => void;
}

/**
 * 加载**业务侧**语言包的钩子函数。
 *
 * @remarks
 * 由应用层实现并通过 {@link LocaleSetupOptions.loadMessages} 注入，
 * 用于在 comm 层内置文案之外补充各 app 自己的词条；返回的词条会与内置词条合并。
 * 允许返回 `undefined` 表示该语种没有额外词条（不视为错误）。
 */
export type LoadMessageFn = (
  lang: SupportedLanguagesType,
) => Promise<Record<string, string> | undefined>;

/**
 * 国际化初始化配置。
 */
export interface LocaleSetupOptions {
  /**
   * Default language
   * @default zh-CN
   */
  defaultLocale?: SupportedLanguagesType;
  /**
   * 业务侧语言包加载函数；缺省时只加载 comm 层内置词条
   */
  loadMessages?: LoadMessageFn;
  /**
   * 词条缺失时是否在控制台告警；生产环境建议关闭以免刷屏
   */
  missingWarn?: boolean;
}
