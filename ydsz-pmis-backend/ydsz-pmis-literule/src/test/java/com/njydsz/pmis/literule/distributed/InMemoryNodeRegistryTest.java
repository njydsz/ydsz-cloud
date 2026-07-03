package com.njydsz.pmis.literule.distributed;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * InMemoryNodeRegistry 测试
 */
@DisplayName("内存节点注册表测试")
class InMemoryNodeRegistryTest {

    private InMemoryNodeRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new InMemoryNodeRegistry("self", 30_000L);
    }

    @Test
    @DisplayName("注册节点后应出现在存活列表中")
    void shouldRegisterNode() {
        registry.register(new ClusterNode("n1", "h1"));
        List<ClusterNode> alive = registry.getAliveNodes();
        assertEquals(1, alive.size());
        assertEquals("n1", alive.get(0).getNodeId());
    }

    @Test
    @DisplayName("注销节点后不应出现在存活列表中")
    void shouldUnregisterNode() {
        registry.register(new ClusterNode("n1", "h1"));
        registry.register(new ClusterNode("n2", "h2"));
        registry.unregister("n1");
        List<ClusterNode> alive = registry.getAliveNodes();
        assertEquals(1, alive.size());
        assertEquals("n2", alive.get(0).getNodeId());
    }

    @Test
    @DisplayName("心跳超时的节点不应出现在存活列表中")
    void shouldEvictDeadNodes() throws InterruptedException {
        // 使用极短的超时时间
        InMemoryNodeRegistry shortRegistry = new InMemoryNodeRegistry("self", 100L);
        shortRegistry.register(new ClusterNode("n1", "h1"));
        Thread.sleep(150);
        List<ClusterNode> alive = shortRegistry.getAliveNodes();
        assertEquals(0, alive.size());
    }

    @Test
    @DisplayName("heartbeat 应更新 lastHeartbeatAt")
    void shouldUpdateHeartbeat() throws InterruptedException {
        InMemoryNodeRegistry shortRegistry = new InMemoryNodeRegistry("self", 200L);
        shortRegistry.register(new ClusterNode("n1", "h1"));
        Thread.sleep(100);
        shortRegistry.heartbeat("n1");
        Thread.sleep(100);
        // 心跳在 100ms 前更新，超时 200ms，应该还活着
        List<ClusterNode> alive = shortRegistry.getAliveNodes();
        assertEquals(1, alive.size());
    }

    @Test
    @DisplayName("evictDeadNodes 应清理并返回清理数量")
    void shouldEvictAndReturnCount() throws InterruptedException {
        InMemoryNodeRegistry shortRegistry = new InMemoryNodeRegistry("self", 100L);
        shortRegistry.register(new ClusterNode("n1", "h1"));
        shortRegistry.register(new ClusterNode("n2", "h2"));
        Thread.sleep(150);
        int evicted = shortRegistry.evictDeadNodes();
        assertEquals(2, evicted);
    }

    @Test
    @DisplayName("getSelfNodeId 返回当前节点 ID")
    void shouldReturnSelfNodeId() {
        assertEquals("self", registry.getSelfNodeId());
    }

    @Test
    @DisplayName("register null 节点应安全忽略")
    void shouldIgnoreNullNode() {
        registry.register(null);
        registry.register(new ClusterNode(null, null));
        assertEquals(0, registry.getAliveNodes().size());
    }
}
