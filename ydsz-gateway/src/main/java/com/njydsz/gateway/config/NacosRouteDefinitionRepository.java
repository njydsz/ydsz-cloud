package com.njydsz.gateway.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.nacos.api.config.listener.Listener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionRepository;
import org.springframework.context.ApplicationEventPublisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.json.type.JsonType;

/**
 * Nacos 动态路由仓库。
 *
 * <p>从 Nacos 配置中心加载网关路由定义，实现路由动态刷新： 在 Nacos Dashboard 修改路由配置后，网关秒级生效，无需重启。
 *
 * <h3>Nacos 配置格式</h3>
 *
 * <p>DataId: {@code gateway-routes.json}（可通过 {@code ydsz.gateway.dynamic-routes.data-id} 配置） <br>
 * Group: 当前环境对应的 group（dev/sit/uat/prod）
 *
 * <p>JSON 数组格式，每项为一个 {@link RouteDefinition}：
 *
 * <pre>
 * [
 *   {
 *     "id": "ydsz-userinfo",
 *     "uri": "lb://ydsz-userinfo",
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
 *
 * <p>Nacos 路由优先于 {@link GatewayRouteConfig} 中的 Java 路由。 若 Nacos 中无路由配置（空或解析失败），则回退到 Java 路由。
 *
 * <h3>设计说明</h3>
 *
 * <p>路由校验（Route ID 唯一性、格式、URI 合法性等）应在配置时（Nacos Dashboard / CI/CD）完成， 而非在网关运行时执行。网关仅做基础 JSON 解析，解析失败时回退到 Java 路由。
 *
 * @since 1.0.0
 * @author ydsz-team
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

  /** Spring 事件发布器（用于触发路由刷新） */
  private final ApplicationEventPublisher eventPublisher;

  /** 配置变更监听器执行器（由 ydsz-common-thread 统一管理，可为 null） */
  private final Executor routeListenerExecutor;

  /** 配置变更监听器（已注册状态标记） */
  private volatile boolean listenerRegistered = false;

  /**
   * 内存缓存（避免每次请求同步阻塞调用 Nacos getConfig）。
   *
   * <p>启动时加载路由到内存，配置变更时通过 Listener 回调刷新。 使用 AtomicReference 保证线程安全的缓存切换。
   */
  private final AtomicReference<List<RouteDefinition>> routeCache =
      new AtomicReference<>(Collections.emptyList());

  /**
   * 构造 Nacos 动态路由仓库。
   *
   * @param nacosConfigManager Nacos 配置管理器
   * @param dataId 路由配置 DataId
   * @param group Nacos 配置 Group
   * @param enabled 是否启用
   * @param eventPublisher Spring 事件发布器（用于触发路由刷新）
   * @param routeListenerExecutor 配置变更监听器执行器（ydsz-common-thread 托管；可为 null）
   */
  public NacosRouteDefinitionRepository(
      NacosConfigManager nacosConfigManager,
      String dataId,
      String group,
      boolean enabled,
      ApplicationEventPublisher eventPublisher,
      Executor routeListenerExecutor) {
    this.nacosConfigManager = nacosConfigManager;
    this.dataId = (dataId != null && !dataId.isBlank()) ? dataId : DEFAULT_DATA_ID;
    this.group = group;
    this.enabled = enabled;
    this.eventPublisher = eventPublisher;
    this.routeListenerExecutor = routeListenerExecutor;

    if (enabled) {
      loadRoutesFromNacos();
      registerConfigListener();
    }
  }

  /**
   * 从内存缓存返回路由定义（避免每次请求同步阻塞调用 Nacos getConfig）。
   *
   * <p>路由在构造时加载到 {@link #routeCache}，配置变更时通过 Nacos Listener 回调刷新。 此方法仅读取内存缓存，无网络 I/O，不会阻塞 Netty
   * EventLoop 线程。
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
   * 从 Nacos 加载路由到内存缓存。
   *
   * <p>在构造器和配置变更监听器中调用。同步调用 Nacos getConfig 仅发生在启动/刷新时， 不在请求处理路径中，不会阻塞 Netty EventLoop。
   */
  private void loadRoutesFromNacos() {
    try {
      String config = nacosConfigManager.getConfigService().getConfig(dataId, group, 5000);
      if (config == null || config.isBlank()) {
        log.debug("[NacosRoutes] Nacos 中无路由配置 dataId={} group={}，回退到 Java 路由", dataId, group);
        routeCache.set(Collections.emptyList());
        return;
      }

      List<RouteDefinition> routes =
          YdszJson.fromJson(config, new JsonType<List<RouteDefinition>>() {});
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
   * <p>路由配置的唯一定义来源是 Nacos 配置中心（{@code gateway-routes.json}）， 网关不提供通过 API 动态写入路由的能力，故此处直接返回空的完成信号。
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
   * <p>与 {@link #save} 同理，路由生命周期完全由 Nacos 配置管理， 网关侧不提供运行时删除入口，直接返回空完成信号。
   *
   * @param routeId 待删除的路由 ID（本实现忽略）
   * @return 空完成信号
   */
  @Override
  public Mono<Void> delete(Mono<String> routeId) {
    return Mono.empty();
  }

  /**
   * 注册 Nacos 配置变更监听器。
   *
   * <p>当 Nacos 中的路由配置变更时：
   *
   * <ol>
   *   <li>重新加载路由到内存缓存
   *   <li>触发 {@code RefreshRoutesEvent} 通知 Spring Cloud Gateway 刷新路由表
   * </ol>
   *
   * <p>监听器回调执行器由 {@link #routeListenerExecutor}（ydsz-common-thread 托管）提供，避免自建线程池。
   */
  private void registerConfigListener() {
    if (listenerRegistered) {
      return;
    }

    try {
      nacosConfigManager
          .getConfigService()
          .addListener(
              dataId,
              group,
              new Listener() {
                /**
                 * Nacos 配置变更回调：路由定义被修改时触发。
                 *
                 * <p>重新加载路由到内存缓存（{@link #routeCache}）并发布 {@code RefreshRoutesEvent}，使 Spring Cloud
                 * Gateway 实时刷新路由表， 实现秒级生效、无需重启网关。
                 *
                 * @param configInfo Nacos 推送的最新路由配置（JSON 数组字符串）
                 */
                @Override
                public void receiveConfigInfo(String configInfo) {
                  // P0-B5: 路由变更审计——记录变更时间、来源配置与路由条数，供变更追溯
                  int routeCount = routeCache.get().size();
                  log.info(
                      "[NacosRoutes] 检测到路由配置变更 dataId={} group={} 变更前路由数={} (触发时间={})",
                      dataId,
                      group,
                      routeCount,
                      java.time.OffsetDateTime.now());
                  loadRoutesFromNacos();
                  eventPublisher.publishEvent(new RefreshRoutesEvent(this));
                  log.info(
                      "[NacosRoutes] 已触发路由刷新事件 dataId={} group={} 变更后路由数={}",
                      dataId,
                      group,
                      routeCache.get().size());
                }

                /**
                 * 返回监听器回调执行的线程池。
                 *
                 * <p>返回类级 {@link #routeListenerExecutor}（由 ydsz-common-thread 托管， 配置项
                 * ydsz.thread.pools.nacosRouteListener），避免自建线程池（规范 15.4.1）。 若该托管执行器未配置则为 null，由 Nacos
                 * 客户端使用默认线程。
                 *
                 * @return 监听器回调执行器
                 */
                @Override
                public Executor getExecutor() {
                  return routeListenerExecutor;
                }
              });
      listenerRegistered = true;
      log.info("[NacosRoutes] 配置变更监听器已注册 dataId={} group={}", dataId, group);
    } catch (Exception e) {
      log.warn("[NacosRoutes] 注册配置变更监听器失败: {}", e.getMessage());
    }
  }
}
