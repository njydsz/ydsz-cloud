package com.njydsz.common.locales.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Import;

/**
 * 显式启用 YDSZ 国际化基座（L2 基础设施）
 *
 * <p>标注本注解后，通过 {@link Import} 导入 {@link LocalesAutoConfiguration}，激活国际化基础设施：
 *
 * <ul>
 *   <li>{@code ydszMessageSource} — MessageSource（多模块资源聚合 + 通配符自动扫描）
 *   <li>{@code ydszLocaleResolver} — LocaleResolver（accept-header / user-priority）
 *   <li>{@code localeChangeInterceptor} — {@code ?lang=} 参数切换 Locale
 *   <li>{@code ydszValidator} — JSR-303 校验器的 i18n 消息源
 *   <li>{@code i18nMessages} — 可注入的 i18n 消息工具 Bean
 * </ul>
 *
 * <p><b>默认情况下无需使用此注解：</b>引入 {@code ydsz-common-locales} 依赖后，Spring Boot 自动装配机制（{@code
 * META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}）已注册 {@link
 * LocalesAutoConfiguration}，本模块的所有 Bean 会在符合条件时自动创建。
 *
 * <p>推荐在以下场景显式标注：
 *
 * <ul>
 *   <li>业务启动类需要明确表达"本服务已启用 i18n"的意图（IDE 搜索友好）
 *   <li>开发者想快速定位国际化基座入口（跳转注解 → 自动配置 → 工具类）
 *   <li>团队编码规范要求显式声明基础设施模块
 * </ul>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * &#64;SpringBootApplication
 * &#64;EnableYdszI18n
 * public class Application {
 *     public static void main(String[] args) {
 *         SpringApplication.run(Application.class, args);
 *     }
 * }
 * }</pre>
 *
 * <p><b>等价效果：</b>{@code @Import(LocalesAutoConfiguration.class)}
 *
 * @author ydsz-team
 * @since 26.09.19
 * @see LocalesAutoConfiguration
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Import(LocalesAutoConfiguration.class)
public @interface EnableYdszI18n {
}
