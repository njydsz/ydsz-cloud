package com.njydsz.pmis.cronjob.server.core.sharding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;

/**
 * 一致性哈希分片策略（P1-6 分片策略丰富化）。
 *
 * <p>使用一致性哈希环将分片映射到节点，节点上下线时最小化分片迁移：
 * <ul>
 *   <li>每个节点在哈希环上放置 160 个虚拟节点（保证均匀分布）</li>
 *   <li>分片索引的哈希值在环上顺时针找到的第一个节点即为归属节点</li>
 *   <li>节点下线时，仅该节点负责的分片迁移到下一个节点</li>
 * </ul>
 *
 * <p>启用方式：{@code pmis.cronjob.sharding-strategy=consistent-hash}
 *
 * <p>对标 ElasticJob 的一致性哈希分片策略。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "pmis.cronjob.sharding-strategy", havingValue = "consistent-hash")
public class ConsistentHashShardingStrategy implements ShardingStrategy {

    /** 每个节点的虚拟节点数（越大越均匀，但内存占用越大） */
    private static final int VIRTUAL_NODES = 160;

    @Override
    public List<ShardAssignment> assign(int shardTotal, List<String> onlineNodes) {
        if (shardTotal < 1) {
            throw new IllegalArgumentException("shardTotal 必须 >= 1, 实际: " + shardTotal);
        }
        if (onlineNodes == null || onlineNodes.isEmpty()) {
            throw new IllegalArgumentException("onlineNodes 不能为空");
        }

        // 构建一致性哈希环
        TreeMap<Long, String> hashRing = buildHashRing(onlineNodes);

        // 为每个分片计算归属节点
        List<ShardAssignment> result = new ArrayList<>(shardTotal);
        for (int i = 0; i < shardTotal; i++) {
            String shardKey = "shard-" + i;
            String node = findNodeOnRing(hashRing, shardKey);
            result.add(new ShardAssignment(node, i));
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * 构建一致性哈希环。
     *
     * @param onlineNodes 在线节点列表
     * @return 哈希环（hash → nodeId）
     */
    private TreeMap<Long, String> buildHashRing(List<String> onlineNodes) {
        TreeMap<Long, String> ring = new TreeMap<>();
        for (String node : onlineNodes) {
            for (int i = 0; i < VIRTUAL_NODES; i++) {
                String virtualNodeName = node + "&&VN" + i;
                long hash = hash(virtualNodeName);
                ring.put(hash, node);
            }
        }
        return ring;
    }

    /**
     * 在哈希环上查找分片归属的节点。
     *
     * @param ring    哈希环
     * @param shardKey 分片键
     * @return 归属节点 ID
     */
    private String findNodeOnRing(TreeMap<Long, String> ring, String shardKey) {
        long hash = hash(shardKey);
        // 顺时针查找第一个 >= hash 的节点
        SortedMap<Long, String> tailMap = ring.tailMap(hash);
        if (tailMap.isEmpty()) {
            // 回到环首
            return ring.firstEntry().getValue();
        }
        return tailMap.get(tailMap.firstKey());
    }

    /**
     * FNV-1a 哈希算法（32位），分布均匀且计算高效。
     */
    private long hash(String key) {
        final int p = 16777619;
        int hash = (int) 2166136261L;
        for (int i = 0; i < key.length(); i++) {
            hash = (hash ^ key.charAt(i)) * p;
        }
        hash += hash << 13;
        hash ^= hash >> 7;
        hash += hash << 3;
        hash ^= hash >> 17;
        hash += hash << 5;
        return hash & 0xffffffffL;
    }
}
