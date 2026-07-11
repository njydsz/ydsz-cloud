package com.njydsz.pmis.agent.server.engine.trace;

import com.alibaba.fastjson2.JSON;
import com.njydsz.pmis.agent.server.engine.AgentContext;
import com.njydsz.pmis.agent.domain.entity.agent.AgentTraceDO;
import com.njydsz.pmis.agent.infra.mapper.agent.AgentTraceMapper;
import com.njydsz.pmis.common.util.SnowflakeIdGenerator;
import com.njydsz.pmis.common.util.TraceIdUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 默认 Agent Tracer 实现（P2-3 落地）。
 *
 * <p>所有 span 同步落库到 {@code pmis_agent_trace} 表，落库失败仅记录 WARN 日志，
 * 不影响主流程。无 DB 环境（单元测试）下使用 {@code ObjectProvider} 自动降级为空操作。
 *
 * <p>配置开关：{@code pmis.agent.trace.enabled}（默认 true）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-3)
 */
@Slf4j
@Component
public class DefaultAgentTracer implements AgentTracer {

    /** 使用 ObjectProvider 避免 Mapper 在无 DB 环境下（单元测试）启动失败 */
    private final ObjectProvider<AgentTraceMapper> mapperProvider;

    /** Tracing 开关（pmis.agent.trace.enabled，默认 true） */
    private final boolean enabled;

    public DefaultAgentTracer(ObjectProvider<AgentTraceMapper> mapperProvider,
                              @Value("${pmis.agent.trace.enabled:true}") boolean enabled) {
        this.mapperProvider = mapperProvider;
        this.enabled = enabled;
        log.info("[AgentTracer] 初始化完成, enabled={}, mapperAvailable={}",
                enabled, mapperProvider.getIfAvailable() != null);
    }

    @Override
    public TraceContext startAgent(AgentContext ctx) {
        // 解析 traceId：优先 AgentContext，其次 TraceIdUtil，最后生成
        String traceId = resolveTraceId(ctx);
        String rootSpanId = SnowflakeIdGenerator.nextIdStr();

        TraceContext traceCtx = TraceContext.builder()
                .traceId(traceId)
                .rootSpanId(rootSpanId)
                .agentType(resolveAgentType(ctx))
                .bizType(ctx.getBizType())
                .bizId(ctx.getBizId())
                .bizRef(ctx.getBizRef())
                .providerTraceId(ctx.getProviderTraceId())
                .tenantId("1")
                .startMs(System.currentTimeMillis())
                .stepStartMs(System.currentTimeMillis())
                .build();

        if (enabled) {
            AgentSpan span = AgentSpan.builder()
                    .traceId(traceId)
                    .spanId(rootSpanId)
                    .parentSpanId(null)
                    .agentType(traceCtx.getAgentType())
                    .bizType(ctx.getBizType())
                    .bizId(ctx.getBizId())
                    .bizRef(ctx.getBizRef())
                    .spanName(AgentSpanName.AGENT_START)
                    .stepIndex(0)
                    .status(AgentSpanName.STATUS_SUCCESS)
                    .inputData(safeJson(ctx.getParams()))
                    .costMs(0L)
                    .providerTraceId(ctx.getProviderTraceId())
                    .tenantId("1")
                    .build();
            persist(span);
            log.info("[AgentTracer] AGENT_START traceId={} agent={} biz={}",
                    traceId, traceCtx.getAgentType(), ctx.getBizRef());
        }
        return traceCtx;
    }

    @Override
    public void span(TraceContext traceCtx, String spanName, int stepIndex,
                     String inputData, String outputData) {
        if (!enabled || traceCtx == null) {
            return;
        }
        long costMs = traceCtx.stepCostMs();
        AgentSpan span = AgentSpan.builder()
                .traceId(traceCtx.getTraceId())
                .spanId(SnowflakeIdGenerator.nextIdStr())
                .parentSpanId(traceCtx.getRootSpanId())
                .agentType(traceCtx.getAgentType())
                .bizType(traceCtx.getBizType())
                .bizId(traceCtx.getBizId())
                .bizRef(traceCtx.getBizRef())
                .spanName(spanName)
                .stepIndex(stepIndex)
                .status(AgentSpanName.STATUS_SUCCESS)
                .inputData(inputData)
                .outputData(outputData)
                .costMs(costMs)
                .providerTraceId(traceCtx.getProviderTraceId())
                .tenantId(traceCtx.getTenantId())
                .build();
        persist(span);
        // 标记步骤开始时间（用于下一个 span 的耗时计算）
        traceCtx.markStepStart();
        log.debug("[AgentTracer] {} traceId={} step={} cost={}ms",
                spanName, traceCtx.getTraceId(), stepIndex, costMs);
    }

    @Override
    public void error(TraceContext traceCtx, Throwable error) {
        if (!enabled || traceCtx == null || error == null) {
            return;
        }
        long costMs = System.currentTimeMillis() - traceCtx.getStartMs();
        AgentSpan span = AgentSpan.builder()
                .traceId(traceCtx.getTraceId())
                .spanId(SnowflakeIdGenerator.nextIdStr())
                .parentSpanId(traceCtx.getRootSpanId())
                .agentType(traceCtx.getAgentType())
                .bizType(traceCtx.getBizType())
                .bizId(traceCtx.getBizId())
                .bizRef(traceCtx.getBizRef())
                .spanName(AgentSpanName.AGENT_ERROR)
                .stepIndex(0)
                .status(AgentSpanName.STATUS_FAILED)
                .errorMsg(error.getMessage())
                .costMs(costMs)
                .providerTraceId(traceCtx.getProviderTraceId())
                .tenantId(traceCtx.getTenantId())
                .build();
        persist(span);
        log.warn("[AgentTracer] AGENT_ERROR traceId={} cost={}ms err={}",
                traceCtx.getTraceId(), costMs, error.getMessage());
    }

    @Override
    public void endAgent(TraceContext traceCtx, String outputData, boolean success) {
        if (!enabled || traceCtx == null) {
            return;
        }
        long costMs = System.currentTimeMillis() - traceCtx.getStartMs();
        AgentSpan span = AgentSpan.builder()
                .traceId(traceCtx.getTraceId())
                .spanId(SnowflakeIdGenerator.nextIdStr())
                .parentSpanId(traceCtx.getRootSpanId())
                .agentType(traceCtx.getAgentType())
                .bizType(traceCtx.getBizType())
                .bizId(traceCtx.getBizId())
                .bizRef(traceCtx.getBizRef())
                .spanName(AgentSpanName.AGENT_END)
                .stepIndex(0)
                .status(success ? AgentSpanName.STATUS_SUCCESS : AgentSpanName.STATUS_FAILED)
                .outputData(outputData)
                .costMs(costMs)
                .providerTraceId(traceCtx.getProviderTraceId())
                .tenantId(traceCtx.getTenantId())
                .build();
        persist(span);
        log.info("[AgentTracer] AGENT_END traceId={} success={} cost={}ms",
                traceCtx.getTraceId(), success, costMs);
    }

    // ==================== 私有方法 ====================

    /** 解析 traceId：AgentContext 优先 → TraceIdUtil → 雪花生成 */
    private String resolveTraceId(AgentContext ctx) {
        if (ctx.getTraceId() != null && !ctx.getTraceId().isEmpty()) {
            return ctx.getTraceId();
        }
        String traceId = TraceIdUtil.get();
        if (traceId != null && !traceId.isEmpty()) {
            return traceId;
        }
        return SnowflakeIdGenerator.nextTraceId();
    }

    /** 从 AgentContext 推断 Agent 类型（bizType 兜底） */
    private String resolveAgentType(AgentContext ctx) {
        if (ctx.getParams() != null && ctx.getParams().get("agentType") instanceof String at) {
            return at;
        }
        return ctx.getBizType() == null ? "UNKNOWN" : ctx.getBizType();
    }

    /** 持久化 span 到 DB（无 DB 环境降级为空操作） */
    private void persist(AgentSpan span) {
        try {
            AgentTraceMapper mapper = mapperProvider.getIfAvailable();
            if (mapper == null) {
                return;
            }
            AgentTraceDO entity = toDO(span);
            mapper.insert(entity);
        } catch (Exception e) {
            log.warn("[AgentTracer] 落库失败 span={} err={}",
                    span.getSpanName(), e.getMessage());
        }
    }

    /** AgentSpan → AgentTraceDO */
    private AgentTraceDO toDO(AgentSpan span) {
        AgentTraceDO entity = new AgentTraceDO();
        entity.setId(span.getSpanId());
        entity.setTraceId(span.getTraceId());
        entity.setSpanId(span.getSpanId());
        entity.setParentSpanId(span.getParentSpanId());
        entity.setAgentType(span.getAgentType());
        entity.setBizType(span.getBizType());
        entity.setBizId(span.getBizId());
        entity.setBizRef(span.getBizRef());
        entity.setSpanName(span.getSpanName());
        entity.setStepIndex(span.getStepIndex());
        entity.setStatus(span.getStatus());
        entity.setInputData(span.getInputData());
        entity.setOutputData(span.getOutputData());
        entity.setErrorMsg(span.getErrorMsg());
        entity.setCostMs(span.getCostMs());
        entity.setProviderTraceId(span.getProviderTraceId() == null ? "" : span.getProviderTraceId());
        entity.setTenantId(span.getTenantId() == null ? "1" : span.getTenantId());
        return entity;
    }

    /** 安全 JSON 序列化（失败返回 null） */
    private String safeJson(Object o) {
        if (o == null) return null;
        try {
            return JSON.toJSONString(o);
        } catch (Exception e) {
            return String.valueOf(o);
        }
    }
}
