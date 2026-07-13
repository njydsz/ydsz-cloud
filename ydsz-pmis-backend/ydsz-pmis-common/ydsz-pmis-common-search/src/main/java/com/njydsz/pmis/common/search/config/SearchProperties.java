package com.njydsz.pmis.common.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * 搜索服务配置属性
 * <p>
 * 配置前缀：{@code ydsz.search}
 *
 * <pre>
 * ydsz:
 *   search:
 *     engine: pg          # pg / memory / es
 *     highlight: true
 *     fuzzy: true
 *     page-size: 20
 *     suggest-limit: 10
 *     cache:
 *       enabled: true
 *       ttl: 60
 *     index:
 *       sync-mode: event   # event / mq
 *       batch-size: 100
 *       rebuild-batch-size: 500
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Data
@ConfigurationProperties(prefix = "ydsz.search")
public class SearchProperties {

    /** 搜索引擎类型：pg / memory / es */
    private String engine = "pg";

    /** 是否启用高亮 */
    private boolean highlight = true;

    /** 是否启用模糊匹配 */
    private boolean fuzzy = true;

    /** 默认每页大小 */
    private int pageSize = 20;

    /** 搜索建议最大返回数 */
    private int suggestLimit = 10;

    /** 高亮前置标签 */
    private String highlightPreTag = "<em>";

    /** 高亮后置标签 */
    private String highlightPostTag = "</em>";

    /** 高亮片段最大长度 */
    private int highlightFragmentSize = 120;

    /** 模糊匹配最小相似度 */
    private double fuzzyMinSimilarity = 0.3;

    /** 缓存配置 */
    private CacheConfig cache = new CacheConfig();

    /** 索引配置 */
    private IndexConfig index = new IndexConfig();

    /** 降级配置 */
    private DegradeConfig degrade = new DegradeConfig();

    @Data
    public static class CacheConfig {
        /** 是否启用搜索结果缓存 */
        private boolean enabled = true;
        /** 缓存 TTL（秒） */
        private long ttl = 60;
        /** 最大缓存条数 */
        private long maxSize = 1000;
    }

    @Data
    public static class IndexConfig {
        /** 索引同步模式：event / mq */
        private String syncMode = "event";
        /** 批量索引大小 */
        private int batchSize = 100;
        /** 全量重建批量大小 */
        private int rebuildBatchSize = 500;
        /** 索引同步线程池大小 */
        private int threadPoolSize = 4;
    }

    @Data
    public static class DegradeConfig {
        /** 是否启用降级 */
        private boolean enabled = true;
        /** 降级到 LIKE 匹配 */
        private boolean fallbackToLike = true;
    }
}
