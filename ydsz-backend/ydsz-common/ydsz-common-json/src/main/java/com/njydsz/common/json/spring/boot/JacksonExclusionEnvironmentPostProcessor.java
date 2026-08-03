package com.njydsz.common.json.spring.boot;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

/**
 * 按需排除 Spring Boot Jackson 自动配置。
 *
 * <p>当 {@code ydsz.json.disable-jackson-auto-configuration=true} 时，
 * 将 {@code org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration}
 * 加入 {@code spring.autoconfigure.exclude}，使 Spring 容器不再注册 {@code ObjectMapper} Bean。
 *
 * <p>EnvironmentPostProcessor 在 Spring Boot 启动早期执行，
 * 此时 {@code @ConfigurationProperties} 尚未绑定，因此直接从 {@link Environment} 读取原始属性值。
 *
 * <p>实现方式：通过添加高优先级 {@link MapPropertySource} 覆盖
 * {@code spring.autoconfigure.exclude} 属性，合并已有值与 Jackson 排除项。
 *
 * <p><b>风险提示：</b>禁用 Jackson 自动配置后，依赖 {@code ObjectMapper} 的 Spring 内部组件
 * （Actuator 部分端点、Spring Data Redis 默认序列化器等）可能降级或需额外适配。
 * 建议仅在确认无 Jackson 依赖后启用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class JacksonExclusionEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String PROPERTY_NAME = "ydsz.json.disable-jackson-auto-configuration";
    private static final String EXCLUDE_PROPERTY = "spring.autoconfigure.exclude";
    private static final String JACKSON_AUTO_CONFIGURATION =
            "org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration";
    private static final String PROPERTY_SOURCE_NAME = "ydszJsonJacksonExclusion";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!Boolean.TRUE.equals(environment.getProperty(PROPERTY_NAME, Boolean.class, Boolean.FALSE))) {
            return;
        }

        // 读取已有的 spring.autoconfigure.exclude 值（可能来自 application.yml / 命令行参数等）
        String existing = environment.getProperty(EXCLUDE_PROPERTY, "");
        Set<String> excludes = new LinkedHashSet<>();
        if (StringUtils.hasText(existing)) {
            Collections.addAll(excludes, existing.split(","));
        }
        excludes.add(JACKSON_AUTO_CONFIGURATION);

        // 通过高优先级 PropertySource 覆盖 spring.autoconfigure.exclude
        String merged = String.join(",", excludes);
        environment.getPropertySources()
                .addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME,
                        Collections.singletonMap(EXCLUDE_PROPERTY, merged)));
    }
}
