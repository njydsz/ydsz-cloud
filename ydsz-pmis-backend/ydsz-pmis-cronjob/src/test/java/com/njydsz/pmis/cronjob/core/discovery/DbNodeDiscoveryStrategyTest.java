package com.njydsz.pmis.cronjob.core.discovery;

import com.njydsz.pmis.cronjob.config.CronjobProperties;
import com.njydsz.pmis.cronjob.entity.JobNodeDO;
import com.njydsz.pmis.cronjob.mapper.JobNodeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@link DbNodeDiscoveryStrategy} 单元测试。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>getOnlineNodes 查询 pmis_job_node 表返回在线节点</li>
 *   <li>getOnlineNodes 异常时返回空列表</li>
 *   <li>getLocalNodeId 返回 hostname:port 格式</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("DbNodeDiscoveryStrategy DB 心跳表节点发现策略测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DbNodeDiscoveryStrategyTest {

    @Mock
    private JobNodeMapper jobNodeMapper;

    private CronjobProperties cronjobProperties;
    private DbNodeDiscoveryStrategy strategy;

    @BeforeEach
    void setUp() {
        cronjobProperties = new CronjobProperties();
        // serverPort=9004 用于测试
        strategy = new DbNodeDiscoveryStrategy(jobNodeMapper, cronjobProperties, 9004);
    }

    @Test
    @DisplayName("getOnlineNodes 查询 pmis_job_node 表返回在线节点")
    void getOnlineNodes_nodesPresent_returnsNodeList() {
        JobNodeDO node1 = buildNode("host1:9004", "10.0.0.1", 9004);
        JobNodeDO node2 = buildNode("host2:9004", "10.0.0.2", 9004);
        when(jobNodeMapper.selectList(any())).thenReturn(List.of(node1, node2));

        List<JobNodeDO> nodes = strategy.getOnlineNodes();

        assertEquals(2, nodes.size());
        assertEquals("host1:9004", nodes.get(0).getNodeId());
        assertEquals("host2:9004", nodes.get(1).getNodeId());
    }

    @Test
    @DisplayName("getOnlineNodes 无在线节点时返回空列表")
    void getOnlineNodes_noNodes_returnsEmptyList() {
        when(jobNodeMapper.selectList(any())).thenReturn(List.of());

        List<JobNodeDO> nodes = strategy.getOnlineNodes();

        assertTrue(nodes.isEmpty());
    }

    @Test
    @DisplayName("getOnlineNodes mapper 异常时返回空列表")
    void getOnlineNodes_mapperThrows_returnsEmptyList() {
        when(jobNodeMapper.selectList(any())).thenThrow(new RuntimeException("DB connection failed"));

        List<JobNodeDO> nodes = strategy.getOnlineNodes();

        assertTrue(nodes.isEmpty());
    }

    @Test
    @DisplayName("getLocalNodeId 返回 hostname:port 格式")
    void getLocalNodeId_returnsHostnamePortFormat() {
        String nodeId = strategy.getLocalNodeId();

        assertNotNull(nodeId);
        assertTrue(nodeId.endsWith(":9004"), "nodeId 应以 :9004 结尾, actual=" + nodeId);
    }

    /**
     * 构建测试用节点。
     */
    private JobNodeDO buildNode(String nodeId, String host, int port) {
        JobNodeDO node = new JobNodeDO();
        node.setNodeId(nodeId);
        node.setHost(host);
        node.setPort(port);
        node.setStatus("ONLINE");
        return node;
    }
}
