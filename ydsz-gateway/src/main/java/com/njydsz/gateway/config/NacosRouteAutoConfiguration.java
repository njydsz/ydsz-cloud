package com.njydsz.gateway.config;

import java.util.concurrent.Executor;

import com.alibaba.cloud.nacos.NacosConfigManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Nacos 动态路由装配（网关唯一"运行时可改"的路由来源）。
 *
 * <p>整改前 {@code GatewayRouteConfig} 的 {@code @Configuration} 被注释掉，却仍留在
 * {@code AutoConfiguration.imports} 中；{@link NacosRouteDefinitionRepository} 也没有任何地方构造它。
 * 于是"路由的唯一入口是 Nacos"这句注释从不成立 —— 运维在 Nacos 上改 {@code gateway-routes.json}
 * 不会生效，真正生效的一直是 jar 内的静态路由。本类把这条链接上，并在链接再次断开时显式报错。
 *
 * <h3>生效条件（三者同时满足）</h3>
 *
 * <ol>
 *   <li>{@code ydsz.gateway.dynamic-routes.enabled=true}（默认关闭：本地无 Nacos 时不该启用）
 *   <li>容器中存在 {@link NacosConfigManager}，即 {@code spring.cloud.nacos.config.enabled=true}
 *       （bootstrap.yml 默认 false，部署时用环境变量 SPRING_CLOUD_NACOS_CONFIG_ENABLED 打开）
 *   <li>Nacos 中已在 {@code spring.cloud.nacos.config.group} 分组下发布 DataId
 *       {@code ydsz.gateway.dynamic-routes.data-id}（默认 gateway-routes.json）
 * </ol>
 *
 * <h3>与静态路由的关系</h3>
 *
 * <p>Spring Cloud Gateway 的 CompositeRouteDefinitionLocator 会把"配置属性路由"和
 * {@link NacosRouteDefinitionRepository} 合并，<b>同 id 时本仓库覆盖静态清单</b>。因此
 * {@code routes-nacos.yaml} 与 application.yml 的生成块必须逐条等价；该不变量由
 * {@code data/scripts/gen-gateway-routes.py}（生成）与 {@code data/scripts/check-gateway-routes.py}
 * （校验，R1/R8）在 CI 拦截。
 *
 * @author ydsz-team
 * @since 26.10.26
 */
@Slf4j
@AutoConfiguration(afterName = "com.alibaba.cloud.nacos.NacosConfigAutoConfiguration")
@ConditionalOnClass(NacosConfigManager.class)
@ConditionalOnProperty(
    prefix = "ydsz.gateway.dynamic-routes",
    name = "enabled",
    havingValue = "true")
public class NacosRouteAutoConfiguration {

  /** 动态路由 DataId 配置键。 */
  private static final String DATA_ID_KEY = "ydsz.gateway.dynamic-routes.data-id";

  /** 动态路由 DataId 默认值（与 routes-nacos.yaml 头部约定一致）。 */
  private static final String DEFAULT_DATA_ID = "gateway-routes.json";

  /** 监听回调线程池名（= ydsz.thread.pools 下的键，由 ThreadPoolRegistrar 按名注册）。 */
  private static final String LISTENER_EXECUTOR_BEAN = "nacosRouteListener";

  /**
   * 注册 Nacos 动态路由仓库。
   *
   * <p>监听回调执行器取 ydsz-common-thread 托管的 {@code nacosRouteListener} 池（规范 15.4.1
   * 禁止自建线程池）；未配置该池时传 null，由 Nacos 客户端使用默认线程。
   *
   * @param nacosConfigManager Nacos 配置管理器
   * @param eventPublisher Spring 事件发布器，用于发布 RefreshRoutesEvent
   * @param listenerExecutorProvider 托管监听回调执行器（可为空）
   * @param environment 环境，用于解析 DataId 与 Nacos 分组
   * @return 动态路由仓库
   */
  @Bean
  @ConditionalOnBean(NacosConfigManager.class)
  public NacosRouteDefinitionRepository nacosRouteDefinitionRepository(
      NacosConfigManager nacosConfigManager,
      ApplicationEventPublisher eventPublisher,
      @Qualifier(LISTENER_EXECUTOR_BEAN) ObjectProvider<Executor> listenerExecutorProvider,
      Environment environment) {
    String dataId = environment.getProperty(DATA_ID_KEY, DEFAULT_DATA_ID);
    String group =
        environment.getProperty(
            "spring.cloud.nacos.config.group",
            environment.getProperty("spring.profiles.active", "DEFAULT_GROUP"));
    Executor listenerExecutor = listenerExecutorProvider.getIfAvailable();
    log.info(
        "[NacosRoutes] 动态路由已装配 dataId={} group={} 回调线程={}",
        dataId,
        group,
        listenerExecutor == null ? "Nacos 默认线程" : LISTENER_EXECUTOR_BEAN);
    return new NacosRouteDefinitionRepository(
        nacosConfigManager, dataId, group, true, eventPublisher, listenerExecutor);
  }

  /**
   * 启动就绪后复核动态路由是否真的生效。
   *
   * <p>典型误配是打开了 {@code dynamic-routes.enabled=true} 却忘了打开 {@code
   * spring.cloud.nacos.config.enabled}：此时仓库不会创建，流量仍走 jar 内静态路由，
   * 而运维以为 Nacos 上刚改的路由已经下发。这种偏差必须显式可见，不能只留一行 DEBUG。
   *
   * @param repositoryProvider 动态路由仓库提供者
   * @param environment 环境，用于回报 Nacos 配置客户端开关
   * @return 启动就绪事件监听器
   */
  @Bean
  public ApplicationListener<ApplicationReadyEvent> dynamicRouteActivationGuard(
      ObjectProvider<NacosRouteDefinitionRepository> repositoryProvider,
      Environment environment) {
    return event -> {
      if (repositoryProvider.getIfAvailable() != null) {
        return;
      }
      log.error(
          "[NacosRoutes] 已声明 ydsz.gateway.dynamic-routes.enabled=true，但仓库未创建"
              + "（spring.cloud.nacos.config.enabled={}）：当前生效的是 jar 内静态路由，"
              + "Nacos 上的 {} 不会被加载",
          environment.getProperty("spring.cloud.nacos.config.enabled", "false"),
          environment.getProperty(DATA_ID_KEY, DEFAULT_DATA_ID));
    };
  }
}
