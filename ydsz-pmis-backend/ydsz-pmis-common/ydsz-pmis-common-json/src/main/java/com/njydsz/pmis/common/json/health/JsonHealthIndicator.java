package com.njydsz.pmis.common.json.health;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.njydsz.pmis.common.json.autotype.AutoTypeChecker;
import com.njydsz.pmis.common.json.cache.JsonCacheStats;
import com.njydsz.pmis.common.json.config.JsonConfig;

/**
 * Json 健康检查指标。
 *
 * <p>检查 Json 引擎的运行状态：
 * <ul>
 *   <li>AutoType SafeMode 是否开启</li>
 *   <li>ASM 类阈值配置</li>
 *   <li>最大 JSON 大小限制</li>
 *   <li>最大序列化深度</li>
 * </ul>
 *
 * @since 1.0.0
 */
public class JsonHealthIndicator implements HealthIndicator {

    @Override
    public Health health() {
        JsonConfig config = JsonConfig.getInstance();
        boolean safeMode = AutoTypeChecker.isSafeMode();

        Health.Builder builder = safeMode ? Health.up() : Health.down();
        builder.withDetail("safeMode", safeMode);
        builder.withDetail("asmThreshold", config.getAsmThreshold());
        builder.withDetail("maxJsonSize", config.getMaxJsonSize());
        builder.withDetail("maxDepth", config.getMaxDepth());
        builder.withDetail("namingStrategy", config.getNamingStrategy());
        builder.withDetail("circularReferenceStrategy", config.getCircularReferenceStrategy());

        builder.withDetail("asmLevel", JsonCacheStats.getAsmLevel());
        builder.withDetail("asmGeneratedCount", JsonCacheStats.getAsmGeneratedCount());

        if (!safeMode) {
            builder.withDetail("warning", "AutoType SafeMode is disabled, RCE risk exists");
        }

        return builder.build();
    }
}
