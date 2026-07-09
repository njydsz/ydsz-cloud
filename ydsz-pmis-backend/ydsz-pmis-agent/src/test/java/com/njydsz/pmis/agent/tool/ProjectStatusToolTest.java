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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * ProjectStatusTool 单元测试（P0-5）
 *
 * <p>验证项目指标查询工具的核心行为：mock 模式内置数据、真实数据模式 EVM + 风险分页聚合、
 * EVM 降级（CPI/SPI 归零）、全部降级返回零值。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P0-5)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProjectStatusTool 项目指标查询工具测试")
class ProjectStatusToolTest {

    @Mock
    private ProjectServiceClient projectServiceClient;

    private ProjectStatusTool tool;

    @BeforeEach
    void setUp() {
        tool = new ProjectStatusTool();
    }

    @Test
    @DisplayName("mock 模式：返回内置模拟指标数据")
    void testMockData() {
        Map<String, Object> data = tool.fetchMockData("P001");

        assertEquals(0.80, data.get("cpi"));
        assertEquals(0.90, data.get("spi"));
        assertEquals(0.25, data.get("costOverrunRatio"));
        assertEquals(5, data.get("riskEventCount"));
        assertEquals(-0.10, data.get("marginRatio"));
    }

    @Test
    @DisplayName("真实数据模式：EVM + 风险分页正常聚合")
    void testRealData_正常聚合() {
        ReflectionTestUtils.setField(tool, "mockEnabled", false);
        ReflectionTestUtils.setField(tool, "projectServiceClient", projectServiceClient);

        // EVM 仪表盘返回 CPI/SPI
        Map<String, Object> dash = new HashMap<>();
        dash.put("latestCpi", 0.8);
        dash.put("latestSpi", 0.9);
        when(projectServiceClient.evmDashboard("P001")).thenReturn(Result.ok(dash));

        // 风险分页返回 total=5
        Map<String, Object> riskPage = new HashMap<>();
        riskPage.put("total", 5L);
        when(projectServiceClient.riskPage(eq(1), eq(1), eq("P001"), isNull()))
                .thenReturn(Result.ok(riskPage));

        Map<String, Object> data = tool.fetchRealData("P001", null);

        // CPI/SPI 来自 EVM 仪表盘
        assertEquals(0.8, ((Double) data.get("cpi")), 0.0001);
        assertEquals(0.9, ((Double) data.get("spi")), 0.0001);
        // costOverrunRatio = 1/CPI - 1 = 1/0.8 - 1 = 0.25
        assertEquals(0.25, ((Double) data.get("costOverrunRatio")), 0.0001);
        // riskEventCount 来自风险分页 total
        assertEquals(5, data.get("riskEventCount"));
        // marginRatio 需收入数据，EVM 无收入，保持 0.0
        assertEquals(0.0, ((Double) data.get("marginRatio")), 0.0001);
    }

    @Test
    @DisplayName("真实数据模式：EVM 降级，CPI/SPI 归零，风险事件数正常")
    void testRealData_EVM降级() {
        ReflectionTestUtils.setField(tool, "mockEnabled", false);
        ReflectionTestUtils.setField(tool, "projectServiceClient", projectServiceClient);

        // EVM 仪表盘降级
        when(projectServiceClient.evmDashboard("P001"))
                .thenReturn(Result.failed(BizErrorCode.SERVICE_UNAVAILABLE));

        // 风险分页正常
        Map<String, Object> riskPage = new HashMap<>();
        riskPage.put("total", 5L);
        when(projectServiceClient.riskPage(eq(1), eq(1), eq("P001"), isNull()))
                .thenReturn(Result.ok(riskPage));

        Map<String, Object> data = tool.fetchRealData("P001", null);

        // EVM 降级后 CPI/SPI 保持零值
        assertEquals(0.0, ((Double) data.get("cpi")), 0.0001);
        assertEquals(0.0, ((Double) data.get("spi")), 0.0001);
        // 风险事件数不受影响
        assertEquals(5, data.get("riskEventCount"));
    }

    @Test
    @DisplayName("真实数据模式：EVM 与风险分页均降级，全零值")
    void testRealData_全部降级() {
        ReflectionTestUtils.setField(tool, "mockEnabled", false);
        ReflectionTestUtils.setField(tool, "projectServiceClient", projectServiceClient);

        when(projectServiceClient.evmDashboard("P001"))
                .thenReturn(Result.failed(BizErrorCode.SERVICE_UNAVAILABLE));
        when(projectServiceClient.riskPage(eq(1), eq(1), eq("P001"), isNull()))
                .thenReturn(Result.failed(BizErrorCode.SERVICE_UNAVAILABLE));

        Map<String, Object> data = tool.fetchRealData("P001", null);

        assertEquals(0.0, ((Double) data.get("cpi")), 0.0001);
        assertEquals(0.0, ((Double) data.get("spi")), 0.0001);
        assertEquals(0, data.get("riskEventCount"));
        assertEquals(0.0, ((Double) data.get("costOverrunRatio")), 0.0001);
        assertEquals(0.0, ((Double) data.get("marginRatio")), 0.0001);
    }
}
