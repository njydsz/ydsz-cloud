package com.njydsz.pmis.cronjob.core.dispatch;

import com.njydsz.pmis.cronjob.entity.JobNodeDO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link LeastLoadNodeSelector} 单元测试。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>候选节点为空/null 时返回 null</li>
 *   <li>单个节点直接返回</li>
 *   <li>多节点按 running_count 升序选择</li>
 *   <li>running_count 相同时按 cpu_usage 升序</li>
 *   <li>cpu_usage 也相同时按 nodeId 字典序</li>
 *   <li>null 字段安全降级</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("LeastLoadNodeSelector 节点选择策略测试")
class LeastLoadNodeSelectorTest {

    private final LeastLoadNodeSelector selector = new LeastLoadNodeSelector();

    @Test
    @DisplayName("candidates 为 null 时返回 null")
    void select_nullCandidates_returnsNull() {
        JobNodeDO result = selector.select(buildJob("k1"), null);
        assertNull(result);
    }

    @Test
    @DisplayName("candidates 为空列表时返回 null")
    void select_emptyCandidates_returnsNull() {
        JobNodeDO result = selector.select(buildJob("k1"), Collections.emptyList());
        assertNull(result);
    }

    @Test
    @DisplayName("单节点直接返回该节点")
    void select_singleCandidate_returnsIt() {
        JobNodeDO node = buildNode("node-1", 0, new BigDecimal("10.00"));
        JobNodeDO result = selector.select(buildJob("k1"), List.of(node));
        assertEquals("node-1", result.getNodeId());
    }

    @Test
    @DisplayName("多节点时按 running_count 升序选择最小者")
    void select_multipleNodes_picksLowestRunningCount() {
        JobNodeDO busy = buildNode("busy", 5, new BigDecimal("50.00"));
        JobNodeDO idle = buildNode("idle", 0, new BigDecimal("80.00"));
        JobNodeDO mid = buildNode("mid", 2, new BigDecimal("30.00"));

        JobNodeDO result = selector.select(buildJob("k1"), List.of(busy, idle, mid));
        assertEquals("idle", result.getNodeId());
    }

    @Test
    @DisplayName("running_count 相同时按 cpu_usage 升序")
    void select_sameRunningCount_picksLowerCpu() {
        JobNodeDO highCpu = buildNode("highCpu", 1, new BigDecimal("80.00"));
        JobNodeDO lowCpu = buildNode("lowCpu", 1, new BigDecimal("20.00"));

        JobNodeDO result = selector.select(buildJob("k1"), List.of(highCpu, lowCpu));
        assertEquals("lowCpu", result.getNodeId());
    }

    @Test
    @DisplayName("running_count 和 cpu_usage 都相同时按 nodeId 字典序")
    void select_sameLoad_picksLowestNodeId() {
        JobNodeDO nodeB = buildNode("nodeB", 2, new BigDecimal("50.00"));
        JobNodeDO nodeA = buildNode("nodeA", 2, new BigDecimal("50.00"));

        JobNodeDO result = selector.select(buildJob("k1"), List.of(nodeB, nodeA));
        assertEquals("nodeA", result.getNodeId());
    }

    @Test
    @DisplayName("running_count 为 null 视为 0")
    void select_nullRunningCount_treatedAsZero() {
        JobNodeDO nullCount = buildNode("nullCount", null, new BigDecimal("80.00"));
        JobNodeDO zeroCount = buildNode("zeroCount", 0, new BigDecimal("20.00"));

        // 两者 running_count 都视为 0，cpu_usage 较低者胜
        JobNodeDO result = selector.select(buildJob("k1"), List.of(nullCount, zeroCount));
        assertEquals("zeroCount", result.getNodeId());
    }

    @Test
    @DisplayName("cpu_usage 为 null 视为 0（最低优先）")
    void select_nullCpuUsage_treatedAsZero() {
        JobNodeDO nullCpu = buildNode("nullCpu", 1, null);
        JobNodeDO zeroCpu = buildNode("zeroCpu", 1, new BigDecimal("0.00"));

        // 两者比较：cpu_usage null → BigDecimal.ZERO，与 0.00 相等，按 nodeId 字典序
        JobNodeDO result = selector.select(buildJob("k1"), List.of(nullCpu, zeroCpu));
        assertEquals("nullCpu", result.getNodeId());
    }

    private com.njydsz.pmis.cronjob.entity.JobDO buildJob(String jobKey) {
        com.njydsz.pmis.cronjob.entity.JobDO job = new com.njydsz.pmis.cronjob.entity.JobDO();
        job.setJobKey(jobKey);
        return job;
    }

    private JobNodeDO buildNode(String nodeId, Integer runningCount, BigDecimal cpuUsage) {
        JobNodeDO node = new JobNodeDO();
        node.setNodeId(nodeId);
        node.setRunningCount(runningCount);
        node.setCpuUsage(cpuUsage);
        return node;
    }
}
