package com.njydsz.pmis.agent.controller.agent;

import com.njydsz.pmis.common.annotation.Idempotent;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.agent.dto.agent.AgentRunRequestDTO;
import com.njydsz.pmis.agent.dto.agent.AgentInternalExecuteDTO;
import com.njydsz.pmis.agent.engine.AgentContext;
import com.njydsz.pmis.agent.engine.AgentResult;
import com.njydsz.pmis.agent.engine.stream.SseEventListener;
import com.njydsz.pmis.agent.entity.hitl.AgentPredictionDO;
import com.njydsz.pmis.agent.mapper.hitl.AgentPredictionMapper;
import com.njydsz.pmis.agent.service.agent.AgentService;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.constant.AsyncExecutorNames;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.validation.annotation.Validated;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * AI 智能体 Controller
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Tag(name = "AI 智能体")
@RestController
@RequestMapping("/agent")
@Validated
public class AgentController {

    /** Agent 服务 */
    private final AgentService service;
    /** Agent 预测记录 Mapper */
    private final AgentPredictionMapper predictionMapper;
    /** SSE 流式输出专用线程池（复用 agentExecutor，避免新增线程池） */
    private final ThreadPoolTaskExecutor streamExecutor;

    /**
     * 构造注入。
     *
     * @param service           Agent 服务
     * @param predictionMapper  Agent 预测记录 Mapper
     * @param streamExecutor    SSE 流式执行线程池（Bean name = {@link AsyncExecutorNames#AGENT}）
     */
    public AgentController(AgentService service,
                          AgentPredictionMapper predictionMapper,
                          @Qualifier(AsyncExecutorNames.AGENT) ThreadPoolTaskExecutor streamExecutor) {
        this.service = service;
        this.predictionMapper = predictionMapper;
        this.streamExecutor = streamExecutor;
    }

    /**
     * 同步执行 Agent，结果落库。
     *
     * @param req Agent 执行请求
     * @return 落库后的 Agent 预测记录
     */
    @Operation(summary = "执行 Agent（同步）")
    @PrePermission("agent:task:run")
    @Idempotent(key = "agent:run", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/run")
    public Result<AgentPredictionDO> run(@Valid @RequestBody AgentRunRequestDTO req) {
        return Result.ok(service.run(req));
    }

    /**
     * 异步执行 Agent，立即返回，结果落库后通过查询获取。
     *
     * @param req Agent 执行请求
     * @return 空结果
     */
    @Operation(summary = "执行 Agent（异步）")
    @PrePermission("agent:task:run")
    @Idempotent(key = "agent:runAsync", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/runAsync")
    public Result<Void> runAsync(@Valid @RequestBody AgentRunRequestDTO req) {
        service.runAsync(req);
        return Result.ok();
    }

    /**
     * 内存执行 Agent（不落库），用于实时交互场景。
     *
     * @param agentType Agent 类型（AgentType.code）
     * @param ctx       Agent 执行上下文
     * @return Agent 执行结果
     */
    @Operation(summary = "内存执行（不落库）")
    @PrePermission("agent:task:run")
    @Idempotent(key = "agent:inMemory", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/inMemory")
    public Result<AgentResult> inMemory(@RequestParam String agentType,
                                    @Valid @RequestBody AgentContext ctx) {
        return Result.ok(service.executeInMemory(agentType, ctx));
    }

    /**
     * 流式执行 Agent（P2-1 落地）。
     *
     * <p>通过 SSE 推送 ReAct 推理过程事件，前端可实时展示「思考中 → 调用工具 → 观察 → 最终回答」。
     * 对标 Coze / Dify 的 Chat Stream API。
     *
     * <p>SSE 事件类型：
     * <ul>
     *   <li>{@code STEP_START}      - 步骤开始</li>
     *   <li>{@code THOUGHT}         - LLM 思考文本</li>
     *   <li>{@code ACTION}          - LLM 决策动作（含工具名 + 参数）</li>
     *   <li>{@code OBSERVATION}     - 工具执行结果</li>
     *   <li>{@code FINAL_ANSWER}   - 最终答案</li>
     *   <li>{@code STEP_END}       - 步骤结束</li>
     *   <li>{@code DONE}           - 整个循环完成</li>
     *   <li>{@code ERROR}          - 异常终止</li>
     * </ul>
     *
     * <p>使用示例（前端 EventSource）：
     * <pre>
     *   const es = new EventSource('/agent/run/stream?agentType=FLOW_GENERATOR', {
     *     method: 'POST',
     *     body: JSON.stringify({...ctx})
     *   });
     *   es.addEventListener('THOUGHT', e =&gt; console.log(JSON.parse(e.data)));
     *   es.addEventListener('DONE', e =&gt; es.close());
     * </pre>
     *
     * <p><b>注意</b>：标准 EventSource 仅支持 GET，POST 场景需使用 fetch + ReadableStream 或
     * 第三方库（如 @microsoft/fetch-event-source）。
     *
     * @param agentType Agent 类型（AgentType.code）
     * @param ctx       Agent 执行上下文
     * @return SseEmitter，Spring MVC 会自动处理 SSE 流
     */
    @Operation(summary = "流式执行 Agent（SSE）")
    @PrePermission("agent:task:run")
    @Idempotent(key = "agent:runStream", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping(value = "/run/stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter runStream(@RequestParam String agentType,
                                @Valid @RequestBody AgentContext ctx) {
        // 超时 5 分钟（覆盖多步 ReAct + LLM 调用）
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);
        SseEventListener listener = new SseEventListener(emitter);

        // 设置超时和错误回调
        emitter.onTimeout(() -> {
            log.warn("[SSE] agentType={} biz={} 超时", agentType, ctx == null ? null : ctx.getBizRef());
            try {
                emitter.complete();
            } catch (Exception ignore) {
                // emitter 已关闭
            }
        });
        emitter.onError(throwable -> {
            log.warn("[SSE] agentType={} biz={} 客户端异常: {}",
                    agentType, ctx == null ? null : ctx.getBizRef(), throwable.getMessage());
        });

        // 异步执行 ReAct 循环
        streamExecutor.submit(() -> {
            try {
                service.executeStream(agentType, ctx, listener);
            } catch (Exception e) {
                log.error("[SSE] agentType={} biz={} 执行异常",
                        agentType, ctx == null ? null : ctx.getBizRef(), e);
                listener.onError(0, e);
                try {
                    emitter.completeWithError(e);
                } catch (Exception ignore) {
                    // emitter 已关闭
                }
            }
        });
        return emitter;
    }

    /**
     * 查询 Agent 预测记录详情。
     *
     * @param id 记录 ID
     * @return Agent 预测记录
     */
    @Operation(summary = "记录详情")
    @PrePermission("agent:task:view")
    @GetMapping("/{id}")
    public Result<AgentPredictionDO> get(@PathVariable String id) {
        return Result.ok(service.getById(id));
    }

    /**
     * 分页查询 Agent 预测记录。
     *
     * @param page        页码（从 1 开始）
     * @param size        每页大小
     * @param agentType   Agent 类型（可空）
     * @param alertLevel  告警等级（可空）
     * @param status      执行状态（可空）
     * @param bizType     关联业务类型（可空）
     * @param bizId       关联业务 ID（可空）
     * @return 分页结果
     */
    @Operation(summary = "分页查询")
    @PrePermission("agent:task:list")
    @GetMapping("/page")
    public Result<Page<AgentPredictionDO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String agentType,
            @RequestParam(required = false) String alertLevel,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) String bizId) {
        return Result.ok(service.page(page, size, agentType, alertLevel, status, bizType, bizId));
    }

    /**
     * 查询最近的 Agent 预测记录。
     *
     * @param agentType  Agent 类型（可空）
     * @param alertLevel 告警等级（可空）
     * @param limit      返回条数，默认 20
     * @return 最近记录列表
     */
    @Operation(summary = "最近记录")
    @PrePermission("agent:task:list")
    @GetMapping("/recent")
    public Result<List<AgentPredictionDO>> recent(
            @RequestParam(required = false) String agentType,
            @RequestParam(required = false) String alertLevel,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer limit) {
        return Result.ok(service.listRecent(agentType, alertLevel, limit));
    }

    /**
     * 按 Agent 类型与告警等级聚合计数。
     *
     * @param tenantId 租户 ID（可空）
     * @return 聚合结果列表
     */
    @Operation(summary = "按类型/告警等级聚合")
    @PrePermission("agent:task:list")
    @GetMapping("/aggregate/type")
    public Result<List<Map<String, Object>>> aggregateByType(@RequestParam(required = false) String tenantId) {
        return Result.ok(service.aggregateByType(tenantId));
    }

    /**
     * 按告警等级统计 Agent 记录数量。
     *
     * @param alertLevel 告警等级（可空）
     * @param agentType  Agent 类型（可空）
     * @param tenantId   租户 ID（可空）
     * @return 记录数量
     */
    @Operation(summary = "告警计数")
    @PrePermission("agent:task:list")
    @GetMapping("/count")
    public Result<String> countByAlertLevel(
            @RequestParam(required = false) String alertLevel,
            @RequestParam(required = false) String agentType,
            @RequestParam(required = false) String tenantId) {
        return Result.ok(service.countByAlertLevel(alertLevel, agentType, tenantId));
    }

    /**
     * 批次 21 / P2: AI Agent 执行耗时 P50/P90/P95 统计
     * <p>通过 PostgreSQL percentile_cont 聚合 cost_ms, 性能优于 Java 端排序</p>
     */
    @Operation(summary = "AI Agent 执行耗时统计 (P50/P90/P95)")
    @PrePermission("agent:task:list")
    @GetMapping("/durationStats")
    public Result<Map<String, Object>> durationStats(
            @RequestParam(required = false) String agentType,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime to,
            @RequestParam(required = false) String tenantId) {
        return Result.ok(predictionMapper.selectDurationStats(agentType, from, to, tenantId));
    }

    /**
     * 批次 21 / P2: 按 Agent 类型分组的耗时 P50/P95 统计
     */
    @Operation(summary = "按 Agent 类型统计耗时")
    @PrePermission("agent:task:list")
    @GetMapping("/durationStats/byAgentType")
    public Result<List<Map<String, Object>>> durationStatsByAgentType(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime to,
            @RequestParam(required = false) String tenantId) {
        return Result.ok(predictionMapper.selectDurationStatsByAgentType(from, to, tenantId));
    }

    // ===========================================
    // P2-1: 内部供 Feign 调用的 execute 接口
    // ===========================================

    /**
     * P2-1: 内部 execute 端点（供其他模块 Feign 调用，不走落库）
     *
     * <p>与 {@link #inMemory} 区别：直接接收 Map 形式参数，内部构造 AgentContext，
     * 方便跨模块调用，避免各业务方都去学习 AgentContext 结构。
     *
     * <p><b>安全加固</b>：校验 {@code X-Internal-Sig} 请求头，确保请求来自网关或内部 Feign 调用，
     * 拦截外部直接访问。Feign 拦截器 {@code PmisFeignInterceptor} 会自动透传该头。
     *
     * @param dto          必填字段：agentType / bizType / bizId；可选：bizRef / params
     * @param internalSig  内部签名头（由网关注入或 Feign 拦截器透传）
     * @return Agent 执行结果（payload 字段承载结构化输出）
     */
    @Operation(summary = "[内部] 同步执行 Agent（Feign 入口）")
    @Idempotent(key = "agent:internalExecute", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/internal/execute")
    public Result<Map<String, Object>> internalExecute(
            @Valid @RequestBody AgentInternalExecuteDTO dto,
            @RequestHeader(value = "X-Internal-Sig", required = false) String internalSig) {
        // 安全加固：校验内部签名头，拦截绕过网关/Feign 的外部直接调用
        if (internalSig == null || internalSig.isBlank()) {
            log.warn("[Security] internal/execute 被外部直接调用，缺少 X-Internal-Sig 头");
            return Result.failed(403, "禁止外部直接访问内部接口");
        }
        // @NotBlank 已校验 agentType 非空，移除手动校验
        String agentType = dto.getAgentType();
        String bizType = dto.getBizType() == null ? "INTERNAL" : dto.getBizType();
        String bizId = dto.getBizId() == null ? "0" : dto.getBizId();
        String bizRef = dto.getBizRef() == null ? "" : dto.getBizRef();
        Map<String, Object> params = dto.getParams() == null ? Map.of() : dto.getParams();

        AgentContext ctx = new AgentContext();
        ctx.setBizType(bizType);
        ctx.setBizId(bizId);
        ctx.setBizRef(bizRef);
        ctx.setCallerId(null);
        ctx.setCallerName(null);
        ctx.setSource("FEIGN");
        ctx.setParams(params);

        AgentResult result = service.executeInMemory(agentType, ctx);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("agentType", result.getAgentType() == null ? null : result.getAgentType().getCode());
        data.put("alertLevel", result.getAlertLevel() == null ? null : result.getAlertLevel().getCode());
        data.put("score", result.getScore());
        data.put("confidence", result.getConfidence());
        data.put("suggestion", result.getSuggestion());
        data.put("matchedRules", result.getMatchedRules());
        data.put("payload", result.getPayload());
        return Result.ok(data);
    }
}
