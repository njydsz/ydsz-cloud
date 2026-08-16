package com.njydsz.gateway.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
 * Nacos 动态路由仓库（P3-7 增强版：配置校验）
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
 * <p>Nacos 路由优先于 {@link RouteConfig} 中的 Java 路由。 若 Nacos 中无路由配置（空或解析失败），则回退到 Java 路由。
 *
 * <h3>P3-7 增强：路由配置校验</h3>
 *
 * <p>加载路由时自动执行以下校验，校验失败的路由会被跳过并记录告警日志：
 *
 * <ul>
 *   <li>Route ID 唯一性校验（重复 ID 仅保留第一个）
 *   <li>Route ID 格式校验（仅允许字母、数字、连字符、下划线）
 *   <li>URI 合法性校验（必须以 {@code lb://} 开头）
 *   <li>Predicate 存在性校验（至少有一个 Path predicate）
 * </ul>
 *
 * @since 3.7.0
 * @author ydsz-team
 */
@Slf4j
public class NacosRouteDefinitionRepository implements RouteDefinitionRepository {

  /** Nacos 配置中路由定义的 DataId */
  private static final String DEFAULT_DATA_ID = "gateway-routes.json";

  /** Route ID 格式正则：字母开头，允许字母、数字、连字符、下划线 */
  private static final String ROUTE_ID_PATTERN = "^[a-zA-Z][a-zA-Z0-9_-]{1,63}$";

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
   *
   * <p>启动时加载路由到内存，配置变更时通过 Listener 回调刷新。 使用 AtomicReference 保证线程安全的缓存切换。
   */
  private final AtomicReference<List<RouteDefinition>> routeCache =
      new AtomicReference<>(Collections.emptyList());

  /**
   * P1-1: 路由配置变更监听器执行器（由 ydsz-common-thread 统一管理，配置项: ydsz.thread.pools.nacosRouteListener）。
   * 若未配置托管线程池，构造时传入 null，由 Nacos 客户端使用默认线程执行回调，避免自建线程池（规范 15.4.1）。
   */
  private final Executor routeListenerExecutor;

  /** P3-7: 路由校验结果（供监控和健康检查使用） */
  private final AtomicReference<RouteValidationResult> validationResult =
      new AtomicReference<>(new RouteValidationResult());

  /**
   * 构造 Nacos 动态路由仓库
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
   * P3-7: 获取路由校验结果
   *
   * @return 最近一次路由校验结果
   */
  public RouteValidationResult getValidationResult() {
    return validationResult.get();
  }

  /**
   * P0-5: 从 Nacos 加载路由到内存缓存（含 P3-7 配置校验）
   *
   * <p>在构造器和配置变更监听器中调用。同步调用 Nacos getConfig 仅发生在启动/刷新时， 不在请求处理路径中，不会阻塞 Netty EventLoop。
   */
  private void loadRoutesFromNacos() {
    try {
      String config = nacosConfigManager.getConfigService().getConfig(dataId, group, 5000);
      if (config == null || config.isBlank()) {
        log.debug("[NacosRoutes] Nacos 中无路由配置 dataId={} group={}，回退到 Java 路由", dataId, group);
        routeCache.set(Collections.emptyList());
        validationResult.set(new RouteValidationResult());
        return;
      }

      List<RouteDefinition> routes =
          YdszJson.fromJson(config, new JsonType<List<RouteDefinition>>() {});
      if (routes == null) {
        routes = Collections.emptyList();
      }

      // P3-7: 路由配置校验
      RouteValidationResult validation = validateRoutes(routes);
      validationResult.set(validation);

      routeCache.set(validation.getValidRoutes());
      log.info(
          "[NacosRoutes] 从 Nacos 加载 {} 条路由定义（有效 {} 条，跳过 {} 条） dataId={} group={}",
          routes.size(),
          validation.getValidRoutes().size(),
          validation.getSkippedCount(),
          dataId,
          group);
    } catch (Exception e) {
      log.warn(
          "[NacosRoutes] 从 Nacos 加载路由失败，回退到 Java 路由: dataId={} err={}", dataId, e.getMessage());
      routeCache.set(Collections.emptyList());
      validationResult.set(new RouteValidationResult());
    }
  }

  /**
   * P3-7: 校验路由配置列表
   *
   * <p>执行以下校验规则：
   *
   * <ul>
   *   <li>Route ID 唯一性（重复 ID 仅保留第一个）
   *   <li>Route ID 格式（字母开头，2-64 字符，仅含字母数字连字符下划线）
   *   <li>URI 必须以 {@code lb://} 开头
   *   <li>至少有一个 Path predicate
   * </ul>
   *
   * @param routes 原始路由列表
   * @return 校验结果（含有效路由列表和跳过原因）
   */
  private RouteValidationResult validateRoutes(List<RouteDefinition> routes) {
    List<RouteDefinition> validRoutes = new ArrayList<>();
    List<String> skipReasons = new ArrayList<>();
    Set<String> seenIds = new HashSet<>();

    for (int i = 0; i < routes.size(); i++) {
      RouteDefinition route = routes.get(i);
      String indexInfo = "routes[" + i + "]";

      // 校验 Route ID
      if (route.getId() == null || route.getId().isBlank()) {
        skipReasons.add(indexInfo + ": Route ID 为空");
        log.warn("[NacosRoutes] 跳过路由: {} Route ID 为空", indexInfo);
        continue;
      }

      // Route ID 唯一性校验
      if (seenIds.contains(route.getId())) {
        skipReasons.add(indexInfo + ": Route ID 重复 '" + route.getId() + "'");
        log.warn("[NacosRoutes] 跳过路由: {} Route ID 重复 '{}'", indexInfo, route.getId());
        continue;
      }

      // Route ID 格式校验
      if (!route.getId().matches(ROUTE_ID_PATTERN)) {
        skipReasons.add(indexInfo + ": Route ID 格式非法 '" + route.getId() + "'（需字母开头，2-64字符）");
        log.warn("[NacosRoutes] 跳过路由: {} Route ID 格式非法 '{}'", indexInfo, route.getId());
        continue;
      }

      // URI 合法性校验
      String uri = route.getUri() != null ? route.getUri().toString() : "";
      if (!uri.startsWith("lb://")) {
        skipReasons.add(indexInfo + ": URI 非法 '" + uri + "'（需以 lb:// 开头）");
        log.warn("[NacosRoutes] 跳过路由: {} URI 非法 '{}'", indexInfo, uri);
        continue;
      }

      // Predicate 存在性校验
      if (route.getPredicates() == null || route.getPredicates().isEmpty()) {
        skipReasons.add(indexInfo + ": 缺少 Predicate 配置");
        log.warn("[NacosRoutes] 跳过路由: {} 缺少 Predicate 配置", indexInfo);
        continue;
      }

      // 通过校验
      seenIds.add(route.getId());
      validRoutes.add(route);
    }

    return new RouteValidationResult(validRoutes, skipReasons);
  }

  /**
   * P3-7: 路由配置预检（供外部调用，不实际加载路由）
   *
   * <p>用于在 Nacs 保存配置前预检，发现潜在问题。
   *
   * @param configJson Nacos 路由配置 JSON 字符串
   * @return 校验结果
   */
  public RouteValidationResult validateConfig(String configJson) {
    if (configJson == null || configJson.isBlank()) {
      return new RouteValidationResult();
    }
    try {
      List<RouteDefinition> routes =
          YdszJson.fromJson(configJson, new JsonType<List<RouteDefinition>>() {});
      if (routes == null) {
        return new RouteValidationResult();
      }
      return validateRoutes(routes);
    } catch (Exception e) {
      RouteValidationResult result = new RouteValidationResult();
      result.setSkipReasons(List.of("JSON 解析失败: " + e.getMessage()));
      return result;
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
   * P2-12 + P0-5 + P0-6: 注册 Nacos 配置变更监听器
   *
   * <p>当 Nacos 中的路由配置变更时：
   *
   * <ol>
   *   <li>P0-5: 重新加载路由到内存缓存
   *   <li>触发 {@code RefreshRoutesEvent} 通知 Spring Cloud Gateway 刷新路由表
   * </ol>
   *
   * <p>P1-1: 监听器回调执行器由 {@link #routeListenerExecutor}（ydsz-common-thread 托管）提供，避免自建线程池。
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
                 * <p>返回类级 {@link #routeListenerExecutor}（由 ydsz-common-thread 托管， 配置项
                 * ydsz.thread.pools.nacosRouteListener），避免自建线程池（P1-1）。 若该托管执行器未配置则为 null，由 Nacos
                 * 客户端使用默认线程。
                 *
                 * @return 监听器回调执行器
                 */
                @Override
                public Executor getExecutor() {
                  // P1-1: 返回 ydsz-common-thread 托管执行器；若未配置则 null，由 Nacos 使用默认线程
                  return routeListenerExecutor;
                }
              });
      listenerRegistered = true;
      log.info("[NacosRoutes] 配置变更监听器已注册 dataId={} group={}", dataId, group);
    } catch (Exception e) {
      log.warn("[NacosRoutes] 注册配置变更监听器失败: {}", e.getMessage());
    }
  }

  /** P3-7: 路由校验结果 */
  public static class RouteValidationResult {
    /** 有效路由列表 */
    private final List<RouteDefinition> validRoutes;

    /** 跳过原因列表 */
    private List<String> skipReasons;

    /** 跳过的路由数量 */
    private final int skippedCount;

    /** 默认构造（空结果） */
    public RouteValidationResult() {
      this.validRoutes = Collections.emptyList();
      this.skipReasons = Collections.emptyList();
      this.skippedCount = 0;
    }

    /**
     * 构造校验结果
     *
     * @param validRoutes 有效路由
     * @param skipReasons 跳过原因
     */
    public RouteValidationResult(List<RouteDefinition> validRoutes, List<String> skipReasons) {
      this.validRoutes = validRoutes != null ? validRoutes : Collections.emptyList();
      this.skipReasons = skipReasons != null ? skipReasons : Collections.emptyList();
      this.skippedCount = this.skipReasons.size();
    }

    public List<RouteDefinition> getValidRoutes() {
      return validRoutes;
    }

    public List<String> getSkipReasons() {
      return skipReasons;
    }

    public int getSkippedCount() {
      return skippedCount;
    }

    public void setSkipReasons(List<String> skipReasons) {
      this.skipReasons = skipReasons;
    }

    /**
     * 是否全部通过校验
     *
     * @return true=全部有效
     */
    public boolean isValid() {
      return skippedCount == 0;
    }
  }
}
