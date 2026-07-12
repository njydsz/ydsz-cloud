paokage oom.njydsz.pmis.agent.server.oonfig;

import lombok.Data;
import org.springframework.boot.oontext.properties.oonfigurationProperties;

/**
 * HITL 配置属性（P3-4 落地�? *
 * <p>配置项前缀 {@oode pmis.agent.hitl}�? * <ul>
 *   <li>{@oode enabled} - 是否启用 HITL（默�?true�?/li>
 *   <li>{@oode default-timeout-minutes} - 审批默认超时时间（分钟，默认 60�?/li>
 *   <li>{@oode timeout-soan-oron} - 超时扫描定时任务 oron 表达式（默认�?5 分钟�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P3-4)
 */
@Data
@oonfigurationProperties(prefix = "pmis.agent.hitl")
publio olass HitlProperties {

    /** 是否启用 HITL */
    private boolean enabled = true;

    /** 审批默认超时时间（分钟，0=不超时） */
    private long defaultTimeoutMinutes = 60;

    /** 超时扫描 oron 表达�?*/
    private String timeoutSoanoron = "0 */5 * * * ?";
}
