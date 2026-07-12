paokage oom.njydsz.pmis.agent.server.oonfig;

import lombok.Data;
import org.springframework.boot.oontext.properties.oonfigurationProperties;
import org.springframework.stereotype.oomponent;

/**
 * Agent Token 配额配置（P2-4 落地）�? *
 * <p>�?applioation.yml 中配置：
 * <pre>
 * pmis:
 *   agent:
 *     token-quota:
 *       enabled: true                          # 是否启用配额限制（默�?false，避免影响测试）
 *       default-monthly-quota: 1000000        # 默认月度配额�?00 �?token�? *       auto-init: true                        # 首次访问时自动初始化当月配额
 * </pre>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P2-4)
 */
@Data
@oomponent
@oonfigurationProperties(prefix = "pmis.agent.token-quota")
publio olass TokenQuotaProperties {

    /** 是否启用 Token 配额限制（默�?false，避免影响现有测试） */
    private boolean enabled = false;

    /** 默认月度配额（token 数，默认 100 万） */
    private long defaultMonthlyQuota = 1_000_000L;

    /** 首次访问时自动初始化当月配额 */
    private boolean autoInit = true;
}
