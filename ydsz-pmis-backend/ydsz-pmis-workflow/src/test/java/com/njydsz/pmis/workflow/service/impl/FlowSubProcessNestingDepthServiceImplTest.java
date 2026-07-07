package com.njydsz.pmis.workflow.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.WorkflowFacade;
import com.njydsz.pmis.workflow.dto.FlowStartProcessDTO;
import com.njydsz.pmis.workflow.engine.FlowAdvancer;
import com.njydsz.pmis.workflow.engine.FlowEventListener;
import com.njydsz.pmis.workflow.entity.FlowDefinitionDO;
import com.njydsz.pmis.workflow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.entity.FlowNodeDO;
import com.njydsz.pmis.workflow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.service.FlowDefinitionService;
import com.njydsz.pmis.workflow.service.FlowInstanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link FlowSubProcessServiceImpl} P2-8 子流程嵌套深度配置化 单元测试。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>默认深度 3：depth=2 通过 / depth=3 抛异常</li>
 *   <li>自定义深度 5：depth=4 通过 / depth=5 抛异常</li>
 *   <li>深度 0（无父流程）：始终通过</li>
 *   <li>异常消息包含配置的最大深度值</li>
 * </ul>
 *
 * <p>注意：{@code maxNestingDepth} 是 {@code @Value} 注入字段，通过
 * {@link ReflectionTestUtils#setField} 手动设置。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("P2-8: 子流程嵌套深度配置化 - FlowSubProcessServiceImpl")
class FlowSubProcessNestingDepthServiceImplTest {

    @Mock
    private FlowInstanceMapper instanceMapper;
    @Mock
    private FlowDefinitionService definitionService;
    @Mock
    private FlowInstanceService instanceService;
    @Mock
    private FlowAdvancer advancer;
    @Mock
    private WorkflowFacade workflowFacade;
    @Mock
    private List<FlowEventListener> eventListeners;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private FlowSubProcessServiceImpl service;

    @BeforeEach
    void setUp() {
        // P2-8: 默认深度 3
        ReflectionTestUtils.setField(service, "maxNestingDepth", 3);
    }

    // ============== 辅助方法 ==============

    /**
     * 构建带 callActivityFlowCode 的节点
     */
    private FlowNodeDO buildCallActivityNode() {
        FlowNodeDO node = new FlowNodeDO();
        node.setId("node-1");
        node.setNodeCode("callSub1");
        node.setNodeName("调用子流程");
        node.setExt("{\"callActivityFlowCode\":\"sub_flow\"}");
        return node;
    }

    /**
     * 构建父流程实例
     */
    private FlowInstanceDO buildParentInstance(String id, String parentInstanceId) {
        FlowInstanceDO inst = new FlowInstanceDO();
        inst.setId(id);
        inst.setFlowCode("parent_flow");
        inst.setFlowName("父流程");
        inst.setTenantId("1");
        inst.setBusinessType("test");
        inst.setBusinessId("biz-1");
        inst.setFlowStatus("RUNNING");
        inst.setParentInstanceId(parentInstanceId);
        return inst;
    }

    /**
     * Mock 父流程链：构建 N 层嵌套的父实例链
     *
     * @param parentInstanceId 顶层父实例 ID（startSubProcess 的 parentInstance.getId()）
     * @param chainDepth       链深度（每层有一个 parentInstanceId 指向上层）
     *                         例如 chainDepth=2 表示：inst1.parentInstanceId=inst2, inst2.parentInstanceId=inst3, inst3.parentInstanceId=null
     */
    private void mockParentChain(String parentInstanceId, int chainDepth) {
        String currentId = parentInstanceId;
        for (int i = 0; i < chainDepth; i++) {
            String parentId = "parent-" + (i + 1);
            FlowInstanceDO inst = buildParentInstance(currentId, parentId);
            when(instanceMapper.selectById(currentId)).thenReturn(inst);
            currentId = parentId;
        }
        // 最顶层：parentInstanceId = null
        FlowInstanceDO topInst = buildParentInstance(currentId, null);
        when(instanceMapper.selectById(currentId)).thenReturn(topInst);
    }

    /**
     * Mock 子流程定义已发布
     */
    private void mockSubFlowDefinition() {
        FlowDefinitionDO subDef = new FlowDefinitionDO();
        subDef.setId("def-sub-1");
        subDef.setFlowCode("sub_flow");
        subDef.setFlowName("子流程");
        subDef.setFlowVersion("1.0");
        when(definitionService.getPublished(eq("sub_flow"), isNull(), eq("1")))
                .thenReturn(subDef);
    }

    // ============== 默认深度 3 ==============

    @Nested
    @DisplayName("默认深度 3")
    class DefaultDepthTest {

        @Test
        @DisplayName("depth=0（无父流程）— 深度检查通过")
        void depthZero_passes() {
            FlowInstanceDO parent = buildParentInstance("inst-1", null);
            FlowNodeDO node = buildCallActivityNode();
            mockSubFlowDefinition();
            // 无父链：selectById 返回的实例 parentInstanceId=null
            when(instanceMapper.selectById("inst-1")).thenReturn(parent);
            // 后续步骤的 mock
            when(instanceService.getVariables("inst-1")).thenReturn(new HashMap<>());
            when(workflowFacade.startProcess(any(FlowStartProcessDTO.class))).thenReturn("child-1");
            when(eventListeners.iterator()).thenReturn(Collections.emptyIterator());

            String result = service.startSubProcess(parent, node, new HashMap<>());

            assertNotNull(result);
            assertEquals("child-1", result);
        }

        @Test
        @DisplayName("depth=2 — 深度检查通过（2 < 3）")
        void depthTwo_passes() {
            FlowInstanceDO parent = buildParentInstance("inst-1", "parent-1");
            FlowNodeDO node = buildCallActivityNode();
            mockSubFlowDefinition();
            // 2 层父链：inst-1 → parent-1 → parent-2(顶层, parentId=null)
            mockParentChain("inst-1", 2);
            // 后续步骤的 mock
            when(instanceService.getVariables("inst-1")).thenReturn(new HashMap<>());
            when(workflowFacade.startProcess(any(FlowStartProcessDTO.class))).thenReturn("child-1");
            when(eventListeners.iterator()).thenReturn(Collections.emptyIterator());

            String result = service.startSubProcess(parent, node, new HashMap<>());

            assertNotNull(result);
            assertEquals("child-1", result);
        }

        @Test
        @DisplayName("depth=3 — 抛出 BizException（3 >= 3）")
        void depthThree_throws() {
            FlowInstanceDO parent = buildParentInstance("inst-1", "parent-1");
            FlowNodeDO node = buildCallActivityNode();
            mockSubFlowDefinition();
            // 3 层父链
            mockParentChain("inst-1", 3);

            BizException ex = assertThrows(BizException.class,
                    () -> service.startSubProcess(parent, node, new HashMap<>()));

            assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
            assertEquals("error.workflow.msg_14aff96e", ex.getErrorMessage());
            // 验证参数：maxDepth=3, currentDepth=3, parentInstanceId="inst-1"
            Object[] args = ex.getArgs();
            assertNotNull(args);
            assertEquals(3, args.length);
            assertEquals(3, args[0]); // maxNestingDepth
            assertEquals(3, args[1]); // nestingDepth
            assertEquals("inst-1", args[2]); // parentInstance.getId()
        }
    }

    // ============== 自定义深度 5 ==============

    @Nested
    @DisplayName("自定义深度 5")
    class CustomDepthTest {

        @BeforeEach
        void setUpCustomDepth() {
            ReflectionTestUtils.setField(service, "maxNestingDepth", 5);
        }

        @Test
        @DisplayName("depth=4 — 深度检查通过（4 < 5）")
        void depthFour_passes() {
            FlowInstanceDO parent = buildParentInstance("inst-1", "parent-1");
            FlowNodeDO node = buildCallActivityNode();
            mockSubFlowDefinition();
            mockParentChain("inst-1", 4);
            when(instanceService.getVariables("inst-1")).thenReturn(new HashMap<>());
            when(workflowFacade.startProcess(any(FlowStartProcessDTO.class))).thenReturn("child-1");
            when(eventListeners.iterator()).thenReturn(Collections.emptyIterator());

            String result = service.startSubProcess(parent, node, new HashMap<>());

            assertNotNull(result);
            assertEquals("child-1", result);
        }

        @Test
        @DisplayName("depth=5 — 抛出 BizException（5 >= 5）")
        void depthFive_throws() {
            FlowInstanceDO parent = buildParentInstance("inst-1", "parent-1");
            FlowNodeDO node = buildCallActivityNode();
            mockSubFlowDefinition();
            mockParentChain("inst-1", 5);

            BizException ex = assertThrows(BizException.class,
                    () -> service.startSubProcess(parent, node, new HashMap<>()));

            assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
            assertEquals("error.workflow.msg_14aff96e", ex.getErrorMessage());
            Object[] args = ex.getArgs();
            assertNotNull(args);
            assertEquals(5, args[0]); // maxNestingDepth=5
            assertEquals(5, args[1]); // nestingDepth=5
        }
    }

    // ============== 边界场景 ==============

    @Nested
    @DisplayName("边界场景")
    class EdgeCaseTest {

        @Test
        @DisplayName("maxNestingDepth=1 — 仅允许顶层流程，depth=1 即抛异常")
        void maxDepthOne() {
            ReflectionTestUtils.setField(service, "maxNestingDepth", 1);
            FlowInstanceDO parent = buildParentInstance("inst-1", "parent-1");
            FlowNodeDO node = buildCallActivityNode();
            mockSubFlowDefinition();
            mockParentChain("inst-1", 1);

            BizException ex = assertThrows(BizException.class,
                    () -> service.startSubProcess(parent, node, new HashMap<>()));

            Object[] args = ex.getArgs();
            assertEquals(1, args[0]); // maxNestingDepth=1
            assertEquals(1, args[1]); // nestingDepth=1
        }

        @Test
        @DisplayName("父实例 null — 抛出 BAD_REQUEST（父实例/callActivity 节点不能为空）")
        void nullParent_throws() {
            FlowNodeDO node = buildCallActivityNode();

            BizException ex = assertThrows(BizException.class,
                    () -> service.startSubProcess(null, node, new HashMap<>()));

            assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("callActivityNode null — 抛出 BAD_REQUEST")
        void nullNode_throws() {
            FlowInstanceDO parent = buildParentInstance("inst-1", null);

            BizException ex = assertThrows(BizException.class,
                    () -> service.startSubProcess(parent, null, new HashMap<>()));

            assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        }
    }
}
