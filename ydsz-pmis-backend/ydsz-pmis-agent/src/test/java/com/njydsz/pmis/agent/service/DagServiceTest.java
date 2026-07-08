package com.njydsz.pmis.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.njydsz.pmis.agent.entity.DagDefinitionDO;
import com.njydsz.pmis.agent.entity.DagInstanceDO;
import com.njydsz.pmis.agent.entity.DagNodeInstanceDO;
import com.njydsz.pmis.agent.mapper.DagDefinitionMapper;
import com.njydsz.pmis.agent.mapper.DagInstanceMapper;
import com.njydsz.pmis.agent.mapper.DagNodeInstanceMapper;
import com.njydsz.pmis.agent.orchestration.dag.DagDefinition;
import com.njydsz.pmis.agent.orchestration.dag.DagExecutionResult;
import com.njydsz.pmis.agent.orchestration.dag.DagExecutor;
import com.njydsz.pmis.agent.orchestration.dag.DagInstanceStatus;
import com.njydsz.pmis.agent.orchestration.dag.DagNode;
import com.njydsz.pmis.agent.orchestration.dag.DagNodeStatus;
import com.njydsz.pmis.common.api.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DagService 单元测试（P3-2 落地）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-2)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DagService DAG 编排服务")
class DagServiceTest {

    @Mock
    private ObjectProvider<DagDefinitionMapper> defMapperProvider;
    @Mock
    private ObjectProvider<DagInstanceMapper> instMapperProvider;
    @Mock
    private ObjectProvider<DagNodeInstanceMapper> nodeMapperProvider;
    @Mock
    private ObjectProvider<DagExecutor> executorProvider;
    @Mock
    private ObjectProvider<List<com.njydsz.pmis.agent.engine.Agent>> agentsProvider;
    @Mock
    private DagDefinitionMapper defMapper;
    @Mock
    private DagInstanceMapper instMapper;
    @Mock
    private DagNodeInstanceMapper nodeMapper;
    @Mock
    private DagExecutor executor;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private DagService service;

    @BeforeEach
    void setUp() {
        when(defMapperProvider.getIfAvailable()).thenReturn(defMapper);
        when(instMapperProvider.getIfAvailable()).thenReturn(instMapper);
        when(nodeMapperProvider.getIfAvailable()).thenReturn(nodeMapper);
        when(executorProvider.getIfAvailable()).thenReturn(executor);
        when(agentsProvider.getIfAvailable()).thenReturn(List.of());
        // 模拟 insert 自动填充 id
        doAnswer(invocation -> {
            DagDefinitionDO def = invocation.getArgument(0);
            if (def.getId() == null) {
                def.setId("dag-def-" + System.nanoTime());
            }
            return 1;
        }).when(defMapper).insert(any(DagDefinitionDO.class));
        doAnswer(invocation -> {
            DagInstanceDO inst = invocation.getArgument(0);
            if (inst.getId() == null) {
                inst.setId("dag-inst-" + System.nanoTime());
            }
            return 1;
        }).when(instMapper).insert(any(DagInstanceDO.class));
        doAnswer(invocation -> {
            DagNodeInstanceDO node = invocation.getArgument(0);
            if (node.getId() == null) {
                node.setId("dag-node-" + System.nanoTime());
            }
            return 1;
        }).when(nodeMapper).insert(any(DagNodeInstanceDO.class));
        service = new DagService(defMapperProvider, instMapperProvider, nodeMapperProvider,
                executorProvider, agentsProvider, objectMapper);
    }

    @Nested
    @DisplayName("createDefinition 创建 DAG 定义")
    class CreateDefinitionTest {

        @Test
        @DisplayName("正常创建并序列化 JSON")
        void shouldCreateAndSerialize() {
            DagNode a = DagNode.builder().name("a").agentType("RISK_WARNING").build();
            DagNode b = DagNode.builder().name("b").dependsOn(List.of("a")).build();
            DagDefinition dag = DagDefinition.builder()
                    .name("test-dag")
                    .description("测试")
                    .bizType("RISK_ASSESS")
                    .nodes(List.of(a, b))
                    .failureStrategy(com.njydsz.pmis.agent.orchestration.dag.DagFailureStrategy.ABORT)
                    .maxRetries(3)
                    .defaultTimeoutMs(5000L)
                    .build();

            DagDefinitionDO result = service.createDefinition(dag);

            assertThat(result.getId()).isNotNull();
            assertThat(result.getName()).isEqualTo("test-dag");
            assertThat(result.getBizType()).isEqualTo("RISK_ASSESS");
            assertThat(result.getFailureStrategy()).isEqualTo("ABORT");
            assertThat(result.getMaxRetries()).isEqualTo(3);
            assertThat(result.getDefaultTimeoutMs()).isEqualTo(5000L);
            assertThat(result.getEnabled()).isEqualTo(1);
            assertThat(result.getDefinitionJson()).contains("test-dag");
            assertThat(result.getDefinitionJson()).contains("RISK_WARNING");
            // 验证 dag.id 被回填
            assertThat(dag.getId()).isNotNull();
            verify(defMapper).insert(any(DagDefinitionDO.class));
        }

        @Test
        @DisplayName("Mapper 不可用时抛异常")
        void shouldThrowWhenMapperUnavailable() {
            when(defMapperProvider.getIfAvailable()).thenReturn(null);
            DagDefinition dag = DagDefinition.builder()
                    .name("test").nodes(List.of(DagNode.builder().name("a").build())).build();

            assertThatThrownBy(() -> service.createDefinition(dag))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("默认值填充：tenantId/version/enabled")
        void shouldFillDefaults() {
            DagDefinition dag = DagDefinition.builder()
                    .name("defaults")
                    .nodes(List.of(DagNode.builder().name("a").build())).build();

            DagDefinitionDO result = service.createDefinition(dag);

            assertThat(result.getTenantId()).isEqualTo("1");
            assertThat(result.getVersion()).isEqualTo("1.0.0");
            assertThat(result.getEnabled()).isEqualTo(1);
            assertThat(result.getFailureStrategy()).isEqualTo("ABORT");
            assertThat(result.getMaxRetries()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("getDefinition 查询定义")
    class GetDefinitionTest {

        @Test
        @DisplayName("正常查询")
        void shouldReturnDefinition() {
            DagDefinitionDO def = new DagDefinitionDO();
            def.setId("dag-1");
            def.setName("test");
            when(defMapper.selectById("dag-1")).thenReturn(def);

            DagDefinitionDO result = service.getDefinition("dag-1");

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo("dag-1");
        }

        @Test
        @DisplayName("不存在返回 null")
        void shouldReturnNullWhenNotFound() {
            when(defMapper.selectById("ghost")).thenReturn(null);

            DagDefinitionDO result = service.getDefinition("ghost");

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Mapper 不可用返回 null")
        void shouldReturnNullWhenMapperUnavailable() {
            when(defMapperProvider.getIfAvailable()).thenReturn(null);

            DagDefinitionDO result = service.getDefinition("any");

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("pageDefinitions 分页查询")
    class PageDefinitionsTest {

        @Test
        @DisplayName("Mapper 不可用返回空分页")
        void shouldReturnEmptyWhenMapperUnavailable() {
            when(defMapperProvider.getIfAvailable()).thenReturn(null);

            PageResult<DagDefinitionDO> result = service.pageDefinitions(1, 10, null);

            assertThat(result.getList()).isEmpty();
            assertThat(result.getTotal()).isZero();
        }
    }

    @Nested
    @DisplayName("execute 执行 DAG")
    class ExecuteTest {

        @Test
        @DisplayName("从 DB 读取定义并执行后持久化结果")
        void shouldExecuteAndPersist() throws Exception {
            // 1. 构造 DB 中的定义
            DagDefinition dag = DagDefinition.builder()
                    .name("exec-test")
                    .nodes(List.of(DagNode.builder().name("a").agentType("RISK_WARNING").build()))
                    .failureStrategy(com.njydsz.pmis.agent.orchestration.dag.DagFailureStrategy.ABORT)
                    .build();
            String json = objectMapper.writeValueAsString(dag);
            DagDefinitionDO defDO = new DagDefinitionDO();
            defDO.setId("dag-1");
            defDO.setTenantId("1");
            defDO.setName("exec-test");
            defDO.setBizType("RISK_ASSESS");
            defDO.setDefinitionJson(json);
            when(defMapper.selectById("dag-1")).thenReturn(defDO);

            // 2. mock 执行结果
            Map<String, DagNodeStatus> statuses = new HashMap<>();
            statuses.put("a", DagNodeStatus.SUCCESS);
            Map<String, Object> outputs = new HashMap<>();
            outputs.put("a", "result-a");
            DagExecutionResult execResult = DagExecutionResult.builder()
                    .instanceId("inst-1")
                    .status(DagInstanceStatus.SUCCESS)
                    .nodeStatuses(statuses)
                    .nodeOutputs(outputs)
                    .totalCostMs(100L)
                    .successCount(1)
                    .failedCount(0)
                    .skippedCount(0)
                    .totalNodes(1)
                    .build();
            when(executor.execute(any(), any(), any(), any())).thenReturn(execResult);

            // 3. 执行
            Map<String, Object> inputs = new HashMap<>();
            inputs.put("key", "value");
            DagExecutionResult result = service.execute("dag-1", inputs);

            // 4. 验证返回结果
            assertThat(result.getStatus()).isEqualTo(DagInstanceStatus.SUCCESS);
            assertThat(result.getInstanceId()).isEqualTo("inst-1");

            // 5. 验证持久化 DAG 实例
            ArgumentCaptor<DagInstanceDO> instCaptor = ArgumentCaptor.forClass(DagInstanceDO.class);
            verify(instMapper).insert(instCaptor.capture());
            DagInstanceDO inst = instCaptor.getValue();
            assertThat(inst.getDagDefinitionId()).isEqualTo("dag-1");
            assertThat(inst.getDagName()).isEqualTo("exec-test");
            assertThat(inst.getStatus()).isEqualTo("SUCCESS");
            assertThat(inst.getSuccessCount()).isEqualTo(1);
            assertThat(inst.getGlobalInputsJson()).contains("key");

            // 6. 验证持久化节点实例
            ArgumentCaptor<DagNodeInstanceDO> nodeCaptor = ArgumentCaptor.forClass(DagNodeInstanceDO.class);
            verify(nodeMapper).insert(nodeCaptor.capture());
            DagNodeInstanceDO nodeDO = nodeCaptor.getValue();
            assertThat(nodeDO.getNodeName()).isEqualTo("a");
            assertThat(nodeDO.getStatus()).isEqualTo("SUCCESS");
        }

        @Test
        @DisplayName("定义不存在时抛异常")
        void shouldThrowWhenDefinitionNotFound() {
            when(defMapper.selectById(anyString())).thenReturn(null);

            assertThatThrownBy(() -> service.execute("ghost", null))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("Executor 不可用时抛异常")
        void shouldThrowWhenExecutorUnavailable() {
            when(executorProvider.getIfAvailable()).thenReturn(null);
            DagDefinitionDO defDO = new DagDefinitionDO();
            defDO.setId("dag-1");
            defDO.setTenantId("1");
            defDO.setName("test");
            defDO.setDefinitionJson("{\"name\":\"test\",\"nodes\":[{\"name\":\"a\"}]}");
            when(defMapper.selectById("dag-1")).thenReturn(defDO);

            assertThatThrownBy(() -> service.execute("dag-1", null))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("executeDirect 直接执行")
    class ExecuteDirectTest {

        @Test
        @DisplayName("直接执行不持久化")
        void shouldExecuteDirectlyWithoutPersist() {
            DagDefinition dag = DagDefinition.builder()
                    .name("direct")
                    .nodes(List.of(DagNode.builder().name("a").build()))
                    .failureStrategy(com.njydsz.pmis.agent.orchestration.dag.DagFailureStrategy.ABORT)
                    .build();
            DagExecutionResult execResult = DagExecutionResult.builder()
                    .status(DagInstanceStatus.SUCCESS)
                    .nodeStatuses(Map.of("a", DagNodeStatus.SUCCESS))
                    .successCount(1).totalNodes(1)
                    .build();
            when(executor.execute(any(), any(), any(), any())).thenReturn(execResult);

            DagExecutionResult result = service.executeDirect(dag, new HashMap<>());

            assertThat(result.getStatus()).isEqualTo(DagInstanceStatus.SUCCESS);
            verify(instMapper, never()).insert(any(DagInstanceDO.class));
            verify(nodeMapper, never()).insert(any(DagNodeInstanceDO.class));
        }
    }

    @Nested
    @DisplayName("历史查询")
    class HistoryQueryTest {

        @Test
        @DisplayName("pageInstances 正常返回")
        void shouldPageInstances() {
            when(instMapperProvider.getIfAvailable()).thenReturn(instMapper);

            PageResult<DagInstanceDO> result = service.pageInstances("dag-1", 1, 10);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("getInstance 正常返回")
        void shouldGetInstance() {
            DagInstanceDO inst = new DagInstanceDO();
            inst.setId("inst-1");
            when(instMapper.selectById("inst-1")).thenReturn(inst);

            DagInstanceDO result = service.getInstance("inst-1");

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo("inst-1");
        }

        @Test
        @DisplayName("getInstance Mapper 不可用返回 null")
        void shouldReturnNullWhenMapperUnavailable() {
            when(instMapperProvider.getIfAvailable()).thenReturn(null);

            DagInstanceDO result = service.getInstance("any");

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("listNodeInstances 正常返回")
        void shouldListNodeInstances() {
            DagNodeInstanceDO node = new DagNodeInstanceDO();
            node.setNodeName("a");
            node.setStatus("SUCCESS");
            when(nodeMapper.selectList(any())).thenReturn(List.of(node));

            List<DagNodeInstanceDO> result = service.listNodeInstances("inst-1");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getNodeName()).isEqualTo("a");
        }

        @Test
        @DisplayName("listNodeInstances Mapper 不可用返回空列表")
        void shouldReturnEmptyWhenMapperUnavailable() {
            when(nodeMapperProvider.getIfAvailable()).thenReturn(null);

            List<DagNodeInstanceDO> result = service.listNodeInstances("inst-1");

            assertThat(result).isEmpty();
        }
    }
}
