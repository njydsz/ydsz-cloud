package com.remisoft.gateway.config;

import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.nacos.api.config.listener.Listener;
import com.remisoft.common.json.RemiJson;
import com.remisoft.common.json.type.JsonType;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.annotation.PreDestroy;

/**
 * Nacos 动态路由仓库（P1-6 + P2-12 增强）
 *
 * <p>从 Nacos 配置中心加载网关路由定义，实现路由动态刷新：
 * 在 Nacos Dashboard 修改路由配置后，网关秒级生效，无需重启。
 *
 * <h3>Nacos 配置格式</h3>
 * <p>DataId: {@code gateway-routes.json}（可通过 {@code remi.gateway.dynamic-routes.data-id} 配置）
 * <br>Group: 当前环境对应的 group（dev/sit/uat/prod）
 *
 * <p>JSON 数组格式，每项为一个 {@link RouteDefinition}：
 * <pre>
 * [
 *   {
 *     "id": "remi-userinfo",
 *     "uri": "lb://remi-userinfo",
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
 * @since 1.0.0
 * @author remi-team
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
     * P0-5: 内存缓存（避免每次请求同步阻塞调用 Nacos getConfig）
     * <p>启动时加载路由到内存，配置变更时通过 Listener 回调刷新。
     * 使用 AtomicReference 保证线程安全的缓存切换。
     */
    private final AtomicReference<List<RouteDefinition>> routeCache = new AtomicReference<>(Collections.emptyList());

    /**
     * P0-6: 共享单线程 Executor（避免每次 listener 回调创建新线程池导致泄漏）
     */
    private final ExecutorService sharedExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "nacos-route-listener");
        t.setDaemon(true);
        return t;
    });

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
            // P0-5: 启动时加载路由到内存缓存
            loadRoutesFromNacos();
            registerConfigListener();
        }
    }

    /**
     * P0-5: 从内存缓存返回路由定义（避免每次请求同步阻塞调用 Nacos getConfig）
     *
     * <p>路由在构造时加载到 {@link #routeCache}，配置变更时通过 Nacos Listener 回调刷新。
     * 此方法仅读取内存缓存，无网络 I/O，不会阻塞 Netty EventLoop 线程。
     *
     * @return 路由定义 Flux
     */
    @Override
    public Flux<RouteDefinition> getRouteDefinitions() {
        if (!enabled) {
            return Flux.empty();
        }
        return Flux.fromIterable(routeCache.get());
    }

    /**
     * P0-5: 从 Nacos 加载路由到内存缓存
     *
     * <p>在构造器和配置变更监听器中调用。同步调用 Nacos getConfig 仅发生在启动/刷新时，
     * 不在请求处理路径中，不会阻塞 Netty EventLoop。
     */
    private void loadRoutesFromNacos() {
        try {
            String config = nacosConfigManager.getConfigService()
                    .getConfig(dataId, group, 5000);
            if (config == null || config.isBlank()) {
                log.debug("[NacosRoutes] Nacos 中无路由配置 dataId={} group={}，回退到 Java 路由", dataId, group);
                routeCache.set(Collections.emptyList());
                return;
            }

            List<RouteDefinition> routes = RemiJson.fromJson(config,
                    new JsonType<List<RouteDefinition>>() {});
            if (routes == null) {
                routes = Collections.emptyList();
            }

            routeCache.set(routes);
            log.info("[NacosRoutes] 从 Nacos 加载 {} 条路由定义 dataId={} group={}", routes.size(), dataId, group);
        } catch (Exception e) {
            log.warn("[NacosRoutes] 从 Nacos 加载路由失败，回退到 Java 路由: dataId={} err={}", dataId, e.getMessage());
            routeCache.set(Collections.emptyList());
        }
    }

    /**
     * 保存路由定义（当前为只读实现，不支持写操作）。
     *
     * <p>路由配置的唯一定义来源是 Nacos 配置中心（{@code gateway-routes.json}），
     * 网关不提供通过 API 动态写入路由的能力，故此处直接返回空的完成信号。
     * 新增 / 变更路由请在 Nacos Dashboard 修改配置后由 {@link #receiveConfigInfo} 监听刷新。
     *
     * @param route 待保存的路由定义（本实现忽略）
     * @return 空完成信号
     */
    @Override
    public Mono<Void> save(Mono<RouteDefinition> route) {
        return Mono.empty();
    }

    /**
     * 删除路由定义（当前为只读实现，不支持删除）。
     *
     * <p>与 {@link #save} 同理，路由生命周期完全由 Nacos 配置管理，
     * 网关侧不提供运行时删除入口，直接返回空完成信号。
     *
     * @param routeId 待删除的路由 ID（本实现忽略）
     * @return 空完成信号
     */
    @Override
    public Mono<Void> delete(Mono<String> routeId) {
        return Mono.empty();
    }

    /**
     * P2-12 + P0-5 + P0-6: 注册 Nacos 配置变更监听器
     * <p>当 Nacos 中的路由配置变更时：
     * <ol>
     *   <li>P0-5: 重新加载路由到内存缓存</li>
     *   <li>触发 {@code RefreshRoutesEvent} 通知 Spring Cloud Gateway 刷新路由表</li>
     * </ol>
     * <p>P0-6: 使用共享 {@link #sharedExecutor} 替代每次创建新线程池。
     */
    private void registerConfigListener() {
        if (listenerRegistered) {
            return;
        }

        try {
            nacosConfigManager.getConfigService().addListener(dataId, group, new Listener() {
                /**
                 * Nacos 配置变更回调：路由定义被修改时触发。
                 *
                 * <p>重新加载路由到内存缓存（{@link #routeCache}）并发布
                 * {@code RefreshRoutesEvent}，使 Spring Cloud Gateway 实时刷新路由表，
                 * 实现秒级生效、无需重启网关。
                 *
                 * @param configInfo Nacos 推送的最新路由配置（JSON 数组字符串）
                 */
                @Override
                public void receiveConfigInfo(String configInfo) {
                    log.info("[NacosRoutes] 检测到路由配置变更 dataId={} group={}", dataId, group);
                    // P0-5: 重新加载路由到内存缓存
                    loadRoutesFromNacos();
                    // 触发路由刷新事件
                    eventPublisher.publishEvent(new RefreshRoutesEvent(this));
                    log.info("[NacosRoutes] 已触发路由刷新事件");
                }

                /**
                 * 返回监听器回调执行的线程池。
                 *
                 * <p>复用类级共享单线程 {@link #sharedExecutor}（守护线程），
                 * 避免每次配置变更都新建线程池导致线程泄漏（P0-6）。
                 *
                 * @return 共享单线程执行器
                 */
                @Override
                public Executor getExecutor() {
                    // P0-6: 返回共享 Executor，避免每次创建新线程池导致线程泄漏
                    return sharedExecutor;
                }
            });
            listenerRegistered = true;
            log.info("[NacosRoutes] 配置变更监听器已注册 dataId={} group={}", dataId, group);
        } catch (Exception e) {
            log.warn("[NacosRoutes] 注册配置变更监听器失败: {}", e.getMessage());
        }
    }

    /**
     * P0-2: 优雅关闭线程池，避免线程泄漏
     */
    @PreDestroy
    public void shutdown() {
        if (!sharedExecutor.isShutdown()) {
            sharedExecutor.shutdown();
            log.info("[NacosRoutes] 共享线程池已关闭");
        }
    }
}
