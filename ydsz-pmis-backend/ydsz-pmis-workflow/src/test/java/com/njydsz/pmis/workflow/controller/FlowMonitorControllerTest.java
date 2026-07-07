package com.njydsz.pmis.workflow.controller;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.workflow.mapper.FlowHisTaskMapper;
import com.njydsz.pmis.workflow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.mapper.FlowRunTaskMapper;
import com.njydsz.pmis.workflow.service.FlowEfficiencyService;
import com.njydsz.pmis.workflow.service.FlowInstanceService;
import com.njydsz.pmis.workflow.service.FlowTaskService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link FlowMonitorController} P2-7 监控仪表盘 UI 增强 单元测试。
 *
 * <p>覆盖 P2-7 新增的 4 个端点：
 * <ul>
 *   <li>monitorDashboard — 仪表盘聚合端点（成功 / 部分降级）</li>
 *   <li>monitorOverdueTasks — 超期任务 Top N（成功 / 空 / null）</li>
 *   <li>monitorApproverWorkload — 审批人负载分布（成功 / 空 / null）</li>
 *   <li>monitorFlowEfficiencyComparison — 流程效率对比（成功 / 带时间范围 / null）</li>
 * </ul>
 *
 * <p>注意：SecurityContext.getTenantIdOrDefault("1") 在无 ThreadLocal 用户时返回 "1"，
 * 无需 Mock。私有方法 buildOverview / buildInstanceTrend 通过 monitorDashboard 间接测试。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("P2-7: 监控仪表盘 UI 增强 - FlowMonitorController")
class FlowMonitorControllerTest {

    @Mock
    private FlowEfficiencyService efficiencyService;
    @Mock
    private FlowInstanceMapper instanceMapper;
    @Mock
    private FlowHisTaskMapper hisTaskMapper;
    @Mock
    private FlowTaskService taskService;
    @Mock
    private FlowInstanceService instanceService;
    @Mock
    private FlowRunTaskMapper runTaskMapper;

    @InjectMocks
    private FlowMonitorController controller;

    // ============== monitorDashboard ==============

    @Nested
    @DisplayName("monitorDashboard - 仪表盘聚合端点")
    class DashboardTest {

        @Test
        @DisplayName("全部子模块成功 — 返回完整 dashboard 结构")
        void dashboard_allModulesSuccess() {
            String tenantId = "1";
            // overview
            when(instanceMapper.selectCountGroupByStatus(tenantId))
                    .thenReturn(List.of(statusRow("RUNNING", 5L), statusRow("COMPLETED", 10L)));
            Map<String, Object> todayCount = new LinkedHashMap<>();
            todayCount.put("todayNewCount", 3L);
            todayCount.put("todayCompletedCount", 2L);
            when(instanceMapper.selectTodayCount(tenantId)).thenReturn(todayCount);
            when(taskService.countPending(tenantId)).thenReturn(8L);
            when(taskService.countOverdue(null, tenantId)).thenReturn(2L);

            // instanceTrend
            when(instanceMapper.selectDailyNewCount(eq(tenantId), any(), any()))
                    .thenReturn(List.of(dateRow("2026-07-08", "newCount", 3L)));
            when(instanceMapper.selectDailyCompletedCount(eq(tenantId), any(), any()))
                    .thenReturn(List.of(dateRow("2026-07-08", "completedCount", 2L)));

            // overdueTop5
            List<Map<String, Object>> overdueTop = List.of(overdueTaskRow("t1", 48.5));
            when(runTaskMapper.selectOverdueTopN(tenantId, 5)).thenReturn(overdueTop);

            // anomalyTop5
            List<Map<String, Object>> anomalies = List.of(anomalyRow("STUCK", 100L));
            when(efficiencyService.detectAnomalies(tenantId, 5, 24, 7)).thenReturn(anomalies);

            // efficiency
            Map<String, Object> efficiency = new LinkedHashMap<>();
            efficiency.put("totalCount", 100L);
            when(efficiencyService.efficiencyStats(tenantId, null, null)).thenReturn(efficiency);

            // healthScore
            Map<String, Object> health = new LinkedHashMap<>();
            health.put("score", 85);
            when(efficiencyService.healthScore(tenantId, null, null)).thenReturn(health);

            Result<Map<String, Object>> result = controller.monitorDashboard();

            assertNotNull(result);
            Map<String, Object> dashboard = result.getData();
            assertNotNull(dashboard);
            assertEquals(6, dashboard.size());
            assertTrue(dashboard.containsKey("overview"));
            assertTrue(dashboard.containsKey("instanceTrend"));
            assertTrue(dashboard.containsKey("overdueTop5"));
            assertTrue(dashboard.containsKey("anomalyTop5"));
            assertTrue(dashboard.containsKey("efficiency"));
            assertTrue(dashboard.containsKey("healthScore"));

            // overview 字段校验
            @SuppressWarnings("unchecked")
            Map<String, Object> overview = (Map<String, Object>) dashboard.get("overview");
            assertEquals(5L, overview.get("runningCount"));
            assertEquals(3L, overview.get("todayNewCount"));
            assertEquals(2L, overview.get("todayCompletedCount"));
            assertEquals(8L, overview.get("pendingTaskCount"));
            assertEquals(2L, overview.get("overdueTaskCount"));

            // instanceTrend 7 天
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> trend = (List<Map<String, Object>>) dashboard.get("instanceTrend");
            assertEquals(7, trend.size());

            // overdueTop5
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> overdue = (List<Map<String, Object>>) dashboard.get("overdueTop5");
            assertEquals(1, overdue.size());

            // healthScore
            @SuppressWarnings("unchecked")
            Map<String, Object> healthResult = (Map<String, Object>) dashboard.get("healthScore");
            assertEquals(85, healthResult.get("score"));
        }

        @Test
        @DisplayName("部分子模块异常 — 异常模块降级为空值，不阻塞其他模块")
        void dashboard_partialFailureDegrades() {
            String tenantId = "1";
            // overview 全部失败
            when(instanceMapper.selectCountGroupByStatus(tenantId)).thenThrow(new RuntimeException("DB down"));
            when(instanceMapper.selectTodayCount(tenantId)).thenThrow(new RuntimeException("DB down"));
            when(taskService.countPending(tenantId)).thenThrow(new RuntimeException("DB down"));
            when(taskService.countOverdue(null, tenantId)).thenThrow(new RuntimeException("DB down"));

            // instanceTrend 失败
            when(instanceMapper.selectDailyNewCount(eq(tenantId), any(), any()))
                    .thenThrow(new RuntimeException("DB down"));

            // overdueTop5 成功
            when(runTaskMapper.selectOverdueTopN(tenantId, 5))
                    .thenReturn(List.of(overdueTaskRow("t1", 12.0)));

            // anomalyTop5 失败
            when(efficiencyService.detectAnomalies(tenantId, 5, 24, 7))
                    .thenThrow(new RuntimeException("service down"));

            // efficiency 成功
            when(efficiencyService.efficiencyStats(tenantId, null, null))
                    .thenReturn(Map.of("totalCount", 50L));

            // healthScore 失败
            when(efficiencyService.healthScore(tenantId, null, null))
                    .thenThrow(new RuntimeException("service down"));

            Result<Map<String, Object>> result = controller.monitorDashboard();

            assertNotNull(result);
            Map<String, Object> dashboard = result.getData();
            assertNotNull(dashboard);
            // 所有 6 个 key 都应存在（失败模块降级为空）
            assertEquals(6, dashboard.size());

            // overview 降级为空 Map（但 runningCount 等字段仍存在，因为 buildOverview 内部有 try-catch）
            @SuppressWarnings("unchecked")
            Map<String, Object> overview = (Map<String, Object>) dashboard.get("overview");
            assertEquals(0L, overview.get("runningCount"));
            assertEquals(0L, overview.get("todayNewCount"));
            assertEquals(0L, overview.get("pendingTaskCount"));

            // instanceTrend 降级为空 list
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> trend = (List<Map<String, Object>>) dashboard.get("instanceTrend");
            assertTrue(trend.isEmpty());

            // overdueTop5 成功返回数据
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> overdue = (List<Map<String, Object>>) dashboard.get("overdueTop5");
            assertEquals(1, overdue.size());

            // anomalyTop5 降级为空 list
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> anomalies = (List<Map<String, Object>>) dashboard.get("anomalyTop5");
            assertTrue(anomalies.isEmpty());

            // efficiency 成功
            @SuppressWarnings("unchecked")
            Map<String, Object> efficiency = (Map<String, Object>) dashboard.get("efficiency");
            assertEquals(50L, efficiency.get("totalCount"));

            // healthScore 降级为空 Map
            @SuppressWarnings("unchecked")
            Map<String, Object> health = (Map<String, Object>) dashboard.get("healthScore");
            assertTrue(health.isEmpty());
        }

        @Test
        @DisplayName("overview 中 todayCount 返回 null — 降级为 0")
        void dashboard_todayCountNull() {
            String tenantId = "1";
            when(instanceMapper.selectCountGroupByStatus(tenantId)).thenReturn(new ArrayList<>());
            when(instanceMapper.selectTodayCount(tenantId)).thenReturn(null);
            when(taskService.countPending(tenantId)).thenReturn(0L);
            when(taskService.countOverdue(null, tenantId)).thenReturn(0L);
            when(instanceMapper.selectDailyNewCount(eq(tenantId), any(), any())).thenReturn(new ArrayList<>());
            when(instanceMapper.selectDailyCompletedCount(eq(tenantId), any(), any())).thenReturn(new ArrayList<>());
            when(runTaskMapper.selectOverdueTopN(tenantId, 5)).thenReturn(new ArrayList<>());
            when(efficiencyService.detectAnomalies(tenantId, 5, 24, 7)).thenReturn(new ArrayList<>());
            when(efficiencyService.efficiencyStats(tenantId, null, null)).thenReturn(new LinkedHashMap<>());
            when(efficiencyService.healthScore(tenantId, null, null)).thenReturn(new LinkedHashMap<>());

            Result<Map<String, Object>> result = controller.monitorDashboard();

            @SuppressWarnings("unchecked")
            Map<String, Object> overview = (Map<String, Object>) result.getData().get("overview");
            assertEquals(0L, overview.get("todayNewCount"));
            assertEquals(0L, overview.get("todayCompletedCount"));
        }
    }

    // ============== monitorOverdueTasks ==============

    @Nested
    @DisplayName("monitorOverdueTasks - 超期任务 Top N")
    class OverdueTasksTest {

        @Test
        @DisplayName("返回超期任务列表 — 按超期时长降序")
        void overdueTasks_success() {
            String tenantId = "1";
            List<Map<String, Object>> rows = Arrays.asList(
                    overdueTaskRow("t1", 72.5),
                    overdueTaskRow("t2", 48.0),
                    overdueTaskRow("t3", 12.0));
            when(runTaskMapper.selectOverdueTopN(tenantId, 10)).thenReturn(rows);

            Result<List<Map<String, Object>>> result = controller.monitorOverdueTasks(10);

            assertNotNull(result);
            List<Map<String, Object>> data = result.getData();
            assertEquals(3, data.size());
            assertEquals("t1", data.get(0).get("taskId"));
            assertEquals(72.5, data.get(0).get("overdueHours"));
        }

        @Test
        @DisplayName("无超期任务 — 返回空列表")
        void overdueTasks_empty() {
            when(runTaskMapper.selectOverdueTopN(anyString(), anyInt())).thenReturn(new ArrayList<>());

            Result<List<Map<String, Object>>> result = controller.monitorOverdueTasks(10);

            assertNotNull(result);
            assertTrue(result.getData().isEmpty());
        }

        @Test
        @DisplayName("mapper 返回 null — 降级为空列表")
        void overdueTasks_nullDegradesToEmpty() {
            when(runTaskMapper.selectOverdueTopN(anyString(), anyInt())).thenReturn(null);

            Result<List<Map<String, Object>>> result = controller.monitorOverdueTasks(5);

            assertNotNull(result);
            assertNotNull(result.getData());
            assertTrue(result.getData().isEmpty());
        }
    }

    // ============== monitorApproverWorkload ==============

    @Nested
    @DisplayName("monitorApproverWorkload - 审批人负载分布")
    class ApproverWorkloadTest {

        @Test
        @DisplayName("返回审批人负载列表 — 按 totalCount 降序")
        void workload_success() {
            String tenantId = "1";
            List<Map<String, Object>> rows = Arrays.asList(
                    workloadRow("u1", "Alice", 5, 3, 8, 1),
                    workloadRow("u2", "Bob", 2, 1, 3, 0));
            when(runTaskMapper.selectWorkloadByAssignee(tenantId, 10)).thenReturn(rows);

            Result<List<Map<String, Object>>> result = controller.monitorApproverWorkload(10);

            assertNotNull(result);
            List<Map<String, Object>> data = result.getData();
            assertEquals(2, data.size());
            assertEquals("u1", data.get(0).get("assigneeId"));
            assertEquals("Alice", data.get(0).get("assigneeName"));
            assertEquals(8L, data.get(0).get("totalCount"));
        }

        @Test
        @DisplayName("无待办任务 — 返回空列表")
        void workload_empty() {
            when(runTaskMapper.selectWorkloadByAssignee(anyString(), anyInt())).thenReturn(new ArrayList<>());

            Result<List<Map<String, Object>>> result = controller.monitorApproverWorkload(10);

            assertNotNull(result);
            assertTrue(result.getData().isEmpty());
        }

        @Test
        @DisplayName("mapper 返回 null — 降级为空列表")
        void workload_nullDegradesToEmpty() {
            when(runTaskMapper.selectWorkloadByAssignee(anyString(), anyInt())).thenReturn(null);

            Result<List<Map<String, Object>>> result = controller.monitorApproverWorkload(5);

            assertNotNull(result);
            assertNotNull(result.getData());
            assertTrue(result.getData().isEmpty());
        }
    }

    // ============== monitorFlowEfficiencyComparison ==============

    @Nested
    @DisplayName("monitorFlowEfficiencyComparison - 流程效率对比")
    class FlowEfficiencyComparisonTest {

        @Test
        @DisplayName("无时间范围 — 返回全部流程效率对比")
        void comparison_noTimeRange() {
            String tenantId = "1";
            List<Map<String, Object>> rows = Arrays.asList(
                    flowEfficiencyRow("leave", "请假流程", 100, 80, 20, 0.2, 3600000.0),
                    flowEfficiencyRow("expense", "报销流程", 50, 45, 5, 0.1, 7200000.0));
            when(hisTaskMapper.selectFlowEfficiencyComparison(tenantId, null, null)).thenReturn(rows);

            Result<List<Map<String, Object>>> result = controller.monitorFlowEfficiencyComparison(null, null);

            assertNotNull(result);
            List<Map<String, Object>> data = result.getData();
            assertEquals(2, data.size());
            assertEquals("leave", data.get(0).get("flowCode"));
            assertEquals(100L, data.get(0).get("totalCount"));
            assertEquals(0.2, data.get(0).get("rejectionRate"));
        }

        @Test
        @DisplayName("带时间范围 — 解析时间并传递给 mapper")
        void comparison_withTimeRange() {
            String tenantId = "1";
            when(hisTaskMapper.selectFlowEfficiencyComparison(eq(tenantId), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(new ArrayList<>());

            Result<List<Map<String, Object>>> result = controller.monitorFlowEfficiencyComparison(
                    "2026-07-01 00:00:00", "2026-07-08 23:59:59");

            assertNotNull(result);
            verify(hisTaskMapper).selectFlowEfficiencyComparison(eq(tenantId),
                    argThat(dt -> dt != null && dt.getYear() == 2026),
                    argThat(dt -> dt != null && dt.getDayOfMonth() == 8));
        }

        @Test
        @DisplayName("非法时间格式 — 降级为 null（不过滤时间）")
        void comparison_invalidTimeFormat() {
            String tenantId = "1";
            when(hisTaskMapper.selectFlowEfficiencyComparison(eq(tenantId), isNull(), isNull()))
                    .thenReturn(new ArrayList<>());

            Result<List<Map<String, Object>>> result = controller.monitorFlowEfficiencyComparison(
                    "not-a-date", "2026-13-45");

            assertNotNull(result);
            verify(hisTaskMapper).selectFlowEfficiencyComparison(eq(tenantId), isNull(), isNull());
        }

        @Test
        @DisplayName("仅传日期（无时分秒）— 正常解析为 LocalDateTime")
        void comparison_dateOnly() {
            String tenantId = "1";
            when(hisTaskMapper.selectFlowEfficiencyComparison(eq(tenantId), any(LocalDateTime.class), isNull()))
                    .thenReturn(new ArrayList<>());

            Result<List<Map<String, Object>>> result = controller.monitorFlowEfficiencyComparison(
                    "2026-07-01", null);

            assertNotNull(result);
            verify(hisTaskMapper).selectFlowEfficiencyComparison(eq(tenantId),
                    argThat(dt -> dt != null && dt.getDayOfMonth() == 1),
                    isNull());
        }

        @Test
        @DisplayName("mapper 返回 null — 降级为空列表")
        void comparison_nullDegradesToEmpty() {
            when(hisTaskMapper.selectFlowEfficiencyComparison(anyString(), any(), any())).thenReturn(null);

            Result<List<Map<String, Object>>> result = controller.monitorFlowEfficiencyComparison(null, null);

            assertNotNull(result);
            assertNotNull(result.getData());
            assertTrue(result.getData().isEmpty());
        }
    }

    // ============== 辅助方法 ==============

    private Map<String, Object> statusRow(String status, long cnt) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("flowStatus", status);
        row.put("cnt", cnt);
        return row;
    }

    private Map<String, Object> dateRow(String date, String key, long cnt) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("date", date);
        row.put(key, cnt);
        return row;
    }

    private Map<String, Object> overdueTaskRow(String taskId, double overdueHours) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("taskId", taskId);
        row.put("instanceId", "inst-" + taskId);
        row.put("flowCode", "leave");
        row.put("flowName", "请假流程");
        row.put("title", "请假申请");
        row.put("nodeName", "部门审批");
        row.put("assigneeId", "u1");
        row.put("assigneeName", "Alice");
        row.put("dueAt", LocalDateTime.now().minusHours((long) overdueHours));
        row.put("overdueHours", overdueHours);
        row.put("reminderCount", 1);
        return row;
    }

    private Map<String, Object> anomalyRow(String type, long instanceId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("type", type);
        row.put("instanceId", instanceId);
        row.put("description", "stuck at node");
        return row;
    }

    private Map<String, Object> workloadRow(String assigneeId, String assigneeName,
                                             long pending, long claimed, long total, long overdue) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("assigneeId", assigneeId);
        row.put("assigneeName", assigneeName);
        row.put("pendingCount", pending);
        row.put("claimedCount", claimed);
        row.put("totalCount", total);
        row.put("overdueCount", overdue);
        return row;
    }

    private Map<String, Object> flowEfficiencyRow(String flowCode, String flowName,
                                                   long total, long completed, long rejected,
                                                   double rejectionRate, double avgDurationMs) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("flowCode", flowCode);
        row.put("flowName", flowName);
        row.put("totalCount", total);
        row.put("completedCount", completed);
        row.put("rejectedCount", rejected);
        row.put("rejectionRate", rejectionRate);
        row.put("avgDurationMs", avgDurationMs);
        return row;
    }
}
