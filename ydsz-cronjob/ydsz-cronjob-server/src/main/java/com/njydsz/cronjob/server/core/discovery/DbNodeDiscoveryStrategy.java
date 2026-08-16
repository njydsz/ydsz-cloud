package com.njydsz.cronjob.server.core.discovery;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.njydsz.cronjob.domain.entity.job.JobNode;
import com.njydsz.cronjob.infra.mapper.job.JobNodeMapper;
import com.njydsz.cronjob.server.config.CronjobProperties;

/**
 * 基于心跳表的节点发现策略（P1-1，向后兼容）。
 *
 * <p>查询 {@code ydsz_job_node} 表中 {@code last_heartbeat} 在阈值内的节点， 与 {@link
 * com.njydsz.cronjob.server.core.executor.JobNodeHeartbeat} + {@link
 * com.njydsz.cronjob.server.core.executor.JobNodeReaper} 配合使用。
 *
 * <p>通过 {@code ydsz.cronjob.node-discovery.type=db} 启用， 启用时 JobNodeHeartbeat 和 JobNodeReaper
 * 也会自动注册。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ydsz.cronjob.node-discovery.type", havingValue = "db")
public class DbNodeDiscoveryStrategy implements NodeDiscoveryStrategy {

  private final JobNodeMapper jobNodeMapper;
  private final CronjobProperties cronjobProperties;

  /** 当前节点 ID（hostname:port，与 JobNodeHeartbeat 保持一致） */
  private final String localNodeId;

  /**
   * 构造基于心跳表的节点发现策略。
   *
   * @param jobNodeMapper 节点表 Mapper
   * @param cronjobProperties 调度配置（读取离线阈值）
   * @param serverPort 本节点服务端口，用于拼接 localNodeId
   */
  public DbNodeDiscoveryStrategy(
      JobNodeMapper jobNodeMapper,
      CronjobProperties cronjobProperties,
      @Value("${server.port:0}") int serverPort) {
    this.jobNodeMapper = jobNodeMapper;
    this.cronjobProperties = cronjobProperties;
    this.localNodeId = resolveHostName() + ":" + serverPort;
    log.info("[DbNodeDiscovery] 初始化完成, localNodeId={}", localNodeId);
  }

  /**
   * 查询在线执行器节点（心跳在离线阈值内且状态为 ONLINE）。
   *
   * <p>查询异常时返回空列表，避免影响调度主流程（降级为无可用节点）。
   *
   * @return 在线节点列表，无可用时返回空列表
   */
  @Override
  public List<JobNode> getOnlineNodes() {
    try {
      long threshold = cronjobProperties.getExecutor().getOfflineThresholdSeconds();
      LocalDateTime cutoff = LocalDateTime.now().minusSeconds(threshold);
      LambdaQueryWrapper<JobNode> wrapper = new LambdaQueryWrapper<>();
      wrapper
          .eq(JobNode::getStatus, "ONLINE")
          .ge(JobNode::getLastHeartbeat, cutoff)
          .orderByAsc(JobNode::getNodeId);
      return jobNodeMapper.selectList(wrapper);
    } catch (Exception e) {
      log.warn("[DbNodeDiscovery] 查询在线节点失败, 返回空列表: reason={}", e.getMessage());
      return Collections.emptyList();
    }
  }

  /**
   * 获取本节点 ID（hostname:port）。
   *
   * @return 本节点唯一标识，与 {@link com.njydsz.cronjob.server.core.executor.JobNodeHeartbeat} 保持一致
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
