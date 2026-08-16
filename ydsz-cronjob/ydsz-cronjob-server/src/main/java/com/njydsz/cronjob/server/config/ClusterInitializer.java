package com.njydsz.cronjob.server.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.njydsz.cronjob.server.core.dispatch.CrossClusterDispatcher;

/**
 * 跨集群调度初始化器（P3-12）。
 *
 * <p>在应用启动后，从 {@link CronjobProperties} 读取集群端点配置， 初始化 {@link CrossClusterDispatcher} 的集群端点映射。
 *
 * <p>配置示例（application.yml）:
 *
 * <pre>{@code
 * ydsz:
 *   cronjob:
 *     clusters:
 *       endpoints:
 *         cluster-bj: http://10.0.1.10:8080
 *         cluster-sh: http://10.0.2.10:8080
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClusterInitializer {

  private final CronjobProperties cronjobProperties;
  private final CrossClusterDispatcher crossClusterDispatcher;

  /** 应用启动后初始化集群端点。 */
  @EventListener(ApplicationReadyEvent.class)
  public void initClusterEndpoints() {
    var endpoints = cronjobProperties.getClusters().getEndpoints();
    if (endpoints == null || endpoints.isEmpty()) {
      log.info("[ClusterInitializer] 未配置跨集群端点, 跨集群调度功能不可用");
      return;
    }
    crossClusterDispatcher.initClusters(endpoints);
    log.info(
        "[ClusterInitializer] 跨集群端点初始化完成: count={} clusters={}",
        endpoints.size(),
        endpoints.keySet());
  }
}
