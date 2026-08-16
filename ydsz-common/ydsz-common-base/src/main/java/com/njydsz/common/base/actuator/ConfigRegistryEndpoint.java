package com.njydsz.common.base.actuator;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.jspecify.annotations.NonNull;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;

import lombok.extern.slf4j.Slf4j;

/**
 * 配置注册端点 — 暴露运行时配置信息。
 *
 * <p>通过 Actuator 端点暴露所有 ydsz.* 前缀的配置项，
 * 便于运维人员在线排查配置问题。
 *
 * <h3>端点信息</h3>
 * <ul>
 *   <li>{@code GET /actuator/config-registry} — 查看所有 ydsz.* 配置</li>
 *   <li>{@code GET /actuator/config-registry/{prefix}} — 查看指定前缀下的配置</li>
 * </ul>
 *
 * <h3>安全说明</h3>
 * <ul>
 *   <li>敏感属性（password/secret/token/key/cert/private）自动脱敏为 {@code ******}</li>
 *   <li>生产环境应通过 {@code management.endpoint.config-registry.exposure} 控制访问权限</li>
 *   <li>建议仅限内网访问</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Endpoint(id = "config-registry")
public class ConfigRegistryEndpoint {

    private static final String YDSZ_PREFIX = "ydsz.";

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "secret", "token", "key", "cert", "private", "credential", "auth"
    );

    private static final String MASKED_VALUE = "******";

    private final Environment environment;

    public ConfigRegistryEndpoint(Environment environment) {
        this.environment = environment;
    }

    /**
     * 读取所有 ydsz.* 前缀的配置项。
     *
     * @return 配置项 Map（按 key 排序，敏感值已脱敏）
     */
    @ReadOperation
    public Map<String, Object> readConfig() {
        Map<String, Object> result = new TreeMap<>();

        if (environment instanceof ConfigurableEnvironment configurableEnv) {
            for (PropertySource<?> propertySource : configurableEnv.getPropertySources()) {
                if (propertySource.getSource() instanceof Map<?, ?> sourceMap) {
                    for (Map.Entry<?, ?> entry : sourceMap.entrySet()) {
                        String key = String.valueOf(entry.getKey());
                        if (key.startsWith(YDSZ_PREFIX)) {
                            result.putIfAbsent(key, maskIfSensitive(key, entry.getValue()));
                        }
                    }
                }
            }
        }

        return result;
    }

    /**
     * 读取指定前缀下的配置项。
     *
     * @param prefix 配置前缀（如 "ydsz.audit"）
     * @return 匹配的配置项 Map（敏感值已脱敏）
     */
    @ReadOperation
    public Map<String, Object> readConfigByPrefix(@Selector String prefix) {
        String fullPrefix = prefix.startsWith(YDSZ_PREFIX) ? prefix : YDSZ_PREFIX + prefix;
        Map<String, Object> result = new TreeMap<>();

        if (environment instanceof ConfigurableEnvironment configurableEnv) {
            for (PropertySource<?> propertySource : configurableEnv.getPropertySources()) {
                if (propertySource.getSource() instanceof Map<?, ?> sourceMap) {
                    for (Map.Entry<?, ?> entry : sourceMap.entrySet()) {
                        String key = String.valueOf(entry.getKey());
                        if (key.startsWith(fullPrefix + ".") || key.equals(fullPrefix)) {
                            result.putIfAbsent(key, maskIfSensitive(key, entry.getValue()));
                        }
                    }
                }
            }
        }

        return result;
    }

    /**
     * 判断属性名是否包含敏感关键词，如果是则返回脱敏值。
     *
     * @param key   属性名
     * @param value 原始值
     * @return 脱敏后的值或原始值
     */
    private Object maskIfSensitive(@NonNull String key, Object value) {
        String lowerKey = key.toLowerCase();
        for (String sensitive : SENSITIVE_KEYS) {
            if (lowerKey.contains(sensitive)) {
                return MASKED_VALUE;
            }
        }
        return value;
    }
}
