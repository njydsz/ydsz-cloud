package com.njydsz.common.base.config;

/**
 * 国际化配置基类（占位存根）。
 *
 * <p>自 26.09.29 起，本类所有 Bean（messageSource / localeResolver / messageResolverRegistry）均已移除——国际化
 * 基础设施统一由 {@code ydsz-common-locales} 的
 * {@link com.njydsz.common.locales.config.LocalesAutoConfiguration} 提供（支持负缓存、缺失节流、运行时覆盖、
 * user-priority Locale 解析等增强能力）。
 *
 * <p>本类保留为抽象空壳，防止外部子类（ydsz-common-app 的 AppI18nConfiguration /
 * ydsz-common-web 的 WebI18nConfiguration）编译断裂——这两个子类已在本次清理中一并移除。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @deprecated 自 26.09.29 起弃用。国际化配置请使用 LocalesAutoConfiguration；业务代码直接使用
 *     {@link com.njydsz.common.locales.util.I18n} /
 *     {@link com.njydsz.common.locales.util.I18nMessages}。
 */
@Deprecated
public abstract class BaseI18nConfiguration {
  // 空壳存根——所有 Bean 方法已迁移至 LocalesAutoConfiguration
}
