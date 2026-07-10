package com.njydsz.pmis.workflow.facade;

import com.njydsz.pmis.common.api.PageResult;
import com.njydsz.pmis.workflow.entity.instance.FlowInstanceDO;
import com.njydsz.pmis.workflow.mapper.analytics.FlowAuditLogMapper;
import com.njydsz.pmis.workflow.mapper.instance.FlowHisTaskMapper;
import com.njydsz.pmis.workflow.service.definition.FlowDefinitionService;
import com.njydsz.pmis.workflow.service.instance.FlowInstanceService;
import com.njydsz.pmis.workflow.service.instance.FlowTaskService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PmisWorkflowFacade 单元测试
 *
 * <p>P0-2 修复：针对 {@link PmisWorkflowFacade#listAllInstances} 返回
 * {@link PageResult} 的行为验证（list / total / page / size 正确传递，
 * FlowInstanceDO → Map 转换正确）。
 *
 * @author ydsz-pmis-team
 * @since 1.7.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PmisWorkflowFacade 单元测试")
class PmisWorkflowFacadeTest {

    @Mock
    private FlowInstanceService instanceService;
    @Mock
    private FlowTaskService taskService;
    @Mock
    private FlowAuditLogMapper auditLogMapper;
    @Mock
    private FlowHisTaskMapper hisTaskMapper;
    @Mock
    private FlowDefinitionService definitionService;

    @InjectMocks
    private PmisWorkflowFacade facade;

    /**
     * 正常调用：mock instanceService.page 返回 1 条数据 / total=5，
     * 验证 facade 返回 PageResult 且 list/total/page/size 正确传递。
     *
     * <p>注：未设置 SecurityContext，{@code getTenantIdOrDefault("1")} 返回默认值 "1"。
     */
    @Test
    @DisplayName("listAllInstances 正常调用 → 返回 PageResult 且 list/total 正确")
    void listAllInstances_normal_returnPageResult() {
        FlowInstanceDO instance = buildInstance("I1", "PROJECT", "U1");
        PageResult<FlowInstanceDO> mocked = PageResult.of(List.of(instance), 5L, 1L, 20L);
        when(instanceService.page(eq("PROJECT"), isNull(), eq("RUNNING"),
                isNull(), isNull(), eq("1"), eq(1), eq(20)))
                .thenReturn(mocked);

        PageResult<Map<String, Object>> result = facade.listAllInstances(
                "PROJECT", "RUNNING", null, null, 1, 20);

        assertNotNull(result);
        assertEquals(5L, result.getTotal());
        assertEquals(1L, result.getPage());
        assertEquals(20L, result.getSize());
        assertEquals(1, result.getList().size());
        verify(instanceService).page(eq("PROJECT"), isNull(), eq("RUNNING"),
                isNull(), isNull(), eq("1"), eq(1), eq(20));
    }

    /**
     * 空结果：mock instanceService.page 返回空列表 / total=0，
     * 验证 facade 返回空 PageResult，list 为空且 total=0。
     */
    @Test
    @DisplayName("listAllInstances 空结果 → 返回空 PageResult，total=0")
    void listAllInstances_empty_returnEmptyPageResult() {
        PageResult<FlowInstanceDO> mocked = PageResult.of(Collections.emptyList(), 0L, 1L, 20L);
        when(instanceService.page(isNull(), isNull(), isNull(),
                isNull(), isNull(), eq("1"), eq(1), eq(20)))
                .thenReturn(mocked);

        PageResult<Map<String, Object>> result = facade.listAllInstances(
                null, null, null, null, 1, 20);

        assertNotNull(result);
        assertEquals(0L, result.getTotal());
        assertTrue(result.getList().isEmpty());
    }

    /**
     * Map 转换：构造带完整字段的 FlowInstanceDO，
     * 验证 facade 调用 instanceToMap 后 Map 中关键字段正确。
     */
    @Test
    @DisplayName("listAllInstances → FlowInstanceDO 正确转换为 Map")
    void listAllInstances_verifyMapConversion() {
        FlowInstanceDO instance = buildInstance("I2", "CONTRACT", "U2");
        instance.setFlowCode("flow_contract");
        instance.setFlowName("合同审批流程");
        instance.setDefinitionId("DEF-1");
        instance.setFlowVersion("1.0.0");
        instance.setBusinessId("BIZ-1001");
        instance.setBusinessNo("NO-2026-001");
        instance.setTitle("合同审批-测试");
        instance.setInitiatorName("张三");
        instance.setCurrentNodeCode("node_approve");
        instance.setCurrentNodeName("部门审批");
        instance.setFlowStatus("COMPLETED");
        instance.setActivityStatus(1);
        LocalDateTime startAt = LocalDateTime.of(2026, 7, 1, 10, 0, 0);
        LocalDateTime endAt = LocalDateTime.of(2026, 7, 3, 18, 0, 0);
        instance.setStartAt(startAt);
        instance.setEndAt(endAt);
        instance.setDurationMs(172800000L);

        PageResult<FlowInstanceDO> mocked = PageResult.of(List.of(instance), 1L, 1L, 20L);
        when(instanceService.page(eq("CONTRACT"), isNull(), eq("COMPLETED"),
                isNull(), isNull(), eq("1"), eq(1), eq(20)))
                .thenReturn(mocked);

        PageResult<Map<String, Object>> result = facade.listAllInstances(
                "CONTRACT", "COMPLETED", null, null, 1, 20);

        assertNotNull(result);
        assertEquals(1, result.getList().size());
        Map<String, Object> m = result.getList().get(0);
        assertEquals("I2", m.get("id"));
        assertEquals("flow_contract", m.get("flowCode"));
        assertEquals("合同审批流程", m.get("flowName"));
        assertEquals("DEF-1", m.get("definitionId"));
        assertEquals("1.0.0", m.get("flowVersion"));
        assertEquals("CONTRACT", m.get("businessType"));
        assertEquals("BIZ-1001", m.get("businessId"));
        assertEquals("NO-2026-001", m.get("businessNo"));
        assertEquals("合同审批-测试", m.get("title"));
        assertEquals("U2", m.get("initiatorId"));
        assertEquals("张三", m.get("initiatorName"));
        assertEquals("node_approve", m.get("currentNodeCode"));
        assertEquals("部门审批", m.get("currentNodeName"));
        assertEquals("COMPLETED", m.get("flowStatus"));
        assertEquals(1, m.get("activityStatus"));
        assertEquals(startAt, m.get("startAt"));
        assertEquals(endAt, m.get("endAt"));
        assertEquals(172800000L, m.get("durationMs"));
    }

    /** 构造最小可用的 FlowInstanceDO（id / businessType / initiatorId / flowStatus） */
    private FlowInstanceDO buildInstance(String id, String businessType, String initiatorId) {
        FlowInstanceDO instance = new FlowInstanceDO();
        instance.setId(id);
        instance.setBusinessType(businessType);
        instance.setInitiatorId(initiatorId);
        instance.setFlowStatus("RUNNING");
        return instance;
    }
}
