package com.njydsz.pmis.literule.distributed;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 一致性 Hash 分片器测试
 */
@DisplayName("一致性 Hash 分片器测试")
class ConsistentHashSharderTest {

    private ConsistentHashSharder sharder;

    @BeforeEach
    void setUp() {
        sharder = new ConsistentHashSharder(150);
    }

    @Test
    @DisplayName("相同 key 在节点列表不变时返回相同节点")
    void shouldReturnStableOwner() {
        List<ClusterNode> nodes = Arrays.asList(
                new ClusterNode("n1", "h1"),
                new ClusterNode("n2", "h2"),
                new ClusterNode("n3", "h3")
        );
        sharder.updateNodes(nodes);
        ClusterNode owner1 = sharder.shard("rule-001");
        ClusterNode owner2 = sharder.shard("rule-001");
        assertNotNull(owner1);
        assertEquals(owner1.getNodeId(), owner2.getNodeId());
    }

    @Test
    @DisplayName("1000 个 key 在 3 节点上均匀分布（偏差 ≤15%）")
    void shouldDistributeEvenly() {
        List<ClusterNode> nodes = Arrays.asList(
                new ClusterNode("n1", "h1"),
                new ClusterNode("n2", "h2"),
                new ClusterNode("n3", "h3")
        );
        sharder.updateNodes(nodes);
        int[] counts = new int[3];
        for (int i = 0; i < 1000; i++) {
            ClusterNode owner = sharder.shard("key-" + i);
            int idx = owner.getNodeId().equals("n1") ? 0
                    : owner.getNodeId().equals("n2") ? 1 : 2;
            counts[idx]++;
        }
        // 每个节点应在 333 ± 15% 范围内
        for (int c : counts) {
            assertTrue(c > 280 && c < 390,
                    "节点分配不均匀: " + Arrays.toString(counts));
        }
    }

    @Test
    @DisplayName("节点下线时受影响的 key 比例应 ≤ 50%")
    void shouldMinimizeMigrationOnNodeDown() {
        List<ClusterNode> threeNodes = Arrays.asList(
                new ClusterNode("n1", "h1"),
                new ClusterNode("n2", "h2"),
                new ClusterNode("n3", "h3")
        );
        sharder.updateNodes(threeNodes);
        // 记录 1000 个 key 的 owner
        String[] owners = new String[1000];
        for (int i = 0; i < 1000; i++) {
            owners[i] = sharder.shard("key-" + i).getNodeId();
        }
        // 下线 n3
        List<ClusterNode> twoNodes = Arrays.asList(
                new ClusterNode("n1", "h1"),
                new ClusterNode("n2", "h2")
        );
        sharder.updateNodes(twoNodes);
        int migrated = 0;
        for (int i = 0; i < 1000; i++) {
            String newOwner = sharder.shard("key-" + i).getNodeId();
            if (!owners[i].equals(newOwner)) {
                migrated++;
            }
        }
        // n3 原本持有约 333 个 key，这些 key 需迁移到 n1/n2
        // 迁移量应 ≤ 400（允许少量偏差）
        assertTrue(migrated <= 400,
                "节点下线后迁移量过大: " + migrated);
    }

    @Test
    @DisplayName("isMine 正确判断归属")
    void shouldCheckOwnership() {
        List<ClusterNode> nodes = Arrays.asList(
                new ClusterNode("n1", "h1"),
                new ClusterNode("n2", "h2")
        );
        sharder.updateNodes(nodes);
        String owner = sharder.shard("rule-X").getNodeId();
        boolean mine = sharder.isMine("rule-X", owner);
        assertTrue(mine);
        String other = owner.equals("n1") ? "n2" : "n1";
        assertFalse(sharder.isMine("rule-X", other));
    }

    @Test
    @DisplayName("环为空时 shard 返回 null")
    void shouldReturnNullOnEmptyRing() {
        sharder.updateNodes(null);
        assertEquals(null, sharder.shard("key"));
    }

    @Test
    @DisplayName("节点签名相同时不重建环")
    void shouldSkipRebuildOnSameSignature() {
        List<ClusterNode> nodes = Arrays.asList(new ClusterNode("n1", "h1"));
        sharder.updateNodes(nodes);
        String sig1 = sharder.getNodeSignature();
        sharder.updateNodes(nodes);
        String sig2 = sharder.getNodeSignature();
        assertEquals(sig1, sig2);
    }

    @Test
    @DisplayName("权重高的节点分到更多 key")
    void shouldRespectWeight() {
        List<ClusterNode> nodes = Arrays.asList(
                new ClusterNode("n1", "h1", 1),
                new ClusterNode("n2", "h2", 3)
        );
        sharder.updateNodes(nodes);
        int n1Count = 0, n2Count = 0;
        for (int i = 0; i < 1000; i++) {
            ClusterNode owner = sharder.shard("key-" + i);
            if ("n1".equals(owner.getNodeId())) n1Count++;
            else n2Count++;
        }
        // n2 权重是 n1 的 3 倍，应分到更多 key
        assertTrue(n2Count > n1Count * 2,
                "权重节点应分到更多 key: n1=" + n1Count + ", n2=" + n2Count);
    }

    @Test
    @DisplayName("静态 isMine 方法在无节点时返回 true")
    void shouldReturnTrueWhenNoNodes() {
        assertTrue(ConsistentHashSharder.isMine("key", "n1", null));
        assertTrue(ConsistentHashSharder.isMine("key", "n1", java.util.Collections.emptyList()));
    }

    @Test
    @DisplayName("getNodeCount 返回物理节点数")
    void shouldReturnPhysicalNodeCount() {
        sharder.updateNodes(Arrays.asList(
                new ClusterNode("n1", "h1"),
                new ClusterNode("n2", "h2")
        ));
        assertEquals(2, sharder.getNodeCount());
    }
}
