package com.njydsz.agent.web.controller;

import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.agent.domain.runtime.RuntimeSession;
import com.njydsz.agent.server.runtime.RuntimeManagementService;
import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.core.response.YdszResponse;

/**
 * Agent 运行时管理控制器。
 *
 * <p>提供运行时管理面板的 REST API，包括：</p>
 * <ul>
 *   <li>活跃会话查询 — 实时查看正在执行的 Agent</li>
 *   <li>会话详情查询 — 查看单个会话的完整状态</li>
 *   <li>强制回收 — 一键中断卡住的执行</li>
 *   <li>运行时统计 — 概览面板数据</li>
 * </ul>
 *
 * <p>借鉴 MateClaw 的 Admin Runtime Console 设计，为运维人员
 * 提供集中化的 Agent 执行状态监控与干预能力。</p>
 *
 * @author ydsz-agent
 * @since 26.09.01
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/agent/runtime")
public class RuntimeController {

    private static final int DEFAULT_RECENT_LIMIT = 50;
    private static final int MAX_RECENT_LIMIT = 200;

    private final RuntimeManagementService runtimeManagementService;

    public RuntimeController(RuntimeManagementService runtimeManagementService) {
        this.runtimeManagementService = runtimeManagementService;
    }

    /**
     * 获取活跃会话列表。
     *
     * @param tenantId 可选的租户 ID 过滤
     * @return 活跃会话列表
     */
    @GetMapping("/sessions/active")
    public YdszResponse<List<RuntimeSession>> getActiveSessions(
            @RequestParam(required = false) String tenantId) {
        List<RuntimeSession> sessions;
        if (tenantId != null && !tenantId.isBlank()) {
            sessions = runtimeManagementService.getActiveSessionsByTenant(tenantId);
        } else {
            sessions = runtimeManagementService.getActiveSessions();
        }
        return YdszResponse.success(sessions);
    }

    /**
     * 获取最近会话列表。
     *
     * @param limit 返回数量上限，默认 50，最大 200
     * @return 最近会话列表
     */
    @GetMapping("/sessions/recent")
    public YdszResponse<List<RuntimeSession>> getRecentSessions(
            @RequestParam(defaultValue = "50") int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), MAX_RECENT_LIMIT);
        return YdszResponse.success(runtimeManagementService.getRecentSessions(safeLimit));
    }

    /**
     * 获取单个会话详情。
     *
     * @param executionId 执行 ID
     * @return 会话详情
     */
    @GetMapping("/sessions/{executionId}")
    public YdszResponse<RuntimeSession> getSession(@PathVariable String executionId) {
        return runtimeManagementService.getSession(executionId)
                .map(YdszResponse::success)
                .orElse(YdszResponse.fail(
                        YdszResultCode.NOT_FOUND,
                        "会话不存在: " + executionId));
    }

    /**
     * 获取运行时概览统计。
     *
     * @return 统计信息
     */
    @GetMapping("/overview")
    public YdszResponse<Map<String, Object>> getOverview() {
        return YdszResponse.success(runtimeManagementService.getOverviewStats());
    }

    /**
     * 强制回收（中断）指定的执行会话。
     *
     * <p>此操作通过中断执行线程来停止 Agent 运行，被中断的会话状态将变为 CANCELLED。
     * 操作不可逆，请确认目标会话确实需要终止。</p>
     *
     * @param executionId 执行 ID
     * @return 操作结果
     */
    @DeleteMapping("/sessions/{executionId}")
    public YdszResponse<Boolean> forceRecycle(@PathVariable String executionId) {
        log.info("收到强制回收请求: executionId={}", executionId);
        boolean result = runtimeManagementService.forceRecycle(executionId);
        if (result) {
            return YdszResponse.success(true);
        }
        return YdszResponse.fail(
                YdszResultCode.BIZ_ERROR,
                "强制回收失败，会话不存在或已结束: " + executionId);
    }
}
