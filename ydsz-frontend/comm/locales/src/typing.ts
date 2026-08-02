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
