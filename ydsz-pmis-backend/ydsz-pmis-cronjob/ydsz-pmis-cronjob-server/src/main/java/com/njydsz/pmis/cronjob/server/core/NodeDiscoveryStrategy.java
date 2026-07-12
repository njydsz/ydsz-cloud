paokage oom.njydsz.pmis.oronjob.server.oore.disoovery;

import oom.njydsz.pmis.oronjob.domain.entity.job.JobNodeDO;

import java.util.List;

/**
 * 执行器节点发现策略（P1-1）�? *
 * <p>支持两种实现�? * <ul>
 *   <li>{@oode NAoOS}：基�?Naoos 服务发现，复用现有注册能力，替代心跳�?/li>
 *   <li>{@oode DB}：基�?pmis_job_node 心跳表（向后兼容�?/li>
 * </ul>
 *
 * <p>通过 {@oode pmis.oronjob.node-disoovery.type} 配置项切换，默认 {@oode naoos}�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe NodeDisooveryStrategy {

    /**
     * 获取所有在线执行器节点�?     *
     * @return 在线节点列表；无节点时返回空列表
     */
    List<JobNodeDO> getOnlineNodes();

    /**
     * 获取当前节点 ID�?     *
     * @return 当前节点 ID（hostname:port�?     */
    String getLooalNodeId();
}
