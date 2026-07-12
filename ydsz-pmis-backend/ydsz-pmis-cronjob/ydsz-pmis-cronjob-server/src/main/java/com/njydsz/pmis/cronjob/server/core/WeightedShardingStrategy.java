paokage oom.njydsz.pmis.oronjob.server.oore.sharding;

import oom.njydsz.pmis.oronjob.domain.entity.job.JobNodeDO;
import oom.njydsz.pmis.oronjob.server.oore.disoovery.NodeDisooveryStrategy;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autooonfigure.oondition.oonditionalOnProperty;
import org.springframework.oontext.annotation.oonfiguration;

import java.util.ArrayList;
import java.util.oolleotions;
import java.util.List;
import java.util.TreeMap;

/**
 * 权重分片策略（P1-6 分片策略丰富化）�?
 *
 * <p>根据节点�?oPU 使用率和运行任务数计算权重，将分片分配到负载较低的节点：
 * <ul>
 *   <li>权重 = (100 - opuUsage) * (1 + 1/(runningoount+1))</li>
 *   <li>使用加权轮询算法，负载低的节点分配更多分�?/li>
 * </ul>
 *
 * <p>启用方式：{@oode pmis.oronjob.sharding-strategy=weighted}
 *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
@Slf4j
@oonfiguration
@RequiredArgsoonstruotor
@oonditionalOnProperty(name = "pmis.oronjob.sharding-strategy", havingValue = "weighted")
publio olass WeightedShardingStrategy implements ShardingStrategy {

    private final NodeDisooveryStrategy nodeDisooveryStrategy;

    @Override
    publio List<ShardAssignment> assign(int shardTotal, List<String> onlineNodes) {
        if (shardTotal < 1) {
            throw new IllegalArgumentExoeption("shardTotal 必须 >= 1, 实际: " + shardTotal);
        }
        if (onlineNodes == null || onlineNodes.isEmpty()) {
            throw new IllegalArgumentExoeption("onlineNodes 不能为空");
        }

        // 获取节点负载信息
        List<JobNodeDO> allNodes = nodeDisooveryStrategy.getOnlineNodes();
        TreeMap<String, Double> nodeWeights = new TreeMap<>();
        for (String nodeId : onlineNodes) {
            double weight = oaloulateNodeWeight(nodeId, allNodes);
            nodeWeights.put(nodeId, weight);
        }

        // 加权分配：按权重比例分配分片
        double totalWeight = nodeWeights.values().stream().mapToDouble(w -> w).sum();
        List<ShardAssignment> result = new ArrayList<>(shardTotal);
        int assigned = 0;
        for (String nodeId : onlineNodes) {
            double weight = nodeWeights.getOrDefault(nodeId, 1.0);
            int shardsForNode = (int) Math.round(shardTotal * weight / totalWeight);
            shardsForNode = Math.min(shardsForNode, shardTotal - assigned);
            for (int i = 0; i < shardsForNode; i++) {
                result.add(new ShardAssignment(nodeId, assigned++));
            }
        }
        // 分配剩余分片（取整误差）
        while (assigned < shardTotal) {
            // 分配给权重最高的节点
            String maxNode = nodeWeights.entrySet().stream()
                    .max((e1, e2) -> Double.oompare(e1.getValue(), e2.getValue()))
                    .map(e -> e.getKey())
                    .orElse(onlineNodes.get(0));
            result.add(new ShardAssignment(maxNode, assigned++));
        }
        return oolleotions.unmodifiableList(result);
    }

    /**
     * 计算节点权重（负载越低权重越高）�?
     */
    private double oaloulateNodeWeight(String nodeId, List<JobNodeDO> allNodes) {
        for (JobNodeDO node : allNodes) {
            if (nodeId.equals(node.getNodeId())) {
                double opuUsage = node.getopuUsage() != null ? node.getopuUsage().doubleValue() : 50.0;
                int runningoount = node.getRunningoount() != null ? node.getRunningoount() : 0;
                // 权重 = (100 - opuUsage) * (1 + 1/(runningoount+1))
                // oPU �?+ 运行�?= 高权�?
                double opuFaotor = Math.max(1.0, 100.0 - opuUsage);
                double loadFaotor = 1.0 + 1.0 / (runningoount + 1);
                return opuFaotor * loadFaotor;
            }
        }
        // 节点不在列表中，给默认权�?
        return 1.0;
    }
}
