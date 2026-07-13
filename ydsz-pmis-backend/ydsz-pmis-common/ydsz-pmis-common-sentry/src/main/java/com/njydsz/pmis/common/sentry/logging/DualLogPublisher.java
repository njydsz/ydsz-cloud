package com.njydsz.pmis.common.sentry.logging;

import java.util.List;

import com.njydsz.pmis.common.sentry.domain.LogEvent;
import com.njydsz.pmis.common.sentry.spi.LogPublisher;

import lombok.extern.slf4j.Slf4j;

/**
 * 双发日志发布器
 *
 * <p>同时向多个 LogPublisher 发布日志，支持主备切换和降级。
 *
 * <p>策略：
 * <ul>
 *   <li>正常情况下同时发布到所有 Publisher</li>
 *   <li>某个 Publisher 不可用时自动跳过</li>
 *   <li>所有 Publisher 不可用时降级到本地文件（由 Logback 处理）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@Slf4j
public class DualLogPublisher implements LogPublisher {

    private final List<LogPublisher> publishers;
    private final boolean failOnAllError;

    public DualLogPublisher(List<LogPublisher> publishers, boolean failOnAllError) {
        this.publishers = publishers;
        this.failOnAllError = failOnAllError;
        log.info("[Sentry] DualLogPublisher 初始化: publishers={}, failOnAllError={}",
                publishers != null ? publishers.stream().map(LogPublisher::getName).toList() : List.of(),
                failOnAllError);
    }

    @Override
    public boolean publish(LogEvent event) {
        if (publishers == null || publishers.isEmpty()) {
            return false;
        }

        int successCount = 0;
        for (LogPublisher publisher : publishers) {
            if (!publisher.isAvailable()) {
                log.debug("[Sentry] 日志发布器 {} 不可用, 跳过", publisher.getName());
                continue;
            }
            try {
                boolean success = publisher.publish(event);
                if (success) {
                    successCount++;
                }
            } catch (Exception e) {
                log.debug("[Sentry] 日志发布器 {} 发布异常: {}", publisher.getName(), e.getMessage());
            }
        }

        if (failOnAllError) {
            return successCount > 0;
        }
        return successCount == publishers.size();
    }

    @Override
    public boolean isAvailable() {
        if (publishers == null || publishers.isEmpty()) {
            return false;
        }
        return publishers.stream().anyMatch(LogPublisher::isAvailable);
    }

    @Override
    public String getName() {
        return "dual";
    }

    @Override
    public String getScheme() {
        return "dual";
    }

    /**
     * 获取所有发布器
     */
    public List<LogPublisher> getPublishers() {
        return publishers;
    }
}
