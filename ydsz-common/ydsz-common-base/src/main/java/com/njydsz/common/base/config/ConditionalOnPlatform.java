package com.njydsz.common.base.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Conditional;

/**
 * 平台模式条件注解。
 *
 * <p>仅在当前应用运行模式匹配指定平台时生效，用于实现 Web/App 模块隔离。
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * @Configuration
 * @ConditionalOnPlatform(PlatformMode.WEB)
 * public class WebOnlyConfiguration {
 *     // 仅在 Web 端加载的 Bean
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see PlatformMode
 * @see PlatformCondition
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Conditional(PlatformCondition.class)
public @interface ConditionalOnPlatform {

  /**
   * 要求的平台模式。
   *
   * @return 平台模式
   */
  PlatformMode value();
}
