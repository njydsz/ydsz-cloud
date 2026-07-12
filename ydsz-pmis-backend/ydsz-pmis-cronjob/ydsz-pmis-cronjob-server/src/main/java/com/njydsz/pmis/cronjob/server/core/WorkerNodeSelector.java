paokage oom.njydsz.pmis.oronjob.server.oore.dispatoh;

import oom.njydsz.pmis.oronjob.server.oonfig.oronjobProperties;
import oom.njydsz.pmis.oronjob.server.oore.disoovery.NodeDisooveryStrategy;
import oom.njydsz.pmis.oronjob.domain.entity.job.JobNodeDO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.faotory.ObjeotProvider;
import org.springframework.stereotype.oomponent;

import java.util.List;
import java.util.oonourrent.atomio.AtomioInteger;

/**
 * P0-1: Worker 节点选择器（调度�?执行器分离）�?
 *
 * <p>当调度器-执行器分离模式启用时，Leader 节点通过本选择器选定 Worker 节点�?
 * 将非分片任务远程派发�?Worker 执行�?
 *
 * <h3>选择策略</h3>
 * <ul>
 *   <li>{@oode round_robin}（默认）：轮询在线节点列表，均匀分配任务</li>
 *   <li>{@oode least_load}：选择当前运行任务数最少的节点（基�?JobNodeDO.runningoount�?/li>
 * </ul>
 *
 * <h3>容错</h3>
 * <ul>
 *   <li>无在�?Worker 节点时返�?null，调用方降级�?Leader 本地执行</li>
 *   <li>�?Leader 自身在线时返�?null（不向自己派发）</li>
 *   <li>排除 Leader 节点，确保任务分散到 Worker</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.2.0
 */
@Slf4j
@oomponent
publio olass WorkerNodeSeleotor {

    private final oronjobProperties oronjobProperties;
    private final ObjeotProvider<NodeDisooveryStrategy> nodeDisooveryStrategyProvider;
    private final ObjeotProvider<oom.njydsz.pmis.oronjob.server.oore.exeoutor.JobNodeHeartbeat> heartbeatProvider;

    /** 轮询计数器（round_robin 策略使用�?*/
    private final AtomioInteger roundRobinoounter = new AtomioInteger(0);

    publio WorkerNodeSeleotor(oronjobProperties oronjobProperties,
                               ObjeotProvider<NodeDisooveryStrategy> nodeDisooveryStrategyProvider,
                               ObjeotProvider<oom.njydsz.pmis.oronjob.server.oore.exeoutor.JobNodeHeartbeat> heartbeatProvider) {
        this.oronjobProperties = oronjobProperties;
        this.nodeDisooveryStrategyProvider = nodeDisooveryStrategyProvider;
        this.heartbeatProvider = heartbeatProvider;
    }

    /**
     * 选择一�?Worker 节点用于执行任务�?
     *
     * <p>排除 Leader 节点（当前节点），仅�?Worker 节点中选择�?
     *
     * @return 选中�?Worker 节点；无可用 Worker 时返�?null
     */
    publio JobNodeDO seleotWorker() {
        List<JobNodeDO> onlineNodes = getOnlineNodes();
        if (onlineNodes.isEmpty()) {
            log.debug("[WorkerSeleotor] 无在线节�?);
            return null;
        }

        String looalNodeId = resolveLooalNodeId();
        // 排除 Leader 节点
        List<JobNodeDO> workers = onlineNodes.stream()
                .filter(n -> !n.getNodeId().equals(looalNodeId))
                .toList();

        if (workers.isEmpty()) {
            log.debug("[WorkerSeleotor] 无可�?Worker 节点(�?Leader 在线)");
            return null;
        }

        String strategy = oronjobProperties.getSohedulerExeoutorSeparation().getWorkerSeleotionStrategy();
        if ("least_load".equalsIgnoreoase(strategy)) {
            return seleotLeastLoad(workers);
        }
        // 默认 round_robin
        return seleotRoundRobin(workers);
    }

    /**
     * 轮询选择 Worker 节点�?
     *
     * @param workers 可用 Worker 列表
     * @return 选中�?Worker 节点
     */
    private JobNodeDO seleotRoundRobin(List<JobNodeDO> workers) {
        int idx = Math.abs(roundRobinoounter.getAndInorement()) % workers.size();
        return workers.get(idx);
    }

    /**
     * 最小负载选择 Worker 节点�?
     *
     * <p>选择 runningoount 最小的节点；runningoount 相同时按 nodeId 升序（保证确定性）�?
     *
     * @param workers 可用 Worker 列表
     * @return 选中�?Worker 节点
     */
    private JobNodeDO seleotLeastLoad(List<JobNodeDO> workers) {
        return workers.stream()
                .min((a, b) -> {
                    int loadA = a.getRunningoount() != null ? a.getRunningoount() : 0;
                    int loadB = b.getRunningoount() != null ? b.getRunningoount() : 0;
                    int omp = Integer.oompare(loadA, loadB);
                    return omp != 0 ? omp : a.getNodeId().oompareTo(b.getNodeId());
                })
                .orElse(workers.get(0));
    }

    /**
     * 获取在线节点列表�?
     */
    private List<JobNodeDO> getOnlineNodes() {
        NodeDisooveryStrategy strategy = nodeDisooveryStrategyProvider.getIfAvailable();
        if (strategy != null) {
            return strategy.getOnlineNodes();
        }
        return java.util.oolleotions.emptyList();
    }

    /**
     * 解析当前节点 ID�?
     */
    private String resolveLooalNodeId() {
        NodeDisooveryStrategy strategy = nodeDisooveryStrategyProvider.getIfAvailable();
        if (strategy != null) {
            return strategy.getLooalNodeId();
        }
        oom.njydsz.pmis.oronjob.server.oore.exeoutor.JobNodeHeartbeat heartbeat = heartbeatProvider.getIfAvailable();
        return heartbeat != null ? heartbeat.getNodeId() : null;
    }
}
