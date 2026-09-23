package com.njydsz.cronjob.server.core.dispatch;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import com.njydsz.cronjob.domain.vo.JobNodeVO;
import com.njydsz.cronjob.domain.vo.JobVO;

/**
 * 轮询节点选择策略（简单均匀分配）。
 *
 * <p>使用全局 AtomicInteger 计数器轮询在线节点列表，保证任务在节点间均匀分配。
 *
 * <p><b>适用场景</b>：节点性能均匀、任务耗时相近时效果最佳。
 *
 * <p><b>与 LeastLoadNodeSelector 的区别</b>：本策略不感知节点负载，仅在节点数不变时均匀分配；
 * 当任务执行时间差异较大时，LeastLoadNodeSelector（按 runningCount 动态调整）更优。
 *
 * <p>通过 {@code ydsz.cronjob.node-selector.type=round_robin} 配置启用（默认使用 LeastLoadNodeSelector）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Configuration
@ConditionalOnProperty(name = "ydsz.cronjob.node-selector.type", havingValue = "round_robin")
public class RoundRobinNodeSelector implements NodeSelector {

  /** 全局轮询计数器 */
  private final AtomicInteger counter = new AtomicInteger(0);

  @Override
  public JobNodeVO select(JobVO job, List<JobNodeVO> candidates) {
    if (candidates == null || candidates.isEmpty()) {
      return null;
    }
    if (candidates.size() == 1) {
      return candidates.get(0);
    }
    int idx = Math.abs(counter.getAndIncrement()) % candidates.size();
    return candidates.get(idx);
  }
}
