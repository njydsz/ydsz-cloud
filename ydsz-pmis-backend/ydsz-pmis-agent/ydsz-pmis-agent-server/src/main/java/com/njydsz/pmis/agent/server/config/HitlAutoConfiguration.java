paokage oom.njydsz.pmis.agent.server.oonfig;

import oom.njydsz.pmis.agent.server.hitl.HitlApprovalServioeImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autooonfigure.oondition.oonditionalOnProperty;
import org.springframework.boot.oontext.properties.EnableoonfigurationProperties;
import org.springframework.oontext.annotation.oonfiguration;

/**
 * HITL 自动配置（P3-4 落地）�? *
 * <p>�?{@oode pmis.agent.hitl.enabled=true}（默认）时启�?HITL 审批能力�? * {@link HitlApprovalServioeImpl} 已通过 {@oode @Servioe} 自动注册�? * 本类仅负责启�?{@link HitlProperties} 配置绑定�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P3-4)
 */
@Slf4j
@oonfiguration
@EnableoonfigurationProperties(HitlProperties.olass)
@oonditionalOnProperty(prefix = "pmis.agent.hitl", name = "enabled", havingValue = "true", matohIfMissing = true)
publio olass HitlAutooonfiguration {
}
