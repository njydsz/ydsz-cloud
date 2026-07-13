package com.njydsz.pmis.common.search.health;

import org.springframework.boot.health.Health;
import org.springframework.boot.health.HealthContributor;
import org.springframework.boot.health.HealthIndicator;

import com.njydsz.pmis.common.search.core.SearchEngine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 搜索引擎健康检查
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Slf4j
@RequiredArgsConstructor
public class SearchHealthIndicator implements HealthIndicator, HealthContributor {

    private final SearchEngine searchEngine;

    @Override
    public Health health() {
        if (searchEngine == null) {
            return Health.down().withDetail("error", "SearchEngine not configured").build();
        }

        try {
            boolean available = searchEngine.isAvailable();
            Health.Builder builder = available ? Health.up() : Health.down();
            return builder
                    .withDetail("engine", searchEngine.getName())
                    .withDetail("available", available)
                    .build();
        } catch (Exception e) {
            log.warn("[SearchHealth] 健康检查失败: {}", e.getMessage());
            return Health.down(e)
                    .withDetail("engine", searchEngine.getName())
                    .build();
        }
    }
}
