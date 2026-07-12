package com.njydsz.pmis.workflow.server.service.impl.ai;

import com.alibaba.fastjson2.JSON;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.njydsz.pmis.common.core.response.StandardResultCode;
import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.agent.api.client.AgentClient;
import com.njydsz.pmis.workflow.domain.entity.ai.FlowAiFeedbackDO;
import com.njydsz.pmis.workflow.infra.mapper.ai.FlowAiFeedbackMapper;
import com.njydsz.pmis.workflow.server.service.ai.FlowAiAssistService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * P2-1: 工作流 AI 辅助服务实现
 *
 * <p>通过 {@link AgentClient} Feign 接口调用 agent 模块的 executeInMemory 接口。
 * Feign fallback 工厂保证 agent 服务不可用时本服务仍可调用，仅返回降级空结果。
 *
 * <p>P3-2: 为 predictRisk / smartRemind / predictSla 三个 AI 预测方法添加 Caffeine
 * 本地缓存，TTL 5 分钟，避免相同输入参数短时间内反复调用 Agent（LLM 调用代价高）。
 * <b>降级结果不缓存</b>：仅在 Agent 成功返回有效 payload 时才写入缓存，确保 Agent
 * 恢复后能立即返回真实结果。缓存策略与 {@link com.njydsz.pmis.workflow.server.engine.FlowDefinitionCacheService}
 * 一致（Caffeine 本地缓存 + 可注入 Ticker 用于测试）。
 *
 * <p>P3-3: 推荐审批人反馈闭环 — recommendApprovers 返回结果携带 traceId + rank，
 * 便于前端反馈时关联；recordApproverFeedback 持久化反馈；getApproverFeedbackStats
 * 统计推荐准确率。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
public class FlowAiAssistServiceImpl implements FlowAiAssistService {

    /** AI 调用结果缓存 TTL：5 分钟（AI 结果时效性短，但调用代价高） */
    private static final Duration AI_CACHE_TTL = Duration.ofMinutes(5);
    /** AI 调用结果缓存最大条目数（每个方法独立缓存） */
    private static final int AI_CACHE_MAX_SIZE = 500;
    /** 默认租户 ID */
    private static final String DEFAULT_TENANT_ID = "1";

    /** Agent 模块 Feign 客户端，用于调用 LLM 执行 AI 辅助能力（推荐审批人/预测风险/智能催办等） */
    private final AgentClient agentClient;
    /** AI 推荐审批人反馈 Mapper，持久化用户对推荐结果的采纳/拒绝记录 */
    private final FlowAiFeedbackMapper feedbackMapper;
    /** 风险预测结果本地缓存（TTL 5 分钟，仅缓存 Agent 成功返回的真实结果） */
    private final Cache<String, Map<String, Object>> riskCache;
    /** 智能催办结果本地缓存（TTL 5 分钟，仅缓存 Agent 成功返回的真实结果） */
    private final Cache<String, Map<String, Object>> remindCache;
    /** SLA 预测结果本地缓存（TTL 5 分钟，仅缓存 Agent 成功返回的真实结果） */
    private final Cache<String, Map<String, Object>> slaCache;

    /**
     * Spring 主构造器，使用系统时钟。
     */
    public FlowAiAssistServiceImpl(@Lazy AgentClient agentClient,
                                   FlowAiFeedbackMapper feedbackMapper) {
        this(agentClient, feedbackMapper, Ticker.systemTicker());
    }

    /**
     * 测试用构造器，可注入自定义 {@link Ticker} 以模拟 TTL 过期。
     */
    FlowAiAssistServiceImpl(AgentClient agentClient,
                            FlowAiFeedbackMapper feedbackMapper,
                            Ticker ticker) {
        this.agentClient = agentClient;
        this.feedbackMapper = feedbackMapper;
        Ticker actualTicker = ticker == null ? Ticker.systemTicker() : ticker;
        this.riskCache = buildAiCache(actualTicker);
        this.remindCache = buildAiCache(actualTicker);
        this.slaCache = buildAiCache(actualTicker);
    }

    private static Cache<String, Map<String, Object>> buildAiCache(Ticker ticker) {
        return Caffeine.newBuilder()
                .expireAfterWrite(AI_CACHE_TTL)
                .maximumSize(AI_CACHE_MAX_SIZE)
                .ticker(ticker)
                .build();
    }

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

        // P3-3: 为本次推荐生成唯一 traceId，所有推荐项共享，便于反馈闭环关联
        String traceId = UUID.randomUUID().toString().replace("-", "");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("candidates", candidates);
        params.put("topN", topN);
        params.put("traceId", traceId);
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
            BaseResponse<Map<String, Object>> res = agentClient.execute(body);
            if (res == null || res.isSuccess() == false) {
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
                int rank = 1;
                for (Object o : raw) {
                    if (o instanceof Map<?, ?> m) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> cast = (Map<String, Object>) m;
                        // P3-3: 为每个推荐项注入 traceId 和 rank，供前端反馈时关联
                        cast.put("traceId", traceId);
                        cast.put("rank", rank++);
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
            BaseResponse<Map<String, Object>> res = agentClient.execute(body);
            if (res == null || res.isSuccess() == false) {
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
        // P3-2: 查询本地缓存，命中则直接返回，避免反复调用 Agent
        String cacheKey = cacheKey(params);
        Map<String, Object> cached = riskCache.getIfPresent(cacheKey);
        if (cached != null) {
            log.debug("[FlowAiAssist] predictRisk 缓存命中: key={}", cacheKey);
            return cached;
        }
        Map<String, Object> body = buildAgentBody("Risk_Predict".toUpperCase(), "FLOW_INSTANCE",
                params, "instanceId", "flowCode");
        try {
            BaseResponse<Map<String, Object>> res = agentClient.execute(body);
            if (res == null || res.isSuccess() == false) {
                log.warn("[FlowAiAssist] predictRisk 调用失败: code={} msg={}",
                        res == null ? "null" : res.getCode(),
                        res == null ? "" : res.getMessage());
                return defaultRiskResult("Agent 服务暂不可用");
            }
            Object payload = res.getData() == null ? null : res.getData().get("payload");
            if (payload instanceof Map<?, ?> m) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cast = (Map<String, Object>) m;
                // P3-2: 仅缓存 Agent 成功返回的真实结果，降级结果不缓存
                riskCache.put(cacheKey, cast);
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
        // P3-2: 查询本地缓存，命中则直接返回，避免反复调用 Agent
        String cacheKey = cacheKey(params);
        Map<String, Object> cached = remindCache.getIfPresent(cacheKey);
        if (cached != null) {
            log.debug("[FlowAiAssist] smartRemind 缓存命中: key={}", cacheKey);
            return cached;
        }
        Map<String, Object> body = buildAgentBody("Smart_Remind".toUpperCase(), "FLOW_TASK",
                params, "taskId", "flowCode");
        try {
            BaseResponse<Map<String, Object>> res = agentClient.execute(body);
            if (res == null || res.isSuccess() == false) {
                log.warn("[FlowAiAssist] smartRemind 调用失败: code={} msg={}",
                        res == null ? "null" : res.getCode(),
                        res == null ? "" : res.getMessage());
                return defaultRemindResult("Agent 服务暂不可用");
            }
            Object payload = res.getData() == null ? null : res.getData().get("payload");
            if (payload instanceof Map<?, ?> m) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cast = (Map<String, Object>) m;
                // P3-2: 仅缓存 Agent 成功返回的真实结果，降级结果不缓存
                remindCache.put(cacheKey, cast);
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
        // P3-2: 查询本地缓存，命中则直接返回，避免反复调用 Agent
        String cacheKey = cacheKey(params);
        Map<String, Object> cached = slaCache.getIfPresent(cacheKey);
        if (cached != null) {
            log.debug("[FlowAiAssist] predictSla 缓存命中: key={}", cacheKey);
            return cached;
        }
        Map<String, Object> body = buildAgentBody("Sla_Predict".toUpperCase(), "FLOW_INSTANCE",
                params, "instanceId", "flowCode");
        try {
            BaseResponse<Map<String, Object>> res = agentClient.execute(body);
            if (res == null || res.isSuccess() == false) {
                log.warn("[FlowAiAssist] predictSla 调用失败: code={} msg={}",
                        res == null ? "null" : res.getCode(),
                        res == null ? "" : res.getMessage());
                return defaultSlaResult("Agent 服务暂不可用");
            }
            Object payload = res.getData() == null ? null : res.getData().get("payload");
            if (payload instanceof Map<?, ?> m) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cast = (Map<String, Object>) m;
                // P3-2: 仅缓存 Agent 成功返回的真实结果，降级结果不缓存
                slaCache.put(cacheKey, cast);
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
        r.put("channel", "INAPP");
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

    // ========== P3-2: 缓存工具方法 ==========

    /**
     * P3-2: 生成 AI 调用缓存的稳定 key。
     *
     * <p>使用 fastjson2 序列化参数 Map 为 JSON 字符串作为 cache key，保证同一参数组合
     * 生成同一 key。相比 {@code Map.hashCode()}（依赖元素顺序且易碰撞），JSON 字符串
     * 更稳定可读，便于调试。
     *
     * <p>注意：调用方应保证参数 Map 的 key 顺序一致（推荐使用 {@link LinkedHashMap}），
     * 否则相同参数不同顺序会产生不同 key。当前 Controller 层使用 {@code @RequestBody Map}，
     * Jackson 反序列化默认使用 {@link LinkedHashMap}，前端传参顺序固定，可正常工作。
     *
     * @param params AI 调用参数
     * @return 稳定的缓存 key（JSON 字符串）
     */
    private static String cacheKey(Map<String, Object> params) {
        return JSON.toJSONString(params);
    }

    /**
     * P3-2: 清除全部 AI 调用缓存（risk / remind / sla）。
     *
     * <p>用于测试或运维场景手动失效缓存。正常情况下由 TTL 自动过期，无需手动调用。
     */
    public void evictAllAiCache() {
        riskCache.invalidateAll();
        remindCache.invalidateAll();
        slaCache.invalidateAll();
        log.debug("[FlowAiAssist] 已清除全部 AI 调用缓存");
    }

    // ============================== P3-3: 推荐审批人反馈闭环 ==============================

    @Override
    public String recordApproverFeedback(Map<String, Object> feedback) {
        if (feedback == null) {
            throw new BizException(StandardResultCode.BAD_REQUEST, "error.workflow.msg_e0f1a2b3");
        }
        String traceId = strOrEmpty(feedback.get("traceId"));
        String recommendedUserId = strOrEmpty(feedback.get("recommendedUserId"));
        String action = strOrEmpty(feedback.get("action"));
        if (!StringUtils.hasText(traceId)
                || !StringUtils.hasText(recommendedUserId)
                || !StringUtils.hasText(action)) {
            throw new BizException(StandardResultCode.BAD_REQUEST, "error.workflow.msg_e0f1a2b3");
        }
        // 校验 action 合法性
        if (!isValidFeedbackAction(action)) {
            throw new BizException(StandardResultCode.BAD_REQUEST, "error.workflow.msg_e0f1a2b3");
        }
        // CHOSEN_OTHER 时必须有 actualUserId
        if ("CHOSEN_OTHER".equals(action)
                && !StringUtils.hasText(strOrEmpty(feedback.get("actualUserId")))) {
            throw new BizException(StandardResultCode.BAD_REQUEST, "error.workflow.msg_e0f1a2b4");
        }

        FlowAiFeedbackDO entity = new FlowAiFeedbackDO();
        entity.setTenantId(strOrEmpty(feedback.getOrDefault("tenantId", DEFAULT_TENANT_ID)));
        if (!StringUtils.hasText(entity.getTenantId())) {
            entity.setTenantId(DEFAULT_TENANT_ID);
        }
        entity.setTraceId(traceId);
        entity.setTaskId(strOrEmpty(feedback.get("taskId")));
        entity.setInstanceId(strOrEmpty(feedback.get("instanceId")));
        entity.setFlowCode(strOrEmpty(feedback.get("flowCode")));
        entity.setNodeCode(strOrEmpty(feedback.get("nodeCode")));
        entity.setRecommendedUserId(recommendedUserId);
        entity.setRecommendedUserName(strOrEmpty(feedback.get("recommendedUserName")));
        entity.setRecommendedScore(toBigDecimal(feedback.get("recommendedScore")));
        entity.setRecommendedRank(toIntOrNull(feedback.get("recommendedRank")));
        entity.setAction(action);
        entity.setActualUserId(strOrEmpty(feedback.get("actualUserId")));
        entity.setActualUserName(strOrEmpty(feedback.get("actualUserName")));
        entity.setFeedbackSource(strOrEmpty(feedback.getOrDefault("feedbackSource", "USER_EXPLICIT")));
        entity.setRemark(strOrEmpty(feedback.get("remark")));
        entity.setProviderTraceId(strOrEmpty(feedback.get("providerTraceId")));

        feedbackMapper.insert(entity);
        log.info("[FlowAiAssist] 记录推荐反馈: traceId={} userId={} action={}",
                traceId, recommendedUserId, action);
        return entity.getId();
    }

    @Override
    public Map<String, Object> getApproverFeedbackStats(Map<String, Object> params) {
        String tenantId = params == null
                ? DEFAULT_TENANT_ID
                : strOrEmpty(params.getOrDefault("tenantId", DEFAULT_TENANT_ID));
        if (!StringUtils.hasText(tenantId)) {
            tenantId = DEFAULT_TENANT_ID;
        }
        String recommendedUserId = params == null ? "" : strOrEmpty(params.get("recommendedUserId"));

        List<Map<String, Object>> rows = feedbackMapper.selectFeedbackStats(tenantId, recommendedUserId);
        long accepted = 0L;
        long rejected = 0L;
        long chosenOther = 0L;
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                String act = strOrEmpty(row.get("action"));
                long cnt = toLong(row.get("cnt"));
                switch (act) {
                    case "ACCEPTED" -> accepted = cnt;
                    case "REJECTED" -> rejected = cnt;
                    case "CHOSEN_OTHER" -> chosenOther = cnt;
                    default -> log.debug("[FlowAiAssist] 未知反馈动作: {}", act);
                }
            }
        }
        long total = accepted + rejected + chosenOther;
        double acceptanceRate = total == 0 ? 0.0 : (double) accepted / total;

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", total);
        stats.put("accepted", accepted);
        stats.put("rejected", rejected);
        stats.put("chosenOther", chosenOther);
        stats.put("acceptanceRate", acceptanceRate);
        stats.put("tenantId", tenantId);
        if (StringUtils.hasText(recommendedUserId)) {
            stats.put("recommendedUserId", recommendedUserId);
        }
        return stats;
    }

    // ============================== P3-3: 校验工具 ==============================

    private static boolean isValidFeedbackAction(String action) {
        return "ACCEPTED".equals(action) || "REJECTED".equals(action) || "CHOSEN_OTHER".equals(action);
    }

    private static BigDecimal toBigDecimal(Object o) {
        if (o == null) return null;
        if (o instanceof BigDecimal bd) return bd;
        if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            return new BigDecimal(o.toString());
        } catch (Exception ignore) {
            return null;
        }
    }

    private static Integer toIntOrNull(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(o.toString());
        } catch (Exception ignore) {
            return null;
        }
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
