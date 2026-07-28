package com.njydsz.common.core.featureflag;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link FeatureToggle} 注解 AOP 切面
 *
 * <p>拦截标注了 {@code @FeatureToggle} 的 Controller 方法或 Service 方法，
 * 当特性开关关闭时根据 {@link FeatureToggle.ToggleBehavior} 执行降级逻辑：
 * <ul>
 *   <li>{@code NOT_FOUND} — 抛出 {@link FeatureNotEnabledException}（Controller 层可映射为 404）</li>
 *   <li>{@code RETURN_NULL} — 返回 null</li>
 *   <li>{@code THROW_EXCEPTION} — 抛出 {@link FeatureNotEnabledException}</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * @FeatureToggle("NEW_DASHBOARD")
 * @GetMapping("/dashboard/v2")
 * public BaseResponse<DashboardVO> getDashboardV2() {
 *     return BaseResponse.success(dashboardService.getDashboardV2());
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see FeatureToggle
 * @see FeatureFlagService
 */
@Aspect
public class FeatureToggleAspect {

    private static final Logger log = LoggerFactory.getLogger(FeatureToggleAspect.class);

    private final FeatureFlagService featureFlagService;

    /**
     * 创建 FeatureToggleAspect 实例
     *
     * @param featureFlagService 特性开关服务
     */
    public FeatureToggleAspect(FeatureFlagService featureFlagService) {
        this.featureFlagService = featureFlagService;
    }

    /**
     * 环绕通知：拦截 @FeatureToggle 注解方法
     *
     * @param pjp            连接点
     * @param featureToggle 注解实例
     * @return 方法返回值（特性关闭时根据 behavior 返回 null 或抛异常）
     * @throws Throwable 方法执行异常或特性关闭异常
     */
    @Around("@annotation(featureToggle)")
    public Object around(ProceedingJoinPoint pjp, FeatureToggle featureToggle) throws Throwable {
        String featureName = featureToggle.value();
        boolean enabled = featureFlagService.isEnabled(featureName);

        if (enabled) {
            return pjp.proceed();
        }

        log.info("[FeatureToggle] 特性开关 '{}' 已关闭，执行降级: method={}", featureName, pjp.getSignature().toShortString());

        FeatureToggle.ToggleBehavior behavior = featureToggle.behavior();
        switch (behavior) {
            case RETURN_NULL -> {
                return null;
            }
            case NOT_FOUND, THROW_EXCEPTION -> throw new FeatureNotEnabledException(featureName);
            default -> throw new FeatureNotEnabledException(featureName);
        }
    }

    /**
     * 特性未启用异常
     *
     * <p>当 {@link FeatureToggle} 注解的行为为 {@code NOT_FOUND} 或 {@code THROW_EXCEPTION} 时抛出。
     * Controller 层的全局异常处理器可将此异常映射为 HTTP 404 或业务错误码。
     */
    public static class FeatureNotEnabledException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final String featureName;

        /**
         * 创建特性未启用异常
         *
         * @param featureName 特性名称
         */
        public FeatureNotEnabledException(String featureName) {
            super("Feature not enabled: " + featureName);
            this.featureName = featureName;
        }

        /**
         * 获取特性名称
         *
         * @return 特性名称
         */
        public String getFeatureName() {
            return featureName;
        }
    }
}
