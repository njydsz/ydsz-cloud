package com.njydsz.pmis.literule.distributed;

import com.alibaba.fastjson2.JSON;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * RedisNodeRegistry 单元测试
 *
 * <p>使用 Mockito mock RedissonClient，验证节点注册/注销/心跳/存活判断的核心逻辑。
 * 不依赖真实 Redis 实例。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@DisplayName("Redis 节点注册表测试")
class RedisNodeRegistryTest {

    private RedissonClient redissonClient;
    private RMap<String, String> mockMap;
    private RedisNodeRegistry registry;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redissonClient = mock(RedissonClient.class);
        mockMap = mock(RMap.class);
        when(redissonClient.<String, String>getMap(anyString())).thenReturn(mockMap);
        registry = new RedisNodeRegistry(redissonClient, "self-node", 30_000L);
    }

    @Test
    @DisplayName("注册节点 - 应写入 Redis Hash")
    void shouldRegisterNodeToRedis() {
        ClusterNode node = new ClusterNode("n1", "host1:8080");
        registry.register(node);

        verify(mockMap, times(1)).put(eq("n1"), anyString());
    }

    @Test
    @DisplayName("注册 Null 节点应安全忽略")
    void shouldIgnoreNullNode() {
        registry.register(null);
        registry.register(new ClusterNode(null, null));

        verify(mockMap, never()).put(anyString(), anyString());
    }

    @Test
    @DisplayName("注销节点 - 应从 Redis Hash 移除")
    void shouldUnregisterNodeFromRedis() {
        registry.unregister("n1");

        verify(mockMap, times(1)).remove("n1");
    }

    @Test
    @DisplayName("心跳 - 应更新节点的 lastHeartbeatAt")
    void shouldUpdateHeartbeat() {
        ClusterNode node = new ClusterNode("n1", "host1:8080");
        when(mockMap.get("n1")).thenReturn(JSON.toJSONString(node));

        registry.heartbeat("n1");

        verify(mockMap, times(1)).put(eq("n1"), anyString());
    }

    @Test
    @DisplayName("心跳 - 节点不存在时应安全忽略")
    void shouldIgnoreHeartbeatForMissingNode() {
        when(mockMap.get("missing")).thenReturn(null);

        registry.heartbeat("missing");

        verify(mockMap, never()).put(anyString(), anyString());
    }

    @Test
    @DisplayName("获取存活节点 - 应过滤超时节点")
    void shouldFilterDeadNodes() {
        // 存活节点
        ClusterNode alive = new ClusterNode("alive", "h1");
        // 超时节点（心跳时间为 60 秒前）
        ClusterNode dead = new ClusterNode("dead", "h2");
        dead.setLastHeartbeatAt(System.currentTimeMillis() - 60_000L);

        Map<String, String> data = new HashMap<>();
        data.put("alive", JSON.toJSONString(alive));
        data.put("dead", JSON.toJSONString(dead));
        when(mockMap.entrySet()).thenReturn(data.entrySet());

        List<ClusterNode> result = registry.getAliveNodes();

        assertEquals(1, result.size());
        assertEquals("alive", result.get(0).getNodeId());
    }

    @Test
    @DisplayName("获取存活节点 - 应按 nodeId 排序")
    void shouldSortByNodeId() {
        ClusterNode n3 = new ClusterNode("n3", "h3");
        ClusterNode n1 = new ClusterNode("n1", "h1");
        ClusterNode n2 = new ClusterNode("n2", "h2");

        Map<String, String> data = new HashMap<>();
        data.put("n3", JSON.toJSONString(n3));
        data.put("n1", JSON.toJSONString(n1));
        data.put("n2", JSON.toJSONString(n2));
        when(mockMap.entrySet()).thenReturn(data.entrySet());

        List<ClusterNode> result = registry.getAliveNodes();

        assertEquals(3, result.size());
        assertEquals("n1", result.get(0).getNodeId());
        assertEquals("n2", result.get(1).getNodeId());
        assertEquals("n3", result.get(2).getNodeId());
    }

    @Test
    @DisplayName("获取存活节点 - Redis 异常时返回空列表")
    void shouldReturnEmptyWhenRedisFails() {
        when(mockMap.entrySet()).thenThrow(new RuntimeException("Redis connection failed"));

        List<ClusterNode> result = registry.getAliveNodes();

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getSelfNodeId 返回当前节点 ID")
    void shouldReturnSelfNodeId() {
        assertEquals("self-node", registry.getSelfNodeId());
    }
}
