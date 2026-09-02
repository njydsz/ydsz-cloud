package com.njydsz.literule.server.distributed;

import java.net.InetAddress;

import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.njydsz.literule.server.config.LiteRuleProperties;
import com.njydsz.literule.server.spi.RuleConfigBroadcaster;

/**
 * 分布式模式自动配置。
 *
 * <p>集群模式下的自动装配：基于 Redis 的规则分发、节点注册、心跳维护（当前实现仅 Redis，无 ZooKeeper 支持）。
 *
 * <p>保证多个节点之间规则一致性与故障节点自动剔除。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Configuration
@ConditionalOnProperty(prefix = "ydsz.literule.distributed", name = "enabled", havingValue = "true")
public class DistributedAutoConfiguration {

  private static final Logger log = LoggerFactory.getLogger(DistributedAutoConfiguration.class);

  /** 当前节点 ID（hostname:pid） */
  @Bean
  @ConditionalOnMissingBean
  public String nodeId() {
    String host;
    try {
      host = InetAddress.getLocalHost().getHostName();
    } catch (Exception e) {
      host = "localhost";
    }
    String pid = String.valueOf(ProcessHandle.current().pid());
    return host + ":" + pid;
  }

  /**
   * 节点注册表（Redis 实现，Redisson 可用时优先）
   *
   * <p>生产环境推荐使用此实现，所有节点共享 Redis 中的注册表， 实现跨实例节点发现与心跳管理。
   */
  @Bean
  @ConditionalOnClass(name = "org.redisson.api.RedissonClient")
  @ConditionalOnMissingBean(NodeRegistry.class)
  public NodeRegistry redisNodeRegistry(
      RedissonClient redissonClient, String nodeId, LiteRuleProperties properties) {
    RedisNodeRegistry registry =
        new RedisNodeRegistry(
            redissonClient, nodeId, properties.getDistributed().getHeartbeatTimeoutMs());
    // 注册自身
    ClusterNode self = new ClusterNode(nodeId, nodeId);
    registry.register(self);
    log.info("[Distributed] 节点注册表已初始化（self={}, type=Redis）", nodeId);
    return registry;
  }

  /**
   * 规则配置广播器（Redis Pub/Sub 实现，Redisson 可用时优先）
   *
   * <p>生产环境推荐使用此实现，确保多实例规则配置一致。
   */
  @Bean
  @ConditionalOnClass(name = "org.redisson.api.RedissonClient")
  @ConditionalOnMissingBean(RuleConfigBroadcaster.class)
  public RuleConfigBroadcaster redisRuleConfigBroadcaster(
      RedissonClient redissonClient, String nodeId, ApplicationEventPublisher eventPublisher) {
    RedisRuleConfigBroadcaster broadcaster =
        new RedisRuleConfigBroadcaster(redissonClient, nodeId, eventPublisher);
    // 启动时订阅 Topic
    broadcaster.subscribe();
    log.info("[Distributed] 规则配置广播器已初始化（self={}, type=Redis）", nodeId);
    return broadcaster;
  }
}
