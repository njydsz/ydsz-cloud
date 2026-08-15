package com.njydsz.common.feign.assembler;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * NameAssembler 配置属性。
 *
 * <p>前缀：{@code ydsz.feign.name-assembler}
 *
 * <p>示例 application.yml：
 * <pre>{@code
 * ydsz:
 *   feign:
 *     name-assembler:
 *       cache-ttl: 10m          # 缓存存活时间，默认 5m
 *       cache-max-size: 20000   # 缓存最大条目数，默认 10000
 *       fallback-to-id: true    # Feign 失败时是否用 ID 顶替 name，默认 true
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "ydsz.feign.name-assembler")
public class NameAssemblerProperties {

    /**
     * 本地缓存存活时间（TTL）。
     *
     * <p>超过此时间的缓存条目会在下次访问时被剔除并重新拉取。
     * 默认 5 分钟，平衡新鲜度与 Feign 调用频次。
     */
    private Duration cacheTtl = Duration.ofMinutes(5);

    /**
     * 本地缓存最大条目数。
     *
     * <p>超过此规模时清理最早的条目（简易 LRU）。
     * 默认 10000，单实例足够支撑中型业务流量。
     */
    private int cacheMaxSize = 10000;

    /**
     * Feign 调用失败或 ID 未命中时，是否用 ID 字符串本身顶替 name 字段。
     *
     * <p>{@code true}（默认）：兜底显示 ID，避免前端空白。
     * {@code false}：保持 name 字段原值（可能为 null）。
     */
    private boolean fallbackToId = true;

    /**
     * P2-1: 是否启用 Redis L2 分布式缓存。
     *
     * <p>=false（默认）：仅使用本地 Caffeine L1 缓存。
     * <p>=true：优先 L1 → L2（Redis）→ Feign，适用于多实例共享缓存场景，
     * 可显著降低 Feign 调用频次，需要在 classpath 中存在 common-redis 模块。
     */
    private boolean redisCacheEnabled = false;

    /**
     * P2-1: Redis L2 缓存存活时间（TTL）。
     *
     * <p>默认 10 分钟，应大于本地 L1 TTL 以保证 L2 兜底新鲜度。
     */
    private Duration redisCacheTtl = Duration.ofMinutes(10);
}
