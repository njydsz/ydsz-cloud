/**
 * ydsz-common-locales 工具层：i18n 与 Locale 上下文工具
 *
 * <p>包含：
 *
 * <ul>
 *   <li>{@link com.njydsz.common.locales.util.I18n} — 静态消息解析工具（供异常/DTO/工具类无注入场景使用）
 *   <li>{@link com.njydsz.common.locales.util.I18nMessages} — 可注入 Bean（Service/Controller 推荐）
 *   <li>{@link com.njydsz.common.locales.util.Locales} — Locale 上下文工具（含 withLocale 临时切换）
 *   <li>{@link com.njydsz.common.locales.util.MessageSourceHolder} — 静态桥接（异常体系消费 i18n 的核心入口）
 * </ul>
 *
 * @since 26.09.18
 */
package com.njydsz.common.locales.util;
