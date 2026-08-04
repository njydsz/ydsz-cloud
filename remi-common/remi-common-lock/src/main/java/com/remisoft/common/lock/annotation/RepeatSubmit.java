package com.remisoft.common.lock.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 表单重复提交防护注解（Token 令牌模式）
 *
 * <p>与 {@link Idempotent} 的区别：
 * <ul>
 *   <li>{@link Idempotent}：服务端去重，基于请求参数摘要，适用于接口幂等性</li>
 *   <li>{@link RepeatSubmit}：表单 Token 模式，前端先获取 token 再提交，适用于表单重复提交防护</li>
 * </ul>
 *
 * <p><b>使用流程：</b>
 * <ol>
 *   <li>前端调用 {@code GET /repeat-submit/token} 获取 token</li>
 *   <li>前端提交表单时携带 {@code X-Repeat-Token} 请求头</li>
 *   <li>后端校验 token 有效性，成功后删除 token（一次性使用）</li>
 * </ol>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * @RepeatSubmit(interval = 3000, message = "请勿重复提交")
 * @PostMapping("/orders")
 * public Result<Order> createOrder(@RequestBody OrderDTO dto) { ... }
 * }</pre>
 *
 * @author remi-team
 * @since 1.0.0
 * @see Idempotent
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RepeatSubmit {

    /**
     * 防重复提交时间窗口（毫秒）
     *
     * <p>同一用户在此时间窗口内只能提交一次。
     * 默认 3000ms（3 秒），覆盖大部分快速双击场景。
     *
     * @return 时间窗口毫秒数
     */
    long interval() default 3000;

    /**
     * 重复提交时的提示信息
     *
     * @return 提示信息
     */
    String message() default "请勿重复提交";

    /**
     * Token 在请求头中的名称
     *
     * @return 请求头名称
     */
    String headerName() default "X-Repeat-Token";
}
