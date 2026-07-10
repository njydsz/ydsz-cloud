package com.njydsz.pmis.agent.tool;

import com.njydsz.pmis.agent.feign.ProjectServiceClient;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.api.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TimesheetStatTool 单元测试（P0-5）
 *
 * <p>验证工时异常统计工具的核心行为：mock 模式内置数据、真实数据模式 Feign 调用成功、
 * 服务降级返回零值、未注入 Client 的安全降级。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P0-5)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TimesheetStatTool 工时异常统计工具测试")
class TimesheetStatToolTest {

    @Mock
    private ProjectServiceClient projectServiceClient;

    private TimesheetStatTool tool;

    @BeforeEach
    void setUp() {
        tool = new TimesheetStatTool(projectServiceClient);
    }

    @Test
    @DisplayName("默认 mock 模式：返回内置模拟数据（加班8/漏报3/异常2/总工时320）")
    void testMockData_默认mock模式() {
        // mockEnabled 默认为 true，无需注入 Client
        Map<String, Object> params = new HashMap<>();
        params.put("projectId", "P001");
        params.put("month", "2026-07");

        ToolResult result = tool.execute(params, null);

        assertNotNull(result);
        Map<String, Object> data = result.getData();
        assertEquals(8, data.get("overtimeCount"));
        assertEquals(3, data.get("missingCount"));
        assertEquals(2, data.get("abnormalCount"));
        assertEquals(320, data.get("totalHours"));
    }

    @Test
    @DisplayName("真实数据模式：Feign 调用成功，返回远端统计值")
    void testRealData_Feign调用成功() {
        ReflectionTestUtils.setField(tool, "mockEnabled", false);

        // 构造远端返回的统计数据
        Map<String, Object> remote = new HashMap<>();
        remote.put("overtimeCount", 5);
        remote.put("missingCount", 1);
        remote.put("abnormalCount", 2);
        remote.put("totalHours", 200);
        when(projectServiceClient.timeEntryAbnormalStat("P001", "2026-07"))
                .thenReturn(Result.ok(remote));

        Map<String, Object> params = new HashMap<>();
        params.put("projectId", "P001");
        params.put("month", "2026-07");

        ToolResult result = tool.execute(params, null);

        Map<String, Object> data = result.getData();
        assertEquals(5, data.get("overtimeCount"));
        assertEquals(1, data.get("missingCount"));
        assertEquals(2, data.get("abnormalCount"));
        assertEquals(200, data.get("totalHours"));
        verify(projectServiceClient).timeEntryAbnormalStat("P001", "2026-07");
    }

    @Test
    @DisplayName("真实数据模式：Feign 服务降级，返回零值统计")
    void testRealData_Feign服务降级() {
        ReflectionTestUtils.setField(tool, "mockEnabled", false);

        when(projectServiceClient.timeEntryAbnormalStat("P001", "2026-07"))
                .thenReturn(Result.failed(BizErrorCode.SERVICE_UNAVAILABLE));

        Map<String, Object> params = new HashMap<>();
        params.put("projectId", "P001");
        params.put("month", "2026-07");

        ToolResult result = tool.execute(params, null);

        Map<String, Object> data = result.getData();
        assertEquals(0, data.get("overtimeCount"));
        assertEquals(0, data.get("missingCount"));
        assertEquals(0, data.get("abnormalCount"));
        assertEquals(0, data.get("totalHours"));
    }

    @Test
    @DisplayName("真实数据模式：未注入 Client，返回零值且不抛异常")
    void testRealData_未注入Client() {
        // 通过构造器显式传 null，模拟未注入场景
        TimesheetStatTool nullTool = new TimesheetStatTool(null);
        ReflectionTestUtils.setField(nullTool, "mockEnabled", false);

        Map<String, Object> params = new HashMap<>();
        params.put("projectId", "P001");
        params.put("month", "2026-07");

        ToolResult result = nullTool.execute(params, null);

        Map<String, Object> data = result.getData();
        assertEquals(0, data.get("overtimeCount"));
        assertEquals(0, data.get("missingCount"));
        assertEquals(0, data.get("abnormalCount"));
        assertEquals(0, data.get("totalHours"));
    }
}
