package com.njydsz.common.search.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.Data;

/**
 * 搜索服务配置属性
 * <p>
 * 配置前缀：{@code ydsz.search}
 *
 * <pre>
 * ydsz:
 *   search:
 *     enabled: true
 *     primary: pg                    # 主引擎（pg / memory）
 *     fallbacks: [memory]            # 降级链
 *     page-size: 20
 *     search-timeout: 5
 *     cache:
 *       enabled: true
 *       ttl: 60
 *     pg:                            # PG 引擎特定配置
 *       search-config: search_zh
 *       index-table: ydsz_search_index
 *       field-weights: { title: 1.0, subtitle: 0.7, content: 0.4, tags: 0.2 }
 *       time-decay-days: 0
 *     text-processor:                # 文本处理配置
 *       synonym-enabled: false
 *       synonym-file: classpath:synonyms.txt
 *       pinyin-enabled: false
 *       pinyin-file: classpath:pinyin.txt
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Validated
@ConfigurationProperties(prefix = "ydsz.search")
public class SearchProperties {

    /** 是否启用搜索服务 */
    private boolean enabled = true;

    /** 主引擎名称（pg / memory） */
    private String primary = "pg";

    /** 降级引擎链（按顺序尝试） */
    private List<String> fallbacks = List.of("memory");

    // ==================== 通用配置 ====================

    /** 默认每页大小 */
    @Min(1)
    @Max(100)
    private int pageSize = 20;

    /** 搜索建议最大返回数 */
    @Min(1)
    @Max(50)
    private int suggestLimit = 10;

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

    /** 是否启用高亮 */
    private boolean highlight = true;

    /** 是否启用模糊匹配 */
    private boolean fuzzy = true;

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

    // ==================== 通用子配置 ====================

    /** 缓存配置 */
    private CacheConfig cache = new CacheConfig();

    /** 索引配置 */
    private IndexConfig index = new IndexConfig();

    /** 熔断配置 */
    private CircuitBreakerConfig circuitBreaker = new CircuitBreakerConfig();

    /** 文本处理配置（同义词 / 拼音） */
    private TextProcessorConfig textProcessor = new TextProcessorConfig();

    // ==================== 引擎特定配置 ====================

    /** PG 引擎配置 */
    private PgConfig pg = new PgConfig();

    // ==================== 通用内部类 ====================

    @Data
    public static class CacheConfig {
        private boolean enabled = true;
        private long ttl = 60;
        private long maxSize = 1000;
    }

    /**
     * 索引构建与同步配置（批大小、线程池、重试策略）。
     */
    @Data
    public static class IndexConfig {
        private int batchSize = 100;
        private int rebuildBatchSize = 500;
        private int threadPoolSize = 4;
        @Min(0)
        @Max(10)
        private int maxRetries = 3;
        private long retryIntervalMs = 1000;
    }

    /**
     * 搜索引擎熔断配置（连续失败阈值与恢复参数）。
     */
    @Data
    public static class CircuitBreakerConfig {
        private boolean enabled = true;
        @Min(1)
        private int failureThreshold = 5;
        @Min(1)
        private int waitDuration = 30;
        @Min(1)
        private int halfOpenRequests = 3;
    }

    /**
     * 文本处理配置（同义词扩展与拼音搜索）。
     */
    @Data
    public static class TextProcessorConfig {
        private boolean synonymEnabled = false;
        private String synonymFile = "classpath:synonyms.txt";
        private boolean pinyinEnabled = false;
        private String pinyinFile = "classpath:pinyin.txt";
    }

    /** 字段权重（引擎通用） */
    @Data
    public static class FieldWeights {
        private double title = 1.0;
        private double subtitle = 0.7;
        private double content = 0.4;
        private double tags = 0.2;
    }

    // ==================== 引擎特定内部类 ====================

    @Data
    public static class PgConfig {
        /** PG tsvector 搜索配置（search_zh / simple） */
        private String searchConfig = "search_zh";
        /** 索引表名 */
        private String indexTable = "ydsz_search_index";
        /** 字段权重 */
        private FieldWeights fieldWeights = new FieldWeights();
        /** 时间衰减半衰期（天），0 表示不衰减 */
        private double timeDecayDays = 0;
    }
}
