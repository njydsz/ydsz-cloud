package com.njydsz.pmis.cronjob.core.discovery;

import com.njydsz.pmis.cronjob.entity.JobNodeDO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * {@link NacosNodeDiscoveryStrategy} 单元测试。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>getOnlineNodes 将 ServiceInstance 列表转为 JobNodeDO 列表</li>
 *   <li>空实例列表返回空列表</li>
 *   <li>DiscoveryClient 异常时返回空列表</li>
 *   <li>getLocalNodeId 返回 hostname:port 格式</li>
 *   <li>节点列表按 nodeId 升序排列</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("NacosNodeDiscoveryStrategy Nacos 节点发现策略测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NacosNodeDiscoveryStrategyTest {

    @Mock
    private DiscoveryClient discoveryClient;

    private NacosNodeDiscoveryStrategy strategy;

    @BeforeEach
    void setUp() {
        // serverPort=9004 用于测试
        strategy = new NacosNodeDiscoveryStrategy(discoveryClient, 9004);
    }

    @Test
    @DisplayName("getOnlineNodes 将 ServiceInstance 列表转为 JobNodeDO 列表")
    void getOnlineNodes_instancesPresent_returnsJobNodeList() {
        ServiceInstance instance1 = mockInstance("10.0.0.1", 9004);
        ServiceInstance instance2 = mockInstance("10.0.0.2", 9004);
        when(discoveryClient.getInstances("ydsz-pmis-cronjob"))
                .thenReturn(List.of(instance1, instance2));

        List<JobNodeDO> nodes = strategy.getOnlineNodes();

        assertEquals(2, nodes.size());
        // 验证节点属性转换
        JobNodeDO node1 = nodes.get(0);
        assertEquals("10.0.0.1:9004", node1.getNodeId());
        assertEquals("10.0.0.1", node1.getHost());
        assertEquals(9004, node1.getPort());
        assertEquals("ONLINE", node1.getStatus());
        assertNotNull(node1.getLastHeartbeat(), "lastHeartbeat 应为当前时间");
        assertEquals("ydsz-pmis-cronjob", node1.getAppName());
    }

    @Test
    @DisplayName("getOnlineNodes 空实例列表返回空列表")
    void getOnlineNodes_emptyInstances_returnsEmptyList() {
        when(discoveryClient.getInstances("ydsz-pmis-cronjob"))
                .thenReturn(Collections.emptyList());

        List<JobNodeDO> nodes = strategy.getOnlineNodes();

        assertTrue(nodes.isEmpty());
    }

    @Test
    @DisplayName("getOnlineNodes null 实例列表返回空列表")
    void getOnlineNodes_nullInstances_returnsEmptyList() {
        when(discoveryClient.getInstances(anyString())).thenReturn(null);

        List<JobNodeDO> nodes = strategy.getOnlineNodes();

        assertTrue(nodes.isEmpty());
    }

    @Test
    @DisplayName("getOnlineNodes DiscoveryClient 异常时返回空列表")
    void getOnlineNodes_discoveryClientThrows_returnsEmptyList() {
        when(discoveryClient.getInstances(anyString()))
                .thenThrow(new RuntimeException("Nacos connection refused"));

        List<JobNodeDO> nodes = strategy.getOnlineNodes();

        assertTrue(nodes.isEmpty());
    }

    @Test
    @DisplayName("getOnlineNodes 节点列表按 nodeId 升序排列")
    void getOnlineNodes_multipleInstances_sortedByNodeId() {
        // 逆序添加，验证输出是否升序
        ServiceInstance instance2 = mockInstance("10.0.0.2", 9004);
        ServiceInstance instance1 = mockInstance("10.0.0.1", 9004);
        when(discoveryClient.getInstances("ydsz-pmis-cronjob"))
                .thenReturn(List.of(instance2, instance1));

        List<JobNodeDO> nodes = strategy.getOnlineNodes();

        assertEquals(2, nodes.size());
        assertEquals("10.0.0.1:9004", nodes.get(0).getNodeId());
        assertEquals("10.0.0.2:9004", nodes.get(1).getNodeId());
    }

    @Test
    @DisplayName("getLocalNodeId 返回 hostname:port 格式")
    void getLocalNodeId_returnsHostnamePortFormat() {
        String nodeId = strategy.getLocalNodeId();

        assertNotNull(nodeId);
        assertTrue(nodeId.endsWith(":9004"), "nodeId 应以 :9004 结尾, actual=" + nodeId);
    }

    /**
     * 创建 mock ServiceInstance。
     */
    private ServiceInstance mockInstance(String host, int port) {
        ServiceInstance instance = org.mockito.Mockito.mock(ServiceInstance.class);
        when(instance.getHost()).thenReturn(host);
        when(instance.getPort()).thenReturn(port);
        return instance;
    }
}
