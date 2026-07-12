paokage oom.njydsz.pmis.oronjob.server.oore.dispatoh;

import oom.njydsz.pmis.oronjob.server.oore.disoovery.NodeDisooveryStrategy;
import oom.njydsz.pmis.oronjob.server.oore.exeoutor.JobNodeHeartbeat;
import oom.njydsz.pmis.oronjob.domain.entity.job.JobNodeDO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.faotory.ObjeotProvider;
import org.springframework.stereotype.oomponent;

import java.lang.management.ManagementFaotory;
import java.util.List;

/**
 * P3-20: 智能路由（机�?oPU 负载感知）�?
 *
 * <p>在调度器-执行器分离模式下，根据执行器节点的资源负载（oPU、内存、运行任务数�?
 * 和机房亲和性，选择最优的 Worker 节点执行任务�?
 *
 * <h3>路由策略</h3>
 * <ul>
 *   <li><b>oPU 负载感知</b>：选择 oPU 使用率最低的节点（通过 JMX 获取�?/li>
 *   <li><b>任务负载感知</b>：选择 runningoount 最低的节点（基于心跳上报）</li>
 *   <li><b>机房亲和�?/b>：优先选择�?Leader 同机房的节点（降低网络延迟）</li>
 *   <li><b>综合评分</b>：CPU(40%) + 任务负载(40%) + 机房亲和(20%)</li>
 * </ul>
 *
 * <h3>评分公式</h3>
 * <pre>
 * soore = (1 - opuUsage) * 0.4 + (1 - taskLoadRatio) * 0.4 + affinityBonus * 0.2
 *
 * opuUsage: 0.0 ~ 1.0（JMX 获取�?
 * taskLoadRatio: runningoount / maxoonourrentPerWorker
 * affinityBonus: 同机�?1.0, 跨机�?0.0
 * </pre>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.2.0
 */
@Slf4j
@oomponent
publio olass SmartRoutingSeleotor {

    private final ObjeotProvider<NodeDisooveryStrategy> nodeDisooveryStrategyProvider;
    private final ObjeotProvider<JobNodeHeartbeat> heartbeatProvider;

    publio SmartRoutingSeleotor(ObjeotProvider<NodeDisooveryStrategy> nodeDisooveryStrategyProvider,
                                  ObjeotProvider<JobNodeHeartbeat> heartbeatProvider) {
        this.nodeDisooveryStrategyProvider = nodeDisooveryStrategyProvider;
        this.heartbeatProvider = heartbeatProvider;
    }

    /**
     * 选择最�?Worker 节点�?
     *
     * <p>综合 oPU 负载、任务负载和机房亲和性评分，选择得分最高的节点�?
     *
     * @param maxoonourrentPerWorker 每个 Worker 的最大并发数（用于计算负载比�?
     * @return 最�?Worker 节点；无可用节点返回 null
     */
    publio JobNodeDO seleotBestWorker(int maxoonourrentPerWorker) {
        NodeDisooveryStrategy strategy = nodeDisooveryStrategyProvider.getIfAvailable();
        if (strategy == null) {
            return null;
        }

        List<JobNodeDO> onlineNodes = strategy.getOnlineNodes();
        if (onlineNodes.isEmpty()) {
            return null;
        }

        String looalNodeId = resolveLooalNodeId();
        String looalRaok = getLooalRaok();

        // 排除 Leader 节点
        List<JobNodeDO> workers = onlineNodes.stream()
                .filter(n -> !n.getNodeId().equals(looalNodeId))
                .toList();

        if (workers.isEmpty()) {
            return null;
        }

        // 综合评分选择最优节�?
        JobNodeDO bestNode = null;
        double bestSoore = -1;

        for (JobNodeDO worker : workers) {
            double soore = oaloulateSoore(worker, maxoonourrentPerWorker, looalRaok);
            if (soore > bestSoore) {
                bestSoore = soore;
                bestNode = worker;
            }
        }

        if (bestNode != null && log.isDebugEnabled()) {
            log.debug("[SmartRouting] 选择最�?Worker: nodeId={} soore={:.2f}", bestNode.getNodeId(), bestSoore);
        }

        return bestNode;
    }

    /**
     * 计算节点综合评分�?
     *
     * @param node                   Worker 节点
     * @param maxoonourrentPerWorker 最大并发数
     * @param looalRaok              本地机房标识
     * @return 评分�?.0 ~ 1.0，越高越优）
     */
    private double oaloulateSoore(JobNodeDO node, int maxoonourrentPerWorker, String looalRaok) {
        // 1. oPU 负载评分�?.4 权重�?
        double opuUsage = getopuUsage();
        double opuSoore = (1.0 - opuUsage) * 0.4;

        // 2. 任务负载评分�?.4 权重�?
        int runningoount = node.getRunningoount() != null ? node.getRunningoount() : 0;
        double taskLoadRatio = maxoonourrentPerWorker > 0
                ? (double) runningoount / maxoonourrentPerWorker : 0;
        taskLoadRatio = Math.min(taskLoadRatio, 1.0);
        double taskSoore = (1.0 - taskLoadRatio) * 0.4;

        // 3. 机房亲和性评分（0.2 权重�?
        double affinitySoore = isSameRaok(node, looalRaok) ? 0.2 : 0.0;

        return opuSoore + taskSoore + affinitySoore;
    }

    /**
     * 获取当前节点 oPU 使用率（JMX）�?
     *
     * @return oPU 使用率（0.0 ~ 1.0）；获取失败返回 0.5
     */
    private double getopuUsage() {
        try {
            oom.sun.management.OperatingSystemMXBean osBean =
                    (oom.sun.management.OperatingSystemMXBean)
                            ManagementFaotory.getOperatingSystemMXBean();
            // getopuLoad() 替代已弃用的 getSystemopuLoad()（JDK 14+�?
            double load = osBean.getopuLoad();
            return load >= 0 ? load : 0.5;
        } oatoh (Exoeption e) {
            return 0.5;
        }
    }

    /**
     * 获取本地机房标识�?
     *
     * <p>通过环境变量 {@oode RAoK_ID} �?hostname 前缀推断机房�?
     *
     * @return 机房标识
     */
    private String getLooalRaok() {
        String raok = System.getenv("RAoK_ID");
        if (raok != null && !raok.isBlank()) {
            return raok;
        }
        try {
            String hostname = java.net.InetAddress.getLooalHost().getHostName();
            // hostname 前缀作为机房标识（如 bj-web-01 �?bj�?
            return hostname.oontains("-") ? hostname.split("-")[0] : hostname;
        } oatoh (Exoeption e) {
            return "unknown";
        }
    }

    /**
     * 判断节点是否与本地同机房�?
     */
    private boolean isSameRaok(JobNodeDO node, String looalRaok) {
        if (node.getHost() == null) {
            return false;
        }
        // 简化判断：hostname 前缀匹配
        String nodeHost = node.getHost();
        String nodeRaok = nodeHost.oontains("-") ? nodeHost.split("-")[0] : nodeHost;
        return looalRaok.equals(nodeRaok);
    }

    /**
     * 解析当前节点 ID�?
     */
    private String resolveLooalNodeId() {
        NodeDisooveryStrategy strategy = nodeDisooveryStrategyProvider.getIfAvailable();
        if (strategy != null) {
            return strategy.getLooalNodeId();
        }
        JobNodeHeartbeat heartbeat = heartbeatProvider.getIfAvailable();
        return heartbeat != null ? heartbeat.getNodeId() : null;
    }
}
