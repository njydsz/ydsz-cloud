package com.njydsz.common.audit.health;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.njydsz.common.audit.config.AuditProperties;
import com.njydsz.common.audit.core.AsyncAuditRecorder;
import com.njydsz.common.audit.core.AuditRecorder;
import com.njydsz.common.audit.core.DisruptorAuditRecorder;

import lombok.extern.slf4j.Slf4j;

/**
 * 审计模块健康检查指示器
 * <p>
 * 通过 Spring Boot Actuator 暴露 {@code /actuator/health/audit} 端点，
 * 用于监控审计记录器的运行状态，包括队列/缓冲区状态、丢弃计数、累计成功/失败等。
 * </p>
 *
 * <p><b>检测维度：</b></p>
 * <ul>
 *   <li>记录器运行状态（通过实例类型判断）</li>
 *   <li>AsyncAuditRecorder 专属：队列积压量与使用率</li>
 *   <li>队列满累计丢弃次数</li>
 *   <li>累计成功/失败写入数</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class AuditHealthIndicator implements HealthIndicator {

    /** 队列使用率告警阈值 */
    private static final double QUEUE_USAGE_WARN_THRESHOLD = 0.8;

    /** 审计记录器 */
    private final AuditRecorder auditRecorder;

    /** 审计配置属性 */
    private final AuditProperties auditProperties;

    /**
     * 构造审计健康检查指示器
     *
     * @param auditRecorder   审计记录器
     * @param auditProperties 审计配置属性
     */
    public AuditHealthIndicator(AuditRecorder auditRecorder, AuditProperties auditProperties) {
        this.auditRecorder = auditRecorder;
        this.auditProperties = auditProperties;
    }

    /**
     * 执行健康检查
     *
     * @return Health 状态（UP / DOWN）及明细
     */
    @Override
    public Health health() {
        Health.Builder builder;
        Map<String, Object> details = new LinkedHashMap<>();

        details.put("module", "audit");
        details.put("recorder", auditRecorder.getName());
        details.put("storageType", auditProperties.getStorageType());

        if (auditRecorder instanceof AsyncAuditRecorder asyncRecorder) {
            int queueSize = asyncRecorder.getQueueSize();
            double usageRatio = asyncRecorder.getQueueUsageRatio();
            long queueFullCount = asyncRecorder.getQueueFullWarnCount();
            details.put("queueSize", queueSize);
            details.put("queueUsageRatio", String.format("%.1f%%", usageRatio * 100));
            details.put("queueFullCount", queueFullCount);

            builder = buildHealthStatus(usageRatio, queueFullCount, details);
        } else if (auditRecorder instanceof DisruptorAuditRecorder disruptorRecorder) {
            long queueFullCount = disruptorRecorder.getQueueFullWarnCount();
            long successCount = disruptorRecorder.getSuccessCount();
            long failureCount = disruptorRecorder.getFailureCount();
            details.put("queueFullCount", queueFullCount);
            details.put("successCount", successCount);
            details.put("failureCount", failureCount);

            // Disruptor 有失败记录则标记为 DOWN
            builder = failureCount > 0 ? Health.down() : Health.up();
        } else {
            // 默认（DefaultAuditRecorder 或自定义实现）
            builder = Health.up();
        }

        return builder.withDetails(details).build();
    }

    /**
     * 根据 AsyncAuditRecorder 队列状态构建健康状态
     *
     * @param usageRatio     队列使用率
     * @param queueFullCount 队列满触发次数
     * @param details        健康详情 Map
     * @return Health.Builder
     */
    private Health.Builder buildHealthStatus(double usageRatio, long queueFullCount,
                                              Map<String, Object> details) {
        // 队列使用率超过阈值时标记为 DOWN
        if (usageRatio > QUEUE_USAGE_WARN_THRESHOLD) {
            details.put("error", "队列使用率超过80%，审计日志可能被丢弃");
            return Health.down();
        }
        // 有丢弃但当前队列水位正常，标记为 UP with warning
        if (queueFullCount > 0) {
            details.put("warning", "累计丢弃审计日志: " + queueFullCount + " 条");
        }
        return Health.up();
    }
}
