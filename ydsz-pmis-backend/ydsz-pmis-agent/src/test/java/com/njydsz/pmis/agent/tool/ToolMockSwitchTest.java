package com.njydsz.pmis.agent.tool;

import com.njydsz.pmis.agent.engine.AgentContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 工具数据源切换测试（P1-5 落地）
 *
 * <p>验证 {@code pmis.agent.tool.mock-enabled} 配置开关在 3 个内置工具上的行为：
 * <ul>
 *   <li>{@code true}（默认）：返回 mock 数据，不抛异常</li>
 *   <li>{@code false}：调用 {@code fetchRealData}，默认实现抛出
 *       {@link UnsupportedOperationException}，提示未实现真实数据源</li>
 * </ul>
 *
 * <p>测试策略：工具字段 {@code mockEnabled} 为 {@code protected}，同包测试类可直接设置。
 * 通过子类化覆盖 {@code fetchRealData} 方法，验证 false 路径确实被调用。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P1-5)
 */
@DisplayName("P1-5: 工具数据源切换测试")
class ToolMockSwitchTest {

    /** 构造 AgentContext，bizRef 用于工具兜底逻辑测试 */
    private AgentContext ctx() {
        AgentContext ctx = new AgentContext();
        ctx.setBizType("project");
        ctx.setBizId("P001");
        ctx.setBizRef("PRJ-001");
        ctx.setCallerId("U001");
        ctx.setCallerName("张三");
        ctx.setSource("unit-test");
        ctx.setTraceId("trace-mock-001");
        return ctx;
    }

    // ==================== ProjectStatusTool 切换测试 ====================

    @Nested
    @DisplayName("ProjectStatusTool 数据源切换")
    class ProjectStatusToolSwitchTest {

        @Test
        @DisplayName("mockEnabled=true（默认）返回 mock 数据，包含 cpi/spi 等指标")
        void shouldReturnMockDataWhenEnabled() {
            ProjectStatusTool tool = new ProjectStatusTool();
            // 字段默认值为 true，显式设置以确保测试意图
            tool.mockEnabled = true;

            Map<String, Object> params = new HashMap<>();
            params.put("projectId", "PRJ-001");

            ToolResult result = tool.execute(params, ctx());

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData())
                    .containsKeys("cpi", "spi", "costOverrunRatio", "riskEventCount", "marginRatio");
            // mock 数据的 CPI 固定为 0.80
            assertThat((double) result.getData().get("cpi")).isEqualTo(0.80);
        }

        @Test
        @DisplayName("mockEnabled=false 调用 fetchRealData，默认抛出 UnsupportedOperationException")
        void shouldThrowWhenDisabledAndRealDataNotImplemented() {
            ProjectStatusTool tool = new ProjectStatusTool();
            tool.mockEnabled = false;

            Map<String, Object> params = new HashMap<>();
            params.put("projectId", "PRJ-001");

            assertThatThrownBy(() -> tool.execute(params, ctx()))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("ProjectStatusTool 真实数据源未实现")
                    .hasMessageContaining("pmis.agent.tool.mock-enabled=true");
        }

        @Test
        @DisplayName("mockEnabled=false 且子类覆盖 fetchRealData 时返回真实数据")
        void shouldReturnRealDataWhenSubclassOverridesFetchRealData() {
            ProjectStatusTool tool = new ProjectStatusTool() {
                @Override
                protected Map<String, Object> fetchRealData(String projectId, AgentContext ctx) {
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("projectId", projectId);
                    data.put("cpi", 1.05);
                    data.put("spi", 0.98);
                    data.put("costOverrunRatio", -0.05);
                    data.put("riskEventCount", 2);
                    data.put("marginRatio", 0.15);
                    return data;
                }
            };
            tool.mockEnabled = false;

            Map<String, Object> params = new HashMap<>();
            params.put("projectId", "PRJ-REAL");

            ToolResult result = tool.execute(params, ctx());

            assertThat(result.isSuccess()).isTrue();
            assertThat((double) result.getData().get("cpi")).isEqualTo(1.05);
            assertThat(result.getData().get("projectId")).isEqualTo("PRJ-REAL");
        }
    }

    // ==================== RiskEventQueryTool 切换测试 ====================

    @Nested
    @DisplayName("RiskEventQueryTool 数据源切换")
    class RiskEventQueryToolSwitchTest {

        @Test
        @DisplayName("mockEnabled=true（默认）返回 mock 风险事件列表")
        void shouldReturnMockDataWhenEnabled() {
            RiskEventQueryTool tool = new RiskEventQueryTool();
            tool.mockEnabled = true;

            Map<String, Object> params = new HashMap<>();
            params.put("projectId", "PRJ-001");
            params.put("severity", "HIGH");

            ToolResult result = tool.execute(params, ctx());

            assertThat(result.isSuccess()).isTrue();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> events = (List<Map<String, Object>>) result.getData().get("events");
            // mock 数据 HIGH 级别有 2 个事件
            assertThat(events).hasSize(2);
        }

        @Test
        @DisplayName("mockEnabled=false 调用 fetchRealData，默认抛出 UnsupportedOperationException")
        void shouldThrowWhenDisabledAndRealDataNotImplemented() {
            RiskEventQueryTool tool = new RiskEventQueryTool();
            tool.mockEnabled = false;

            Map<String, Object> params = new HashMap<>();
            params.put("projectId", "PRJ-001");
            params.put("severity", "ALL");

            assertThatThrownBy(() -> tool.execute(params, ctx()))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("RiskEventQueryTool 真实数据源未实现")
                    .hasMessageContaining("pmis.agent.tool.mock-enabled=true");
        }

        @Test
        @DisplayName("mockEnabled=false 且子类覆盖 fetchRealData 时返回真实风险事件")
        void shouldReturnRealDataWhenSubclassOverridesFetchRealData() {
            RiskEventQueryTool tool = new RiskEventQueryTool() {
                @Override
                protected List<Map<String, Object>> fetchRealData(String projectId, String severity, AgentContext ctx) {
                    Map<String, Object> event = new LinkedHashMap<>();
                    event.put("name", "真实风险事件");
                    event.put("severity", "HIGH");
                    event.put("description", "来自真实数据源的风险事件");
                    return List.of(event);
                }
            };
            tool.mockEnabled = false;

            Map<String, Object> params = new HashMap<>();
            params.put("projectId", "PRJ-REAL");
            params.put("severity", "HIGH");

            ToolResult result = tool.execute(params, ctx());

            assertThat(result.isSuccess()).isTrue();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> events = (List<Map<String, Object>>) result.getData().get("events");
            assertThat(events).hasSize(1);
            assertThat(events.get(0).get("name")).isEqualTo("真实风险事件");
        }
    }

    // ==================== TimesheetStatTool 切换测试 ====================

    @Nested
    @DisplayName("TimesheetStatTool 数据源切换")
    class TimesheetStatToolSwitchTest {

        @Test
        @DisplayName("mockEnabled=true（默认）返回 mock 工时统计数据")
        void shouldReturnMockDataWhenEnabled() {
            TimesheetStatTool tool = new TimesheetStatTool();
            tool.mockEnabled = true;

            Map<String, Object> params = new HashMap<>();
            params.put("projectId", "PRJ-001");
            params.put("month", "2026-06");

            ToolResult result = tool.execute(params, ctx());

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData())
                    .containsKeys("overtimeCount", "missingCount", "abnormalCount", "totalHours");
            // mock 数据的 overtimeCount 固定为 8
            assertThat(result.getData().get("overtimeCount")).isEqualTo(8);
        }

        @Test
        @DisplayName("mockEnabled=false 调用 fetchRealData，默认抛出 UnsupportedOperationException")
        void shouldThrowWhenDisabledAndRealDataNotImplemented() {
            TimesheetStatTool tool = new TimesheetStatTool();
            tool.mockEnabled = false;

            Map<String, Object> params = new HashMap<>();
            params.put("projectId", "PRJ-001");
            params.put("month", "2026-06");

            assertThatThrownBy(() -> tool.execute(params, ctx()))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("TimesheetStatTool 真实数据源未实现")
                    .hasMessageContaining("pmis.agent.tool.mock-enabled=true");
        }

        @Test
        @DisplayName("mockEnabled=false 且子类覆盖 fetchRealData 时返回真实工时数据")
        void shouldReturnRealDataWhenSubclassOverridesFetchRealData() {
            TimesheetStatTool tool = new TimesheetStatTool() {
                @Override
                protected Map<String, Object> fetchRealData(String projectId, String month, AgentContext ctx) {
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("projectId", projectId);
                    data.put("month", month);
                    data.put("overtimeCount", 15);
                    data.put("missingCount", 6);
                    data.put("abnormalCount", 4);
                    data.put("totalHours", 480);
                    return data;
                }
            };
            tool.mockEnabled = false;

            Map<String, Object> params = new HashMap<>();
            params.put("projectId", "PRJ-REAL");
            params.put("month", "2026-06");

            ToolResult result = tool.execute(params, ctx());

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData().get("overtimeCount")).isEqualTo(15);
            assertThat(result.getData().get("totalHours")).isEqualTo(480);
        }
    }
}
