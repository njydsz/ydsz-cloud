package com.njydsz.pmis.agent.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.agent.dto.AgentRunRequestDTO;
import com.njydsz.pmis.agent.engine.AgentContext;
import com.njydsz.pmis.agent.engine.AgentResult;
import com.njydsz.pmis.agent.entity.AgentPredictionDO;
import com.njydsz.pmis.agent.mapper.AgentPredictionMapper;
import com.njydsz.pmis.agent.service.AgentService;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
@Tag(name = "AI 智能体")
@RestController
@RequestMapping("/api/v1/agent")
@RequiredArgsConstructor
public class AgentController {

    /** Agent 服务 */
    private final AgentService service;
    /** Agent 预测记录 Mapper */
    private final AgentPredictionMapper predictionMapper;

    /**
     * 同步执行 Agent，结果落库。
     *
     * @param req Agent 执行请求
     * @return 落库后的 Agent 预测记录
     */
    @Operation(summary = "执行 Agent（同步）")
    @PrePermission("agent:task:run")
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
    @PostMapping("/run-async")
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
    @PostMapping("/in-memory")
    public Result<AgentResult> inMemory(@RequestParam String agentType,
                                    @RequestBody AgentContext ctx) {
        return Result.ok(service.executeInMemory(agentType, ctx));
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
    public Result<AgentPredictionDO> get(@PathVariable Long id) {
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
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String agentType,
            @RequestParam(required = false) String alertLevel,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) Long bizId) {
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
            @RequestParam(defaultValue = "20") Integer limit) {
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
    public Result<List<Map<String, Object>>> aggregateByType(@RequestParam(required = false) Long tenantId) {
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
    public Result<Long> countByAlertLevel(
            @RequestParam(required = false) String alertLevel,
            @RequestParam(required = false) String agentType,
            @RequestParam(required = false) Long tenantId) {
        return Result.ok(service.countByAlertLevel(alertLevel, agentType, tenantId));
    }

    /**
     * 批次 21 / P2: AI Agent 执行耗时 P50/P90/P95 统计
     * <p>通过 PostgreSQL percentile_cont 聚合 cost_ms, 性能优于 Java 端排序</p>
     */
    @Operation(summary = "AI Agent 执行耗时统计 (P50/P90/P95)")
    @PrePermission("agent:task:list")
    @GetMapping("/duration-stats")
    public Result<Map<String, Object>> durationStats(
            @RequestParam(required = false) String agentType,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime to,
            @RequestParam(required = false) Long tenantId) {
        return Result.ok(predictionMapper.selectDurationStats(agentType, from, to, tenantId));
    }

    /**
     * 批次 21 / P2: 按 Agent 类型分组的耗时 P50/P95 统计
     */
    @Operation(summary = "按 Agent 类型统计耗时")
    @PrePermission("agent:task:list")
    @GetMapping("/duration-stats/by-agent-type")
    public Result<List<Map<String, Object>>> durationStatsByAgentType(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime to,
            @RequestParam(required = false) Long tenantId) {
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
     * @param body 必填字段：agentType / bizType / bizId；可选：bizRef / params
     * @return Agent 执行结果（payload 字段承载结构化输出）
     */
    @Operation(summary = "[内部] 同步执行 Agent（Feign 入口）")
    @PostMapping("/internal/execute")
    public Result<Map<String, Object>> internalExecute(@RequestBody Map<String, Object> body) {
        if (body == null) {
            return Result.failed(com.njydsz.pmis.common.api.BizErrorCode.BAD_REQUEST, "请求体不能为空");
        }
        String agentType = body.get("agentType") == null ? null : body.get("agentType").toString();
        if (agentType == null) {
            return Result.failed(com.njydsz.pmis.common.api.BizErrorCode.BAD_REQUEST, "agentType 必填");
        }
        String bizType = body.get("bizType") == null ? "INTERNAL" : body.get("bizType").toString();
        Long bizId = body.get("bizId") instanceof Number n ? n.longValue() : 0L;
        String bizRef = body.get("bizRef") == null ? "" : body.get("bizRef").toString();
        @SuppressWarnings("unchecked")
        Map<String, Object> params = body.get("params") instanceof Map<?, ?>
                ? (Map<String, Object>) body.get("params")
                : Map.of();

        AgentContext ctx = new AgentContext();
        ctx.setBizType(bizType);
        ctx.setBizId(bizId);
        ctx.setBizRef(bizRef);
        ctx.setCallerId(null);
        ctx.setCallerName(null);
        ctx.setSource("FEIGN");
        ctx.setParams(params);

        AgentResult result = service.executeInMemory(agentType, ctx);

        Map<String, Object> data = new java.util.LinkedHashMap<>();
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
