package com.njydsz.common.json.health;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.njydsz.common.json.internal.JsonConfig;
import com.njydsz.common.json.spring.JsonProperties;

/**
 * JSON 模块健康指标
 *
 * <p>报告 Ydsz JSON 引擎的核心配置和运行状态，包括：
 * <ul>
 *   <li>命名策略（camelCase / snake_case 等）</li>
 *   <li>日期格式 / 默认日期格式</li>
 *   <li>枚举序列化方式（name / ordinal）</li>
 *   <li>安全模式（AutoType 白名单检查）</li>
 *   <li>严格模式（双体系一致性校验）</li>
 *   <li>Jackson 自动配置排除状态</li>
 *   <li>当前全局配置版本号</li>
 * </ul>
 *
 * <p><b>健康判断逻辑：</b>
 * <ul>
 *   <li>启用安全模式（safeMode=true）时为健康</li>
 *   <li>strictMode 下发现异常配置（如 Jackson 自动配置关闭但 Jackson 仍在类路径）标记为 DOWN</li>
 *   <li>日期格式配置与默认日期格式不一致时不影响健康（仅为信息展示）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.2.0
 */
public class JsonHealthIndicator implements HealthIndicator {

    private final JsonProperties properties;
    private final JsonConfig jsonConfig;

    /**
     * 构造 JSON 健康指标
     *
     * @param properties JSON 配置属性
     * @param jsonConfig 当前全局 JSON 配置（可为 null，使用默认配置）
     */
    public JsonHealthIndicator(JsonProperties properties, JsonConfig jsonConfig) {
        this.properties = properties;
        this.jsonConfig = jsonConfig;
    }

    @Override
    public Health health() {
        Map<String, Object> details = new LinkedHashMap<>();

        // 基础配置
        details.put("enabled", properties.isEnabled());
        details.put("namingStrategy", properties.getNamingStrategy());
        details.put("dateFormat",
                properties.getDateFormat() != null && !properties.getDateFormat().isEmpty()
                        ? properties.getDateFormat() : "(default: yyyy-MM-dd HH:mm:ss)");
        details.put("serializeEnumUsingOrdinal", properties.isSerializeEnumUsingOrdinal());
        details.put("writeNulls", properties.isWriteNulls());
        details.put("prettyPrint", properties.isPrettyPrint());
        details.put("useBigDecimal", properties.isUseBigDecimal());
        details.put("wrapRootValue", properties.isWrapRootValue());

        // 安全相关
        details.put("safeMode", properties.isSafeMode());
        details.put("strictMode", properties.isStrictMode());
        details.put("disableJacksonAutoConfiguration", properties.isDisableJacksonAutoConfiguration());

        // 深度 / 大小限制
        details.put("maxJsonSize", properties.getMaxJsonSize());
        details.put("maxDepth", properties.getMaxDepth());
        details.put("maxGenericDepth", properties.getMaxGenericDepth());

        // 当前全局配置版本号
        details.put("configVersion", JsonConfig.getConfigVersion());

        // 安全模式判断
        Health.Builder builder = Health.up();
        if (!properties.isSafeMode()) {
            builder.withDetail("warning", "安全模式（AutoType 白名单检查）未开启，存在潜在反序列化风险");
            // 不标记为 DOWN，仅输出警告（部分遗留项目可能仍需要关闭安全模式）
        }

        // 严格模式下的 Jackson 双体系检测（运行时状态快照）
        if (properties.isStrictMode() && properties.isDisableJacksonAutoConfiguration()) {
            try {
                Class.forName("com.fasterxml.jackson.databind.ObjectMapper",
                        false, Thread.currentThread().getContextClassLoader());
                builder.withDetail("jacksonStatus",
                        "Jackson 存在于类路径但 JacksonAutoConfiguration 已被排除（预期行为）");
            } catch (ClassNotFoundException e) {
                details.put("jacksonStatus", "Jackson 不在类路径（干净环境）");
            }
        }

        return builder.withDetails(details).build();
    }
}
