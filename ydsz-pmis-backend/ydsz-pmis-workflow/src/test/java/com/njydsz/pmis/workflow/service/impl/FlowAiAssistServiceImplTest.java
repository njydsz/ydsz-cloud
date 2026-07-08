package com.njydsz.pmis.workflow.service.impl;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.feign.AgentClient;
import com.njydsz.pmis.workflow.entity.FlowAiFeedbackDO;
import com.njydsz.pmis.workflow.mapper.FlowAiFeedbackMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * {@link FlowAiAssistServiceImpl} 单元测试。
 *
 * <p>P3-1: 覆盖 3 个 AI 方法：predictRisk / smartRemind / predictSla。
 * 每个方法测试：成功调用 / params=null 降级 / Agent 失败降级 / Agent 异常降级。
 *
 * <p>P3-2: 新增 {@link CacheTest} 验证 Caffeine 本地缓存逻辑：
 * 缓存命中跳过 Agent 调用 / 降级结果不缓存 / 不同参数不命中。
 *
 * <p>P3-3: 新增 {@link FeedbackTest} 验证推荐审批人反馈闭环：
 * 记录反馈 / 参数校验 / 统计查询 / recommendApprovers 返回 traceId+rank。
 *
 * <p>注意：AgentClient 是 Feign 接口，通过 @Mock 注入。成功路径需 mock
 * {@code agentClient.execute(body)} 返回 {@code Result.ok(payload)}。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FlowAiAssistServiceImpl - P3-1 AI 能力 + P3-2 缓存 + P3-3 反馈闭环")
class FlowAiAssistServiceImplTest {

    @Mock
    private AgentClient agentClient;

    @Mock
    private FlowAiFeedbackMapper feedbackMapper;

    @InjectMocks
    private FlowAiAssistServiceImpl service;

    /**
     * P3-2: 每个测试前清除 AI 缓存，避免测试间缓存累积导致 verify 失败。
     */
    @BeforeEach
    void clearAiCache() {
        service.evictAllAiCache();
    }

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
        @SuppressWarnings("unchecked")
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
        @SuppressWarnings("unchecked")
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
        @SuppressWarnings("unchecked")
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

    // ============== P3-2: AI 调用缓存 ==============

    @Nested
    @DisplayName("P3-2: AI 调用缓存")
    class CacheTest {

        @Test
        @DisplayName("相同参数第二次调用 → 命中缓存，Agent 只调用一次")
        void cacheHitSkipsAgentCall() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("instanceId", "inst-cache-001");
            params.put("flowCode", "leave");
            when(agentClient.execute(any()))
                    .thenReturn(Result.ok(payload(riskPayload("HIGH", 0.8, 0.2))));

            // 第一次调用：未命中缓存，调用 Agent
            Map<String, Object> r1 = service.predictRisk(params);
            assertEquals("HIGH", r1.get("riskLevel"));

            // 第二次调用：命中缓存，不调用 Agent
            Map<String, Object> r2 = service.predictRisk(params);
            assertEquals("HIGH", r2.get("riskLevel"));

            // Agent 只被调用一次（缓存命中跳过第二次）
            verify(agentClient, times(1)).execute(any());
        }

        @Test
        @DisplayName("降级结果不缓存 → Agent 失败后再次调用仍触发 Agent")
        void degradedResultNotCached() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("instanceId", "inst-degrade-001");
            when(agentClient.execute(any()))
                    .thenReturn(Result.fail("agent down"));

            // 第一次调用：Agent 失败，返回降级结果（不缓存）
            Map<String, Object> r1 = service.predictRisk(params);
            assertEquals("UNKNOWN", r1.get("riskLevel"));

            // 第二次调用：降级结果未缓存，仍调用 Agent
            Map<String, Object> r2 = service.predictRisk(params);
            assertEquals("UNKNOWN", r2.get("riskLevel"));

            // Agent 被调用两次（降级结果不缓存）
            verify(agentClient, times(2)).execute(any());
        }

        @Test
        @DisplayName("不同参数不命中缓存 → Agent 各调用一次")
        void differentParamsNotHitCache() {
            Map<String, Object> params1 = new LinkedHashMap<>();
            params1.put("instanceId", "inst-a");
            Map<String, Object> params2 = new LinkedHashMap<>();
            params2.put("instanceId", "inst-b");
            when(agentClient.execute(any()))
                    .thenReturn(Result.ok(payload(riskPayload("LOW", 0.1, 0.1))));

            service.predictRisk(params1);
            service.predictRisk(params2);

            // 两个不同参数各调用一次 Agent
            verify(agentClient, times(2)).execute(any());
        }

        @Test
        @DisplayName("smartRemind 缓存命中 → Agent 只调用一次")
        void smartRemindCacheHit() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("taskId", "task-cache-001");
            params.put("assigneeId", "user-001");
            when(agentClient.execute(any()))
                    .thenReturn(Result.ok(payload(remindPayload("AFTERNOON", "IN_APP"))));

            Map<String, Object> r1 = service.smartRemind(params);
            Map<String, Object> r2 = service.smartRemind(params);

            assertEquals("AFTERNOON", r1.get("bestTime"));
            assertEquals("AFTERNOON", r2.get("bestTime"));
            verify(agentClient, times(1)).execute(any());
        }

        @Test
        @DisplayName("predictSla 缓存命中 → Agent 只调用一次")
        void predictSlaCacheHit() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("instanceId", "inst-sla-cache");
            params.put("flowCode", "leave");
            when(agentClient.execute(any()))
                    .thenReturn(Result.ok(payload(slaPayload(7200000L, 0.9))));

            Map<String, Object> r1 = service.predictSla(params);
            Map<String, Object> r2 = service.predictSla(params);

            assertEquals(7200000L, r1.get("estimatedDurationMs"));
            assertEquals(7200000L, r2.get("estimatedDurationMs"));
            verify(agentClient, times(1)).execute(any());
        }

        @Test
        @DisplayName("evictAllAiCache → 清除后再次调用触发 Agent")
        void evictAllTriggersAgentAgain() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("instanceId", "inst-evict-001");
            when(agentClient.execute(any()))
                    .thenReturn(Result.ok(payload(riskPayload("MEDIUM", 0.5, 0.3))));

            // 第一次调用：缓存结果
            service.predictRisk(params);
            // 手动清除缓存
            service.evictAllAiCache();
            // 第二次调用：缓存已清空，重新调用 Agent
            service.predictRisk(params);

            verify(agentClient, times(2)).execute(any());
        }
    }

    // ============== P3-3: 推荐审批人反馈闭环 ==============

    @Nested
    @DisplayName("P3-3: 推荐审批人反馈闭环")
    class FeedbackTest {

        // ---------- recommendApprovers 增强 traceId+rank ----------

        @Test
        @DisplayName("recommendApprovers 返回结果携带 traceId 和 rank")
        void recommendApproversReturnsTraceIdAndRank() {
            Map<String, Object> ctx = new LinkedHashMap<>();
            ctx.put("taskId", "task-001");
            ctx.put("flowCode", "leave");

            Map<String, Object> candidate1 = new LinkedHashMap<>();
            candidate1.put("userId", "u001");
            candidate1.put("name", "张三");
            Map<String, Object> candidate2 = new LinkedHashMap<>();
            candidate2.put("userId", "u002");
            candidate2.put("name", "李四");

            Map<String, Object> payloadData = new LinkedHashMap<>();
            Map<String, Object> top1 = new LinkedHashMap<>();
            top1.put("userId", "u001");
            top1.put("_score", 0.9);
            Map<String, Object> top2 = new LinkedHashMap<>();
            top2.put("userId", "u002");
            top2.put("_score", 0.8);
            payloadData.put("top", List.of(top1, top2));

            when(agentClient.execute(any())).thenReturn(Result.ok(payload(payloadData)));

            List<Map<String, Object>> result = service.recommendApprovers(ctx,
                    List.of(candidate1, candidate2), 3);

            assertEquals(2, result.size());
            // 第一项 rank=1
            assertNotNull(result.get(0).get("traceId"));
            assertEquals(1, result.get(0).get("rank"));
            assertEquals("u001", result.get(0).get("userId"));
            // 第二项 rank=2
            assertNotNull(result.get(1).get("traceId"));
            assertEquals(2, result.get(1).get("rank"));
            assertEquals("u002", result.get(1).get("userId"));
            // 同一次调用共享同一 traceId
            assertEquals(result.get(0).get("traceId"), result.get(1).get("traceId"));
        }

        @Test
        @DisplayName("recommendApprovers 无候选 → 返回空列表（无 traceId）")
        void recommendApproversNoCandidatesReturnsEmpty() {
            List<Map<String, Object>> result = service.recommendApprovers(
                    new LinkedHashMap<>(), List.of(), 3);
            assertTrue(result.isEmpty());
            verify(agentClient, never()).execute(any());
        }

        // ---------- recordApproverFeedback ----------

        @Test
        @DisplayName("recordApproverFeedback 成功记录 → 返回反馈 ID")
        void recordFeedbackSuccess() {
            Map<String, Object> feedback = new LinkedHashMap<>();
            feedback.put("traceId", "trace-001");
            feedback.put("recommendedUserId", "u001");
            feedback.put("action", "ACCEPTED");
            feedback.put("taskId", "task-001");
            feedback.put("flowCode", "leave");
            feedback.put("recommendedScore", 0.85);
            feedback.put("recommendedRank", 1);
            // mock insert 会通过 MyBatis-Plus 设置 ID（这里模拟不了，验证调用即可）
            when(feedbackMapper.insert(any(FlowAiFeedbackDO.class))).thenReturn(1);

            service.recordApproverFeedback(feedback);

            // 验证 insert 被调用，且参数正确
            ArgumentCaptor<FlowAiFeedbackDO> captor = ArgumentCaptor.forClass(FlowAiFeedbackDO.class);
            verify(feedbackMapper).insert(captor.capture());
            FlowAiFeedbackDO entity = captor.getValue();
            assertEquals("trace-001", entity.getTraceId());
            assertEquals("u001", entity.getRecommendedUserId());
            assertEquals("ACCEPTED", entity.getAction());
            assertEquals("task-001", entity.getTaskId());
            assertEquals("leave", entity.getFlowCode());
            assertNotNull(entity.getRecommendedScore());
            assertEquals(1, entity.getRecommendedRank());
        }

        @Test
        @DisplayName("recordApproverFeedback 缺少 traceId → 抛 BizException")
        void recordFeedbackMissingTraceIdThrows() {
            Map<String, Object> feedback = new LinkedHashMap<>();
            feedback.put("recommendedUserId", "u001");
            feedback.put("action", "ACCEPTED");

            assertThrows(BizException.class, () -> service.recordApproverFeedback(feedback));
            verify(feedbackMapper, never()).insert(any(FlowAiFeedbackDO.class));
        }

        @Test
        @DisplayName("recordApproverFeedback 缺少 recommendedUserId → 抛 BizException")
        void recordFeedbackMissingUserIdThrows() {
            Map<String, Object> feedback = new LinkedHashMap<>();
            feedback.put("traceId", "trace-001");
            feedback.put("action", "ACCEPTED");

            assertThrows(BizException.class, () -> service.recordApproverFeedback(feedback));
            verify(feedbackMapper, never()).insert(any(FlowAiFeedbackDO.class));
        }

        @Test
        @DisplayName("recordApproverFeedback 非法 action → 抛 BizException")
        void recordFeedbackInvalidActionThrows() {
            Map<String, Object> feedback = new LinkedHashMap<>();
            feedback.put("traceId", "trace-001");
            feedback.put("recommendedUserId", "u001");
            feedback.put("action", "INVALID");

            assertThrows(BizException.class, () -> service.recordApproverFeedback(feedback));
            verify(feedbackMapper, never()).insert(any(FlowAiFeedbackDO.class));
        }

        @Test
        @DisplayName("recordApproverFeedback CHOSEN_OTHER 缺少 actualUserId → 抛 BizException")
        void recordFeedbackChosenOtherWithoutActualThrows() {
            Map<String, Object> feedback = new LinkedHashMap<>();
            feedback.put("traceId", "trace-001");
            feedback.put("recommendedUserId", "u001");
            feedback.put("action", "CHOSEN_OTHER");
            // 故意不传 actualUserId

            assertThrows(BizException.class, () -> service.recordApproverFeedback(feedback));
            verify(feedbackMapper, never()).insert(any(FlowAiFeedbackDO.class));
        }

        @Test
        @DisplayName("recordApproverFeedback CHOSEN_OTHER 带 actualUserId → 成功")
        void recordFeedbackChosenOtherWithActualSuccess() {
            Map<String, Object> feedback = new LinkedHashMap<>();
            feedback.put("traceId", "trace-001");
            feedback.put("recommendedUserId", "u001");
            feedback.put("action", "CHOSEN_OTHER");
            feedback.put("actualUserId", "u002");
            feedback.put("actualUserName", "李四");
            when(feedbackMapper.insert(any(FlowAiFeedbackDO.class))).thenReturn(1);

            service.recordApproverFeedback(feedback);

            ArgumentCaptor<FlowAiFeedbackDO> captor = ArgumentCaptor.forClass(FlowAiFeedbackDO.class);
            verify(feedbackMapper).insert(captor.capture());
            assertEquals("CHOSEN_OTHER", captor.getValue().getAction());
            assertEquals("u002", captor.getValue().getActualUserId());
        }

        @Test
        @DisplayName("recordApproverFeedback feedback=null → 抛 BizException")
        void recordFeedbackNullThrows() {
            assertThrows(BizException.class, () -> service.recordApproverFeedback(null));
            verify(feedbackMapper, never()).insert(any(FlowAiFeedbackDO.class));
        }

        // ---------- getApproverFeedbackStats ----------

        @Test
        @DisplayName("getApproverFeedbackStats 有数据 → 返回正确统计")
        void feedbackStatsWithData() {
            List<Map<String, Object>> rows = new ArrayList<>();
            rows.add(Map.of("action", "ACCEPTED", "cnt", 7L));
            rows.add(Map.of("action", "REJECTED", "cnt", 2L));
            rows.add(Map.of("action", "CHOSEN_OTHER", "cnt", 1L));
            when(feedbackMapper.selectFeedbackStats(eq("1"), eq("")))
                    .thenReturn(rows);

            Map<String, Object> stats = service.getApproverFeedbackStats(null);

            assertEquals(10L, stats.get("total"));
            assertEquals(7L, stats.get("accepted"));
            assertEquals(2L, stats.get("rejected"));
            assertEquals(1L, stats.get("chosenOther"));
            assertEquals(0.7, stats.get("acceptanceRate"));
        }

        @Test
        @DisplayName("getApproverFeedbackStats 无数据 → 返回全 0 统计")
        void feedbackStatsEmpty() {
            when(feedbackMapper.selectFeedbackStats(eq("1"), eq("")))
                    .thenReturn(List.of());

            Map<String, Object> stats = service.getApproverFeedbackStats(null);

            assertEquals(0L, stats.get("total"));
            assertEquals(0L, stats.get("accepted"));
            assertEquals(0.0, stats.get("acceptanceRate"));
        }

        @Test
        @DisplayName("getApproverFeedbackStats 按推荐人过滤 → 传递 recommendedUserId")
        void feedbackStatsByUser() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("recommendedUserId", "u001");
            params.put("tenantId", "1");
            when(feedbackMapper.selectFeedbackStats(eq("1"), eq("u001")))
                    .thenReturn(List.of(Map.of("action", "ACCEPTED", "cnt", 5L)));

            Map<String, Object> stats = service.getApproverFeedbackStats(params);

            assertEquals(5L, stats.get("total"));
            assertEquals(5L, stats.get("accepted"));
            assertEquals(1.0, stats.get("acceptanceRate"));
            assertEquals("u001", stats.get("recommendedUserId"));
        }
    }
}
