package com.njydsz.pmis.agent.controller;

import com.njydsz.pmis.agent.entity.AgentTraceDO;
import com.njydsz.pmis.agent.mapper.AgentTraceMapper;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Agent 链路追踪 Controller。
 *
 * <p>提供按 traceId 查询完整链路、按业务维度查询最近 trace 的接口，
 * 供前端链路追踪可视化页面使用。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Tag(name = "AI 智能体 - 链路追踪")
@RestController
@RequestMapping("/agent/trace")
@RequiredArgsConstructor
public class AgentTraceController {

    /** Agent 链路追踪 Mapper */
    private final AgentTraceMapper traceMapper;

    /**
     * 按 traceId 查询完整链路。
     *
     * @param traceId 链路 ID
     * @return span 列表（按时间顺序）
     */
    @Operation(summary = "按 traceId 查询链路")
    @PrePermission("agent:task:list")
    @GetMapping("/{traceId}")
    public Result<List<AgentTraceDO>> getByTraceId(@PathVariable String traceId) {
        return Result.ok(traceMapper.selectByTraceId(traceId));
    }

    /**
     * 按业务维度查询最近 trace。
     *
     * @param bizType 业务类型
     * @param bizId   业务 ID
     * @param limit   返回条数（默认 50）
     * @return span 列表
     */
    @Operation(summary = "按业务查询最近 trace")
    @PrePermission("agent:task:list")
    @GetMapping("/recent")
    public Result<List<AgentTraceDO>> recentByBiz(
            @RequestParam String bizType,
            @RequestParam String bizId,
            @RequestParam(defaultValue = "50") int limit) {
        return Result.ok(traceMapper.selectByBiz(bizType, bizId, limit));
    }
}
