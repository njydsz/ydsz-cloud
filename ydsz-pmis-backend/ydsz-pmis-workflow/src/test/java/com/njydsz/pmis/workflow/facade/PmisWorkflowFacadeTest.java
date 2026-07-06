package com.njydsz.pmis.workflow.facade;

import com.njydsz.pmis.common.api.PageResult;
import com.njydsz.pmis.common.security.SecurityContext;
import com.njydsz.pmis.workflow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.entity.FlowRunTaskDO;
import com.njydsz.pmis.workflow.mapper.FlowAuditLogMapper;
import com.njydsz.pmis.workflow.mapper.FlowHisTaskMapper;
import com.njydsz.pmis.workflow.service.FlowDefinitionService;
import com.njydsz.pmis.workflow.service.FlowInstanceService;
import com.njydsz.pmis.workflow.service.FlowTaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PmisWorkflowFacade 单元测试
 *
 * <p>GAP-P0-1: 重点覆盖 {@link PmisWorkflowFacade#listAllInstances} 管理员"全部"视图，
 * 确保不按 initiatorId 过滤、返回 Map 结构、字段完整。
 *
 * @author ydsz-pmis-team
 * @since 1.6.0
 */
@ExtendWith(MockitoExtension.class)
class PmisWorkflowFacadeTest {

    @Mock private FlowInstanceService instanceService;
    @Mock private FlowTaskService taskService;
    @Mock private FlowAuditLogMapper auditLogMapper;
    @Mock private FlowHisTaskMapper hisTaskMapper;
    @Mock private FlowDefinitionService definitionService;

    private PmisWorkflowFacade facade;
    private MockedStatic<SecurityContext> securityContextMock;

    @BeforeEach
    void setUp() {
        facade = new PmisWorkflowFacade(instanceService, taskService,
                auditLogMapper, hisTaskMapper, definitionService);
        securityContextMock = mockStatic(SecurityContext.class);
        securityContextMock.when(() -> SecurityContext.getTenantIdOrDefault(anyLong()))
                .thenReturn(1L);
        securityContextMock.when(() -> SecurityContext.getUserId()).thenReturn(500L);
    }

    @AfterEach
    void tearDown() {
        securityContextMock.close();
    }

    // ============ GAP-P0-1: listAllInstances 管理员"全部"视图 ============

    @Test
    @DisplayName("listAllInstances - 不按 initiatorId 过滤，返回全部实例 Map 列表")
    void listAllInstancesShouldNotFilterByInitiator() {
        FlowInstanceDO inst = buildInstance(1001L, "PROJECT_INITIATION", "RUNNING");
        PageResult<FlowInstanceDO> pageResult = PageResult.of(List.of(inst), 1L, 1L, 20L);
        // 关键断言：initiatorId 参数必须为 null（即不按发起人过滤）
        when(instanceService.page(anyString(), isNull(), anyString(),
                any(), any(), anyLong(), anyInt(), anyInt()))
                .thenReturn(pageResult);

        List<Map<String, Object>> result = facade.listAllInstances(
                "PROJECT_INITIATION", "RUNNING", null, null, 1, 20);

        assertThat(result).hasSize(1);
        Map<String, Object> m = result.get(0);
        assertThat(m.get("id")).isEqualTo(1001L);
        assertThat(m.get("flowCode")).isEqualTo("PROJECT_INITIATION");
        assertThat(m.get("flowName")).isEqualTo("立项审批");
        assertThat(m.get("businessType")).isEqualTo("PROJECT_INITIATION");
        assertThat(m.get("initiatorId")).isEqualTo(500L);
        assertThat(m.get("flowStatus")).isEqualTo("RUNNING");
        assertThat(m.get("currentNodeName")).isEqualTo("部门经理审批");
    }

    @Test
    @DisplayName("listAllInstances - 无参数时返回空筛选条件下的全部实例")
    void listAllInstancesWithNullFiltersShouldReturnAll() {
        FlowInstanceDO inst1 = buildInstance(1001L, "PROJECT_INITIATION", "RUNNING");
        FlowInstanceDO inst2 = buildInstance(1002L, "CONTRACT_APPROVAL", "COMPLETED");
        PageResult<FlowInstanceDO> pageResult = PageResult.of(List.of(inst1, inst2), 2L, 1L, 20L);
        when(instanceService.page(isNull(), isNull(), isNull(),
                isNull(), isNull(), anyLong(), anyInt(), anyInt()))
                .thenReturn(pageResult);

        List<Map<String, Object>> result = facade.listAllInstances(
                null, null, null, null, 1, 20);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).get("id")).isEqualTo(1001L);
        assertThat(result.get(1).get("id")).isEqualTo(1002L);
    }

    @Test
    @DisplayName("listAllInstances - 空结果返回空列表")
    void listAllInstancesWithEmptyResultShouldReturnEmptyList() {
        PageResult<FlowInstanceDO> emptyPage = PageResult.of(Collections.emptyList(), 0L, 1L, 20L);
        when(instanceService.page(any(), any(), any(), any(), any(), anyLong(), anyInt(), anyInt()))
                .thenReturn(emptyPage);

        List<Map<String, Object>> result = facade.listAllInstances(
                null, null, null, null, 1, 20);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("listAllInstances - 时间范围参数正确传递")
    void listAllInstancesShouldPassTimeRange() {
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 7, 5, 23, 59);
        FlowInstanceDO inst = buildInstance(1001L, "PROJECT_INITIATION", "RUNNING");
        PageResult<FlowInstanceDO> pageResult = PageResult.of(List.of(inst), 1L, 1L, 20L);
        when(instanceService.page(isNull(), isNull(), isNull(),
                eq(start), eq(end), anyLong(), anyInt(), anyInt()))
                .thenReturn(pageResult);

        List<Map<String, Object>> result = facade.listAllInstances(
                null, null, start, end, 1, 20);

        assertThat(result).hasSize(1);
    }

    // ============ GAP-P0-4: passAllTodoTasks 一键通过所有待办 ============

    @Test
    @DisplayName("passAllTodoTasks - 有待办时查询并批量通过，返回通过数量")
    void passAllTodoTasksShouldQueryAndBatchPass() {
        FlowRunTaskDO t1 = buildTodoTask(501L);
        FlowRunTaskDO t2 = buildTodoTask(502L);
        FlowRunTaskDO t3 = buildTodoTask(503L);
        PageResult<FlowRunTaskDO> pageResult = PageResult.of(List.of(t1, t2, t3), 3L, 1L, 100L);
        when(taskService.listTodoByAssigneePage(eq("500"), eq(1L), eq(1), eq(100)))
                .thenReturn(pageResult);

        int count = facade.passAllTodoTasks(500L, "一键通过");

        assertThat(count).isEqualTo(3);
        verify(taskService).batchPass(eq(List.of(501L, 502L, 503L)), eq(500L), eq("一键通过"));
    }

    @Test
    @DisplayName("passAllTodoTasks - 无待办时返回 0 且不调用 batchPass")
    void passAllTodoTasksShouldReturnZeroWhenNoTodo() {
        PageResult<FlowRunTaskDO> emptyPage = PageResult.of(Collections.emptyList(), 0L, 1L, 100L);
        when(taskService.listTodoByAssigneePage(anyString(), anyLong(), anyInt(), anyInt()))
                .thenReturn(emptyPage);

        int count = facade.passAllTodoTasks(500L, null);

        assertThat(count).isZero();
        verify(taskService, org.mockito.Mockito.never())
                .batchPass(any(), anyLong(), any());
    }

    // ============ 辅助方法 ============

    private FlowInstanceDO buildInstance(Long id, String businessType, String flowStatus) {
        FlowInstanceDO inst = new FlowInstanceDO();
        inst.setId(id);
        inst.setFlowCode(businessType);
        inst.setFlowName(businessType.equals("PROJECT_INITIATION") ? "立项审批" : "合同审批");
        inst.setDefinitionId(1L);
        inst.setFlowVersion("1");
        inst.setBusinessType(businessType);
        inst.setBusinessId("BIZ-" + id);
        inst.setBusinessNo("NO-" + id);
        inst.setTitle("测试流程-" + id);
        inst.setInitiatorId(500L);
        inst.setInitiatorName("张三");
        inst.setCurrentNodeCode("node_approve_1");
        inst.setCurrentNodeName("部门经理审批");
        inst.setFlowStatus(flowStatus);
        inst.setActivityStatus(1);
        inst.setStartAt(LocalDateTime.of(2026, 7, 1, 10, 0));
        inst.setEndAt(flowStatus.equals("COMPLETED") ? LocalDateTime.of(2026, 7, 3, 15, 0) : null);
        inst.setDurationMs(180000L);
        return inst;
    }

    private FlowRunTaskDO buildTodoTask(Long id) {
        FlowRunTaskDO task = new FlowRunTaskDO();
        task.setId(id);
        task.setInstanceId(1001L);
        task.setNodeCode("node_approve_1");
        task.setTaskStatus("PENDING");
        return task;
    }
}
