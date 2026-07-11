package com.njydsz.pmis.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 敏感操作二次认证注解
 *
 * <p>标注在敏感 Controller 方法上：
 * <ul>
 *   <li>需要请求头携带 {@code X-Re-Auth-Token}</li>
 *   <li>token 在指定时间窗口（默认 5 分钟）内有效</li>
 *   <li>由 SensitiveOperationAspect 拦截校验</li>
 * </ul>
 *
 * <p>典型场景：删除项目、调整合同金额、批量改密、薪酬变更等。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireReAuth {

    /**
     * 操作编码（用于 Redis 中二次认证 token 的 key 维度区分）
     * <p>示例：{@code "project:delete"}、{@code "contract:amount:adjust"}
     */
    String code();

    /**
     * 操作名称（用于审计日志展示与前端弹窗提示）
     * <p>示例：{@code "删除项目"}、{@code "调整合同金额"}
     */
    String name();

    /**
     * 二次认证 token 有效时间（秒）
     * <p>默认 300 秒（5 分钟），超时后需重新认证
     */
    int ttlSeconds() default 300;
}
