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

    private final AgentService service;
    private final AgentPredictionMapper predictionMapper;

    @Operation(summary = "执行 Agent（同步）")
    @PrePermission("agent:task:run")
    @PostMapping("/run")
    public Result<AgentPredictionDO> run(@Valid @RequestBody AgentRunRequestDTO req) {
        return Result.ok(service.run(req));
    }

    @Operation(summary = "执行 Agent（异步）")
    @PrePermission("agent:task:run")
    @PostMapping("/run-async")
    public Result<Void> runAsync(@Valid @RequestBody AgentRunRequestDTO req) {
        service.runAsync(req);
        return Result.ok();
    }

    @Operation(summary = "内存执行（不落库）")
    @PrePermission("agent:task:run")
    @PostMapping("/in-memory")
    public Result<AgentResult> inMemory(@RequestParam String agentType,
                                    @RequestBody AgentContext ctx) {
        return Result.ok(service.executeInMemory(agentType, ctx));
    }

    @Operation(summary = "记录详情")
    @PrePermission("agent:task:view")
    @GetMapping("/{id}")
    public Result<AgentPredictionDO> get(@PathVariable Long id) {
        return Result.ok(service.getById(id));
    }

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

    @Operation(summary = "最近记录")
    @PrePermission("agent:task:list")
    @GetMapping("/recent")
    public Result<List<AgentPredictionDO>> recent(
            @RequestParam(required = false) String agentType,
            @RequestParam(required = false) String alertLevel,
            @RequestParam(defaultValue = "20") Integer limit) {
        return Result.ok(service.listRecent(agentType, alertLevel, limit));
    }

    @Operation(summary = "按类型/告警等级聚合")
    @PrePermission("agent:task:list")
    @GetMapping("/aggregate/type")
    public Result<List<Map<String, Object>>> aggregateByType(@RequestParam(required = false) Long tenantId) {
        return Result.ok(service.aggregateByType(tenantId));
    }

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
}
