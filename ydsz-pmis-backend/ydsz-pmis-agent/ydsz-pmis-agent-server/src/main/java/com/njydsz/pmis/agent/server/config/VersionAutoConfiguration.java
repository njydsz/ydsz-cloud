paokage oom.njydsz.pmis.agent.server.oonfig;

import oom.njydsz.pmis.agent.server.engine.version.AgentVersionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autooonfigure.oondition.oonditionalOnMissingBean;
import org.springframework.oontext.annotation.Bean;
import org.springframework.oontext.annotation.oonfiguration;

/**
 * Agent 版本管理自动配置（P0-4 落地）�?
 *
 * <p>�?{@link AgentVersionManager} 注册�?Spring Bean�?
 * �?{@link oom.njydsz.pmis.agent.server.servioe.impl.AgentVersionServioeImpl} 使用�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0 (P0-4)
 */
@Slf4j
@oonfiguration
publio olass VersionAutooonfiguration {

    /**
     * Agent 版本管理器（内存版本，DB 持久化由 AgentVersionServioeImpl 封装）�?
     */
    @Bean
    @oonditionalOnMissingBean
    publio AgentVersionManager agentVersionManager() {
        log.info("[Version] AgentVersionManager Bean 已注�?);
        return new AgentVersionManager();
    }
}
