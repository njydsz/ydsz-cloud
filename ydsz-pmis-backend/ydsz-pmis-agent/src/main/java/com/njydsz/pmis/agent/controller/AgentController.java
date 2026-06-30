package com.njydsz.pmis.agent.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.agent.dto.AgentRunRequestDTO;
import com.njydsz.pmis.agent.engine.AgentContext;
import com.njydsz.pmis.agent.engine.AgentResult;
import com.njydsz.pmis.agent.entity.AgentPredictionDO;
import com.njydsz.pmis.agent.service.AgentService;
import com.njydsz.pmis.common.api.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    @Operation(summary = "执行 Agent（同步）")
    @PostMapping("/run")
    public R<AgentPredictionDO> run(@Valid @RequestBody AgentRunRequestDTO req) {
        return R.ok(service.run(req));
    }

    @Operation(summary = "执行 Agent（异步）")
    @PostMapping("/run-async")
    public R<Void> runAsync(@Valid @RequestBody AgentRunRequestDTO req) {
        service.runAsync(req);
        return R.ok();
    }

    @Operation(summary = "内存执行（不落库）")
    @PostMapping("/in-memory")
    public R<AgentResult> inMemory(@RequestParam String agentType,
                                    @RequestBody AgentContext ctx) {
        return R.ok(service.executeInMemory(agentType, ctx));
    }

    @Operation(summary = "记录详情")
    @GetMapping("/{id}")
    public R<AgentPredictionDO> get(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }

    @Operation(summary = "分页查询")
    @GetMapping("/page")
    public R<Page<AgentPredictionDO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String agentType,
            @RequestParam(required = false) String alertLevel,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) Long bizId) {
        return R.ok(service.page(page, size, agentType, alertLevel, status, bizType, bizId));
    }

    @Operation(summary = "最近记录")
    @GetMapping("/recent")
    public R<List<AgentPredictionDO>> recent(
            @RequestParam(required = false) String agentType,
            @RequestParam(required = false) String alertLevel,
            @RequestParam(defaultValue = "20") Integer limit) {
        return R.ok(service.listRecent(agentType, alertLevel, limit));
    }

    @Operation(summary = "按类型/告警等级聚合")
    @GetMapping("/aggregate/type")
    public R<List<Map<String, Object>>> aggregateByType(@RequestParam(required = false) Long tenantId) {
        return R.ok(service.aggregateByType(tenantId));
    }

    @Operation(summary = "告警计数")
    @GetMapping("/count")
    public R<Long> countByAlertLevel(
            @RequestParam(required = false) String alertLevel,
            @RequestParam(required = false) String agentType,
            @RequestParam(required = false) Long tenantId) {
        return R.ok(service.countByAlertLevel(alertLevel, agentType, tenantId));
    }
}
