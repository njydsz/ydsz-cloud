package com.njydsz.pmis.cronjob.core.sharding;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AverageShardingStrategy} 单元测试（P3-2）。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>单个节点：所有分片分配到同一节点</li>
 *   <li>两个节点：轮询分配</li>
 *   <li>分片数 &lt; 节点数：部分节点空闲</li>
 *   <li>分片数 &gt; 节点数：每节点承担多分片</li>
 *   <li>异常入参：shardTotal &lt; 1 / onlineNodes 为空</li>
 *   <li>确定性：相同输入产生相同输出</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("AverageShardingStrategy 平均分片策略测试")
class AverageShardingStrategyTest {

    private final AverageShardingStrategy strategy = new AverageShardingStrategy();

    @Test
    @DisplayName("单节点：所有分片分配到同一节点")
    void assign_singleNode_allShardsToSameNode() {
        List<String> nodes = List.of("node-A");

        List<ShardAssignment> result = strategy.assign(3, nodes);

        assertEquals(3, result.size());
        for (int i = 0; i < 3; i++) {
            assertEquals("node-A", result.get(i).nodeId());
            assertEquals(i, result.get(i).shardIndex());
        }
    }

    @Test
    @DisplayName("两节点轮询分配: A,B,A,B")
    void assign_twoNodes_roundRobin() {
        List<String> nodes = Arrays.asList("node-A", "node-B");

        List<ShardAssignment> result = strategy.assign(4, nodes);

        assertEquals(4, result.size());
        assertEquals("node-A", result.get(0).nodeId());
        assertEquals(0, result.get(0).shardIndex());
        assertEquals("node-B", result.get(1).nodeId());
        assertEquals(1, result.get(1).shardIndex());
        assertEquals("node-A", result.get(2).nodeId());
        assertEquals(2, result.get(2).shardIndex());
        assertEquals("node-B", result.get(3).nodeId());
        assertEquals(3, result.get(3).shardIndex());
    }

    @Test
    @DisplayName("分片数 < 节点数: 多余节点空闲")
    void assign_fewerShardsThanNodes_extraNodesIdle() {
        List<String> nodes = Arrays.asList("A", "B", "C");

        List<ShardAssignment> result = strategy.assign(2, nodes);

        assertEquals(2, result.size());
        assertEquals("A", result.get(0).nodeId());
        assertEquals("B", result.get(1).nodeId());
        // C 不被分配
    }

    @Test
    @DisplayName("分片数 > 节点数: 每节点承担多分片")
    void assign_moreShardsThanNodes_eachNodeMultipleShards() {
        List<String> nodes = Arrays.asList("A", "B");

        List<ShardAssignment> result = strategy.assign(5, nodes);

        assertEquals(5, result.size());
        // A: shard 0, 2, 4
        assertEquals("A", result.get(0).nodeId());
        assertEquals("A", result.get(2).nodeId());
        assertEquals("A", result.get(4).nodeId());
        // B: shard 1, 3
        assertEquals("B", result.get(1).nodeId());
        assertEquals("B", result.get(3).nodeId());
    }

    @Test
    @DisplayName("shardTotal < 1 抛 IllegalArgumentException")
    void assign_shardTotalLessThan1_throwsException() {
        List<String> nodes = List.of("A");

        assertThrows(IllegalArgumentException.class, () -> strategy.assign(0, nodes));
    }

    @Test
    @DisplayName("onlineNodes 为空抛 IllegalArgumentException")
    void assign_emptyNodes_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> strategy.assign(2, Collections.emptyList()));
    }

    @Test
    @DisplayName("onlineNodes 为 null 抛 IllegalArgumentException")
    void assign_nullNodes_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> strategy.assign(2, null));
    }

    @Test
    @DisplayName("相同输入产生相同输出（确定性）")
    void assign_sameInput_sameOutput() {
        List<String> nodes = Arrays.asList("A", "B", "C");

        List<ShardAssignment> r1 = strategy.assign(6, nodes);
        List<ShardAssignment> r2 = strategy.assign(6, nodes);

        assertEquals(r1.size(), r2.size());
        for (int i = 0; i < r1.size(); i++) {
            assertEquals(r1.get(i).nodeId(), r2.get(i).nodeId());
            assertEquals(r1.get(i).shardIndex(), r2.get(i).shardIndex());
        }
    }

    @Test
    @DisplayName("不同节点顺序产生不同分配结果")
    void assign_differentNodeOrder_differentOutput() {
        List<String> nodes1 = Arrays.asList("A", "B");
        List<String> nodes2 = Arrays.asList("B", "A");

        List<ShardAssignment> r1 = strategy.assign(2, nodes1);
        List<ShardAssignment> r2 = strategy.assign(2, nodes2);

        assertNotEquals(r1.get(0).nodeId(), r2.get(0).nodeId());
    }

    @Test
    @DisplayName("返回结果不可变")
    void assign_resultImmutable() {
        List<String> nodes = List.of("A");

        List<ShardAssignment> result = strategy.assign(2, nodes);

        assertThrows(UnsupportedOperationException.class, () -> result.add(new ShardAssignment("X", 99)));
    }

    @Test
    @DisplayName("单分片任务（shardTotal=1）分配到第一个节点")
    void assign_singleShard_assignedToFirstNode() {
        List<String> nodes = Arrays.asList("A", "B");

        List<ShardAssignment> result = strategy.assign(1, nodes);

        assertEquals(1, result.size());
        assertEquals("A", result.get(0).nodeId());
        assertEquals(0, result.get(0).shardIndex());
    }

    @Test
    @DisplayName("分片索引连续递增 0,1,2,...")
    void assign_shardIndicesAreSequential() {
        List<String> nodes = Arrays.asList("A", "B", "C");

        List<ShardAssignment> result = strategy.assign(7, nodes);

        for (int i = 0; i < result.size(); i++) {
            assertEquals(i, result.get(i).shardIndex(), "shardIndex should be sequential at position " + i);
        }
    }
}
