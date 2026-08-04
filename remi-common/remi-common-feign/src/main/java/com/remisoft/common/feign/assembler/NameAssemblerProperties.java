package com.remisoft.common.feign.assembler;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * NameAssembler 配置属性。
 *
 * <p>前缀：{@code remi.feign.name-assembler}
 *
 * <p>示例 application.yml：
 * <pre>{@code
 * remi:
 *   feign:
 *     name-assembler:
 *       cache-ttl: 10m          # 缓存存活时间，默认 5m
 *       cache-max-size: 20000   # 缓存最大条目数，默认 10000
 *       fallback-to-id: true    # Feign 失败时是否用 ID 顶替 name，默认 true
 * }
 * }</pre>
 *
 * @author remi-team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "remi.feign.name-assembler")
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
}
