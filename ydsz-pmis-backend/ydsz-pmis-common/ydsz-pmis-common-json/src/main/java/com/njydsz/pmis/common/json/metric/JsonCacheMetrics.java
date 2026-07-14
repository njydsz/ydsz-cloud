package com.njydsz.pmis.common.json.metric;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

import com.njydsz.pmis.common.json.asm.AsmBeanCodecGenerator;
import com.njydsz.pmis.common.json.cache.BeanSerializerCache;
import com.njydsz.pmis.common.json.cache.JsonCacheStats;

/**
 * Json 缓存指标 Micrometer 绑定器。
 *
 * <p>将 JSON 引擎内部缓存统计信息暴露为 Micrometer 指标，
 * 供 Prometheus / Grafana 监控使用。</p>
 *
 * <p><b>暴露指标：</b></p>
 * <ul>
 *   <li>{@code json.cache.serializer.size} - ASM 序列化器缓存大小</li>
 *   <li>{@code json.cache.bean_serializer.size} - Bean 序列化器缓存大小</li>
 *   <li>{@code json.cache.asm.generated.count} - ASM 已生成类数量</li>
 *   <li>{@code json.cache.asm.level} - ASM 降级级别（0=ASM, 1=REFLECTION）</li>
 * </ul>
 *
 * @since 1.4.0
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

        // ASM 序列化器缓存大小
        Gauge.builder(METRIC_PREFIX + "serializer.size", () -> JsonCacheStats.getSerializerCacheSize())
                .tags(tags)
                .description("ASM serializer cache size")
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
