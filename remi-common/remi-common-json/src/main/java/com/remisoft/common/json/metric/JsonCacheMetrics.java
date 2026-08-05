package com.remisoft.common.json.metric;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

import com.remisoft.common.json.asm.AsmBeanCodecGenerator;
import com.remisoft.common.json.cache.AsmCodecCache;
import com.remisoft.common.json.cache.BeanSerializerCache;
import com.remisoft.common.json.cache.JsonCacheStats;

/**
 * RemiJson 缓存指标 Micrometer 绑定器。
 *
 * <p>将 JSON 引擎内部缓存统计信息暴露为 Micrometer 指标，
 * 供 Prometheus / Grafana 监控使用。</p>
 *
 * <p><b>暴露指标：</b></p>
 * <ul>
 *   <li>{@code json.cache.serializer.l1_size} - ASM 序列化器 L1 (强引用) 缓存大小</li>
 *   <li>{@code json.cache.serializer.l2_size} - ASM 序列化器 L2 (软引用) 缓存大小</li>
 *   <li>{@code json.cache.deserializer.l1_size} - ASM 反序列化器 L1 (强引用) 缓存大小</li>
 *   <li>{@code json.cache.deserializer.l2_size} - ASM 反序列化器 L2 (软引用) 缓存大小</li>
 *   <li>{@code json.cache.serializer.hit_rate} - ASM 序列化器真实命中率 (0.0 ~ 1.0)</li>
 *   <li>{@code json.cache.deserializer.hit_rate} - ASM 反序列化器真实命中率 (0.0 ~ 1.0)</li>
 *   <li>{@code json.cache.bean_serializer.size} - Bean 序列化器缓存大小</li>
 *   <li>{@code json.cache.asm.generated.count} - ASM 已生成类数量</li>
 *   <li>{@code json.cache.asm.level} - ASM 降级级别（0=ASM, 1=REFLECTION）</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 */
public final class JsonCacheMetrics {

    private static final String METRIC_PREFIX = "json.cache.";

    private JsonCacheMetrics() {
        throw new UnsupportedOperationException();
    }

    /**
     * 将缓存统计绑定到 MeterRegistry。
     *
     * @param registry Meter 注册表
     */
    public static void bindTo(MeterRegistry registry) {
        if (registry == null) {
            return;
        }

        Tags tags = Tags.empty();

        // ASM 序列化器 L1 (强引用) 缓存大小
        Gauge.builder(METRIC_PREFIX + "serializer.l1_size", AsmCodecCache::serializerL1Size)
                .tags(tags)
                .description("ASM serializer L1 (strong ref) cache size")
                .register(registry);

        // ASM 序列化器 L2 (软引用) 缓存大小
        Gauge.builder(METRIC_PREFIX + "serializer.l2_size", AsmCodecCache::serializerL2Size)
                .tags(tags)
                .description("ASM serializer L2 (soft ref) cache size")
                .register(registry);

        // ASM 反序列化器 L1 (强引用) 缓存大小
        Gauge.builder(METRIC_PREFIX + "deserializer.l1_size", AsmCodecCache::deserializerL1Size)
                .tags(tags)
                .description("ASM deserializer L1 (strong ref) cache size")
                .register(registry);

        // ASM 反序列化器 L2 (软引用) 缓存大小
        Gauge.builder(METRIC_PREFIX + "deserializer.l2_size", AsmCodecCache::deserializerL2Size)
                .tags(tags)
                .description("ASM deserializer L2 (soft ref) cache size")
                .register(registry);

        // ASM 序列化器真实命中率
        Gauge.builder(METRIC_PREFIX + "serializer.hit_rate", () -> AsmCodecCache.getCacheStats().serializerHitRate())
                .tags(tags)
                .description("ASM serializer cache hit rate (0.0 ~ 1.0, based on real hit/miss counters)")
                .register(registry);

        // ASM 反序列化器真实命中率
        Gauge.builder(METRIC_PREFIX + "deserializer.hit_rate", () -> AsmCodecCache.getCacheStats().deserializerHitRate())
                .tags(tags)
                .description("ASM deserializer cache hit rate (0.0 ~ 1.0, based on real hit/miss counters)")
                .register(registry);

        // Bean 序列化器缓存大小
        Gauge.builder(METRIC_PREFIX + "bean_serializer.size", () -> BeanSerializerCache.size())
                .tags(tags)
                .description("Bean serializer cache size")
                .register(registry);

        // ASM 已生成类数量
        Gauge.builder(METRIC_PREFIX + "asm.generated.count", () -> JsonCacheStats.getAsmGeneratedCount())
                .tags(tags)
                .description("ASM generated class count")
                .register(registry);

        // ASM 降级级别
        Gauge.builder(METRIC_PREFIX + "asm.level", () ->
                        JsonCacheStats.getAsmLevel() == AsmBeanCodecGenerator.AsmLevel.REFLECTION ? 1.0 : 0.0)
                .tags(tags)
                .description("ASM degradation level (0=ASM, 1=REFLECTION)")
                .register(registry);
    }
}
