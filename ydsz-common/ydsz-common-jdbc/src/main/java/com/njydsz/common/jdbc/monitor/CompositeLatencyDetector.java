package com.njydsz.common.jdbc.monitor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.core.Ordered;

import lombok.extern.slf4j.Slf4j;

/**
 * 组合式延迟检测器
 *
 * <p>聚合所有 {@link SlaveLatencyDetector} 实现（含 SPI 扩展），
 * 按 {@link com.springframework.core.Ordered#getOrder()} 排序后，
 * 选择第一个匹配的数据源类型进行检测。
 *
 * <p>扩展方可通过发布自定义 {@link SlaveLatencyDetector} Bean 自动注册到本检测器。
 *
 * @author ydsz-team
 * @since 1.8.0
 */
@Slf4j
public class CompositeLatencyDetector implements SlaveLatencyDetector {

    private final List<SlaveLatencyDetector> detectors;

    /**
     * 构造组合探测器
     *
     * @param detectors 探测器列表（Spring 自动注入所有 SlaveLatencyDetector 实现）
     */
    public CompositeLatencyDetector(List<SlaveLatencyDetector> detectors) {
        this.detectors = new ArrayList<>(detectors);
        this.detectors.sort(Comparator.comparingInt(SlaveLatencyDetector::getOrder));
        if (log.isDebugEnabled()) {
            this.detectors.forEach(d -> log.debug("注册延迟检测器: {} (order={})",
                    d.getClass().getSimpleName(), d.getOrder()));
        }
    }

    @Override
    public Optional<Duration> detect(DataSource dataSource) {
        for (SlaveLatencyDetector detector : detectors) {
            if (detector.isSupported(dataSource)) {
                return detector.detect(dataSource);
            }
        }
        log.warn("无匹配的延迟检测器，数据源: {}", dataSource.getClass().getName());
        return Optional.empty();
    }

    @Override
    public boolean isSupported(DataSource dataSource) {
        return detectors.stream().anyMatch(d -> d.isSupported(dataSource));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
