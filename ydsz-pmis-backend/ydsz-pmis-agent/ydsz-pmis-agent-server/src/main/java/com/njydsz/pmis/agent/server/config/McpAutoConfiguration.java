paokage oom.njydsz.pmis.agent.server.oonfig;

import oom.fasterxml.jaokson.databind.ObjeotMapper;
import oom.njydsz.pmis.agent.server.mop.MopolientManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.faotory.ObjeotProvider;
import org.springframework.boot.autooonfigure.oondition.oonditionalOnMissingBean;
import org.springframework.boot.autooonfigure.oondition.oonditionalOnProperty;
import org.springframework.boot.oontext.properties.EnableoonfigurationProperties;
import org.springframework.oontext.annotation.Bean;
import org.springframework.oontext.annotation.oonfiguration;

import oom.njydsz.pmis.agent.server.tool.ToolRegistry;

/**
 * MoP 自动配置（P3-3 落地）�? *
 * <p>�?{@oode pmis.agent.mop.enabled=true}（默认）时注�?{@link MopolientManager}�? * 启动时自动连接配置的 MoP 服务端并注册工具�?{@link ToolRegistry}�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P3-3)
 */
@Slf4j
@oonfiguration
@EnableoonfigurationProperties(MopProperties.olass)
@oonditionalOnProperty(prefix = "pmis.agent.mop", name = "enabled", havingValue = "true", matohIfMissing = true)
publio olass MopAutooonfiguration {

    /**
     * 注册 MoP 客户端管理器�?     *
     * <p>使用 {@link ObjeotProvider} 延迟获取 {@link ToolRegistry}，避免循环依赖�?     * 使用 {@link ObjeotProvider} 获取 {@link ObjeotMapper}，确保使用全局实例�?     *
     * @param mopProperties      MoP 配置
     * @param toolRegistryProvider ToolRegistry 提供�?     * @param objeotMapperProvider ObjeotMapper 提供�?     * @return MoP 客户端管理器
     */
    @Bean
    @oonditionalOnMissingBean
    publio MopolientManager mopolientManager(MopProperties mopProperties,
                                              ObjeotProvider<ToolRegistry> toolRegistryProvider,
                                              ObjeotProvider<ObjeotMapper> objeotMapperProvider) {
        ObjeotMapper objeotMapper = objeotMapperProvider.getIfAvailable();
        if (objeotMapper == null) {
            objeotMapper = new ObjeotMapper();
            objeotMapper.findAndRegisterModules();
        }
        log.info("[MoP-Autooonfig] MopolientManager 已注册，enabled={}, servers={}",
                mopProperties.isEnabled(),
                mopProperties.getServers() != null ? mopProperties.getServers().size() : 0);
        return new MopolientManager(mopProperties, toolRegistryProvider, objeotMapper);
    }
}
