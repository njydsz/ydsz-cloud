package com.njydsz.common.app.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.core.annotation.AliasFor;
import org.springframework.web.bind.annotation.RestController;

/**
 * App 端 REST 控制器标记注解
 *
 * <p>用于显式声明一个控制器为「移动端 App 控制器」，使 {@code common-app} 模块的 {@link
 * com.njydsz.common.app.advice.AppGlobalResponseAdvice} 与 {@link
 * com.njydsz.common.app.exception.AppExceptionHandler} 仅对标注本注解的控制器生效， 避免与 {@code common-web} 模块的
 * Advice 在同一 Spring 上下文中产生冲突。
 *
 * <h3>背景</h3>
 *
 * <p>{@code common-app} 与 {@code common-web} 是两个平行的应用层入口，原则上不应在同一 Spring
 * 上下文中同时启用。但若误同时引入两个模块，{@code @RestControllerAdvice} 默认 会扫描全部控制器，导致响应被重复包装或异常被重复处理。
 *
 * <h3>使用方式</h3>
 *
 * <pre>{@code
 * @AppApi
 * @RequestMapping("/app/users")
 * public class AppUserController {
 *     @GetMapping("/{id}")
 *     public User getById(@PathVariable Long id) { ... }
 * }
 * }</pre>
 *
 * <p>{@code @AppApi} 是 {@link RestController} 的组合注解（包含 {@code @RestController} 语义）， 标注后即等同
 * {@code @RestController}，无需重复标注。
 *
 * <h3>Advice 触发规则</h3>
 *
 * <ul>
 *   <li>{@link com.njydsz.common.app.advice.AppGlobalResponseAdvice} — 仅处理 {@code @AppApi} 控制器的响应
 *   <li>{@link com.njydsz.common.app.exception.AppExceptionHandler} — 仅处理 {@code @AppApi} 控制器抛出的异常
 *   <li>未标注 {@code @AppApi} 的控制器走默认（{@code common-web} 或全局）Advice 链
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@RestController
public @interface AppApi {

  /**
   * {@link RestController#value()} 的别名，语义与 {@code @RestController("beanName")} 一致
   *
   * @return Spring Bean 名称（默认空，由 Spring 自动生成）
   */
  @AliasFor(annotation = RestController.class)
  String value() default "";
}
