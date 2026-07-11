package com.njydsz.pmis.agent.web.config;

import com.njydsz.pmis.agent.server.engine.version.AgentVersionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 版本管理自动配置（P0-4 落地）。
 *
 * <p>将 {@link AgentVersionManager} 注册为 Spring Bean，
 * 供 {@link com.njydsz.pmis.agent.server.service.impl.AgentVersionServiceImpl} 使用。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0 (P0-4)
 */
@Slf4j
@Configuration
public class VersionAutoConfiguration {

    /**
     * Agent 版本管理器（内存版本，DB 持久化由 AgentVersionServiceImpl 封装）。
     */
    @Bean
    @ConditionalOnMissingBean
    public AgentVersionManager agentVersionManager() {
        log.info("[Version] AgentVersionManager Bean 已注册");
        return new AgentVersionManager();
    }
}
