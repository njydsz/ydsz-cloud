package com.njydsz.pmis.scheduler.handler;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.feign.ExecutionClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("BillableUtilizationJobHandler 定时任务测试")
@SuppressWarnings("unchecked")
class BillableUtilizationJobHandlerTest {

    private ExecutionClient executionClient;
    private BillableUtilizationJobHandler handler;

    @BeforeEach
    void setUp() {
        executionClient = mock(ExecutionClient.class);
        handler = new BillableUtilizationJobHandler(executionClient);
    }

    @Test
    @DisplayName("正常调用：period=2026-06 recomputeAll=false")
    void execute_normal() throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put("ok", true);
        data.put("affectedCount", 30);
        when(executionClient.recomputeBillableUtilization(eq("2026-06"), anyBoolean()))
                .thenReturn(Result.ok(data));

        Object r = handler.execute("{\"period\":\"2026-06\",\"recomputeAll\":false}");
        assertThat(r).isInstanceOf(Map.class);
        Map<String, Object> m = (Map<String, Object>) r;
        assertThat(m.get("ok")).isEqualTo(true);
        assertThat(m.get("period")).isEqualTo("2026-06");
        assertThat(m.get("recomputeAll")).isEqualTo(false);
        assertThat(m.get("affectedCount")).isEqualTo(30);
    }

    @Test
    @DisplayName("recomputeAll=true 强制重算")
    void execute_recomputeAll() throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put("ok", true);
        data.put("affectedCount", 0);
        when(executionClient.recomputeBillableUtilization(eq("2026-05"), eq(true)))
                .thenReturn(Result.ok(data));

        Object r = handler.execute("{\"period\":\"2026-05\",\"recomputeAll\":true}");
        Map<String, Object> m = (Map<String, Object>) r;
        assertThat(m.get("recomputeAll")).isEqualTo(true);
    }

    @Test
    @DisplayName("参数为空时默认上一月")
    void execute_defaultPeriod() throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put("ok", true);
        when(executionClient.recomputeBillableUtilization(anyString(), anyBoolean()))
                .thenReturn(Result.ok(data));

        Object r = handler.execute(null);
        Map<String, Object> m = (Map<String, Object>) r;
        assertThat(m.get("period")).isNotNull();
        // 格式 yyyy-MM
        assertThat(((String) m.get("period"))).matches("\\d{4}-\\d{2}");
    }

    @Test
    @DisplayName("参数 JSON 解析失败时使用默认 period")
    void execute_badJson() throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put("ok", true);
        when(executionClient.recomputeBillableUtilization(anyString(), anyBoolean()))
                .thenReturn(Result.ok(data));

        Object r = handler.execute("not a json {");
        Map<String, Object> m = (Map<String, Object>) r;
        assertThat(m.get("period")).isNotNull();
    }

    @Test
    @DisplayName("Feign 异常时任务仍返回 ok=false 不抛异常")
    void execute_feignException() throws Exception {
        when(executionClient.recomputeBillableUtilization(anyString(), anyBoolean()))
                .thenThrow(new RuntimeException("network down"));

        Object r = handler.execute("{\"period\":\"2026-06\"}");
        Map<String, Object> m = (Map<String, Object>) r;
        assertThat(m.get("ok")).isEqualTo(false);
        assertThat(m.get("error")).asString().contains("network down");
    }

    @Test
    @DisplayName("data 为空时仍返回基础结果")
    void execute_emptyData() throws Exception {
        when(executionClient.recomputeBillableUtilization(anyString(), anyBoolean()))
                .thenReturn(Result.ok(new HashMap<>()));

        Object r = handler.execute("{\"period\":\"2026-06\"}");
        Map<String, Object> m = (Map<String, Object>) r;
        assertThat(m.get("ok")).isEqualTo(true);
        assertThat(m.get("period")).isEqualTo("2026-06");
    }
}
