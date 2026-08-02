package com.njydsz.common.json.health;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.njydsz.common.json.autotype.AutoTypeChecker;
import com.njydsz.common.json.cache.AsmCodecCache;
import com.njydsz.common.json.cache.BeanSerializerCache;
import com.njydsz.common.json.cache.JsonCacheStats;
import com.njydsz.common.json.config.JsonConfig;
import com.njydsz.common.json.provider.SerializationContext;

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
        JsonConfig config = JsonConfig.getInstance();
        boolean safeMode = AutoTypeChecker.isSafeMode();

        Health.Builder builder = safeMode ? Health.up() : Health.down();
        builder.withDetail("safeMode", safeMode);
        builder.withDetail("maxJsonSize", config.getMaxJsonSize());
        builder.withDetail("maxDepth", config.getMaxDepth());
        builder.withDetail("namingStrategy", config.getNamingStrategy());
        builder.withDetail("circularReferenceStrategy", config.getCircularReferenceStrategy());
        builder.withDetail("dateFormat", config.getDateFormat());
        builder.withDetail("useBigDecimal", config.isUseBigDecimal());
        builder.withDetail("wrapRootValue", config.isWrapRootValue());
        builder.withDetail("failOnError", config.isFailOnError());
        builder.withDetail("serializeEnumUsingOrdinal", config.isSerializeEnumUsingOrdinal());

        // ASM 缓存统计
        builder.withDetail("asmLevel", JsonCacheStats.getAsmLevel());
        builder.withDetail("asmGeneratedCount", JsonCacheStats.getAsmGeneratedCount());
        builder.withDetail("serializerCacheSize", JsonCacheStats.getSerializerCacheSize());

        // 缓存详情
        builder.withDetail("codecCacheSize", AsmCodecCache.getCacheSize());
        builder.withDetail("beanSerializerCacheSize", BeanSerializerCache.size());
        builder.withDetail("threadLocalMemoryEstimate", SerializationContext.estimateThreadLocalMemory());

        if (!safeMode) {
            builder.withDetail("warning", "AutoType SafeMode is disabled, RCE risk exists");
        }

        return builder.build();
    }
}
