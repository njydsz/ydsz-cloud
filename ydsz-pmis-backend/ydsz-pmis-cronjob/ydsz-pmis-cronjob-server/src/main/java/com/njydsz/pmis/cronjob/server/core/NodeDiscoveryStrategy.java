package com.njydsz.pmis.cronjob.server.core.discovery;

import com.njydsz.pmis.cronjob.domain.entity.job.JobNodeDO;

import java.util.List;

/**
 * 执行器节点发现策略（P1-1）。
 *
 * <p>支持两种实现：
 * <ul>
 *   <li>{@code NACOS}：基于 Nacos 服务发现，复用现有注册能力，替代心跳表</li>
 *   <li>{@code DB}：基于 pmis_job_node 心跳表（向后兼容）</li>
 * </ul>
 *
 * <p>通过 {@code pmis.cronjob.node-discovery.type} 配置项切换，默认 {@code nacos}。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface NodeDiscoveryStrategy {

    /**
     * 获取所有在线执行器节点。
     *
     * @return 在线节点列表；无节点时返回空列表
     */
    List<JobNodeDO> getOnlineNodes();

    /**
     * 获取当前节点 ID。
     *
     * @return 当前节点 ID（hostname:port）
     */
    String getLocalNodeId();
}
