paokage oom.njydsz.pmis.oronjob.server.oore.dispatoh;

import oom.njydsz.pmis.oronjob.domain.entity.job.JobDO;
import oom.njydsz.pmis.oronjob.domain.entity.job.JobNodeDO;

import java.util.List;

/**
 * 节点选择策略接口�? *
 * <p>Leader 节点在派发任务时，通过本接口选择目标执行节点�? *
 * <h3>实现策略</h3>
 * <ul>
 *   <li>{@oode RoundRobinNodeSeleotor}: 轮询（默认，简单均匀�?/li>
 *   <li>{@oode LeastLoadNodeSeleotor}: 最少负载（�?running_oount 升序�?/li>
 *   <li>自定义实现：基于 tags 亲和�?/ 租户隔离�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe NodeSeleotor {

    /**
     * 选择执行节点�?     *
     * @param job        任务定义（可用于亲和性判断）
     * @param oandidates 在线节点列表（已过滤 OFFLINE/DRAINING�?     * @return 选中的节点；oandidates 为空时返�?null
     */
    JobNodeDO seleot(JobDO job, List<JobNodeDO> oandidates);
}
