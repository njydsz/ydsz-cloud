package com.njydsz.cronjob.server.core.discovery;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;

import com.njydsz.common.feign.FeignClientConstants;
import com.njydsz.cronjob.infra.entity.job.JobNode;

/**
 * 基于 Nacos 服务发现的节点发现策略（P1-1）。
 *
 * <p>复用现有 {@code spring-cloud-starter-alibaba-nacos-discovery} 注册能力， 通过 {@link DiscoveryClient}
 * 获取在线执行器节点，替代手动维护的 ydsz_job_node 心跳表。
 *
 * <h3>工作原理</h3>
 *
 * <ol>
 *   <li>每个 cronjob 实例启动时自动注册到 Nacos（由 @EnableDiscoveryClient 驱动）
 *   <li>{@link #getOnlineNodes()} 调用 {@link DiscoveryClient#getInstances(String)} 获取存活实例
 *   <li>Nacos 自动管理实例上下线，无需手动心跳和僵尸节点回收
 * </ol>
 *
 * <p>默认启用（{@code matchIfMissing = true}），通过 {@code ydsz.cronjob.node-discovery.type=nacos} 显式指定。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnProperty(
    name = "ydsz.cronjob.node-discovery.type",
    havingValue = "nacos",
    matchIfMissing = true)
public class NacosNodeDiscoveryStrategy implements NodeDiscoveryStrategy {

  /** Nacos 注册的服务名（对应 spring.application.name，委托 {@link FeignClientConstants#CRONJOB}） */
  private static final String SERVICE_ID = FeignClientConstants.CRONJOB;

  private final DiscoveryClient discoveryClient;

  /** 当前节点 ID（hostname:port） */
  private final String localNodeId;

  /**
   * 构造基于 Nacos 服务发现的节点发现策略。
   *
   * @param discoveryClient Nacos 服务发现客户端
   * @param serverPort 本节点服务端口，用于拼接 localNodeId
   */
  public NacosNodeDiscoveryStrategy(
      DiscoveryClient discoveryClient, @Value("${server.port:0}") int serverPort) {
    this.discoveryClient = discoveryClient;
    this.localNodeId = resolveHostName() + ":" + serverPort;
    log.info("[NacosNodeDiscovery] 初始化完成, localNodeId={}", localNodeId);
  }

  /**
   * 通过 Nacos 获取在线执行器节点。
   *
   * <p>Nacos 实例本身即存活节点，直接转换为 {@link JobNode}（心跳取当前时间）， 按 nodeId 升序保证分片分配在各节点一致。异常时返回空列表。
   *
   * @return 在线节点列表，无可用时返回空列表
   */
  @Override
  public List<JobNode> getOnlineNodes() {
    try {
      List<ServiceInstance> instances = discoveryClient.getInstances(SERVICE_ID);
      if (instances == null || instances.isEmpty()) {
        log.debug("[NacosNodeDiscovery] 无在线节点实例");
        return Collections.emptyList();
      }
      List<JobNode> nodes = new ArrayList<>(instances.size());
      LocalDateTime now = LocalDateTime.now();
      for (ServiceInstance instance : instances) {
        JobNode node = new JobNode();
        node.setNodeId(instance.getHost() + ":" + instance.getPort());
        node.setHost(instance.getHost());
        node.setPort(instance.getPort());
        node.setStatus("ONLINE");
        // Nacos 实例本身就是存活的，用当前时间作为心跳时间
        node.setLastHeartbeat(now);
        node.setAppName(SERVICE_ID);
        nodes.add(node);
      }
      // 按 nodeId 升序保证分片分配确定性
      nodes.sort(Comparator.comparing(JobNode::getNodeId));
      log.debug("[NacosNodeDiscovery] 获取在线节点: count={}", nodes.size());
      return nodes;
    } catch (Exception e) {
      log.warn("[NacosNodeDiscovery] 获取在线节点失败, 返回空列表: reason={}", e.getMessage());
      return Collections.emptyList();
    }
  }

  /**
   * 获取本节点 ID（hostname:port）。
   *
   * @return 本节点唯一标识
   */
  @Override
  public String getLocalNodeId() {
    return localNodeId;
  }

  private String resolveHostName() {
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (Exception e) {
      return "unknown";
    }
  }
}
