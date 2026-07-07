package com.njydsz.pmis.workflow.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.engine.BpmnXmlParser;
import com.njydsz.pmis.workflow.engine.FlowDefinitionCacheService;
import com.njydsz.pmis.workflow.engine.FlowGraphValidator;
import com.njydsz.pmis.workflow.entity.FlowDefinitionDO;
import com.njydsz.pmis.workflow.entity.FlowNodeDO;
import com.njydsz.pmis.workflow.mapper.FlowDefinitionMapper;
import com.njydsz.pmis.workflow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.mapper.FlowNodeMapper;
import com.njydsz.pmis.workflow.mapper.FlowSkipMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link FlowDefinitionServiceImpl} P2-5 变更影响分析报告 单元测试。
 *
 * <p>覆盖 analyzeMigrationImpact 方法的全部场景：
 * <ul>
 *   <li>参数校验：空参数 / 定义不存在 / flowCode 不一致</li>
 *   <li>风险等级评估：NONE / LOW / MEDIUM（节点变更）/ MEDIUM（数量大）/ HIGH</li>
 *   <li>迁移建议生成：每种风险等级的建议内容</li>
 *   <li>受影响实例识别：卡死节点（删除）和受影响节点（修改）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("P2-5: 变更影响分析报告 - FlowDefinitionServiceImpl")
class FlowDefinitionMigrationImpactServiceImplTest {

    @Mock
    private FlowDefinitionMapper definitionMapper;
    @Mock
    private FlowNodeMapper nodeMapper;
    @Mock
    private FlowSkipMapper skipMapper;
    @Mock
    private BpmnXmlParser bpmnXmlParser;
    @Mock
    private FlowGraphValidator graphValidator;
    @Mock
    private FlowDefinitionCacheService flowDefinitionCacheService;
    @Mock
    private FlowInstanceMapper instanceMapper;

    @InjectMocks
    private FlowDefinitionServiceImpl service;

    @BeforeEach
    void setUp() {
        // P2-4 @Value 字段（P2-5 不使用，但 @InjectMocks 不会注入 @Value）
        ReflectionTestUtils.setField(service, "lockTimeoutMinutes", 30L);
    }

    private FlowDefinitionDO buildDefinition(String id, String flowCode, String version) {
        FlowDefinitionDO def = new FlowDefinitionDO();
        def.setId(id);
        def.setFlowCode(flowCode);
        def.setFlowName("测试流程-" + version);
        def.setFlowVersion(version);
        def.setDeleted(0);
        return def;
    }

    private FlowNodeDO buildNode(String definitionId, String nodeCode, String nodeName,
                                  Integer nodeType, String permissionFlag) {
        FlowNodeDO node = new FlowNodeDO();
        node.setDefinitionId(definitionId);
        node.setNodeCode(nodeCode);
        node.setNodeName(nodeName);
        node.setNodeType(nodeType);
        node.setPermissionFlag(permissionFlag);
        return node;
    }

    /**
     * 准备版本差异 mock 数据：
     * - 老版本有节点：start, approve_v1, end
     * - 新版本有节点：start, approve_v2, end（approve_v1 被删除，新增 approve_v2）
     * - 假设 approve_v1 的 nodeCode 在老版本叫 "approve"，新版本也保留 "approve" 但属性变了
     */
    private void mockDiffVersions(String oldDefId, String newDefId,
                                   List<String> removedNodeCodes,
                                   List<String> modifiedNodeCodes) {
        FlowDefinitionDO oldDef = definitionMapper.selectById(oldDefId);
        FlowDefinitionDO newDef = definitionMapper.selectById(newDefId);

        // diffVersions 内部会按 flowCode 查所有版本
        when(definitionMapper.selectByFlowCode(eq(oldDef.getFlowCode()), any()))
                .thenReturn(Arrays.asList(oldDef, newDef));

        // 老版本节点
        List<FlowNodeDO> oldNodes = new ArrayList<>();
        oldNodes.add(buildNode(oldDefId, "start", "开始", 0, "user1"));
        oldNodes.add(buildNode(oldDefId, "approve", "审批", 1, "user2"));
        oldNodes.add(buildNode(oldDefId, "end", "结束", 2, "user1"));
        for (String code : removedNodeCodes) {
            oldNodes.add(buildNode(oldDefId, code, "节点-" + code, 1, "user3"));
        }
        when(nodeMapper.selectByDefinitionId(oldDefId)).thenReturn(oldNodes);

        // 新版本节点：保留 start/approve/end，但 approve 属性变化（如果有 modifiedNodeCodes）
        List<FlowNodeDO> newNodes = new ArrayList<>();
        newNodes.add(buildNode(newDefId, "start", "开始", 0, "user1"));
        if (modifiedNodeCodes.contains("approve")) {
            newNodes.add(buildNode(newDefId, "approve", "审批（已修改）", 1, "user4"));
        } else {
            newNodes.add(buildNode(newDefId, "approve", "审批", 1, "user2"));
        }
        newNodes.add(buildNode(newDefId, "end", "结束", 2, "user1"));
        when(nodeMapper.selectByDefinitionId(newDefId)).thenReturn(newNodes);

        // 跳转表为空（简化测试，P2-5 不依赖跳转差异）
        when(skipMapper.selectByDefinitionId(oldDefId)).thenReturn(Collections.emptyList());
        when(skipMapper.selectByDefinitionId(newDefId)).thenReturn(Collections.emptyList());
    }

    // ============================== 参数校验测试 ==============================

    @Nested
    @DisplayName("参数校验测试")
    class ValidationTest {

        @Test
        @DisplayName("oldDefinitionId 为空 → BAD_REQUEST")
        void shouldThrowWhenOldDefIdEmpty() {
            BizException ex = assertThrows(BizException.class,
                    () -> service.analyzeMigrationImpact("", "new1"));
            assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
            assertEquals("error.workflow.msg_c2d3e4f5", ex.getErrorMessage());
            verifyNoInteractions(definitionMapper, instanceMapper);
        }

        @Test
        @DisplayName("newDefinitionId 为空 → BAD_REQUEST")
        void shouldThrowWhenNewDefIdEmpty() {
            BizException ex = assertThrows(BizException.class,
                    () -> service.analyzeMigrationImpact("old1", ""));
            assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
            assertEquals("error.workflow.msg_c2d3e4f5", ex.getErrorMessage());
            verifyNoInteractions(definitionMapper, instanceMapper);
        }

        @Test
        @DisplayName("老版本定义不存在 → NOT_FOUND")
        void shouldThrowWhenOldDefNotFound() {
            when(definitionMapper.selectById("old1")).thenReturn(null);
            BizException ex = assertThrows(BizException.class,
                    () -> service.analyzeMigrationImpact("old1", "new1"));
            assertEquals(BizErrorCode.NOT_FOUND.getCode(), ex.getCode());
            assertEquals("error.workflow.msg_e7f8a9b0", ex.getErrorMessage());
        }

        @Test
        @DisplayName("新版本定义不存在 → NOT_FOUND")
        void shouldThrowWhenNewDefNotFound() {
            FlowDefinitionDO oldDef = buildDefinition("old1", "test_flow", "1");
            when(definitionMapper.selectById("old1")).thenReturn(oldDef);
            when(definitionMapper.selectById("new1")).thenReturn(null);
            BizException ex = assertThrows(BizException.class,
                    () -> service.analyzeMigrationImpact("old1", "new1"));
            assertEquals(BizErrorCode.NOT_FOUND.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("老版本和新版本 flowCode 不一致 → BAD_REQUEST")
        void shouldThrowWhenFlowCodeMismatch() {
            FlowDefinitionDO oldDef = buildDefinition("old1", "flow_a", "1");
            FlowDefinitionDO newDef = buildDefinition("new1", "flow_b", "2");
            when(definitionMapper.selectById("old1")).thenReturn(oldDef);
            when(definitionMapper.selectById("new1")).thenReturn(newDef);
            BizException ex = assertThrows(BizException.class,
                    () -> service.analyzeMigrationImpact("old1", "new1"));
            assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
            assertEquals("error.workflow.msg_d3e4f5a6", ex.getErrorMessage());
        }
    }

    // ============================== 风险等级评估测试 ==============================

    @Nested
    @DisplayName("风险等级评估测试")
    class RiskLevelTest {

        @Test
        @DisplayName("无在途实例 → riskLevel=NONE")
        void shouldReturnNoneWhenNoRunning() {
            FlowDefinitionDO oldDef = buildDefinition("old1", "test_flow", "1");
            FlowDefinitionDO newDef = buildDefinition("new1", "test_flow", "2");
            when(definitionMapper.selectById("old1")).thenReturn(oldDef);
            when(definitionMapper.selectById("new1")).thenReturn(newDef);
            mockDiffVersions("old1", "new1", Collections.emptyList(), Collections.emptyList());
            when(instanceMapper.countRunningByDefinition("old1")).thenReturn(0L);
            when(instanceMapper.selectRunningGroupByNode("old1"))
                    .thenReturn(Collections.emptyList());

            Map<String, Object> result = service.analyzeMigrationImpact("old1", "new1");

            assertEquals("NONE", result.get("riskLevel"));
            @SuppressWarnings("unchecked")
            List<String> recs = (List<String>) result.get("recommendations");
            assertFalse(recs.isEmpty());
            assertTrue(recs.get(0).contains("无在途实例"));
        }

        @Test
        @DisplayName("有在途实例但节点未变更 → riskLevel=LOW")
        void shouldReturnLowWhenRunningButNoChange() {
            FlowDefinitionDO oldDef = buildDefinition("old1", "test_flow", "1");
            FlowDefinitionDO newDef = buildDefinition("new1", "test_flow", "2");
            when(definitionMapper.selectById("old1")).thenReturn(oldDef);
            when(definitionMapper.selectById("new1")).thenReturn(newDef);
            mockDiffVersions("old1", "new1", Collections.emptyList(), Collections.emptyList());
            when(instanceMapper.countRunningByDefinition("old1")).thenReturn(5L);
            // 在途实例在 "approve" 节点（该节点未变更）
            Map<String, Object> nodeEntry = new LinkedHashMap<>();
            nodeEntry.put("currentNodeCode", "approve");
            nodeEntry.put("currentNodeName", "审批");
            nodeEntry.put("cnt", 5L);
            when(instanceMapper.selectRunningGroupByNode("old1"))
                    .thenReturn(List.of(nodeEntry));

            Map<String, Object> result = service.analyzeMigrationImpact("old1", "new1");

            assertEquals("LOW", result.get("riskLevel"));
            @SuppressWarnings("unchecked")
            Map<String, Object> impacted = (Map<String, Object>) result.get("impactedInstances");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> stuck = (List<Map<String, Object>>)
                    impacted.get("stuckInstances");
            assertTrue(stuck.isEmpty());
        }

        @Test
        @DisplayName("在途实例在节点修改处 → riskLevel=MEDIUM")
        void shouldReturnMediumWhenNodeModified() {
            FlowDefinitionDO oldDef = buildDefinition("old1", "test_flow", "1");
            FlowDefinitionDO newDef = buildDefinition("new1", "test_flow", "2");
            when(definitionMapper.selectById("old1")).thenReturn(oldDef);
            when(definitionMapper.selectById("new1")).thenReturn(newDef);
            mockDiffVersions("old1", "new1",
                    Collections.emptyList(), List.of("approve"));  // approve 节点修改
            when(instanceMapper.countRunningByDefinition("old1")).thenReturn(3L);
            Map<String, Object> nodeEntry = new LinkedHashMap<>();
            nodeEntry.put("currentNodeCode", "approve");
            nodeEntry.put("currentNodeName", "审批");
            nodeEntry.put("cnt", 3L);
            when(instanceMapper.selectRunningGroupByNode("old1"))
                    .thenReturn(List.of(nodeEntry));

            Map<String, Object> result = service.analyzeMigrationImpact("old1", "new1");

            assertEquals("MEDIUM", result.get("riskLevel"));
            @SuppressWarnings("unchecked")
            Map<String, Object> impacted = (Map<String, Object>) result.get("impactedInstances");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> affected = (List<Map<String, Object>>)
                    impacted.get("affectedInstances");
            assertEquals(1, affected.size());
            assertEquals("approve", affected.get(0).get("nodeCode"));
            assertEquals("NODE_MODIFIED", affected.get(0).get("reason"));
        }

        @Test
        @DisplayName("在途实例数 > 100 但节点未变更 → riskLevel=MEDIUM")
        void shouldReturnMediumWhenManyRunning() {
            FlowDefinitionDO oldDef = buildDefinition("old1", "test_flow", "1");
            FlowDefinitionDO newDef = buildDefinition("new1", "test_flow", "2");
            when(definitionMapper.selectById("old1")).thenReturn(oldDef);
            when(definitionMapper.selectById("new1")).thenReturn(newDef);
            mockDiffVersions("old1", "new1", Collections.emptyList(), Collections.emptyList());
            when(instanceMapper.countRunningByDefinition("old1")).thenReturn(150L);
            Map<String, Object> nodeEntry = new LinkedHashMap<>();
            nodeEntry.put("currentNodeCode", "approve");
            nodeEntry.put("currentNodeName", "审批");
            nodeEntry.put("cnt", 150L);
            when(instanceMapper.selectRunningGroupByNode("old1"))
                    .thenReturn(List.of(nodeEntry));

            Map<String, Object> result = service.analyzeMigrationImpact("old1", "new1");

            assertEquals("MEDIUM", result.get("riskLevel"));
            @SuppressWarnings("unchecked")
            List<String> recs = (List<String>) result.get("recommendations");
            assertTrue(recs.stream().anyMatch(r -> r.contains("在途实例数量较多")));
        }

        @Test
        @DisplayName("在途实例在已删除节点 → riskLevel=HIGH")
        void shouldReturnHighWhenStuck() {
            FlowDefinitionDO oldDef = buildDefinition("old1", "test_flow", "1");
            FlowDefinitionDO newDef = buildDefinition("new1", "test_flow", "2");
            when(definitionMapper.selectById("old1")).thenReturn(oldDef);
            when(definitionMapper.selectById("new1")).thenReturn(newDef);
            // 老版本有节点 "extra_review"，新版本删除
            mockDiffVersions("old1", "new1",
                    List.of("extra_review"), Collections.emptyList());
            when(instanceMapper.countRunningByDefinition("old1")).thenReturn(8L);
            // 在途实例在 "extra_review" 节点（已被删除）
            Map<String, Object> nodeEntry = new LinkedHashMap<>();
            nodeEntry.put("currentNodeCode", "extra_review");
            nodeEntry.put("currentNodeName", "额外复审");
            nodeEntry.put("cnt", 8L);
            when(instanceMapper.selectRunningGroupByNode("old1"))
                    .thenReturn(List.of(nodeEntry));

            Map<String, Object> result = service.analyzeMigrationImpact("old1", "new1");

            assertEquals("HIGH", result.get("riskLevel"));
            @SuppressWarnings("unchecked")
            Map<String, Object> impacted = (Map<String, Object>) result.get("impactedInstances");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> stuck = (List<Map<String, Object>>)
                    impacted.get("stuckInstances");
            assertEquals(1, stuck.size());
            assertEquals("extra_review", stuck.get(0).get("nodeCode"));
            assertEquals("NODE_REMOVED", stuck.get(0).get("reason"));
            assertEquals(8L, stuck.get(0).get("instanceCount"));
            @SuppressWarnings("unchecked")
            List<String> recs = (List<String>) result.get("recommendations");
            assertTrue(recs.stream().anyMatch(r -> r.contains("【高危】")));
            assertTrue(recs.stream().anyMatch(r -> r.contains("禁止")));
        }
    }

    // ============================== 迁移建议测试 ==============================

    @Nested
    @DisplayName("迁移建议测试")
    class RecommendationsTest {

        @Test
        @DisplayName("NONE 等级建议包含'可直接发布'和'停用老版本'")
        void shouldContainNoneRecommendations() {
            FlowDefinitionDO oldDef = buildDefinition("old1", "test_flow", "1");
            FlowDefinitionDO newDef = buildDefinition("new1", "test_flow", "2");
            when(definitionMapper.selectById("old1")).thenReturn(oldDef);
            when(definitionMapper.selectById("new1")).thenReturn(newDef);
            mockDiffVersions("old1", "new1", Collections.emptyList(), Collections.emptyList());
            when(instanceMapper.countRunningByDefinition("old1")).thenReturn(0L);
            when(instanceMapper.selectRunningGroupByNode("old1"))
                    .thenReturn(Collections.emptyList());

            Map<String, Object> result = service.analyzeMigrationImpact("old1", "new1");

            @SuppressWarnings("unchecked")
            List<String> recs = (List<String>) result.get("recommendations");
            assertTrue(recs.stream().anyMatch(r -> r.contains("可直接发布")));
            assertTrue(recs.stream().anyMatch(r -> r.contains("停用老版本")));
        }

        @Test
        @DisplayName("LOW 等级建议包含'等待在途实例自然完成'")
        void shouldContainLowRecommendations() {
            FlowDefinitionDO oldDef = buildDefinition("old1", "test_flow", "1");
            FlowDefinitionDO newDef = buildDefinition("new1", "test_flow", "2");
            when(definitionMapper.selectById("old1")).thenReturn(oldDef);
            when(definitionMapper.selectById("new1")).thenReturn(newDef);
            mockDiffVersions("old1", "new1", Collections.emptyList(), Collections.emptyList());
            when(instanceMapper.countRunningByDefinition("old1")).thenReturn(2L);
            when(instanceMapper.selectRunningGroupByNode("old1"))
                    .thenReturn(Collections.emptyList());

            Map<String, Object> result = service.analyzeMigrationImpact("old1", "new1");

            @SuppressWarnings("unchecked")
            List<String> recs = (List<String>) result.get("recommendations");
            assertTrue(recs.stream().anyMatch(r -> r.contains("自然完成")));
        }
    }

    // ============================== 报告结构测试 ==============================

    @Nested
    @DisplayName("报告结构测试")
    class ReportStructureTest {

        @Test
        @DisplayName("报告包含所有必需字段")
        void shouldContainAllRequiredFields() {
            FlowDefinitionDO oldDef = buildDefinition("old1", "test_flow", "1");
            FlowDefinitionDO newDef = buildDefinition("new1", "test_flow", "2");
            when(definitionMapper.selectById("old1")).thenReturn(oldDef);
            when(definitionMapper.selectById("new1")).thenReturn(newDef);
            mockDiffVersions("old1", "new1", Collections.emptyList(), Collections.emptyList());
            when(instanceMapper.countRunningByDefinition("old1")).thenReturn(0L);
            when(instanceMapper.selectRunningGroupByNode("old1"))
                    .thenReturn(Collections.emptyList());

            Map<String, Object> result = service.analyzeMigrationImpact("old1", "new1");

            // 顶层字段
            assertTrue(result.containsKey("oldDefinition"));
            assertTrue(result.containsKey("newDefinition"));
            assertTrue(result.containsKey("diff"));
            assertTrue(result.containsKey("runningInstances"));
            assertTrue(result.containsKey("impactedInstances"));
            assertTrue(result.containsKey("riskLevel"));
            assertTrue(result.containsKey("recommendations"));

            // oldDefinition / newDefinition 结构
            @SuppressWarnings("unchecked")
            Map<String, Object> oldInfo = (Map<String, Object>) result.get("oldDefinition");
            assertEquals("old1", oldInfo.get("id"));
            assertEquals("test_flow", oldInfo.get("flowCode"));
            assertEquals("1", oldInfo.get("flowVersion"));

            // runningInstances 结构
            @SuppressWarnings("unchecked")
            Map<String, Object> running = (Map<String, Object>) result.get("runningInstances");
            assertEquals(0L, running.get("total"));
            assertNotNull(running.get("byNode"));

            // impactedInstances 结构
            @SuppressWarnings("unchecked")
            Map<String, Object> impacted = (Map<String, Object>) result.get("impactedInstances");
            assertNotNull(impacted.get("stuckInstances"));
            assertNotNull(impacted.get("affectedInstances"));
        }
    }
}
