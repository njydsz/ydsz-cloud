package com.remisoft.common.search.health;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.remisoft.common.search.core.SearchEngineRegistry;
import com.remisoft.common.search.metrics.SearchMetrics;
import com.remisoft.common.search.service.SearchCacheService;

import lombok.extern.slf4j.Slf4j;

/**
 * 搜索引擎健康检查
 *
 * @author remi-team
 * @since 1.0.0
 */
@Slf4j
public class SearchHealthIndicator implements HealthIndicator {

    private final SearchEngineRegistry engineRegistry;
    private final SearchCacheService cacheService;
    private final SearchMetrics metrics;

    public SearchHealthIndicator(SearchEngineRegistry engineRegistry,
                                  SearchCacheService cacheService,
                                  SearchMetrics metrics) {
        this.engineRegistry = engineRegistry;
        this.cacheService = cacheService;
        this.metrics = metrics;
    }

    public SearchHealthIndicator(SearchEngineRegistry engineRegistry) {
        this(engineRegistry, null, null);
    }

    @Override
    public Health health() {
        if (engineRegistry == null || engineRegistry.getPrimary() == null) {
            return Health.down().withDetail("error", "No search engine configured").build();
        }
        try {
            boolean available = engineRegistry.isPrimaryAvailable();
            Health.Builder builder = available ? Health.up() : Health.down();
            builder.withDetail("primaryEngine", engineRegistry.getPrimary().getEngineName());
            builder.withDetail("available", available);
            builder.withDetail("capability", engineRegistry.getPrimaryCapability().toString());
            builder.withDetail("engines", engineRegistry.getAllEngines().stream()
                    .map(e -> e.getEngineName() + "(" + (e.isAvailable() ? "up" : "down") + ")")
                    .toList());
            if (cacheService != null) {
                builder.withDetail("cacheSize", cacheService.size());
            }
            if (metrics != null) {
                builder.withDetail("totalSearches", metrics.getTotalSearches());
                builder.withDetail("zeroResultRate", String.format("%.2f%%", metrics.getZeroResultRate() * 100));
                builder.withDetail("totalIndexOps", metrics.getTotalIndexOps());
                builder.withDetail("indexFailureRate", String.format("%.2f%%", metrics.getIndexFailureRate() * 100));
            }
            return builder.build();
        } catch (Exception e) {
            log.warn("[SearchHealth] 健康检查失败: {}", e.getMessage());
            return Health.down(e).build();
        }
    }
}
