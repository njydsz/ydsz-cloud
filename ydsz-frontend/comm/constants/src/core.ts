/**
 * @zh_CN 登录页面 url 地址
 */
export const LOGIN_PATH = '/auth/login';

/**
 * 语言切换下拉项的数据结构。
 *
 * @remarks
 * `value` 与 vue-i18n 的 locale 标识保持一致，新增语种时需同步扩展
 * `SUPPORT_LANGUAGES` 与对应的语言包，否则切换后词条会全部回退为 key。
 */
export interface LanguageOption {
  /** 展示名称，按惯例使用该语言的**本族语**书写（如 `简体中文`、`English`），不参与翻译 */
  label: string;
  /** 语言标识，需与语言包目录名及 vue-i18n locale 一致 */
  value: 'en-US' | 'zh-CN';
}

/**
 * Supported languages
 */
export const SUPPORT_LANGUAGES: LanguageOption[] = [
  {
    label: '简体中文',
    value: 'zh-CN',
  },
  {
    label: 'English',
    value: 'en-US',
  },
];
