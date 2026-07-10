package com.njydsz.pmis.agent.service.impl.agent;

import com.njydsz.pmis.common.security.TenantContext;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.agent.dto.agent.AgentRunRequestDTO;
import com.njydsz.pmis.agent.engine.Agent;
import com.njydsz.pmis.agent.engine.AgentContext;
import com.njydsz.pmis.agent.engine.AgentResult;
import com.njydsz.pmis.agent.engine.StreamableAgent;
import com.njydsz.pmis.agent.engine.react.ReActResult;
import com.njydsz.pmis.agent.engine.stream.CompositeReActEventListener;
import com.njydsz.pmis.agent.engine.stream.NoOpReActEventListener;
import com.njydsz.pmis.agent.engine.stream.ReActEventListener;
import com.njydsz.pmis.agent.engine.stream.SignalGuardReActEventListener;
import com.njydsz.pmis.agent.engine.trace.AgentTracer;
import com.njydsz.pmis.agent.engine.trace.TraceContext;
import com.njydsz.pmis.agent.engine.trace.TracingReActEventListener;
import com.njydsz.pmis.agent.entity.hitl.AgentPredictionDO;
import com.njydsz.pmis.agent.enums.agent.AgentAlertLevel;
import com.njydsz.pmis.agent.enums.agent.AgentRunStatus;
import com.njydsz.pmis.agent.enums.agent.AgentType;
import com.njydsz.pmis.agent.mapper.hitl.AgentPredictionMapper;
import com.njydsz.pmis.agent.service.agent.AgentService;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.constant.AsyncExecutorNames;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.util.SnowflakeIdGenerator;
import com.njydsz.pmis.common.util.TraceIdUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * AI 智能体服务实现
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
public class AgentServiceImpl implements AgentService {

    /** 已注册的 Agent 列表（Spring 自动注入） */
    private final List<Agent> agents;
    /** Agent 预测记录 Mapper */
    private final AgentPredictionMapper predictionMapper;
    /** Agent 链路追踪器（P2-3：全链路 Tracing） */
    private final AgentTracer tracer;
    /** AI Agent 异步执行线程池（P1-3：显式提交任务以透传 TenantContext） */
    private final ThreadPoolTaskExecutor agentExecutor;

    /**
     * 构造函数。
     *
     * <p>不使用 {@code @RequiredArgsConstructor}，因为 {@code agentExecutor} 需要
     * {@link Qualifier} 指定 Bean 名称，Lombok 默认不会将该注解复制到构造参数。
     *
     * @param agents           已注册的 Agent 列表
     * @param predictionMapper Agent 预测记录 Mapper
     * @param tracer           Agent 链路追踪器
     * @param agentExecutor    AI Agent 线程池（Bean name = {@link AsyncExecutorNames#AGENT}）
     */
    public AgentServiceImpl(List<Agent> agents,
                            AgentPredictionMapper predictionMapper,
                            AgentTracer tracer,
                            @Qualifier(AsyncExecutorNames.AGENT) ThreadPoolTaskExecutor agentExecutor) {
        this.agents = agents;
        this.predictionMapper = predictionMapper;
        this.tracer = tracer;
        this.agentExecutor = agentExecutor;
    }

    @Override
    @SentinelResource(value = "agent:run", blockHandler = "runBlockHandler", fallback = "runFallback")
    public AgentPredictionDO run(AgentRunRequestDTO req) {
        AgentType type = validate(req);
        Agent agent = findAgent(type);

        AgentContext ctx = new AgentContext(req.getBizType(), req.getBizId(), req.getBizRef(),
                req.getCallerId(), req.getCallerName(), req.getSource(), req.getParams());
        // P2-3: 注入 traceId（TraceIdUtil.getOrCreate 兼容无 Brave 环境）
        ctx.setTraceId(TraceIdUtil.getOrCreate());

        // P2-3: 启动 Agent 链路追踪
        TraceContext traceCtx = tracer.startAgent(ctx);

        AgentPredictionDO record = new AgentPredictionDO();
        record.setTaskCode(buildTaskCode(type, req.getBizId()));
        record.setAgentType(type.getCode());
        record.setBizType(req.getBizType());
        record.setBizId(req.getBizId());
        record.setBizRef(req.getBizRef());
        record.setInputSnapshot(safeJson(req.getParams()));
        record.setModelVersion("v1.0.0");
        record.setStatus(AgentRunStatus.RUNNING.getCode());
        record.setCallerId(req.getCallerId());
        record.setCallerName(req.getCallerName());
        record.setSource(StringUtils.hasText(req.getSource()) ? req.getSource() : "MANUAL");
        record.setTenantId(String.valueOf(TenantContext.getTenantId()));
        // P1-4: providerTraceId 在 LLM 调用后由 Provider 写入 AgentContext，此处先置空
        record.setProviderTraceId("");
        predictionMapper.insert(record);

        long t0 = System.currentTimeMillis();
        AgentResult result;
        try {
            result = agent.execute(ctx);
        } catch (Exception e) {
            log.error("[Agent] 执行失败: type={} biz={}", type, req.getBizRef(), e);
            record.setStatus(AgentRunStatus.FAILED.getCode());
            record.setErrorMsg(e.getMessage());
            record.setCostMs(System.currentTimeMillis() - t0);
            // P1-4: 即使失败也记录已获取的 providerTraceId（可能 LLM 调用前就失败，则为空）
            record.setProviderTraceId(resolveProviderTraceId(ctx));
            predictionMapper.updateById(record);
            // P2-3: 记录异常终止 span
            tracer.error(traceCtx, e);
            throw new BizException(BizErrorCode.INTERNAL_ERROR, "error.agent.msg_eaf40df5", e.getMessage());
        }
        long cost = System.currentTimeMillis() - t0;
        // P1-4: 从 AgentContext 读取 LLM Provider 返回的 traceId，用于审计/账单核对
        record.setProviderTraceId(resolveProviderTraceId(ctx));
        record.setAlertLevel(result.getAlertLevel() == null ? AgentAlertLevel.NORMAL.getCode()
                : result.getAlertLevel().getCode());
        record.setScore(result.getScore());
        record.setConfidence(result.getConfidence());
        record.setSuggestion(result.getSuggestion());
        record.setMatchedRules(result.getMatchedRules() == null ? null
                : safeJson(result.getMatchedRules()));
        record.setOutputResult(safeJson(result.getPayload()));
        record.setCostMs(cost);
        record.setStatus(AgentRunStatus.SUCCESS.getCode());
        predictionMapper.updateById(record);

        // P2-3: 结束 Agent 链路追踪
        tracer.endAgent(traceCtx, safeJson(result.getPayload()), true);

        log.info("[Agent] 执行成功: type={} biz={} score={} level={} cost={}ms",
                type, req.getBizRef(), result.getScore(), result.getAlertLevel(), cost);
        return record;
    }

    /**
     * 从 AgentContext 安全提取 providerTraceId（P1-4）。
     *
     * @param ctx Agent 上下文
     * @return providerTraceId；为空时返回空字符串
     */
    private String resolveProviderTraceId(AgentContext ctx) {
        return ctx != null && StringUtils.hasText(ctx.getProviderTraceId())
                ? ctx.getProviderTraceId() : "";
    }

    /**
     * Sentinel 限流 BlockException 处理
     *
     * @param req 原始请求
     * @param ex  限流异常
     * @return 不返回（抛出业务异常）
     */
    public AgentPredictionDO runBlockHandler(AgentRunRequestDTO req, BlockException ex) {
        log.warn("[Agent] Sentinel 限流: {}", ex.getClass().getSimpleName());
        throw new BizException(BizErrorCode.RATE_LIMIT, "error.agent.msg_e12dc2f2");
    }

    /**
     * Sentinel 降级 fallback 处理
     *
     * @param req 原始请求
     * @param e   业务异常
     * @return 不返回（抛出业务异常）
     */
    public AgentPredictionDO runFallback(AgentRunRequestDTO req, Throwable e) {
        log.error("[Agent] Sentinel 降级: {}", e.getMessage());
        throw new BizException(BizErrorCode.SERVICE_UNAVAILABLE, "error.agent.msg_8536a322");
    }

    /**
     * 异步执行 Agent（P1-3 修复：显式透传 TenantContext）。
     *
     * <p><b>P1-3 修复</b>：原实现使用 {@code @Async("agentExecutor")}，异步线程不继承主线程的
     * {@link TenantContext}（ThreadLocal），导致 {@code TokenQuotaAspect.resolveTenantId}
     * 在异步场景下读到默认租户，Token 配额归属错误。
     *
     * <p>现移除 {@code @Async}，改为在主线程显式捕获 {@code tenantId}，通过
     * {@link ThreadPoolTaskExecutor#execute} 提交任务，在异步线程中恢复 TenantContext，
     * finally 块中清理避免线程池复用导致租户串号。
     *
     * <p>MDC 上下文（traceId）由线程池的 {@code TaskDecorator}（mdcTaskDecorator）自动透传，
     * 无需在此处处理。
     *
     * @param req Agent 执行请求
     */
    @Override
    public void runAsync(AgentRunRequestDTO req) {
        // P1-3: 主线程捕获 tenantId，避免异步线程丢失 TenantContext
        final String tenantId = TenantContext.getTenantId();
        agentExecutor.execute(() -> {
            try {
                TenantContext.setTenantId(tenantId);
                run(req);
            } catch (Exception e) {
                log.error("[Agent] 异步执行失败: type={} biz={}", req.getAgentType(), req.getBizRef(), e);
            } finally {
                // P1-3: 清理 TenantContext，避免线程池复用导致租户串号
                TenantContext.clear();
            }
        });
    }

    @Override
    public AgentResult executeInMemory(String agentType, AgentContext context) {
        AgentType type = AgentType.fromCode(agentType);
        if (type == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.agent.msg_3e4d9788", agentType);
        }
        Agent agent = findAgent(type);
        return agent.execute(context);
    }

    /**
     * 流式执行 Agent（P2-1 落地，P2-3 增强 Tracing，P2-6 信号收敛）。
     *
     * <p>实现策略：
     * <ol>
     *   <li>查找 Agent</li>
     *   <li>P2-3: 启动 Tracing，创建复合 listener（业务 listener + tracing listener）</li>
     *   <li>若 Agent 实现 {@link StreamableAgent}，调用其 {@code executeStream}</li>
     *   <li>否则降级为同步 {@link Agent#execute} 后包装为 FINAL_ANSWER 事件</li>
     * </ol>
     *
     * <p><b>P2-6 信号收敛</b>：异常路径的 onError + onComplete 信号保证只发送一次。
     * <ul>
     *   <li>StreamableAgent 路径：异常由 Agent 内部负责通知（如 ReActLoop 的
     *       safeNotifyError + safeNotifyComplete），外层 catch 不再重复发送</li>
     *   <li>非 StreamableAgent 路径：execute 异常时由内层 catch 发送一次信号，
     *       外层 catch 仅负责 tracer.error + 重新抛出</li>
     * </ul>
     * 彻底解决原实现中「StreamableAgent 内部已通知 + 外层 catch 再通知」导致的重复信号问题。
     */
    @Override
    public AgentResult executeStream(String agentType, AgentContext context, ReActEventListener listener) {
        if (listener == null) {
            listener = NoOpReActEventListener.getInstance();
        }
        AgentType type = AgentType.fromCode(agentType);
        if (type == null) {
            BizException ex = new BizException(BizErrorCode.BAD_REQUEST,
                    "error.agent.msg_3e4d9788", agentType);
            listener.onError(0, ex);
            listener.onComplete(ReActResult.failure("无效 agentType: " + agentType, List.of()));
            throw ex;
        }
        Agent agent = findAgent(type);

        // P2-3: 启动 Agent 链路追踪 + 创建复合 listener（业务 + tracing）
        TraceContext traceCtx = tracer.startAgent(context);
        ReActEventListener tracingListener = new TracingReActEventListener(tracer, traceCtx);
        ReActEventListener composite = new CompositeReActEventListener(listener, tracingListener);
        // P2-6：信号保护包装器，保证 onError + onComplete 各最多转发一次
        // 解决 StreamableAgent 内部已通知 + 外层 catch 再通知导致的重复信号
        SignalGuardReActEventListener signalGuard = new SignalGuardReActEventListener(composite);

        try {
            AgentResult result;
            if (agent instanceof StreamableAgent streamable) {
                // StreamableAgent 路径：Agent 内部通过 signalGuard 通知信号
                // 若内部已通知 onComplete，外层 catch 再通知会被 signalGuard 幂等丢弃
                result = streamable.executeStream(context, signalGuard);
            } else {
                // 非 StreamableAgent 降级路径：execute + 手动包装为事件
                try {
                    result = agent.execute(context);
                } catch (RuntimeException e) {
                    // P2-6：execute 异常时发送一次信号（signalGuard 保证幂等）
                    signalGuard.onError(0, e);
                    signalGuard.onComplete(ReActResult.failure("执行异常: " + e.getMessage(), List.of()));
                    throw e;
                }
                signalGuard.onFinalAnswer(1, result.getSuggestion() == null ? "" : result.getSuggestion());
                signalGuard.onComplete(ReActResult.success(result.getSuggestion(), List.of()));
            }
            tracer.endAgent(traceCtx, safeJson(result.getPayload()), true);
            return result;
        } catch (RuntimeException e) {
            log.error("[Agent] 流式执行失败: type={} biz={}", type, context.getBizRef(), e);
            // P2-6：信号收敛 — 兜底发送 onError + onComplete
            // 若 Agent 内部已发送，signalGuard 会幂等丢弃，不会重复
            signalGuard.onError(0, e);
            signalGuard.onComplete(ReActResult.failure("执行异常: " + e.getMessage(), List.of()));
            tracer.error(traceCtx, e);
            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public AgentPredictionDO getById(String id) {
        AgentPredictionDO r = predictionMapper.selectById(id);
        if (r == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "error.agent.msg_99e3df42");
        }
        return r;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AgentPredictionDO> page(int page, int size, String agentType, String alertLevel,
                                        String status, String bizType, String bizId) {
        Page<AgentPredictionDO> p = new Page<>(page, size);
        LambdaQueryWrapper<AgentPredictionDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(agentType)) w.eq(AgentPredictionDO::getAgentType, agentType);
        if (StringUtils.hasText(alertLevel)) w.eq(AgentPredictionDO::getAlertLevel, alertLevel);
        if (StringUtils.hasText(status)) w.eq(AgentPredictionDO::getStatus, status);
        if (StringUtils.hasText(bizType)) w.eq(AgentPredictionDO::getBizType, bizType);
        if (StringUtils.hasText(bizId)) w.eq(AgentPredictionDO::getBizId, bizId);
        w.orderByDesc(AgentPredictionDO::getCreatedAt);
        return predictionMapper.selectPage(p, w);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgentPredictionDO> listRecent(String agentType, String alertLevel, Integer limit) {
        if (limit == null || limit <= 0) limit = 20;
        return predictionMapper.selectByAgentType(agentType, alertLevel, limit);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> aggregateByType(String tenantId) {
        if (tenantId == null) tenantId = "1";
        return predictionMapper.aggregateByType(tenantId);
    }

    @Override
    @Transactional(readOnly = true)
    public String countByAlertLevel(String alertLevel, String agentType, String tenantId) {
        if (tenantId == null) tenantId = "1";
        return predictionMapper.countByAlertLevel(alertLevel, agentType, tenantId).toString();
    }

    // ========== 私有方法 ==========

    /**
     * 从已注册的 Agent 列表中查找指定类型的 Agent。
     *
     * @param type Agent 类型
     * @return 匹配的 Agent 实例
     * @throws BizException 当未找到匹配的 Agent 时抛出
     */
    private Agent findAgent(AgentType type) {
        return agents.stream()
                .filter(a -> a.type() == type)
                .findFirst()
                .orElseThrow(() -> new BizException(BizErrorCode.BAD_REQUEST,
                        "未注册 Agent: " + type.getCode()));
    }

    /**
     * 校验 Agent 执行请求。
     *
     * @param req Agent 执行请求
     * @return 解析后的 Agent 类型
     * @throws BizException 当请求为空或 agentType 无效时抛出
     */
    private AgentType validate(AgentRunRequestDTO req) {
        if (req == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.agent.msg_d9712a58");
        }
        AgentType type = AgentType.fromCode(req.getAgentType());
        if (type == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.agent.msg_3e4d9788", req.getAgentType());
        }
        return type;
    }

    /**
     * 构建 Agent 任务编码（P2-2 修复）。
     *
     * <p><b>P2-2 修复</b>：原实现使用 {@code System.currentTimeMillis() % 100000}，
     * 同一毫秒内并发请求会产生相同 taskCode，导致唯一约束冲突。
     * 现改用 {@link SnowflakeIdGenerator#nextIdStr()}（雪花算法，全局唯一、趋势递增），
     * 彻底消除并发重复风险。
     *
     * <p>格式：{@code yyyyMMdd-{agentType}-{bizId}-{snowflakeId}}
     *
     * @param type  Agent 类型
     * @param bizId 业务 ID（可空，空时用 "0" 占位）
     * @return 全局唯一的任务编码
     */
    private String buildTaskCode(AgentType type, String bizId) {
        return LocalDate.now().toString().replace("-", "")
                + "-" + type.getCode() + "-"
                + (bizId == null ? "0" : bizId)
                + "-" + SnowflakeIdGenerator.nextIdStr();
    }

    /**
     * 安全地将对象序列化为 JSON 字符串。
     *
     * <p>序列化失败时降级为 {@code toString()}，不抛出异常。
     *
     * @param o 待序列化对象
     * @return JSON 字符串；入参为 null 时返回 null
     */
    private String safeJson(Object o) {
        if (o == null) return null;
        try {
            return JSON.toJSONString(o);
        } catch (Exception e) {
            return String.valueOf(o);
        }
    }
}
