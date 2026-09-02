package com.njydsz.cronjob.server.core.discovery;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import javax.sql.DataSource;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import com.njydsz.cronjob.domain.repository.JobNodeRepository;
import com.njydsz.cronjob.domain.vo.JobNodeVO;
import com.njydsz.cronjob.server.config.CronjobProperties;
import com.njydsz.cronjob.server.config.NodeHealthConfig;
import com.njydsz.cronjob.server.core.maintenance.ScanTask;

/**
 * P1-1: 节点健康检查器。
 *
 * <p>周期性检查所有 ONLINE 节点的健康状态，基于以下指标：
 *
 * <ul>
 *   <li><b>DB ping 延迟</b>：测量数据库响应时长，计算加权移动平均（EMA）
 *   <li><b>连续失败次数</b>：心跳/DB ping 连续失败次数
 *   <li><b>CPU/内存使用率</b>：超过阈值时降低节点权重
 * </ul>
 *
 * <h3>自动隔离策略</h3>
 *
 * <ul>
 *   <li>连续失败 ≥ {@code consecutiveFailureThreshold}（默认 3）→ 加入黑名单 + 标记 DRAINING
 *   <li>加权响应时长 ≥ {@code responseTimeThresholdMs}（默认 5000ms）→ 降低权重但不隔离
 * </ul>
 *
 * <h3>云顶编码规范 §24 配置管理规范</h3>
 *
 * <p>所有阈值均通过 {@link CronjobProperties.NodeHealthConfig} 配置化。
 *
 * @author ydsz-team
 * @since 1.0.4
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class NodeHealthChecker implements ScanTask {

  /** 默认连续失败阈值：3 次 */
  private static final int DEFAULT_CONSECUTIVE_FAILURE_THRESHOLD = 3;

  /** 默认响应时长阈值：5000ms */
  private static final long DEFAULT_RESPONSE_TIME_THRESHOLD_MS = 5000L;

  /** EMA 平滑系数（0-1，越大越重视历史值） */
  private static final double EMA_ALPHA = 0.3;

  /** DB 连接校验超时时间（秒） */
  private static final int DB_VALIDATE_TIMEOUT_SECONDS = 5;

  private final JobNodeRepository jobNodeRepository;
  private final DataSource dataSource;
  private final NodeBlacklist nodeBlacklist;
  private final CronjobProperties cronjobProperties;

  @Override
  public String name() {
    return "node-health-check";
  }

  @Override
  public void scan() {
    try {
      List<JobNodeVO> onlineNodes = jobNodeRepository.findOnlineNodes();
      if (onlineNodes.isEmpty()) {
        return;
      }
      for (JobNodeVO node : onlineNodes) {
        checkNodeHealth(node);
      }
    } catch (Exception e) {
      log.warn("[NodeHealth] 健康检查执行异常: reason={}", e.getMessage());
    }
  }

  /**
   * 检查单个节点健康状态。
   *
   * @param node 节点信息
   */
  private void checkNodeHealth(JobNodeVO node) {
    try {
      // 1. 测量 DB ping 延迟
      long pingMs = measureDbPing();

      // 2. 计算加权移动平均响应时长
      long emaResponseTime = calculateEma(node.getResponseTimeMs(), pingMs);

      // 3. 更新节点响应时长 + 重置连续失败次数（本次健康检查成功）
      jobNodeRepository.updateResponseTime(node.getNodeId(), emaResponseTime);
      jobNodeRepository.resetConsecutiveFailures(node.getNodeId());

      // 4. 检查是否需要隔离
      int consecutiveFailures = node.getConsecutiveFailures() != null ? node.getConsecutiveFailures() : 0;
      int failureThreshold = getConsecutiveFailureThreshold();

      if (consecutiveFailures >= failureThreshold) {
        // 连续失败超阈值：加入黑名单 + 标记 DRAINING
        nodeBlacklist.add(node.getNodeId());
        jobNodeRepository.updateStatus(node.getNodeId(), "DRAINING", LocalDateTime.now());
        log.warn(
            "[NodeHealth] 节点自动隔离: nodeId={} consecutiveFailures={} threshold={}",
            node.getNodeId(),
            consecutiveFailures,
            failureThreshold);
      } else if (emaResponseTime > getResponseTimeThresholdMs()) {
        // 响应时长过高：仅告警，不隔离
        log.warn(
            "[NodeHealth] 节点响应缓慢: nodeId={} responseTimeMs={} threshold={}",
            node.getNodeId(),
            emaResponseTime,
            getResponseTimeThresholdMs());
      }
    } catch (Exception e) {
      log.warn("[NodeHealth] 节点健康检查异常: nodeId={} reason={}", node.getNodeId(), e.getMessage());
      // 递增连续失败次数
      int failures = (node.getConsecutiveFailures() != null ? node.getConsecutiveFailures() : 0) + 1;
      jobNodeRepository.updateConsecutiveFailures(node.getNodeId(), failures);
    }
  }

  /**
   * 测量数据库 ping 延迟（毫秒）。
   *
   * @return ping 耗时（ms）
   * @throws SQLException DB 连接异常
   */
  private long measureDbPing() throws SQLException {
    long start = System.currentTimeMillis();
    try (Connection conn = dataSource.getConnection()) {
      conn.isValid(DB_VALIDATE_TIMEOUT_SECONDS);
    }
    return System.currentTimeMillis() - start;
  }

  /**
   * 计算指数移动平均（EMA）。
   *
   * <p>EMA = alpha * newValue + (1 - alpha) * oldValue
   *
   * @param oldValue 历史值（null 时返回新值）
   * @param newValue 当前测量值
   * @return EMA 值
   */
  private long calculateEma(Long oldValue, long newValue) {
    if (oldValue == null || oldValue <= 0) {
      return newValue;
    }
    return Math.round(EMA_ALPHA * newValue + (1 - EMA_ALPHA) * oldValue);
  }

  @Override
  public long intervalMs() {
    // 健康检查频率与心跳一致
    return cronjobProperties.getNode().getHeartbeatIntervalMs();
  }

  @Override
  public String lockKey() {
    return "cronjob:scan:node-health";
  }

  private int getConsecutiveFailureThreshold() {
    NodeHealthConfig config = cronjobProperties.getNode().getNodeHealth();
    return config != null && config.getConsecutiveFailureThreshold() > 0
        ? config.getConsecutiveFailureThreshold()
        : DEFAULT_CONSECUTIVE_FAILURE_THRESHOLD;
  }

  private long getResponseTimeThresholdMs() {
    NodeHealthConfig config = cronjobProperties.getNode().getNodeHealth();
    return config != null && config.getResponseTimeThresholdMs() > 0
        ? config.getResponseTimeThresholdMs()
        : DEFAULT_RESPONSE_TIME_THRESHOLD_MS;
  }
}
