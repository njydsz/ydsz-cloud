package com.njydsz.pmis.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Agent Token 配额配置（P2-4 落地）。
 *
 * <p>在 application.yml 中配置：
 * <pre>
 * pmis:
 *   agent:
 *     token-quota:
 *       enabled: true                          # 是否启用配额限制（默认 false，避免影响测试）
 *       default-monthly-quota: 1000000        # 默认月度配额（100 万 token）
 *       auto-init: true                        # 首次访问时自动初始化当月配额
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-4)
 */
@Data
@Component
@ConfigurationProperties(prefix = "pmis.agent.token-quota")
public class TokenQuotaProperties {

    /** 是否启用 Token 配额限制（默认 false，避免影响现有测试） */
    private boolean enabled = false;

    /** 默认月度配额（token 数，默认 100 万） */
    private long defaultMonthlyQuota = 1_000_000L;

    /** 首次访问时自动初始化当月配额 */
    private boolean autoInit = true;
}
