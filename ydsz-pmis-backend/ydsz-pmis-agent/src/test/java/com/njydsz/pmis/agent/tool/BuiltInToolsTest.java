package com.njydsz.pmis.agent.tool;

import com.njydsz.pmis.agent.engine.AgentContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 内置 Agent 工具单元测试（P1-1 落地）
 *
 * <p>覆盖 4 个内置工具：
 * <ul>
 *   <li>{@link ProjectStatusTool}  - 项目指标查询（CPI/SPI/成本超支率/风险事件数/利润率）</li>
 *   <li>{@link RiskEventQueryTool} - 风险事件查询（按严重级别筛选）</li>
 *   <li>{@link TimesheetStatTool}  - 工时异常统计（超时/漏报/异常打卡）</li>
 *   <li>{@link BpmnValidatorTool}  - BPMN 2.0 XML 结构完整性校验</li>
 * </ul>
 *
 * <p>测试策略：工具为纯对象，每个测试方法直接 new 实例，不依赖 Spring 容器；
 * {@link AgentContext} 直接构造并设置 bizRef / traceId 等必要字段。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P1-1)
 */
@DisplayName("内置 Agent 工具测试")
class BuiltInToolsTest {

    // ==================== 辅助方法 ====================

    /** 构造 AgentContext，bizRef 用于工具兜底逻辑测试 */
    private AgentContext ctx(String bizRef) {
        AgentContext ctx = new AgentContext();
        ctx.setBizType("project");
        ctx.setBizId("P001");
        ctx.setBizRef(bizRef);
        ctx.setCallerId("U001");
        ctx.setCallerName("张三");
        ctx.setSource("unit-test");
        ctx.setTraceId("trace-001");
        return ctx;
    }

    // ==================== ProjectStatusTool 测试 ====================

    @Nested
    @DisplayName("ProjectStatusTool 项目指标查询工具测试")
    class ProjectStatusToolTest {

        @Test
        @DisplayName("name() 返回 project_status")
        void shouldReturnCorrectName() {
            ProjectStatusTool tool = new ProjectStatusTool();
            assertThat(tool.name()).isEqualTo("project_status");
        }

        @Test
        @DisplayName("description() 不为空")
        void shouldReturnNonBlankDescription() {
            ProjectStatusTool tool = new ProjectStatusTool();
            assertThat(tool.description()).isNotBlank();
        }

        @Test
        @DisplayName("parameterSchema() 包含 projectId")
        void shouldContainProjectIdInSchema() {
            ProjectStatusTool tool = new ProjectStatusTool();
            assertThat(tool.parameterSchema()).containsKey("projectId");
        }

        @Test
        @DisplayName("execute 返回成功结果，data 包含 cpi/spi/costOverrunRatio/riskEventCount/marginRatio")
        void shouldReturnSuccessWithMetrics() {
            ProjectStatusTool tool = new ProjectStatusTool();
            Map<String, Object> params = new HashMap<>();
            params.put("projectId", "PRJ-001");

            ToolResult result = tool.execute(params, ctx("PRJ-001"));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData())
                    .containsKeys("cpi", "spi", "costOverrunRatio", "riskEventCount", "marginRatio");
            assertThat(result.getOutput()).isNotBlank();
        }

        @Test
        @DisplayName("projectId 参数为空时使用 ctx.getBizRef() 兜底，不抛异常")
        void shouldFallbackToBizRefWhenProjectIdBlank() {
            ProjectStatusTool tool = new ProjectStatusTool();
            Map<String, Object> params = new HashMap<>();
            params.put("projectId", null);

            ToolResult result = tool.execute(params, ctx("PRJ-FALLBACK"));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData().get("projectId")).isEqualTo("PRJ-FALLBACK");
        }
    }

    // ==================== RiskEventQueryTool 测试 ====================

    @Nested
    @DisplayName("RiskEventQueryTool 风险事件查询工具测试")
    class RiskEventQueryToolTest {

        @Test
        @DisplayName("name() 返回 risk_events")
        void shouldReturnCorrectName() {
            RiskEventQueryTool tool = new RiskEventQueryTool();
            assertThat(tool.name()).isEqualTo("risk_events");
        }

        @Test
        @DisplayName("parameterSchema() 包含 projectId 和 severity")
        void shouldContainProjectIdAndSeverityInSchema() {
            RiskEventQueryTool tool = new RiskEventQueryTool();
            assertThat(tool.parameterSchema()).containsKeys("projectId", "severity");
        }

        @Test
        @DisplayName("execute 返回成功结果，data 包含 events 列表")
        void shouldReturnSuccessWithEventsList() {
            RiskEventQueryTool tool = new RiskEventQueryTool();
            Map<String, Object> params = new HashMap<>();
            params.put("projectId", "PRJ-001");
            params.put("severity", "ALL");

            ToolResult result = tool.execute(params, ctx("PRJ-001"));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData()).containsKey("events");
            assertThat(result.getData().get("events")).isInstanceOf(List.class);
        }

        @Test
        @DisplayName("severity=ALL 返回所有级别事件")
        void shouldReturnAllEventsWhenSeverityAll() {
            RiskEventQueryTool tool = new RiskEventQueryTool();
            Map<String, Object> params = new HashMap<>();
            params.put("projectId", "PRJ-001");
            params.put("severity", "ALL");

            ToolResult result = tool.execute(params, ctx("PRJ-001"));

            List<?> events = (List<?>) result.getData().get("events");
            // ALL 应包含 HIGH(2) + MEDIUM(1) + LOW(1) = 4 个事件
            assertThat(events).hasSize(4);
            assertThat(result.getData().get("total")).isEqualTo(4);
            assertThat(result.getData().get("severity")).isEqualTo("ALL");
        }

        @Test
        @DisplayName("severity=HIGH 只返回高风险事件")
        void shouldReturnOnlyHighSeverityEvents() {
            RiskEventQueryTool tool = new RiskEventQueryTool();
            Map<String, Object> params = new HashMap<>();
            params.put("projectId", "PRJ-001");
            params.put("severity", "HIGH");

            ToolResult result = tool.execute(params, ctx("PRJ-001"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> events =
                    (List<Map<String, Object>>) result.getData().get("events");
            assertThat(events).hasSize(2);
            assertThat(result.getData().get("total")).isEqualTo(2);
            assertThat(events).extracting(e -> e.get("severity")).containsOnly("HIGH");
        }

        @Test
        @DisplayName("parameters=null 时不抛 NPE，使用 ctx.bizRef 兜底并返回成功结果")
        void shouldNotThrowWhenParametersNull() {
            RiskEventQueryTool tool = new RiskEventQueryTool();

            ToolResult result = tool.execute(null, ctx("PRJ-FALLBACK"));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData().get("projectId")).isEqualTo("PRJ-FALLBACK");
            assertThat(result.getData().get("severity")).isEqualTo("ALL");
        }

        @Test
        @DisplayName("projectId 为空时使用 ctx.getBizRef() 兜底")
        void shouldFallbackToBizRefWhenProjectIdBlank() {
            RiskEventQueryTool tool = new RiskEventQueryTool();
            Map<String, Object> params = new HashMap<>();
            params.put("projectId", null);

            ToolResult result = tool.execute(params, ctx("PRJ-FALLBACK"));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData().get("projectId")).isEqualTo("PRJ-FALLBACK");
        }
    }

    // ==================== TimesheetStatTool 测试 ====================

    @Nested
    @DisplayName("TimesheetStatTool 工时异常统计工具测试")
    class TimesheetStatToolTest {

        @Test
        @DisplayName("name() 返回 timesheet_stat")
        void shouldReturnCorrectName() {
            TimesheetStatTool tool = new TimesheetStatTool();
            assertThat(tool.name()).isEqualTo("timesheet_stat");
        }

        @Test
        @DisplayName("parameterSchema() 包含 projectId 和 month")
        void shouldContainProjectIdAndMonthInSchema() {
            TimesheetStatTool tool = new TimesheetStatTool();
            assertThat(tool.parameterSchema()).containsKeys("projectId", "month");
        }

        @Test
        @DisplayName("execute 返回成功结果，data 包含 overtimeCount/missingCount/abnormalCount/totalHours")
        void shouldReturnSuccessWithTimesheetMetrics() {
            TimesheetStatTool tool = new TimesheetStatTool();
            Map<String, Object> params = new HashMap<>();
            params.put("projectId", "PRJ-001");
            params.put("month", "2026-06");

            ToolResult result = tool.execute(params, ctx("PRJ-001"));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData())
                    .containsKeys("overtimeCount", "missingCount", "abnormalCount", "totalHours");
            assertThat(result.getData().get("month")).isEqualTo("2026-06");
        }

        @Test
        @DisplayName("month 参数为空时默认当前月")
        void shouldDefaultToCurrentMonthWhenMonthBlank() {
            TimesheetStatTool tool = new TimesheetStatTool();
            Map<String, Object> params = new HashMap<>();
            params.put("projectId", "PRJ-001");
            params.put("month", null);

            ToolResult result = tool.execute(params, ctx("PRJ-001"));

            String expectedMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData().get("month")).isEqualTo(expectedMonth);
        }
    }

    // ==================== BpmnValidatorTool 测试 ====================

    @Nested
    @DisplayName("BpmnValidatorTool BPMN XML 校验工具测试")
    class BpmnValidatorToolTest {

        /** 完整的 BPMN XML（含 definitions/process/startEvent/endEvent 全部必须标签） */
        private static final String VALID_BPMN_XML =
                "<bpmn:definitions>"
                        + "<bpmn:process id=\"p1\">"
                        + "<bpmn:startEvent id=\"s1\"/>"
                        + "<bpmn:endEvent id=\"e1\"/>"
                        + "</bpmn:process>"
                        + "</bpmn:definitions>";

        @Test
        @DisplayName("name() 返回 bpmn_validate")
        void shouldReturnCorrectName() {
            BpmnValidatorTool tool = new BpmnValidatorTool();
            assertThat(tool.name()).isEqualTo("bpmn_validate");
        }

        @Test
        @DisplayName("parameterSchema() 包含 bpmnXml")
        void shouldContainBpmnXmlInSchema() {
            BpmnValidatorTool tool = new BpmnValidatorTool();
            assertThat(tool.parameterSchema()).containsKey("bpmnXml");
        }

        @Test
        @DisplayName("完整 BPMN XML 返回 valid=true")
        void shouldReturnValidWhenXmlComplete() {
            BpmnValidatorTool tool = new BpmnValidatorTool();
            Map<String, Object> params = new HashMap<>();
            params.put("bpmnXml", VALID_BPMN_XML);

            ToolResult result = tool.execute(params, ctx("PRJ-001"));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData().get("valid")).isEqualTo(Boolean.TRUE);
            @SuppressWarnings("unchecked")
            List<String> missing = (List<String>) result.getData().get("missingElements");
            assertThat(missing).isEmpty();
        }

        @Test
        @DisplayName("缺少结束标签返回 valid=false，missingElements 包含对应元素")
        void shouldReturnInvalidWhenClosingTagMissing() {
            BpmnValidatorTool tool = new BpmnValidatorTool();
            // 缺少 </bpmn:definitions> 结束标签
            String invalidXml = "<bpmn:definitions>"
                    + "<bpmn:process id=\"p1\">"
                    + "<bpmn:startEvent id=\"s1\"/>"
                    + "<bpmn:endEvent id=\"e1\"/>"
                    + "</bpmn:process>";
            Map<String, Object> params = new HashMap<>();
            params.put("bpmnXml", invalidXml);

            ToolResult result = tool.execute(params, ctx("PRJ-001"));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData().get("valid")).isEqualTo(Boolean.FALSE);
            @SuppressWarnings("unchecked")
            List<String> missing = (List<String>) result.getData().get("missingElements");
            assertThat(missing).contains("</bpmn:definitions>");
        }

        @Test
        @DisplayName("bpmnXml 为空返回 failure 结果")
        void shouldReturnFailureWhenBpmnXmlBlank() {
            BpmnValidatorTool tool = new BpmnValidatorTool();
            Map<String, Object> params = new HashMap<>();
            params.put("bpmnXml", "");

            ToolResult result = tool.execute(params, ctx("PRJ-001"));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getError()).isNotBlank();
        }
    }
}
