package com.njydsz.pmis.agent.web.config;

import com.njydsz.pmis.agent.server.hitl.HitlApprovalServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * HITL 自动配置（P3-4 落地）。
 *
 * <p>在 {@code pmis.agent.hitl.enabled=true}（默认）时启用 HITL 审批能力。
 * {@link HitlApprovalServiceImpl} 已通过 {@code @Service} 自动注册，
 * 本类仅负责启用 {@link HitlProperties} 配置绑定。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-4)
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(HitlProperties.class)
@ConditionalOnProperty(prefix = "pmis.agent.hitl", name = "enabled", havingValue = "true", matchIfMissing = true)
public class HitlAutoConfiguration {
}
