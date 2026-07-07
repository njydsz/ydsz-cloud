package com.njydsz.pmis.cronjob.service.impl;

import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.cronjob.core.dag.DagDefinition;
import com.njydsz.pmis.cronjob.core.dag.DagDefinitionCodec;
import com.njydsz.pmis.cronjob.entity.JobDagDO;
import com.njydsz.pmis.cronjob.entity.JobDagInstanceDO;
import com.njydsz.pmis.cronjob.entity.JobDagNodeInstanceDO;
import com.njydsz.pmis.cronjob.mapper.JobDagInstanceMapper;
import com.njydsz.pmis.cronjob.mapper.JobDagMapper;
import com.njydsz.pmis.cronjob.mapper.JobDagNodeInstanceMapper;
import com.njydsz.pmis.cronjob.vo.DagInstanceVisualizationVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

/**
 * {@link JobDagInstanceServiceImpl} DAG 实例服务单元测试（P4-1 DAG 可视化）。
 *
 * <p>覆盖 {@link JobDagInstanceServiceImpl#getVisualization(String)} 方法，
 * 重点验证实例不存在、DAG 定义不存在、定义 JSON 非法及正常返回可视化数据的场景。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("JobDagInstanceServiceImpl DAG 实例服务测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JobDagInstanceServiceImplTest {

    /** 合法的双节点 DAG 定义 JSON（含边关系与前端坐标） */
    private static final String VALID_DAG_DEFINITION =
            "{\"nodes\":[{\"jobKey\":\"job-A\",\"jobId\":\"1\",\"label\":\"A\",\"x\":0,\"y\":0},"
                    + "{\"jobKey\":\"job-B\",\"jobId\":\"2\",\"label\":\"B\",\"x\":100,\"y\":0}],"
                    + "\"edges\":[{\"from\":\"job-A\",\"to\":\"job-B\"}]}";
    /** 非法的 DAG 定义 JSON（结构错误，codec.fromJson 会抛 BizException） */
    private static final String INVALID_DAG_DEFINITION = "{invalid-json";

    @Mock
    private JobDagInstanceMapper jobDagInstanceMapper;
    @Mock
    private JobDagNodeInstanceMapper jobDagNodeInstanceMapper;
    @Mock
    private JobDagMapper jobDagMapper;
    @Spy
    private final DagDefinitionCodec dagDefinitionCodec = new DagDefinitionCodec();

    @InjectMocks
    private JobDagInstanceServiceImpl jobDagInstanceService;

    // ==================== getVisualization ====================

    @Test
    @DisplayName("getVisualization: 实例不存在时抛 BizException")
    void getVisualization_instanceNotFound_throwsException() {
        when(jobDagInstanceMapper.selectById("nonexistent")).thenReturn(null);

        assertThrows(BizException.class,
                () -> jobDagInstanceService.getVisualization("nonexistent"));
    }

    @Test
    @DisplayName("getVisualization: 正常返回可视化数据（instance、definition、nodeInstances 均不为空）")
    void getVisualization_normal_success() {
        JobDagInstanceDO instance = buildInstance("instance-1", "dag-id-1");
        JobDagDO dag = buildDag("dag-id-1", VALID_DAG_DEFINITION);
        JobDagNodeInstanceDO nodeA = buildNodeInstance("node-1", "instance-1", "job-A");
        JobDagNodeInstanceDO nodeB = buildNodeInstance("node-2", "instance-1", "job-B");
        List<JobDagNodeInstanceDO> nodeInstances = List.of(nodeA, nodeB);

        when(jobDagInstanceMapper.selectById("instance-1")).thenReturn(instance);
        when(jobDagMapper.selectById("dag-id-1")).thenReturn(dag);
        when(jobDagNodeInstanceMapper.selectByDagInstanceId("instance-1")).thenReturn(nodeInstances);

        DagInstanceVisualizationVO vo = jobDagInstanceService.getVisualization("instance-1");

        assertNotNull(vo, "可视化数据 VO 不应为空");
        assertSame(instance, vo.getInstance(), "instance 应为查询到的实例");
        assertNotNull(vo.getDefinition(), "definition 不应为空");
        // 校验 DAG 定义解析结果：2 个节点 + 1 条边
        DagDefinition definition = vo.getDefinition();
        assertEquals(2, definition.nodes().size(), "节点数应为 2");
        assertEquals(1, definition.edges().size(), "边数应为 1");
        assertEquals("job-A", definition.nodes().get(0).jobKey(), "第一个节点 jobKey 应为 job-A");
        assertEquals("job-B", definition.nodes().get(1).jobKey(), "第二个节点 jobKey 应为 job-B");
        assertEquals("job-A", definition.edges().get(0).from(), "边的 from 应为 job-A");
        assertEquals("job-B", definition.edges().get(0).to(), "边的 to 应为 job-B");
        // 校验节点实例列表
        assertNotNull(vo.getNodeInstances(), "nodeInstances 不应为空");
        assertEquals(2, vo.getNodeInstances().size(), "节点实例数应为 2");
        assertSame(nodeInstances, vo.getNodeInstances(), "nodeInstances 应为查询到的列表");
    }

    @Test
    @DisplayName("getVisualization: DAG 定义不存在时抛 BizException")
    void getVisualization_dagNotFound_throwsException() {
        JobDagInstanceDO instance = buildInstance("instance-1", "dag-id-1");
        when(jobDagInstanceMapper.selectById("instance-1")).thenReturn(instance);
        when(jobDagMapper.selectById("dag-id-1")).thenReturn(null);

        assertThrows(BizException.class,
                () -> jobDagInstanceService.getVisualization("instance-1"));
    }

    @Test
    @DisplayName("getVisualization: DAG 定义 JSON 非法时抛 BizException")
    void getVisualization_definitionInvalid_throwsException() {
        JobDagInstanceDO instance = buildInstance("instance-1", "dag-id-1");
        JobDagDO dag = buildDag("dag-id-1", INVALID_DAG_DEFINITION);
        when(jobDagInstanceMapper.selectById("instance-1")).thenReturn(instance);
        when(jobDagMapper.selectById("dag-id-1")).thenReturn(dag);

        assertThrows(BizException.class,
                () -> jobDagInstanceService.getVisualization("instance-1"));
    }

    // ==================== 辅助方法 ====================

    /**
     * 构造 JobDagInstanceDO。
     */
    private JobDagInstanceDO buildInstance(String id, String dagId) {
        JobDagInstanceDO instance = new JobDagInstanceDO();
        instance.setId(id);
        instance.setDagId(dagId);
        instance.setDagKey("dag-key-1");
        instance.setStatus("RUNNING");
        instance.setTriggerType("MANUAL");
        instance.setTotalNodes(2);
        return instance;
    }

    /**
     * 构造 JobDagDO（仅设置可视化所需字段）。
     */
    private JobDagDO buildDag(String id, String dagDefinition) {
        JobDagDO dag = new JobDagDO();
        dag.setId(id);
        dag.setDagKey("dag-key-1");
        dag.setDagName("测试DAG");
        dag.setDagDefinition(dagDefinition);
        dag.setStatus("ENABLED");
        dag.setTriggerType("MANUAL");
        return dag;
    }

    /**
     * 构造 JobDagNodeInstanceDO。
     */
    private JobDagNodeInstanceDO buildNodeInstance(String id, String dagInstanceId, String jobKey) {
        JobDagNodeInstanceDO node = new JobDagNodeInstanceDO();
        node.setId(id);
        node.setDagInstanceId(dagInstanceId);
        node.setDagId("dag-id-1");
        node.setJobKey(jobKey);
        node.setNodeStatus("PENDING");
        return node;
    }
}
