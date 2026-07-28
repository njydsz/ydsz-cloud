/**
 * YDSZ Feature Toggle 注解
 *
 * P2-5: 灰度发布与特性开关
 * 配合 Nacos 配置中心实现动态特性开关
 *
 * @author ydsz-team
 * @since 1.0.0
 */
package com.njydsz.common.core.featureflag;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在 Controller 方法或 Service 方法上，当特性开关关闭时：
 * - Controller 层：返回 404 Not Found
 * - Service 层：返回 null（或默认值）
 *
 * <p>使用示例：
 * <pre>{@code
 * @FeatureToggle("NEW_DASHBOARD")
 * @GetMapping("/dashboard/v2")
 * public BaseResponse<DashboardVO> getDashboardV2() {
 *     return BaseResponse.success(dashboardService.getDashboardV2());
 * }
 * }</pre>
 *
 * <p>Nacos 配置示例：
 * <pre>{@code
 * ydsz:
 *   feature-flag:
 *     nacos:
 *       enabled: true
 *     flags:
 *       NEW_DASHBOARD:
 *         enabled: true
 *       BATCH_EXPORT:
 *         enabled: false
 * }</pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface FeatureToggle {

    /**
     * 特性开关名称（对应 Nacos 配置中的 key）
     */
    String value();

    /**
     * 开关关闭时的默认行为（Controller 层返回 404，Service 层返回 null）
     */
    ToggleBehavior behavior() default ToggleBehavior.NOT_FOUND;

    /**
     * 开关行为枚举
     */
    enum ToggleBehavior {
        /** 返回 404 Not Found（适用于 Controller 层） */
        NOT_FOUND,
        /** 返回 null（适用于 Service 层） */
        RETURN_NULL,
        /** 抛出异常 */
        THROW_EXCEPTION
    }
}
