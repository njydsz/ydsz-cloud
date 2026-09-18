/**
 * YDSZ 国际化（i18n）公共基座模块（L2 核心响应与基础设施层）
 *
 * <p>本模块提供 YDSZ 后端的国际化基础设施能力，涵盖：
 *
 * <ul>
 *   <li>MessageSource 自动装配与多模块资源聚合（LocalesAutoConfiguration）
 *   <li>静态 i18n 工具（I18n）
 *   <li>可注入工具 Bean（I18nMessages）
 *   <li>Locale 上下文工具（Locales）
 *   <li>MessageSource 静态桥接（MessageSourceHolder，供异常体系消费）
 * </ul>
 *
 * <p><b>模块层级：L2</b> —— 仅依赖 {@code ydsz-common-core} 与 {@code ydsz-common-util}（通过 core 传递）。
 * 可供异常模块（L3）、Web 基座（L6）、八大引擎业务层平等引用，不违反层级单向依赖原则。
 *
 * <p><b>使用方式：</b>在 starter pom 中引入本模块，即可自动装配所有国际化 Bean（MessageSource、LocaleResolver 等）。
 *
 * @author ydsz-team
 * @since 26.09.18
 */
package com.njydsz.common.locales;
