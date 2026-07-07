package com.njydsz.pmis.workflow.service.impl;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.feign.AgentClient;
import com.njydsz.pmis.workflow.service.FlowAiAssistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * P2-1: 工作流 AI 辅助服务实现
 *
 * <p>通过 {@link AgentClient} Feign 接口调用 agent 模块的 executeInMemory 接口。
 * Feign fallback 工厂保证 agent 服务不可用时本服务仍可调用，仅返回降级空结果。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowAiAssistServiceImpl implements FlowAiAssistService {

    private final @Lazy AgentClient agentClient;

    private static final int DEFAULT_TOP_N = 3;

    @Override
    public List<Map<String, Object>> recommendApprovers(Map<String, Object> ctx,
                                                        List<Map<String, Object>> candidates,
                                                        int topN) {
        if (candidates == null || candidates.isEmpty()) {
            log.info("[FlowAiAssist] recommendApprovers: 无候选审批人");
            return List.of();
        }
        if (topN <= 0) topN = DEFAULT_TOP_N;
        if (topN > 10) topN = 10;

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("candidates", candidates);
        params.put("topN", topN);
        if (ctx != null) {
            copyIfPresent(ctx, params, "requiredLevel");
            copyIfPresent(ctx, params, "requiredRole");
            copyIfPresent(ctx, params, "requiredDepartment");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("agentType", "APPROVER_RECOMMEND");
        body.put("bizType", "FLOW_TASK");
        body.put("bizId", ctx == null ? 0L : toLong(ctx.get("taskId")));
        body.put("bizRef", ctx == null ? "" : strOrEmpty(ctx.get("flowCode")));
        body.put("params", params);

        try {
            Result<Map<String, Object>> res = agentClient.execute(body);
            if (res == null || res.getCode() != 0) {
                log.warn("[FlowAiAssist] recommendApprovers 调用失败: code={} msg={}",
                        res == null ? "null" : res.getCode(),
                        res == null ? "" : res.getMessage());
                return List.of();
            }
            Object payload = res.getData() == null ? null : res.getData().get("payload");
            if (!(payload instanceof Map<?, ?>)) {
                return List.of();
            }
            Object top = ((Map<?, ?>) payload).get("top");
            if (top instanceof List<?> raw) {
                List<Map<String, Object>> out = new ArrayList<>(raw.size());
                for (Object o : raw) {
                    if (o instanceof Map<?, ?> m) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> cast = (Map<String, Object>) m;
                        out.add(cast);
                    }
                }
                return out;
            }
            return List.of();
        } catch (Exception e) {
            // 兜底：Feign 调用异常时返回空列表，绝不影响主流程
            log.warn("[FlowAiAssist] recommendApprovers 异常: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public Map<String, Object> draftComment(Map<String, Object> params) {
        if (params == null) {
            return Map.of("primary", "已审阅，无异议。",
                    "alternatives", List.of(),
                    "reasons", List.of("无参数"));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("agentType", "COMMENT_DRAFT");
        body.put("bizType", "FLOW_TASK");
        body.put("bizId", toLong(params.get("taskId")));
        body.put("bizRef", strOrEmpty(params.get("flowCode")));
        body.put("params", params);

        try {
            Result<Map<String, Object>> res = agentClient.execute(body);
            if (res == null || res.getCode() != 0) {
                log.warn("[FlowAiAssist] draftComment 调用失败: code={} msg={}",
                        res == null ? "null" : res.getCode(),
                        res == null ? "" : res.getMessage());
                return Map.of("primary", "已审阅，无异议。",
                        "alternatives", List.of(),
                        "reasons", List.of("Agent 服务暂不可用"));
            }
            Object payload = res.getData() == null ? null : res.getData().get("payload");
            if (payload instanceof Map<?, ?> m) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cast = (Map<String, Object>) m;
                return cast;
            }
            return Map.of("primary", "已审阅，无异议。",
                    "alternatives", List.of(),
                    "reasons", List.of("Agent 返回格式异常"));
        } catch (Exception e) {
            log.warn("[FlowAiAssist] draftComment 异常: {}", e.getMessage());
            return Map.of("primary", "已审阅，无异议。",
                    "alternatives", List.of(),
                    "reasons", List.of("Agent 调用异常: " + e.getMessage()));
        }
    }

    @Override
    public boolean isAiAvailable() {
        // 通过尝试一次空调用判断，但会消耗资源，所以仅做静态判断：
        // 实际上 Feign fallback 在调用时才触发，静态判断可让前端置灰，运行时仍可能降级
        return true;
    }

    // ============================== P3-1: AI 能力扩展 ==============================

    @Override
    public Map<String, Object> predictRisk(Map<String, Object> params) {
        if (params == null) {
            return defaultRiskResult("无预测参数");
        }
        Map<String, Object> body = buildAgentBody("Risk_Predict".toUpperCase(), "FLOW_INSTANCE",
                params, "instanceId", "flowCode");
        try {
            Result<Map<String, Object>> res = agentClient.execute(body);
            if (res == null || res.getCode() != 0) {
                log.warn("[FlowAiAssist] predictRisk 调用失败: code={} msg={}",
                        res == null ? "null" : res.getCode(),
                        res == null ? "" : res.getMessage());
                return defaultRiskResult("Agent 服务暂不可用");
            }
            Object payload = res.getData() == null ? null : res.getData().get("payload");
            if (payload instanceof Map<?, ?> m) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cast = (Map<String, Object>) m;
                return cast;
            }
            return defaultRiskResult("Agent 返回格式异常");
        } catch (Exception e) {
            log.warn("[FlowAiAssist] predictRisk 异常: {}", e.getMessage());
            return defaultRiskResult("Agent 调用异常: " + e.getMessage());
        }
    }

    @Override
    public Map<String, Object> smartRemind(Map<String, Object> params) {
        if (params == null) {
            return defaultRemindResult("无催办参数");
        }
        Map<String, Object> body = buildAgentBody("Smart_Remind".toUpperCase(), "FLOW_TASK",
                params, "taskId", "flowCode");
        try {
            Result<Map<String, Object>> res = agentClient.execute(body);
            if (res == null || res.getCode() != 0) {
                log.warn("[FlowAiAssist] smartRemind 调用失败: code={} msg={}",
                        res == null ? "null" : res.getCode(),
                        res == null ? "" : res.getMessage());
                return defaultRemindResult("Agent 服务暂不可用");
            }
            Object payload = res.getData() == null ? null : res.getData().get("payload");
            if (payload instanceof Map<?, ?> m) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cast = (Map<String, Object>) m;
                return cast;
            }
            return defaultRemindResult("Agent 返回格式异常");
        } catch (Exception e) {
            log.warn("[FlowAiAssist] smartRemind 异常: {}", e.getMessage());
            return defaultRemindResult("Agent 调用异常: " + e.getMessage());
        }
    }

    @Override
    public Map<String, Object> predictSla(Map<String, Object> params) {
        if (params == null) {
            return defaultSlaResult("无预测参数");
        }
        Map<String, Object> body = buildAgentBody("Sla_Predict".toUpperCase(), "FLOW_INSTANCE",
                params, "instanceId", "flowCode");
        try {
            Result<Map<String, Object>> res = agentClient.execute(body);
            if (res == null || res.getCode() != 0) {
                log.warn("[FlowAiAssist] predictSla 调用失败: code={} msg={}",
                        res == null ? "null" : res.getCode(),
                        res == null ? "" : res.getMessage());
                return defaultSlaResult("Agent 服务暂不可用");
            }
            Object payload = res.getData() == null ? null : res.getData().get("payload");
            if (payload instanceof Map<?, ?> m) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cast = (Map<String, Object>) m;
                return cast;
            }
            return defaultSlaResult("Agent 返回格式异常");
        } catch (Exception e) {
            log.warn("[FlowAiAssist] predictSla 异常: {}", e.getMessage());
            return defaultSlaResult("Agent 调用异常: " + e.getMessage());
        }
    }

    // ========== P3-1: 降级默认结果 ==========

    private static Map<String, Object> defaultRiskResult(String reason) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("riskLevel", "UNKNOWN");
        r.put("rejectProbability", 0.0);
        r.put("overdueProbability", 0.0);
        r.put("reasons", List.of(reason));
        return r;
    }

    private static Map<String, Object> defaultRemindResult(String reason) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("bestTime", "IMMEDIATE");
        r.put("channel", "IN_APP");
        r.put("message", "您有待审批任务，请尽快处理。");
        r.put("reasons", List.of(reason));
        return r;
    }

    private static Map<String, Object> defaultSlaResult(String reason) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("estimatedDurationMs", 0L);
        r.put("estimatedCompleteAt", null);
        r.put("confidence", 0.0);
        r.put("reasons", List.of(reason));
        return r;
    }

    /**
     * P3-1: 构建 Agent 调用请求体（复用公共逻辑）。
     *
     * @param agentType Agent 类型
     * @param bizType   业务类型
     * @param params    业务参数
     * @param bizIdKey  bizId 取值 key（params 中）
     * @param bizRefKey bizRef 取值 key（params 中）
     */
    private Map<String, Object> buildAgentBody(String agentType, String bizType,
                                               Map<String, Object> params,
                                               String bizIdKey, String bizRefKey) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("agentType", agentType);
        body.put("bizType", bizType);
        body.put("bizId", toLong(params.get(bizIdKey)));
        body.put("bizRef", strOrEmpty(params.get(bizRefKey)));
        body.put("params", params);
        return body;
    }

    // ========== 工具方法 ==========

    private static void copyIfPresent(Map<String, Object> src,
                                      Map<String, Object> dst, String key) {
        if (src.containsKey(key) && src.get(key) != null) {
            dst.put(key, src.get(key));
        }
    }

    private static String strOrEmpty(Object o) {
        return o == null ? "" : o.toString();
    }

    private static long toLong(Object o) {
        if (o == null) return 0L;
        if (o instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(o.toString());
        } catch (Exception ignore) {
            log.warn("[FlowAiAssistServiceImpl] Long 解析失败，使用 0L 兜底 o={}: {}", o, ignore.getMessage());
            return 0L;
        }
    }
}
