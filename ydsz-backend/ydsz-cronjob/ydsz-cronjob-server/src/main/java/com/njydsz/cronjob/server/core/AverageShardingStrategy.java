package com.njydsz.cronjob.server.core.sharding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Configuration;

/**
 * 平均分片策略（默认实现，P3 阶段引入）。
 *
 * <p>采用轮询（Round-Robin）算法将分片均匀分配到在线节点：
 * <ul>
 *   <li>shard 0 -&gt; node[0]</li>
 *   <li>shard 1 -&gt; node[1 % nodeCount]</li>
 *   <li>shard 2 -&gt; node[2 % nodeCount]</li>
 *   <li>...</li>
 * </ul>
 *
 * <p>当 {@code shardTotal > nodeCount} 时，每个节点承担多个分片（取模循环）；
 * 当 {@code shardTotal < nodeCount} 时，部分节点空闲（不分配分片）。
 *
 * <p>对标 XXL-Job 的 ShardingUtil.shardingVo，保证相同节点列表产生稳定分配。
 *
 * <h3>示例</h3>
 * <pre>{@code
 * // shardTotal=4, nodes=[A, B]
 * // 结果: [(A,0), (B,1), (A,2), (B,3)]
 *
 * // shardTotal=2, nodes=[A, B, C]
 * // 结果: [(A,0), (B,1)]   // C 空闲
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Configuration
@ConditionalOnMissingBean(ShardingStrategy.class)
public class AverageShardingStrategy implements ShardingStrategy {

    @Override
    public List<ShardAssignment> assign(int shardTotal, List<String> onlineNodes) {
        if (shardTotal < 1) {
            throw new IllegalArgumentException("shardTotal 必须 >= 1, 实际: " + shardTotal);
        }
        if (onlineNodes == null || onlineNodes.isEmpty()) {
            throw new IllegalArgumentException("onlineNodes 不能为空");
        }
        int nodeCount = onlineNodes.size();
        List<ShardAssignment> result = new ArrayList<>(shardTotal);
        for (int i = 0; i < shardTotal; i++) {
            String node = onlineNodes.get(i % nodeCount);
            result.add(new ShardAssignment(node, i));
        }
        return Collections.unmodifiableList(result);
    }
}
