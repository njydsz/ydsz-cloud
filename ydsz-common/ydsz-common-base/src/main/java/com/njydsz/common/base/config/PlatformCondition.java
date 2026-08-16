package com.njydsz.common.base.config;

import java.util.Arrays;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 平台模式条件判断。
 *
 * <p>根据配置属性 {@code ydsz.platform.mode} 或 classpath 自动探测，
 * 判断当前是否匹配指定的平台模式。
 *
 * <p>自动探测规则：
 * <ul>
 *   <li>classpath 中存在 {@code com.njydsz.common.web.config.WebMvcConfiguration} → WEB</li>
 *   <li>classpath 中存在 {@code com.njydsz.common.app.config.AppMvcConfiguration} → APP</li>
 *   <li>两者都存在 → 抛出异常（需显式配置）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class PlatformCondition implements Condition {

    private static final String WEB_CLASS = "com.njydsz.common.web.config.WebMvcConfiguration";
    private static final String APP_CLASS = "com.njydsz.common.app.config.AppMvcConfiguration";

    private final PlatformMode targetMode;

    public PlatformCondition(PlatformMode targetMode) {
        this.targetMode = targetMode;
    }

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String modeProperty = context.getEnvironment().getProperty("ydsz.platform.mode");

        // 优先使用显式配置
        if (modeProperty != null && !modeProperty.isBlank()) {
            try {
                PlatformMode configured = PlatformMode.valueOf(modeProperty.toUpperCase());
                return configured == targetMode;
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException(
                        "无效的平台模式配置: ydsz.platform.mode=" + modeProperty
                                + "，可选值: " + Arrays.toString(PlatformMode.values()));
            }
        }

        // 自动探测
        boolean webPresent = isClassPresent(WEB_CLASS);
        boolean appPresent = isClassPresent(APP_CLASS);

        if (webPresent && appPresent) {
            // 两者都存在时，使用默认值并记录警告
            return targetMode == PlatformMode.DEFAULT;
        }

        if (webPresent) {
            return targetMode == PlatformMode.WEB;
        }

        if (appPresent) {
            return targetMode == PlatformMode.APP;
        }

        // 两者都不存在时，使用默认值
        return targetMode == PlatformMode.DEFAULT;
    }

    private boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, getClass().getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
