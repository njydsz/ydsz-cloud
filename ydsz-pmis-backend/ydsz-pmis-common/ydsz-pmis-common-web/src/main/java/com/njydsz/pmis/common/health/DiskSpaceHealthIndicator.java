package com.njydsz.pmis.common.health;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * 磁盘空间健康检查指示器（P1-8）
 *
 * <p>检查应用部署磁盘的可用空间，防止磁盘打满导致服务不可用。
 * 检查结果暴露在 {@code /actuator/health/diskSpace} 端点。
 *
 * <p>当可用空间低于阈值时返回 DOWN 状态，触发告警。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@Slf4j
@Component
public class DiskSpaceHealthIndicator implements HealthIndicator {

    /** 默认磁盘空间阈值（1GB） */
    private static final long DEFAULT_THRESHOLD = 1024L * 1024L * 1024L;

    /** 磁盘路径，默认为当前工作目录 */
    @Value("${pmis.health.disk.path:.}")
    private String path;

    /** 磁盘空间阈值（字节），低于此值标记为 DOWN */
    @Value("${pmis.health.disk.threshold:1073741824}")
    private long threshold;

    @Override
    public Health health() {
        try {
            File disk = new File(path);
            if (!disk.exists()) {
                return Health.down()
                        .withDetail("error", "path not found: " + path)
                        .build();
            }

            long total = disk.getTotalSpace();
            long free = disk.getFreeSpace();
            long usable = disk.getUsableSpace();

            Health.Builder builder = Health.up()
                    .withDetail("path", disk.getCanonicalPath())
                    .withDetail("total", formatSize(total))
                    .withDetail("free", formatSize(free))
                    .withDetail("usable", formatSize(usable))
                    .withDetail("threshold", formatSize(threshold))
                    .withDetail("usage_pct", total > 0 ? String.format("%.1f%%", (1.0 - (double) free / total) * 100) : "unknown");

            if (free < threshold) {
                log.warn("[HealthCheck] 磁盘空间不足: free={}, threshold={}", formatSize(free), formatSize(threshold));
                return builder.down()
                        .withDetail("status", "DEGRADED")
                        .withDetail("reason", "free space below threshold")
                        .build();
            }

            return builder.build();
        } catch (Exception e) {
            log.warn("[HealthCheck] 磁盘空间健康检查失败: {}", e.getMessage());
            return Health.down(e).build();
        }
    }

    /**
     * 格式化字节大小为人类可读字符串
     *
     * @param bytes 字节数
     * @return 可读字符串（如 1.5 GB）
     */
    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024L * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        if (bytes < 1024L * 1024 * 1024 * 1024) return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
        return String.format("%.1f TB", bytes / (1024.0 * 1024 * 1024 * 1024));
    }
}
