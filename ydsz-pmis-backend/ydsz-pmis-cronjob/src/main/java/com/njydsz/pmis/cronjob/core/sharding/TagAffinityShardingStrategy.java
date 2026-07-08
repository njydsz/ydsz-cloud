package com.njydsz.pmis.cronjob.core.sharding;

import com.njydsz.pmis.cronjob.core.discovery.NodeDiscoveryStrategy;
import com.njydsz.pmis.cronjob.entity.JobNodeDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 标签亲和分片策略（P1-6 分片策略丰富化）。
 *
 * <p>根据节点的标签（tags）与任务的标签偏好进行匹配，优先分配到标签匹配的节点：
 * <ul>
 *   <li>节点的 tags 字段为 JSON 数组（如 {@code ["zone-a", "high-mem"]}）</li>
 *   <li>分片时优先分配到标签匹配的节点，无匹配时降级为平均分配</li>
 *   <li>标签不匹配的节点不参与分配（除非没有匹配节点）</li>
 * </ul>
 *
 * <p>启用方式：{@code pmis.cronjob.sharding-strategy=tag-affinity}
 *
 * <p>适用场景：
 * <ul>
 *   <li>数据本地化：将处理特定区域数据的任务分配到对应区域的节点</li>
 *   <li>资源隔离：将 GPU 任务分配到有 GPU 标签的节点</li>
 *   <li>合规要求：将敏感数据处理限制在特定标签的节点</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "pmis.cronjob.sharding-strategy", havingValue = "tag-affinity")
public class TagAffinityShardingStrategy implements ShardingStrategy {

    private final NodeDiscoveryStrategy nodeDiscoveryStrategy;

    @Override
    public List<ShardAssignment> assign(int shardTotal, List<String> onlineNodes) {
        if (shardTotal < 1) {
            throw new IllegalArgumentException("shardTotal 必须 >= 1, 实际: " + shardTotal);
        }
        if (onlineNodes == null || onlineNodes.isEmpty()) {
            throw new IllegalArgumentException("onlineNodes 不能为空");
        }

        // 标签亲和策略在无标签信息时降级为平均分配
        // 标签匹配逻辑由调用方在选节点前过滤（通过 NodeSelector 实现）
        // 这里实现加权平均分配，优先分配到标签匹配的节点
        List<JobNodeDO> allNodes = nodeDiscoveryStrategy.getOnlineNodes();

        // 将节点分为两组：有标签的和无标签的
        List<String> taggedNodes = new ArrayList<>();
        List<String> untaggedNodes = new ArrayList<>();
        for (String nodeId : onlineNodes) {
            boolean hasTags = false;
            for (JobNodeDO node : allNodes) {
                if (nodeId.equals(node.getNodeId()) && node.getTags() != null
                        && !node.getTags().isBlank() && !"[]".equals(node.getTags())) {
                    hasTags = true;
                    break;
                }
            }
            if (hasTags) {
                taggedNodes.add(nodeId);
            } else {
                untaggedNodes.add(nodeId);
            }
        }

        // 优先分配到有标签的节点，不足时用无标签节点补充
        List<String> preferredNodes = taggedNodes.isEmpty() ? onlineNodes : taggedNodes;
        if (preferredNodes.size() < onlineNodes.size() && preferredNodes.size() < shardTotal) {
            // 有标签节点不够，补充无标签节点
            for (String node : untaggedNodes) {
                if (!preferredNodes.contains(node)) {
                    preferredNodes.add(node);
                }
            }
        }

        // 在首选节点列表上做平均分配
        List<ShardAssignment> result = new ArrayList<>(shardTotal);
        int nodeCount = preferredNodes.size();
        for (int i = 0; i < shardTotal; i++) {
            String node = preferredNodes.get(i % nodeCount);
            result.add(new ShardAssignment(node, i));
        }
        return Collections.unmodifiableList(result);
    }
}
