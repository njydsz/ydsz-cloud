package com.njydsz.pmis.common.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
 *     search-timeout: 5    # 搜索超时（秒）
 *     max-page-size: 100   # 最大每页大小
 *     max-page-depth: 5000 # 最大翻页深度（page * pageSize）
 *     cache:
 *       enabled: true
 *       ttl: 60
 *     index:
 *       sync-mode: event   # event / mq
 *       batch-size: 100
 *       rebuild-batch-size: 500
 *     circuit-breaker:
 *       enabled: true
 *       failure-threshold: 5
 *       wait-duration: 30
 *     synonym:
 *       enabled: false
 *       file: classpath:synonyms.txt
 *     pinyin:
 *       enabled: false
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Data
@Validated
@ConfigurationProperties(prefix = "ydsz.search")
public class SearchProperties {

    /** 搜索引擎类型：pg / memory / es */
    private String engine = "pg";

    /** 是否启用高亮 */
    private boolean highlight = true;

    /** 是否启用模糊匹配 */
    private boolean fuzzy = true;

    /** 默认每页大小 */
    @Min(1)
    @Max(100)
    private int pageSize = 20;

    /** 搜索建议最大返回数 */
    @Min(1)
    @Max(50)
    private int suggestLimit = 10;

    /** 高亮前置标签 */
    private String highlightPreTag = "<em>";

    /** 高亮后置标签 */
    private String highlightPostTag = "</em>";

    /** 高亮片段最大长度 */
    @Min(10)
    @Max(1000)
    private int highlightFragmentSize = 120;

    /** 模糊匹配最小相似度 */
    private double fuzzyMinSimilarity = 0.3;

    /** 搜索超时时间（秒） */
    @Min(1)
    @Max(60)
    private int searchTimeout = 5;

    /** 最大每页大小（深分页保护） */
    @Min(1)
    @Max(500)
    private int maxPageSize = 100;

    /** 最大翻页深度（page * pageSize 上限） */
    @Min(1)
    private int maxPageDepth = 5000;

    /** 缓存配置 */
    private CacheConfig cache = new CacheConfig();

    /** 索引配置 */
    private IndexConfig index = new IndexConfig();

    /** 降级配置 */
    private DegradeConfig degrade = new DegradeConfig();

    /** 熔断配置 */
    private CircuitBreakerConfig circuitBreaker = new CircuitBreakerConfig();

    /** 同义词配置 */
    private SynonymConfig synonym = new SynonymConfig();

    /** 拼音配置 */
    private PinyinConfig pinyin = new PinyinConfig();

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
        /** 索引同步最大重试次数 */
        @Min(0)
        @Max(10)
        private int maxRetries = 3;
        /** 索引同步重试间隔（毫秒） */
        private long retryIntervalMs = 1000;
    }

    @Data
    public static class DegradeConfig {
        /** 是否启用降级 */
        private boolean enabled = true;
        /** 降级到 LIKE 匹配 */
        private boolean fallbackToLike = true;
        /** 降级探测间隔（秒） */
        @Min(5)
        @Max(300)
        private int probeInterval = 30;
    }

    @Data
    public static class CircuitBreakerConfig {
        /** 是否启用熔断 */
        private boolean enabled = true;
        /** 失败阈值（连续失败次数） */
        @Min(1)
        private int failureThreshold = 5;
        /** 熔断等待时间（秒） */
        @Min(1)
        private int waitDuration = 30;
        /** 半开探测请求数 */
        @Min(1)
        private int halfOpenRequests = 3;
    }

    @Data
    public static class SynonymConfig {
        /** 是否启同义词 */
        private boolean enabled = false;
        /** 同义词词典文件路径 */
        private String file = "classpath:synonyms.txt";
    }

    @Data
    public static class PinyinConfig {
        /** 是否启用拼音搜索 */
        private boolean enabled = false;
    }
}
