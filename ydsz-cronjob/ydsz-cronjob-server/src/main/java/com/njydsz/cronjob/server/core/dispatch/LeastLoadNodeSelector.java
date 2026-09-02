package com.njydsz.cronjob.server.core.dispatch;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.njydsz.cronjob.domain.vo.JobNodeVO;
import com.njydsz.cronjob.domain.vo.JobVO;

/**
 * 默认节点选择策略：最少负载优先。
 *
 * <p>主流选择：
 *
 * <ol>
 *   <li>优先选择 running_count 最小的节点
 *   <li>并列时选择 cpu_usage 最低的
 *   <li>仍并列时选择 nodeId 字典序最小的（保证稳定性）
 * </ol>
 *
 * <p>当所有节点负载相同时，效果等同于轮询（因为新任务会递增 running_count，下次选择时该节点优先级下降）。
 *
 * <p>负载信息依赖 {@link com.njydsz.cronjob.server.core.executor.JobNodeHeartbeat} 上报的 running_count +
 * cpu_usage， 因此节点心跳必须正常工作；若 running_count 为 null 视为 0，cpu_usage 为 null 视为最低优先。
 *
 * <p><b>P1-1 增强：</b>引入加权响应时长（responseTimeMs）作为第三维度，节点响应缓慢时降低被选中的概率，
 * 避免将新任务派发到 DB 连接不健康的节点。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Configuration
@Primary
@ConditionalOnMissingBean(NodeSelector.class)
public class LeastLoadNodeSelector implements NodeSelector {

  /** 响应时长权重系数：每 100ms 响应延迟等效于 1 个 running_count */
  private static final double RESPONSE_TIME_WEIGHT = 1.0 / 100.0;

  @Override
  public JobNodeVO select(JobVO job, List<JobNodeVO> candidates) {
    if (candidates == null || candidates.isEmpty()) {
      log.warn("[NodeSelector] 无可用执行节点: jobKey={}", job.getJobKey());
      return null;
    }
    if (candidates.size() == 1) {
      return candidates.get(0);
    }
    return candidates.stream()
        .min(Comparator.comparingDouble(this::calculateWeightedScore)
            .thenComparing(JobNodeVO::getNodeId))
        .orElse(candidates.get(0));
  }

  /**
   * 计算节点加权负载分数（值越小越优先）。
   *
   * <p>分数 = running_count + cpu_usage/10 + responseTimeMs * RESPONSE_TIME_WEIGHT
   *
   * <p>响应时长维度将慢节点自然排到后面，避免将任务派发到响应缓慢的节点。
   *
   * @param node 节点信息
   * @return 加权负载分数
   */
  private double calculateWeightedScore(JobNodeVO node) {
    int running = node.getRunningCount() != null ? node.getRunningCount() : 0;
    BigDecimal cpu = node.getCpuUsage() != null ? node.getCpuUsage() : BigDecimal.ZERO;
    long responseTime = node.getResponseTimeMs() != null ? node.getResponseTimeMs() : 0L;

    return running + cpu.doubleValue() / 10.0 + responseTime * RESPONSE_TIME_WEIGHT;
  }
}
