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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RiskEventQueryTool 单元测试（P0-5）
 *
 * <p>验证风险事件查询工具的核心行为：mock 模式按严重级别筛选、真实数据模式字段映射、
 * 服务降级返回空列表、severity=ALL 时不传 riskLevel 过滤参数。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P0-5)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RiskEventQueryTool 风险事件查询工具测试")
class RiskEventQueryToolTest {

    @Mock
    private ProjectServiceClient projectServiceClient;

    private RiskEventQueryTool tool;

    @BeforeEach
    void setUp() {
        tool = new RiskEventQueryTool(projectServiceClient);
    }

    @Test
    @DisplayName("mock 模式：ALL 返回 4 条风险事件")
    void testMockData_ALL() {
        List<Map<String, Object>> events = tool.fetchMockData("P001", "ALL");

        assertEquals(4, events.size());
    }

    @Test
    @DisplayName("mock 模式：HIGH 返回 2 条风险事件")
    void testMockData_HIGH() {
        List<Map<String, Object>> events = tool.fetchMockData("P001", "HIGH");

        assertEquals(2, events.size());
    }

    @Test
    @DisplayName("真实数据模式：riskTitle→name, riskLevel→severity 字段映射")
    void testRealData_字段映射() {
        ReflectionTestUtils.setField(tool, "mockEnabled", false);

        // 构造远端分页返回的单条风险记录
        Map<String, Object> record = new HashMap<>();
        record.put("riskTitle", "进度延期");
        record.put("riskLevel", "HIGH");
        record.put("description", "延期");
        Map<String, Object> pageData = new HashMap<>();
        pageData.put("records", List.of(record));
        pageData.put("total", 1L);

        when(projectServiceClient.riskPage(1, 100, "P001", "HIGH"))
                .thenReturn(Result.ok(pageData));

        List<Map<String, Object>> events = tool.fetchRealData("P001", "HIGH", null);

        assertEquals(1, events.size());
        assertEquals("进度延期", events.get(0).get("name"));
        assertEquals("HIGH", events.get(0).get("severity"));
        assertEquals("延期", events.get(0).get("description"));
    }

    @Test
    @DisplayName("真实数据模式：Feign 服务降级，返回空列表")
    void testRealData_服务降级() {
        ReflectionTestUtils.setField(tool, "mockEnabled", false);

        when(projectServiceClient.riskPage(1, 100, "P001", "HIGH"))
                .thenReturn(Result.failed(BizErrorCode.SERVICE_UNAVAILABLE));

        List<Map<String, Object>> events = tool.fetchRealData("P001", "HIGH", null);

        assertTrue(events.isEmpty());
    }

    @Test
    @DisplayName("真实数据模式：severity=ALL 时 riskLevel 参数传 null")
    void testRealData_ALL不传riskLevel() {
        ReflectionTestUtils.setField(tool, "mockEnabled", false);

        Map<String, Object> pageData = new HashMap<>();
        pageData.put("records", List.of());
        pageData.put("total", 0L);
        when(projectServiceClient.riskPage(eq(1), eq(100), eq("P001"), isNull()))
                .thenReturn(Result.ok(pageData));

        tool.fetchRealData("P001", "ALL", null);

        // 验证 riskPage 第 4 个参数（riskLevel）为 null
        verify(projectServiceClient).riskPage(eq(1), eq(100), eq("P001"), isNull());
    }
}
