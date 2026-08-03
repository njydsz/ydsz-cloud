package com.njydsz.literule.server.distributed;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ConsistentHashSharder} 一致性哈希分片器单元测试：覆盖虚拟节点、
 * 权重、确定性、环回绕、节点变更、签名去重等核心算法场景。
 *
 * <p>一致性哈希是 ydsz-literule 分布式执行的核心算法，正确性直接决定
 * 规则分片的稳定性与负载均衡性。本测试聚焦：
 * <ul>
 *   <li>相同 key 分片结果确定性</li>
 *   <li>不同 key 在多节点间均匀分布</li>
 *   <li>节点权重影响分片比例</li>
 *   <li>节点变更后分片迁移最小化</li>
 *   <li>边界条件（空节点、null key、环回绕）</li>
 * </ul>
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@DisplayName("一致性哈希分片器 ConsistentHashSharder 测试")
class ConsistentHashSharderTest {

    private ConsistentHashSharder sharder;

    @BeforeEach
    void setUp() {
        sharder = new ConsistentHashSharder();
    }

    private ClusterNode node(String id, String addr) {
        return new ClusterNode(id, addr);
    }

    private ClusterNode node(String id, String addr, int weight) {
        return new ClusterNode(id, addr, weight);
    }

    @Nested
    @DisplayName("updateNodes 节点列表更新")
    /**
     * 测试分组：updateNodes 节点列表更新
     */
    /**
     * 测试分组：「更新非空节点列表后环非空」等
     */
    class UpdateNodes {

        @Test
        @DisplayName("更新非空节点列表后环非空")
        void shouldBuildRingForNonEmptyNodes() {
            List<ClusterNode> nodes = List.of(
                    node("n1", "127.0.0.1:9001"),
                    node("n2", "127.0.0.1:9002"),
                    node("n3", "127.0.0.1:9003"));
            sharder.updateNodes(nodes);
            assertThat(sharder.getNodeCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("更新 null 节点列表清空环")
        void shouldClearRingForNullNodes() {
            sharder.updateNodes(List.of(node("n1", "127.0.0.1:9001")));
            sharder.updateNodes(null);
            assertThat(sharder.getNodeCount()).isEqualTo(0);
            assertThat(sharder.getNodeSignature()).isEmpty();
        }

        @Test
        @DisplayName("更新空节点列表清空环")
        void shouldClearRingForEmptyNodes() {
            sharder.updateNodes(List.of(node("n1", "127.0.0.1:9001")));
            sharder.updateNodes(List.of());
            assertThat(sharder.getNodeCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("相同节点列表重复更新不重建环（签名去重）")
        void shouldNotRebuildForSameSignature() {
            List<ClusterNode> nodes = List.of(node("n1", "127.0.0.1:9001"));
            sharder.updateNodes(nodes);
            String sigBefore = sharder.getNodeSignature();

            sharder.updateNodes(nodes);
            String sigAfter = sharder.getNodeSignature();

            assertThat(sigAfter).isEqualTo(sigBefore);
        }

        @Test
        @DisplayName("节点列表变更后签名变化")
        void shouldChangeSignatureWhenNodesChange() {
            sharder.updateNodes(List.of(node("n1", "127.0.0.1:9001")));
            String sig1 = sharder.getNodeSignature();

            sharder.updateNodes(List.of(
                    node("n1", "127.0.0.1:9001"),
                    node("n2", "127.0.0.1:9002")));
            String sig2 = sharder.getNodeSignature();

            assertThat(sig1).isNotEqualTo(sig2);
        }

        @Test
        @DisplayName("节点权重变化导致签名变化")
        void shouldChangeSignatureWhenWeightChanges() {
            sharder.updateNodes(List.of(node("n1", "127.0.0.1:9001", 1)));
            String sig1 = sharder.getNodeSignature();

            sharder.updateNodes(List.of(node("n1", "127.0.0.1:9001", 3)));
            String sig2 = sharder.getNodeSignature();

            assertThat(sig1).isNotEqualTo(sig2);
        }
    }

    /**
     * 测试分组：「shard 分片确定性」等
     */
    @Nested
    @DisplayName("shard 分片确定性")
    class ShardDeterminism {

        @Test
        @DisplayName("相同 key 多次分片结果一致")
        void shouldReturnSameNodeForSameKey() {
            sharder.updateNodes(List.of(
                    node("n1", "127.0.0.1:9001"),
                    node("n2", "127.0.0.1:9002"),
                    node("n3", "127.0.0.1:9003")));

            ClusterNode first = sharder.shard("rule-001");
            ClusterNode second = sharder.shard("rule-001");
            ClusterNode third = sharder.shard("rule-001");

            assertThat(first).isNotNull();
            assertThat(first).isSameAs(second);
            assertThat(second).isSameAs(third);
        }

        @Test
        @DisplayName("不同 key 分片结果可相同也可不同（不抛异常）")
        void shouldShardDifferentKeysWithoutError() {
            sharder.updateNodes(List.of(
                    node("n1", "127.0.0.1:9001"),
                    node("n2", "127.0.0.1:9002")));

            for (int i = 0; i < 100; i++) {
                ClusterNode owner = sharder.shard("key-" + i);
                assertThat(owner).isNotNull();
                assertThat(owner.getNodeId()).isIn("n1", "n2");
            }
        }

        @Test
        @DisplayName("空环 shard 返回 null")
        void shouldReturnNullForEmptyRing() {
            assertThat(sharder.shard("any-key")).isNull();
        }

        @Test
        @DisplayName("单节点环所有 key 分片到该节点")
        void shouldShardAllKeysToSingleNodes() {
            sharder.updateNodes(List.of(node("only", "127.0.0.1:9001")));

            for (int i = 0; i < 50; i++) {
                ClusterNode owner = sharder.shard("key-" + i);
                assertThat(owner).isNotNull();
                assertThat(owner.getNodeId()).isEqualTo("only");
            }
        }

        @Test
        @DisplayName("环回绕：key hash 大于最大节点时回绕到首个节点")
        void shouldWrapAroundRing() {
            // 通过大量 key 验证至少有部分 key 命中环回绕路径
            sharder.updateNodes(List.of(
                    node("n1", "127.0.0.1:9001"),
                    node("n2", "127.0.0.1:9002"),
                    node("n3", "127.0.0.1:9003")));

            // 任意 key 都应返回非 null 节点（覆盖环回绕与正常路径）
            for (int i = 0; i < 1000; i++) {
                ClusterNode owner = sharder.shard(UUID.randomUUID().toString());
                assertThat(owner).i    /**
     * 测试分组：「isMine 归属判断」等
     */
sNotNull();
            }
        }
    }

    @Nested
    @DisplayName("isMine 归属判断")
    class IsMineCheck {

        @Test
        @DisplayName("key 所属节点 isMine 返回 true")
        void shouldReturnTrueForOwnerNode() {
            sharder.updateNodes(List.of(
                    node("n1", "127.0.0.1:9001"),
                    node("n2", "127.0.0.1:9002")));

            ClusterNode owner = sharder.shard("rule-001");
            assertThat(sharder.isMine("rule-001", owner.getNodeId())).isTrue();
        }

        @Test
        @DisplayName("非归属节点 isMine 返回 false")
        void shouldReturnFalseForNonOwnerNode() {
            sharder.updateNodes(List.of(
                    node("n1", "127.0.0.1:9001"),
                    node("n2", "127.0.0.1:9002")));

            ClusterNode owner = sharder.shard("rule-001");
            String nonOwner = owner.getNodeId().equals("n1") ? "n2" : "n1";
            assertThat(sharder.isMine("rule-001", nonOwner)).isFalse();
        }

        @Test
        @DisplayName("空环时 isMine 返回 false（owner 为 null）")
        void shouldReturnFalseForEmptyRing() {
            assertThat(sharder.isMine("any", "n1")).isFalse();
        }

        @Test
        @DisplayName("null nodeId 时 isMine 返回 false")
        void shouldReturnFalseForNullNodeId() {
            sharder.updateNodes(List.of(node("n1", "127.0.0.1:9001"    /**
     * 测试分组：「静态 isMine 方法」等
     */
)));
            assertThat(sharder.isMine("any", null)).isFalse();
        }
    }

    @Nested
    @DisplayName("静态 isMine 方法")
    class StaticIsMine {

        @Test
        @DisplayName("null 节点列表默认本地执行返回 true")
        void shouldReturnTrueForNullNodes() {
            assertThat(ConsistentHashSharder.isMine("key", "n1", null)).isTrue();
        }

        @Test
        @DisplayName("空节点列表默认本地执行返回 true")
        void shouldReturnTrueForEmptyNodes() {
            assertThat(ConsistentHashSharder.isMine("key", "n1", List.of())).isTrue();
        }

        @Test
        @DisplayName("null key 默认本地执行返回 true")
        void shouldReturnTrueForNullKey() {
            List<ClusterNode> nodes = List.of(node("n1", "127.0.0.1:9001"));
            assertThat(ConsistentHashSharder.isMine(null, "n1", nodes)).isTrue();
        }

        @Test
        @DisplayName("null nodeId 返回 false")
        void shouldReturnFalseForNullNodeId() {
            List<ClusterNode> nodes = List.of(node("n1", "127.0.0.1:9001"));
            assertThat(ConsistentHashSharder.isMine("key", null, nodes)).isFalse();
        }

        @Test
        @DisplayName("单节点列表 nodeId 匹配返回 true")
        void shouldReturnTrueForMatchingNode() {
            List<ClusterNode> nodes = List.of(node("n1", "127.0.0.1:9001"));
            // 静态方法用 hash % size，单节点时 idx=0 必匹配
            assertThat(ConsistentHashSharder.isMine("any-key", "n1", nodes)).isTrue();
        }

        @Test
        @DisplayName("多节点列表分片结果与 nodeId 匹配判定")
        void shouldReturnCorrectResultForMultiNodes() {
            List<ClusterNode> nodes = List.of(
                    node("n1", "127.0.0.1:9001"),
                    node("n2", "127.0.0.1:9002"),
                    node("n3", "127.0.0.1:9003"));
            // 验证对任意 key 至少有一个节点返回 true（即归属判断自洽）
            for (int i = 0; i < 30; i++) {
                String key = "key-" + i;
                boolean n1 = ConsistentHashSharder.isMine(key, "n1", nodes);
                boolean n2 = ConsistentHashSharder.isMine(key, "n2", nodes);
                boolean n3 = ConsistentHashSharder.isMine(key, "n3", nodes);
                // 静态方法基于 hash % size，只有一个节点会返回 true
                long trueCount = 0;
                if (n1) trueCount++;
                if (n2    /**
     * 测试分组：「权重与均匀性」等
     */
) trueCount++;
                if (n3) trueCount++;
                assertThat(trueCount).isEqualTo(1);
            }
        }
    }

    @Nested
    @DisplayName("权重与均匀性")
    class WeightAndDistribution {

        @Test
        @DisplayName("权重 0 节点至少有 1 个虚拟节点（Math.max(1, weight * vnodes)）")
        void shouldHandleZeroWeightNode() {
            // 权重为 0 时 vnodes = max(1, 0 * 150) = 1，节点仍参与分片
            sharder.updateNodes(List.of(
                    node("n1", "127.0.0.1:9001", 0),
                    node("n2", "127.0.0.1:9002", 1)));
            assertThat(sharder.getNodeCount()).isEqualTo(2);
            // 不抛异常且能正常分片
            assertThat(sharder.shard("any-key")).isNotNull();
        }

        @Test
        @DisplayName("高权重节点分得更多 key（统计验证）")
        void shouldDistributeMoreKeysToHighWeightNode() {
            sharder.updateNodes(List.of(
                    node("light", "127.0.0.1:9001", 1),
                    node("heavy", "127.0.0.1:9002", 5)));

            Map<String, Integer> counter = new HashMap<>();
            counter.put("light", 0);
            counter.put("heavy", 0);

            // 1000 个 key 统计分布
            for (int i = 0; i < 1000; i++) {
                ClusterNode owner = sharder.shard("distribution-test-key-" + i);
                counter.merge(owner.getNodeId(), 1, Integer::sum);
            }

            // 高权重节点应分得更多 key（不严格 5:1 比例，但应明显更多）
            assertThat(counter.get("heavy"))
                    .as("高权重节点应分得更多 key")
                    .isGreaterThan(counter.get("light"));
        }

        @Test
        @DisplayName("3 节点等权分片覆盖率 100%（所有 key 命中至少一个节点）")
        void shouldCoverAllKeysWithThreeNodes() {
            sharder.updateNodes(List.of(
                    node("n1", "127.0.0.1:9001"),
                    node("n2", "127.0.0.1:9002"),
                    node("n3", "127.0.0.1:9003")));

            for (int i = 0; i < 500; i++) {
                ClusterNode owner = sharder.shard("coverage-key-" + i);
                assertThat(owner).isNotNull();
                assertThat(owner.getNodeId()).isIn("n1", "n2", "n3");
            }
        }

        @Test
        @DisplayName("三节点等权分片相对均匀（每节点至少 20%）")
        void shouldDistributeRelativelyEvenly() {
            sharder.updateNodes(List.of(
                    node("n1", "127.0.0.1:9001"),
                    node("n2", "127.0.0.1:9002"),
                    node("n3", "127.0.0.1:9003")));

            Map<String, Integer> counter = new HashMap<>();
            for (int i = 0; i < 3000; i++) {
                ClusterNode owner = sharder.shard("evenness-" + i);
                counter.merge(owner.getNodeId(), 1, Integer::sum);
            }

            // 每个节点至少分得 20%（3000 * 0.2 = 600）
            assertThat(counter.g    /**
     * 测试分组：「节点变更最小迁移」等
     */
et("n1")).isGreaterThan(600);
            assertThat(counter.get("n2")).isGreaterThan(600);
            assertThat(counter.get("n3")).isGreaterThan(600);
        }
    }

    @Nested
    @DisplayName("节点变更最小迁移")
    class MinimalMigration {

        @Test
        @DisplayName("新增节点时仅部分 key 迁移（一致性）")
        void shouldMinimizeMigrationOnNodeAdd() {
            // 初始 3 节点
            List<ClusterNode> threeNodes = List.of(
                    node("n1", "127.0.0.1:9001"),
                    node("n2", "127.0.0.1:9002"),
                    node("n3", "127.0.0.1:9003"));
            sharder.updateNodes(threeNodes);

            // 记录 1000 个 key 的归属
            Map<String, String> before = new HashMap<>();
            for (int i = 0; i < 1000; i++) {
                String key = "migration-key-" + i;
                before.put(key, sharder.shard(key).getNodeId());
            }

            // 新增 n4 节点
            List<ClusterNode> fourNodes = new ArrayList<>(threeNodes);
            fourNodes.add(node("n4", "127.0.0.1:9004"));
            sharder.updateNodes(fourNodes);

            // 统计迁移数量
            int migrated = 0;
            for (Map.Entry<String, String> entry : before.entrySet()) {
                String currentOwner = sharder.shard(entry.getKey()).getNodeId();
                if (!currentOwner.equals(entry.getValue())) {
                    migrated++;
                }
            }

            // 一致性哈希核心特性：新增 1 个节点，理论上仅约 1/4 的 key 迁移
            // 实际放宽到 40% 以容忍虚拟节点抖动
            assertThat(migrated)
                    .as("新增 1 节点迁移比例应小于 40%（实际迁移: %d/1000）", migrated)
                    .isLessThan(400);
        }

        @Test
        @DisplayName("移除节点时其 key 迁移到其他节点（不丢失）")
        void shouldMigrateKeysOnNodeRemoval() {
            List<ClusterNode> threeNodes = List.of(
                    node("n1", "127.0.0.1:9001"),
                    node("n2", "127.0.0.1:9002"),
                    node("n3", "127.0.0.1:9003"));
            sharder.updateNodes(threeNodes);

            // 收集 n3 持有的 key
            List<String> n3Keys = new ArrayList<>();
            for (int i = 0; i < 2000; i++) {
                String key = "removal-" + i;
                if ("n3".equals(sharder.shard(key).getNodeId())) {
                    n3Keys.add(key);
                }
            }
            assertThat(n3Keys).isNotEmpty();

            // 移除 n3
            List<ClusterNode> twoNodes = List.of(
                    node("n1", "127.0.0.1:9001"),
                    node("n2", "127.0.0.1:9002"));
            sharder.updateNodes(twoNodes);

            // 原 n3 的 key 必须落到 n1 或 n2，不能丢失
            for (String ke    /**
     * 测试分组：「虚拟节点数配置」等
     */
y : n3Keys) {
                ClusterNode owner = sharder.shard(key);
                assertThat(owner).isNotNull();
                assertThat(owner.getNodeId()).isIn("n1", "n2");
            }
        }
    }

    @Nested
    @DisplayName("虚拟节点数配置")
    class VirtualNodesConfig {

        @Test
        @DisplayName("自定义虚拟节点数生效")
        void shouldUseCustomVirtualNodes() {
            ConsistentHashSharder customSharder = new ConsistentHashSharder(10);
            customSharder.updateNodes(List.of(
                    node("n1", "127.0.0.1:9001"),
                    node("n2", "127.0.0.1:9002")));
            // 能正常分片即可
            assertThat(customSharder.shard("any-key")).isNotNull();
            assertThat(customSharder.getNodeCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("虚拟节点数为 0 时被强制为 1")
        void shouldForceMinVirtualNodes() {
            ConsistentHashSharder minSharder = new ConsistentHashSharder(0);
            minSharder.updateNodes(List.of(node("n1", "127.0.0.1:9001")));
            assertThat(minSharder.shard("any-key")).isNotNull();
        }

        @Test
        @DisplayName("负数虚拟节点数被强制为 1")
        void shouldForceMinForNegativeVirtualNode    /**
     * 测试分组：负数虚拟节点数被强制为 1
     */
s() {
            ConsistentHashSharder negSharder = new ConsistentHashSharder(-5);
            negSharder.updateNodes(List.of(node("n1", "127.0.0.1:9001")));
            assertThat(negSharder.shard("any-key")).isNotNull();
        }
    }

    @Nested
    @DisplayName("getNodeCount 节点计数")
    class NodeCount {

        @Test
        @DisplayName("未更新节点时返回 0")
        void shouldReturnZeroBeforeUpdate() {
            assertThat(sharder.getNodeCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("更新后返回去重节点数")
        void shouldReturnDistinctNodeCount() {
            sharder.updateNodes(List.of(
                    node("n1", "127.0.0.1:9001"),
                    node("n2", "127.0.0.1:9002"),
                    node("n3", "127.0.0.1:9003"),
                    // 重复节点（相同 nodeId）应被去重
                    node("n1", "127.0.0.1:9001-duplicate")));
            // 注意：buildSignature 不会去重，但 ring.values().stream().distinct() 会去重
            assertThat(sharder.getNodeCount()).isLessThanOrEqualTo(3);
        }
    }
}
