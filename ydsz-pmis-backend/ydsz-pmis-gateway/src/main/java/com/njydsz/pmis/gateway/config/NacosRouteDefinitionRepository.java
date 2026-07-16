package com.njydsz.pmis.gateway.config;

import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.nacos.api.config.listener.Listener;
import com.njydsz.pmis.common.json.Json;
import com.njydsz.pmis.common.json.type.JsonType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionRepository;
import org.springframework.context.ApplicationEventPublisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Nacos 动态路由仓库（P1-6 + P2-12 增强）
 *
 * <p>从 Nacos 配置中心加载网关路由定义，实现路由动态刷新：
 * 在 Nacos Dashboard 修改路由配置后，网关秒级生效，无需重启。
 *
 * <h3>Nacos 配置格式</h3>
 * <p>DataId: {@code gateway-routes.json}（可通过 {@code pmis.gateway.dynamic-routes.data-id} 配置）
 * <br>Group: 当前环境对应的 group（dev/sit/uat/prod）
 *
 * <p>JSON 数组格式，每项为一个 {@link RouteDefinition}：
 * <pre>
 * [
 *   {
 *     "id": "ydsz-pmis-userinfo",
 *     "uri": "lb://ydsz-pmis-userinfo",
 *     "predicates": [
 *       { "name": "Path", "args": { "pattern": "/auth/**" } }
 *     ],
 *     "filters": [],
 *     "order": 0
 *   }
 * ]
 * </pre>
 *
 * <h3>与 Java 路由配置的关系</h3>
 * <p>Nacos 路由优先于 {@link RouteConfig} 中的 Java 路由。
 * 若 Nacos 中无路由配置（空或解析失败），则回退到 Java 路由。
 *
 * <h3>P2-12 增强项</h3>
 * <p>配置变更自动监听：Nacos 配置更新后自动触发 {@code RefreshRoutesEvent}，
 * 无需手动重启网关即可实时生效。
 *
 * @since 2.2.0
 */
@Slf4j
public class NacosRouteDefinitionRepository implements RouteDefinitionRepository {

    /** Nacos 配置中路由定义的 DataId */
    private static final String DEFAULT_DATA_ID = "gateway-routes.json";

    /** Nacos 配置管理器 */
    private final NacosConfigManager nacosConfigManager;

    /** 路由配置 DataId（可通过配置覆盖） */
    private final String dataId;

    /** Nacos 配置 Group */
    private final String group;

    /** 是否启用动态路由 */
    private final boolean enabled;

    /** P2-12: Spring 事件发布器（用于触发路由刷新） */
    private final ApplicationEventPublisher eventPublisher;

    /** P2-12: 配置变更监听器（已注册状态标记） */
    private volatile boolean listenerRegistered = false;

    /**
     * 构造 Nacos 动态路由仓库
     *
     * @param nacosConfigManager Nacos 配置管理器
     * @param dataId             路由配置 DataId
     * @param group              Nacos 配置 Group
     * @param enabled            是否启用
     * @param eventPublisher     Spring 事件发布器（用于触发路由刷新）
     */
    public NacosRouteDefinitionRepository(NacosConfigManager nacosConfigManager,
                                         String dataId, String group, boolean enabled,
                                         ApplicationEventPublisher eventPublisher) {
        this.nacosConfigManager = nacosConfigManager;
        this.dataId = (dataId != null && !dataId.isBlank()) ? dataId : DEFAULT_DATA_ID;
        this.group = group;
        this.enabled = enabled;
        this.eventPublisher = eventPublisher;

        // P2-12: 注册配置变更监听器
        if (enabled) {
            registerConfigListener();
        }
    }

    /**
     * 从 Nacos 加载路由定义
     *
     * <p>若 Nacos 中无配置或解析失败，返回空列表（回退到 Java 路由）。
     *
     * @return 路由定义 Flux
     */
    @Override
    public Flux<RouteDefinition> getRouteDefinitions() {
        if (!enabled) {
            return Flux.empty();
        }

        try {
            String config = nacosConfigManager.getConfigService()
                    .getConfig(dataId, group, 5000);
            if (config == null || config.isBlank()) {
                log.debug("[NacosRoutes] Nacos 中无路由配置 dataId={} group={}，回退到 Java 路由", dataId, group);
                return Flux.empty();
            }

            List<RouteDefinition> routes = Json.fromJson(config,
                    new JsonType<List<RouteDefinition>>() {});
            if (routes == null) {
                routes = Collections.emptyList();
            }

            log.info("[NacosRoutes] 从 Nacos 加载 {} 条路由定义 dataId={} group={}", routes.size(), dataId, group);
            return Flux.fromIterable(routes);
        } catch (Exception e) {
            log.warn("[NacosRoutes] 从 Nacos 加载路由失败，回退到 Java 路由: dataId={} err={}", dataId, e.getMessage());
            return Flux.empty();
        }
    }

    @Override
    public Mono<Void> save(Mono<RouteDefinition> route) {
        return Mono.empty();
    }

    @Override
    public Mono<Void> delete(Mono<String> routeId) {
        return Mono.empty();
    }

    /**
     * P2-12: 注册 Nacos 配置变更监听器
     * <p>当 Nacos 中的路由配置变更时，自动触发 {@code RefreshRoutesEvent}。
     */
    private void registerConfigListener() {
        if (listenerRegistered) {
            return;
        }

        try {
            nacosConfigManager.getConfigService().addListener(dataId, group, new Listener() {
                @Override
                public void receiveConfigInfo(String configInfo) {
                    log.info("[NacosRoutes] 检测到路由配置变更 dataId={} group={}", dataId, group);
                    // 触发路由刷新事件
                    eventPublisher.publishEvent(new RefreshRoutesEvent(this));
                    log.info("[NacosRoutes] 已触发路由刷新事件");
                }

                @Override
                public Executor getExecutor() {
                    return Executors.newSingleThreadExecutor(r -> {
                        Thread t = new Thread(r, "nacos-route-listener");
                        t.setDaemon(true);
                        return t;
                    });
                }
            });
            listenerRegistered = true;
            log.info("[NacosRoutes] 配置变更监听器已注册 dataId={} group={}", dataId, group);
        } catch (Exception e) {
            log.warn("[NacosRoutes] 注册配置变更监听器失败: {}", e.getMessage());
        }
    }
}
