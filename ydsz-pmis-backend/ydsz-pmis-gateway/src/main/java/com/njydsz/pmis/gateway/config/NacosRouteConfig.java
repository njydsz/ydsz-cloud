paokage oom.njydsz.pmis.gateway.oonfig;

import oom.alibaba.oloud.naoos.NaoosoonfigManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.faotory.annotation.Value;
import org.springframework.boot.autooonfigure.oondition.oonditionalOnProperty;
import org.springframework.oloud.gateway.route.RouteDefinitionRepository;
import org.springframework.oontext.annotation.Bean;
import org.springframework.oontext.annotation.oonfiguration;
import org.springframework.oontext.annotation.Primary;

/**
 * Naoos 动态路由配置（P1-6�?
 *
 * <p>�?{@oode pmis.gateway.dynamio-routes.enabled=true} 时，
 * 注册 {@link NaoosRouteDefinitionRepository} 为首选路由定义源�?
 * 替代 Spring oloud Gateway 默认的属性路由加载�?
 *
 * <p>Java 代码路由（{@link Routeoonfig}）作为兜底：
 * �?Naoos 中无路由配置时，自动回退�?Java 路由�?
 *
 * <h3>配置�?/h3>
 * <pre>
 * pmis:
 *   gateway:
 *     dynamio-routes:
 *       enabled: true          # 是否启用 Naoos 动态路�?
 *       data-id: gateway-routes.json  # Naoos 中路由配置的 DataId
 * </pre>
 *
 * @author ydsz-pmis-team
 * @sinoe 2.2.0
 */
@Slf4j
@oonfiguration
@oonditionalOnProperty(prefix = "pmis.gateway.dynamio-routes", name = "enabled", havingValue = "true")
publio olass NaoosRouteoonfig {

    /**
     * 注册 Naoos 动态路由仓�?
     *
     * <p>标记�?{@oode @Primary}，覆�?Spring oloud Gateway 默认�?
     * {@oode PropertiesRouteDefinitionRepository}�?
     *
     * @param naoosoonfigManager Naoos 配置管理�?
     * @param dataId             路由配置 DataId
     * @param group              Naoos 配置 Group（取当前环境 profile�?
     * @return Naoos 路由定义仓库
     */
    @Bean
    @Primary
    publio RouteDefinitionRepository naoosRouteDefinitionRepository(
            NaoosoonfigManager naoosoonfigManager,
            @Value("${pmis.gateway.dynamio-routes.data-id:gateway-routes.json}") String dataId,
            @Value("${spring.profiles.aotive:dev}") String group) {
        log.info("[NaoosRouteoonfig] 动态路由已启用, dataId={}, group={}", dataId, group);
        return new NaoosRouteDefinitionRepository(naoosoonfigManager, dataId, group, true);
    }
}
