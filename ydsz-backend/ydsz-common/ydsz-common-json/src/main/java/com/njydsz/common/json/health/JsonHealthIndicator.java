package com.njydsz.common.json.health;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.njydsz.common.json.autotype.AutoTypeChecker;
import com.njydsz.common.json.cache.YdszJsonCacheStats;
import com.njydsz.common.json.config.YdszJsonConfig;

/**
 * YdszJson 健康检查指标。
 *
 * <p>检查 YdszJson 引擎的运行状态：
 * <ul>
 *   <li>AutoType SafeMode 是否开启</li>
 *   <li>最大 JSON 大小限制</li>
 *   <li>最大序列化深度</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class JsonHealthIndicator implements HealthIndicator {

    @Override
    public Health health() {
        YdszJsonConfig config = YdszJsonConfig.getInstance();
        boolean safeMode = AutoTypeChecker.isSafeMode();

        Health.Builder builder = safeMode ? Health.up() : Health.down();
        builder.withDetail("safeMode", safeMode);
        builder.withDetail("maxJsonSize", config.getMaxJsonSize());
        builder.withDetail("maxDepth", config.getMaxDepth());
        builder.withDetail("namingStrategy", config.getNamingStrategy());
        builder.withDetail("circularReferenceStrategy", config.getCircularReferenceStrategy());

        builder.withDetail("asmLevel", YdszJsonCacheStats.getAsmLevel());
        builder.withDetail("asmGeneratedCount", YdszJsonCacheStats.getAsmGeneratedCount());

        if (!safeMode) {
            builder.withDetail("warning", "AutoType SafeMode is disabled, RCE risk exists");
        }

        return builder.build();
    }
}
