package com.njydsz.common.search.health;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.njydsz.common.search.core.SearchEngine;
import com.njydsz.common.search.metrics.SearchMetrics;
import com.njydsz.common.search.service.SearchCacheService;

import lombok.extern.slf4j.Slf4j;

/**
 * 搜索引擎健康检查
 * <p>
 * P2-7: 增强健康检查 — 报告引擎状态、缓存大小、指标摘要
 *
 * @author ydsz-team
 * @since 1.4.0
 */
@Slf4j
public class SearchHealthIndicator implements HealthIndicator {

    private final SearchEngine searchEngine;
    private final SearchCacheService cacheService;
    private final SearchMetrics metrics;

    public SearchHealthIndicator(SearchEngine searchEngine,
                                  SearchCacheService cacheService,
                                  SearchMetrics metrics) {
        this.searchEngine = searchEngine;
        this.cacheService = cacheService;
        this.metrics = metrics;
    }

    public SearchHealthIndicator(SearchEngine searchEngine) {
        this(searchEngine, null, null);
    }

    @Override
    public Health health() {
        if (searchEngine == null) {
            return Health.down().withDetail("error", "SearchEngine not configured").build();
        }

        try {
            boolean available = searchEngine.isAvailable();
            Health.Builder builder = available ? Health.up() : Health.down();
            builder.withDetail("engine", searchEngine.getName());
            builder.withDetail("available", available);

            // P2-7: 缓存状态
            if (cacheService != null) {
                builder.withDetail("cacheSize", cacheService.size());
            }

            // P2-7: 指标摘要
            if (metrics != null) {
                builder.withDetail("totalSearches", metrics.getTotalSearches());
                builder.withDetail("zeroResultRate", String.format("%.2f%%", metrics.getZeroResultRate() * 100));
                builder.withDetail("totalIndexOps", metrics.getTotalIndexOps());
                builder.withDetail("indexFailureRate", String.format("%.2f%%", metrics.getIndexFailureRate() * 100));
            }

            return builder.build();
        } catch (Exception e) {
            log.warn("[SearchHealth] 健康检查失败: {}", e.getMessage());
            return Health.down(e)
                    .withDetail("engine", searchEngine.getName())
                    .build();
        }
    }
}
