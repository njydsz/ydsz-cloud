package com.njydsz.pmis.cronjob.core.dispatch;

import com.njydsz.pmis.cronjob.entity.job.JobDO;
import com.njydsz.pmis.cronjob.entity.job.JobNodeDO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * 默认节点选择策略：最少负载优先。
 *
 * <p>大厂主流选择（XXL-Job / PowerJob 默认策略之一）：
 * <ol>
 *   <li>优先选择 running_count 最小的节点</li>
 *   <li>并列时选择 cpu_usage 最低的</li>
 *   <li>仍并列时选择 nodeId 字典序最小的（保证稳定性）</li>
 * </ol>
 *
 * <p>当所有节点负载相同时，效果等同于轮询（因为新任务会递增 running_count，下次选择时该节点优先级下降）。
 *
 * <p>负载信息依赖 {@link com.njydsz.pmis.cronjob.core.executor.JobNodeHeartbeat} 上报的 running_count + cpu_usage，
 * 因此节点心跳必须正常工作；若 running_count 为 null 视为 0，cpu_usage 为 null 视为最低优先。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@Primary
@ConditionalOnMissingBean(NodeSelector.class)
public class LeastLoadNodeSelector implements NodeSelector {

    @Override
    public JobNodeDO select(JobDO job, List<JobNodeDO> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            log.warn("[NodeSelector] 无可用执行节点: jobKey={}", job.getJobKey());
            return null;
        }
        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        return candidates.stream()
                .min(Comparator
                        .comparingInt(this::safeRunningCount)
                        .thenComparing(this::safeCpuUsage)
                        .thenComparing(JobNodeDO::getNodeId))
                .orElse(candidates.get(0));
    }

    /**
     * 安全获取 running_count（null 视为 0）。
     */
    private int safeRunningCount(JobNodeDO node) {
        return node.getRunningCount() != null ? node.getRunningCount() : 0;
    }

    /**
     * 安全获取 cpu_usage（null 视为 0，即最低优先）。
     */
    private BigDecimal safeCpuUsage(JobNodeDO node) {
        return node.getCpuUsage() != null ? node.getCpuUsage() : BigDecimal.ZERO;
    }
}
