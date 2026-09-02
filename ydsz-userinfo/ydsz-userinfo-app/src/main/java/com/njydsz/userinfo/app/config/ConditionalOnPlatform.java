package com.njydsz.userinfo.app.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Conditional;

/**
 * 平台维度条件注解（P1-2 双入口架构）。
 *
 * <p>根据 {@code ydssz.userinfo.platform} 配置值决定是否激活当前 Bean：
 *
 * <ul>
 *   <li>{@code web} — 仅当平台为 Web 时激活（管理后台 REST API）</li>
 *   <li>{@code app} — 仅当平台为 App 时激活（移动端/应用端入口）</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * @Configuration
 * @ConditionalOnPlatform("app")
 * public class AppAutoConfiguration {
 *     // 仅在 App 端加载的组件
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Conditional(PlatformCondition.class)
public @interface ConditionalOnPlatform {

  /**
   * 期望的平台标识。
   *
   * @return {@code "web"} 或 {@code "app"}
   */
  String value();
}
