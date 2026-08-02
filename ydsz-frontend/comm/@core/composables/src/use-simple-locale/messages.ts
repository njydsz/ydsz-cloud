/**
 * messages 组合式函数
 *
 * @path comm\@core\composables\src\use-simple-locale\messages.ts
 * @author ydsz-team
 * @since 1.0.0
 */
export type Locale = 'en-US' | 'zh-CN';

export const messages: Record<Locale, Record<string, string>> = {
  'en-US': {
    cancel: 'Cancel',
    collapse: 'Collapse',
    confirm: 'Confirm',
    expand: 'Expand',
    prompt: 'Prompt',
    reset: 'Reset',
    submit: 'Submit',
  },
  'zh-CN': {
    cancel: '取消',
    collapse: '收起',
    confirm: '确认',
    expand: '展开',
    prompt: '提示',
    reset: '重置',
    submit: '提交',
  },
};

/**
 * 按语言标识取出对应的内置文案字典。
 *
 * @remarks
 * 这是 `@core` 内部组件（表单按钮、弹窗确认/取消等）的**极简兜底 i18n**，
 * 与业务层的完整 i18n 方案相互独立，目的是让基础组件在宿主未接入 i18n 时也能正常显示中文/英文，
 * 因此只覆盖极少量通用词条，**不要**把业务文案往这里加。
 *
 * 返回的是字典对象的**引用而非副本**，调用方修改返回值会污染全局 {@link messages}，
 * 需要定制文案请在外层做浅拷贝合并。
 *
 * 入参被类型收窄为 {@link Locale}，传入未支持的语言在编译期即报错；
 * 但若通过 `as` 绕过类型检查传入未知值，会返回 `undefined` 而非降级到中文，
 * 上层取词条时将抛出「读取 undefined 属性」错误。
 *
 * @param locale - 目标语言标识，当前仅支持 `'zh-CN'` 与 `'en-US'`
 * @returns 该语言下的「词条 key → 文案」映射表
 */
export const getMessages = (locale: Locale) => messages[locale];
