package com.njydsz.pmis.workflow.service.impl;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.feign.AgentClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@link FlowAiAssistServiceImpl} P3-1 AI 能力扩展 单元测试。
 *
 * <p>覆盖 3 个新方法：predictRisk / smartRemind / predictSla。
 * 每个方法测试：成功调用 / params=null 降级 / Agent 失败降级 / Agent 异常降级。
 *
 * <p>注意：AgentClient 是 Feign 接口，通过 @Mock 注入。成功路径需 mock
 * {@code agentClient.execute(body)} 返回 {@code Result.ok(payload)}。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("P3-1: AI 能力扩展 - FlowAiAssistServiceImpl")
class FlowAiAssistServiceImplTest {

    @Mock
    private AgentClient agentClient;

    @InjectMocks
    private FlowAiAssistServiceImpl service;

    // ============== 辅助方法 ==============

    private Map<String, Object> payload(Map<String, Object> data) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("payload", data);
        return p;
    }

    private Map<String, Object> riskPayload(String level, double reject, double overdue) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("riskLevel", level);
        m.put("rejectProbability", reject);
        m.put("overdueProbability", overdue);
        m.put("reasons", List.of("历史驳回率高", "审批人负载重"));
        return m;
    }

    private Map<String, Object> remindPayload(String bestTime, String channel) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("bestTime", bestTime);
        m.put("channel", channel);
        m.put("message", "请在下班前处理任务 T001");
        m.put("reasons", List.of("审批人下午活跃", "任务已超期 2 小时"));
        return m;
    }

    private Map<String, Object> slaPayload(long durationMs, double confidence) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("estimatedDurationMs", durationMs);
        m.put("estimatedCompleteAt", "2026-07-08T18:00:00");
        m.put("confidence", confidence);
        m.put("reasons", List.of("历史平均耗时 2 小时", "当前节点负载正常"));
        return m;
    }

    // ============== predictRisk ==============

    @Nested
    @DisplayName("predictRisk")
    class PredictRiskTest {

        @Test
        @DisplayName("成功调用 → 返回 Agent payload")
        void successReturnsPayload() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("instanceId", "inst-001");
            params.put("flowCode", "leave_approval");
            when(agentClient.execute(any()))
                    .thenReturn(Result.ok(payload(riskPayload("HIGH", 0.7, 0.3))));

            Map<String, Object> result = service.predictRisk(params);

            assertEquals("HIGH", result.get("riskLevel"));
            assertEquals(0.7, result.get("rejectProbability"));
            assertEquals(0.3, result.get("overdueProbability"));
            // 验证 Agent 调用参数
            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
            verify(agentClient).execute(captor.capture());
            Map<String, Object> body = captor.getValue();
            assertEquals("RISK_PREDICT", body.get("agentType"));
            assertEquals("FLOW_INSTANCE", body.get("bizType"));
        }

        @Test
        @DisplayName("params 为 null → 降级返回 UNKNOWN")
        void nullParamsReturnsDefault() {
            Map<String, Object> result = service.predictRisk(null);

            assertEquals("UNKNOWN", result.get("riskLevel"));
            assertEquals(0.0, result.get("rejectProbability"));
            verify(agentClient, never()).execute(any());
        }

        @Test
        @DisplayName("Agent 返回 code != 0 → 降级返回 UNKNOWN")
        void agentFailureReturnsDefault() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("instanceId", "inst-001");
            when(agentClient.execute(any()))
                    .thenReturn(Result.fail("agent down"));

            Map<String, Object> result = service.predictRisk(params);

            assertEquals("UNKNOWN", result.get("riskLevel"));
        }

        @Test
        @DisplayName("Agent 返回 payload 非 Map → 降级返回 UNKNOWN")
        void nonMapPayloadReturnsDefault() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("flowCode", "leave");
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("payload", "not a map");
            when(agentClient.execute(any())).thenReturn(Result.ok(data));

            Map<String, Object> result = service.predictRisk(params);

            assertEquals("UNKNOWN", result.get("riskLevel"));
        }

        @Test
        @DisplayName("Agent 抛异常 → 降级返回 UNKNOWN")
        void agentExceptionReturnsDefault() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("instanceId", "inst-001");
            when(agentClient.execute(any()))
                    .thenThrow(new RuntimeException("Feign timeout"));

            Map<String, Object> result = service.predictRisk(params);

            assertEquals("UNKNOWN", result.get("riskLevel"));
            assertNotNull(result.get("reasons"));
        }
    }

    // ============== smartRemind ==============

    @Nested
    @DisplayName("smartRemind")
    class SmartRemindTest {

        @Test
        @DisplayName("成功调用 → 返回 Agent payload")
        void successReturnsPayload() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("taskId", "task-001");
            params.put("assigneeId", "user-001");
            when(agentClient.execute(any()))
                    .thenReturn(Result.ok(payload(remindPayload("AFTERNOON", "IN_APP"))));

            Map<String, Object> result = service.smartRemind(params);

            assertEquals("AFTERNOON", result.get("bestTime"));
            assertEquals("IN_APP", result.get("channel"));
            assertNotNull(result.get("message"));
            // 验证 Agent 调用参数
            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
            verify(agentClient).execute(captor.capture());
            Map<String, Object> body = captor.getValue();
            assertEquals("SMART_REMIND", body.get("agentType"));
            assertEquals("FLOW_TASK", body.get("bizType"));
        }

        @Test
        @DisplayName("params 为 null → 降级返回 IMMEDIATE")
        void nullParamsReturnsDefault() {
            Map<String, Object> result = service.smartRemind(null);

            assertEquals("IMMEDIATE", result.get("bestTime"));
            assertEquals("IN_APP", result.get("channel"));
            verify(agentClient, never()).execute(any());
        }

        @Test
        @DisplayName("Agent 返回 code != 0 → 降级返回 IMMEDIATE")
        void agentFailureReturnsDefault() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("taskId", "task-001");
            params.put("assigneeId", "user-001");
            when(agentClient.execute(any()))
                    .thenReturn(Result.fail("agent down"));

            Map<String, Object> result = service.smartRemind(params);

            assertEquals("IMMEDIATE", result.get("bestTime"));
        }

        @Test
        @DisplayName("Agent 抛异常 → 降级返回 IMMEDIATE")
        void agentExceptionReturnsDefault() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("taskId", "task-001");
            params.put("assigneeId", "user-001");
            when(agentClient.execute(any()))
                    .thenThrow(new RuntimeException("Feign timeout"));

            Map<String, Object> result = service.smartRemind(params);

            assertEquals("IMMEDIATE", result.get("bestTime"));
        }
    }

    // ============== predictSla ==============

    @Nested
    @DisplayName("predictSla")
    class PredictSlaTest {

        @Test
        @DisplayName("成功调用 → 返回 Agent payload")
        void successReturnsPayload() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("instanceId", "inst-001");
            params.put("flowCode", "leave_approval");
            when(agentClient.execute(any()))
                    .thenReturn(Result.ok(payload(slaPayload(3600000L, 0.85))));

            Map<String, Object> result = service.predictSla(params);

            assertEquals(3600000L, result.get("estimatedDurationMs"));
            assertEquals(0.85, result.get("confidence"));
            assertNotNull(result.get("estimatedCompleteAt"));
            // 验证 Agent 调用参数
            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
            verify(agentClient).execute(captor.capture());
            Map<String, Object> body = captor.getValue();
            assertEquals("SLA_PREDICT", body.get("agentType"));
            assertEquals("FLOW_INSTANCE", body.get("bizType"));
        }

        @Test
        @DisplayName("params 为 null → 降级返回 confidence=0.0")
        void nullParamsReturnsDefault() {
            Map<String, Object> result = service.predictSla(null);

            assertEquals(0L, result.get("estimatedDurationMs"));
            assertEquals(0.0, result.get("confidence"));
            assertNull(result.get("estimatedCompleteAt"));
            verify(agentClient, never()).execute(any());
        }

        @Test
        @DisplayName("Agent 返回 code != 0 → 降级返回 confidence=0.0")
        void agentFailureReturnsDefault() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("instanceId", "inst-001");
            when(agentClient.execute(any()))
                    .thenReturn(Result.fail("agent down"));

            Map<String, Object> result = service.predictSla(params);

            assertEquals(0.0, result.get("confidence"));
        }

        @Test
        @DisplayName("Agent 抛异常 → 降级返回 confidence=0.0")
        void agentExceptionReturnsDefault() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("instanceId", "inst-001");
            when(agentClient.execute(any()))
                    .thenThrow(new RuntimeException("Feign timeout"));

            Map<String, Object> result = service.predictSla(params);

            assertEquals(0.0, result.get("confidence"));
        }
    }
}
