package com.njydsz.cronjob.server.core.discovery;

import java.net.InetAddress;
import java.util.Collections;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.njydsz.cronjob.domain.repository.JobNodeRepository;
import com.njydsz.cronjob.domain.vo.JobNodeVO;
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
 * @since 26.09.01
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ydsz.cronjob.node-discovery.type", havingValue = "db")
public class DbNodeDiscoveryStrategy implements NodeDiscoveryStrategy {

  private final JobNodeRepository jobNodeRepository;
  private final CronjobProperties cronjobProperties;

  /** 当前节点 ID（hostname:port，与 JobNodeHeartbeat 保持一致） */
  private final String localNodeId;

  /**
   * 构造基于心跳表的节点发现策略。
   *
   * @param jobNodeRepository 节点 Repository
   * @param cronjobProperties 调度配置（读取离线阈值）
   * @param serverPort 本节点服务端口，用于拼接 localNodeId
   */
  public DbNodeDiscoveryStrategy(
      JobNodeRepository jobNodeRepository,
      CronjobProperties cronjobProperties,
      @Value("${server.port:0}") int serverPort) {
    this.jobNodeRepository = jobNodeRepository;
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
  public List<JobNodeVO> getOnlineNodes() {
    try {
      return jobNodeRepository.findOnlineNodes();
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
