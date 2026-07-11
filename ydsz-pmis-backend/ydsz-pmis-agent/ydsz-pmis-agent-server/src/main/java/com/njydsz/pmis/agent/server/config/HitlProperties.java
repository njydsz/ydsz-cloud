package com.njydsz.pmis.agent.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * HITL 配置属性（P3-4 落地）
 *
 * <p>配置项前缀 {@code pmis.agent.hitl}：
 * <ul>
 *   <li>{@code enabled} - 是否启用 HITL（默认 true）</li>
 *   <li>{@code default-timeout-minutes} - 审批默认超时时间（分钟，默认 60）</li>
 *   <li>{@code timeout-scan-cron} - 超时扫描定时任务 cron 表达式（默认每 5 分钟）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-4)
 */
@Data
@ConfigurationProperties(prefix = "pmis.agent.hitl")
public class HitlProperties {

    /** 是否启用 HITL */
    private boolean enabled = true;

    /** 审批默认超时时间（分钟，0=不超时） */
    private long defaultTimeoutMinutes = 60;

    /** 超时扫描 cron 表达式 */
    private String timeoutScanCron = "0 */5 * * * ?";
}
