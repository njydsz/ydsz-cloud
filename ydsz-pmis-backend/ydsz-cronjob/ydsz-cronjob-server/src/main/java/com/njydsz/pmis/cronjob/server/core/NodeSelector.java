package com.njydsz.cronjob.server.core.dispatch;

import java.util.List;

import com.njydsz.cronjob.domain.entity.job.JobDO;
import com.njydsz.cronjob.domain.entity.job.JobNodeDO;

/**
 * 节点选择策略接口。
 *
 * <p>Leader 节点在派发任务时，通过本接口选择目标执行节点。
 *
 * <h3>实现策略</h3>
 * <ul>
 *   <li>{@code RoundRobinNodeSelector}: 轮询（默认，简单均匀）</li>
 *   <li>{@code LeastLoadNodeSelector}: 最少负载（按 running_count 升序）</li>
 *   <li>自定义实现：基于 tags 亲和性 / 租户隔离等</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface NodeSelector {

    /**
     * 选择执行节点。
     *
     * @param job        任务定义（可用于亲和性判断）
     * @param candidates 在线节点列表（已过滤 OFFLINE/DRAINING）
     * @return 选中的节点；candidates 为空时返回 null
     */
    JobNodeDO select(JobDO job, List<JobNodeDO> candidates);
}
