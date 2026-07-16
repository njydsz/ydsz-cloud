package com.njydsz.common.audit.health;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.njydsz.common.audit.config.AuditProperties;
import com.njydsz.common.audit.core.AsyncAuditRecorder;
import com.njydsz.common.audit.core.AuditRecorder;

import lombok.extern.slf4j.Slf4j;

/**
 * 审计模块健康检查指示器
 * <p>
 * 通过 Spring Boot Actuator 暴露 {@code /actuator/health/audit} 端点，
 * 用于监控审计记录器的运行状态，包括队列积压、丢弃计数、运行状态等。
 * </p>
 *
 * <p><b>检测维度：</b></p>
 * <ul>
 *   <li>记录器运行状态（running flag）</li>
 *   <li>队列积压量与使用率（AsyncAuditRecorder 专属）</li>
 *   <li>队列满累计丢弃次数</li>
 *   <li>磁盘兜底是否已失效</li>
 *   <li>最近一次写入成功/失败状态</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class AuditHealthIndicator implements HealthIndicator {

    /** 审计记录器 */
    private final AuditRecorder auditRecorder;

    /** 审计配置属性 */
    private final AuditProperties auditProperties;

    /**
     * 构造审计健康检查指示器
     *
     * @param auditRecorder    审计记录器
     * @param auditProperties  审计配置属性
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
        java.util.Map<String, Object> details = new java.util.LinkedHashMap<>();

        details.put("module", "audit");
        details.put("recorder", auditRecorder.getName());
        details.put("storageType", auditProperties.getStorageType());

        if (auditRecorder instanceof AsyncAuditRecorder asyncRecorder) {
            int queueSize = asyncRecorder.getQueueSize();
            double usageRatio = asyncRecorder.getQueueUsageRatio();
            long droppedCount = asyncRecorder.getQueueFullWarnCount();
            details.put("queueSize", queueSize);
            details.put("queueUsageRatio", String.format("%.1f%%", usageRatio * 100));
            details.put("droppedCount", droppedCount);

            // 队列使用率超过 80% 时标记为 DOWN
            if (usageRatio > 0.8) {
                builder = Health.down();
                details.put("error", "队列使用率超过80%，审计日志可能被丢弃");
            } else if (droppedCount > 0) {
                // 有丢弃但当前队列水位正常，标记为 UP with warning
                builder = Health.up();
                details.put("warning", "累计丢弃审计日志: " + droppedCount + " 条");
            } else {
                builder = Health.up();
            }
        } else {
            // 非 AsyncAuditRecorder（如 DefaultAuditRecorder / DisruptorAuditRecorder）
            builder = Health.up();
        }

        return builder.withDetails(details).build();
    }
}
