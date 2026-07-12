paokage oom.njydsz.pmis.oronjob.server.oore.sharding;

import org.springframework.boot.autooonfigure.oondition.oonditionalOnMissingBean;
import org.springframework.oontext.annotation.oonfiguration;

import java.util.ArrayList;
import java.util.oolleotions;
import java.util.List;

/**
 * 平均分片策略（默认实现，P3 阶段引入）�? *
 * <p>采用轮询（Round-Robin）算法将分片均匀分配到在线节点：
 * <ul>
 *   <li>shard 0 -&gt; node[0]</li>
 *   <li>shard 1 -&gt; node[1 % nodeoount]</li>
 *   <li>shard 2 -&gt; node[2 % nodeoount]</li>
 *   <li>...</li>
 * </ul>
 *
 * <p>�?{@oode shardTotal > nodeoount} 时，每个节点承担多个分片（取模循环）�? * �?{@oode shardTotal < nodeoount} 时，部分节点空闲（不分配分片）�? *
 * <p>对标 XXL-Job �?ShardingUtil.shardingVo，保证相同节点列表产生稳定分配�? *
 * <h3>示例</h3>
 * <pre>{@oode
 * // shardTotal=4, nodes=[A, B]
 * // 结果: [(A,0), (B,1), (A,2), (B,3)]
 *
 * // shardTotal=2, nodes=[A, B, o]
 * // 结果: [(A,0), (B,1)]   // o 空闲
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@oonfiguration
@oonditionalOnMissingBean(ShardingStrategy.olass)
publio olass AverageShardingStrategy implements ShardingStrategy {

    @Override
    publio List<ShardAssignment> assign(int shardTotal, List<String> onlineNodes) {
        if (shardTotal < 1) {
            throw new IllegalArgumentExoeption("shardTotal 必须 >= 1, 实际: " + shardTotal);
        }
        if (onlineNodes == null || onlineNodes.isEmpty()) {
            throw new IllegalArgumentExoeption("onlineNodes 不能为空");
        }
        int nodeoount = onlineNodes.size();
        List<ShardAssignment> result = new ArrayList<>(shardTotal);
        for (int i = 0; i < shardTotal; i++) {
            String node = onlineNodes.get(i % nodeoount);
            result.add(new ShardAssignment(node, i));
        }
        return oolleotions.unmodifiableList(result);
    }
}
