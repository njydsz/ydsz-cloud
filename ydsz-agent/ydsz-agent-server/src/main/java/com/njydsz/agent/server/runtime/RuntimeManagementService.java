package com.njydsz.agent.server.runtime;

import com.njydsz.agent.domain.runtime.RuntimeSession;
import com.njydsz.agent.domain.runtime.RuntimeSessionStatus;
import com.njydsz.agent.domain.runtime.RuntimeSessionStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 运行时管理服务。
 *
 * <p>提供运行时会话的全生命周期管理能力，包括：</p>
 * <ul>
 *   <li>会话注册与状态追踪</li>
 *   <li>活跃会话查询与监控</li>
 *   <li>强制回收（force-recycle）卡住的执行</li>
 *   <li>会话超时检测与自动清理</li>
 * </ul>
 *
 * <p>借鉴 MateClaw 的 Admin Runtime Console 设计，为运维人员提供
 * 实时查看所有 Agent 执行状态的能力，并支持一键强制回收。</p>
 *
 * @author ydsz-agent
 * @since 1.0.0
 */
@Slf4j
@Service
public class RuntimeManagementService {

    private static final String STEP_INITIALIZING = "initializing";
    private static final String STEP_THINKING = "thinking";
    private static final String STEP_TOOL_CALL = "tool_call";
    private static final String STEP_OBSERVATION = "observation";
    private static final String STEP_COMPLETING = "completing";
    private static final String STEP_WAITING_APPROVAL = "waiting_approval";

    private final RuntimeSessionStore sessionStore;
    private final ConcurrentHashMap<String, Thread> executionThreads = new ConcurrentHashMap<>();

    public RuntimeManagementService(RuntimeSessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    /**
     * 注册一个新的 Agent 执行会话。
     *
     * @param tenantId  租户 ID
     * @param userId    用户 ID
     * @param agentCode Agent 编码
     * @param agentType Agent 类型
     * @param model     使用的模型
     * @param source    调用来源
     * @return 生成的执行 ID
     */
    public String registerSession(String tenantId, String userId, String agentCode,
                                  String agentType, String model, String source) {
        String executionId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();

        RuntimeSession session = RuntimeSession.builder()
                .executionId(executionId)
                .agentCode(agentCode)
                .agentType(agentType)
                .tenantId(tenantId)
                .userId(userId)
                .model(model)
                .status(RuntimeSessionStatus.PENDING.getCode())
                .currentStep(STEP_INITIALIZING)
                .currentIteration(0)
                .maxIterations(10)
                .totalTokens(0)
                .costUsd(0.0)
                .startTime(now)
                .lastActiveTime(now)
                .source(source)
                .build();

        sessionStore.save(session);
        log.info("注册 Agent 执行会话: executionId={}, agentCode={}, tenantId={}",
                executionId, agentCode, tenantId);
        return executionId;
    }

    /**
     * 更新会话执行进度。
     *
     * @param executionId 执行 ID
     * @param step        当前步骤
     * @param iteration   当前迭代次数
     */
    public void updateProgress(String executionId, String step, int iteration) {
        RuntimeSession existing = sessionStore.findByExecutionId(executionId).orElse(null);
        if (existing == null) {
            return;
        }
        RuntimeSession updated = RuntimeSession.builder()
                .executionId(existing.getExecutionId())
                .conversationId(existing.getConversationId())
                .agentCode(existing.getAgentCode())
                .agentType(existing.getAgentType())
                .tenantId(existing.getTenantId())
                .userId(existing.getUserId())
                .model(existing.getModel())
                .status(RuntimeSessionStatus.RUNNING.getCode())
                .currentStep(step)
                .currentIteration(iteration)
                .maxIterations(existing.getMaxIterations())
                .totalTokens(existing.getTotalTokens())
                .costUsd(existing.getCostUsd())
                .startTime(existing.getStartTime())
                .lastActiveTime(LocalDateTime.now())
                .source(existing.getSource())
                .build();
        sessionStore.save(updated);
    }

    /**
     * 更新会话 Token 消耗和成本。
     *
     * @param executionId 执行 ID
     * @param totalTokens 累计 Token 数
     * @param costUsd     累计成本（USD）
     */
    public void updateTokenUsage(String executionId, int totalTokens, double costUsd) {
        RuntimeSession existing = sessionStore.findByExecutionId(executionId).orElse(null);
        if (existing == null) {
            return;
        }
        RuntimeSession updated = RuntimeSession.builder()
                .executionId(existing.getExecutionId())
                .conversationId(existing.getConversationId())
                .agentCode(existing.getAgentCode())
                .agentType(existing.getAgentType())
                .tenantId(existing.getTenantId())
                .userId(existing.getUserId())
                .model(existing.getModel())
                .status(existing.getStatus())
                .currentStep(existing.getCurrentStep())
                .currentIteration(existing.getCurrentIteration())
                .maxIterations(existing.getMaxIterations())
                .totalTokens(totalTokens)
                .costUsd(costUsd)
                .startTime(existing.getStartTime())
                .lastActiveTime(LocalDateTime.now())
                .source(existing.getSource())
                .build();
        sessionStore.save(updated);
    }

    /**
     * 标记会话为等待审批状态。
     *
     * @param executionId 执行 ID
     */
    public void markWaitingApproval(String executionId) {
        RuntimeSession existing = sessionStore.findByExecutionId(executionId).orElse(null);
        if (existing == null) {
            return;
        }
        RuntimeSession updated = RuntimeSession.builder()
                .executionId(existing.getExecutionId())
                .conversationId(existing.getConversationId())
                .agentCode(existing.getAgentCode())
                .agentType(existing.getAgentType())
                .tenantId(existing.getTenantId())
                .userId(existing.getUserId())
                .model(existing.getModel())
                .status(RuntimeSessionStatus.WAITING.getCode())
                .currentStep(STEP_WAITING_APPROVAL)
                .currentIteration(existing.getCurrentIteration())
                .maxIterations(existing.getMaxIterations())
                .totalTokens(existing.getTotalTokens())
                .costUsd(existing.getCostUsd())
                .startTime(existing.getStartTime())
                .lastActiveTime(LocalDateTime.now())
                .source(existing.getSource())
                .build();
        sessionStore.save(updated);
    }

    /**
     * 标记会话执行完成。
     *
     * @param executionId 执行 ID
     */
    public void markCompleted(String executionId) {
        RuntimeSession existing = sessionStore.findByExecutionId(executionId).orElse(null);
        if (existing == null) {
            return;
        }
        RuntimeSession completed = RuntimeSession.builder()
                .executionId(existing.getExecutionId())
                .conversationId(existing.getConversationId())
                .agentCode(existing.getAgentCode())
                .agentType(existing.getAgentType())
                .tenantId(existing.getTenantId())
                .userId(existing.getUserId())
                .model(existing.getModel())
                .status(RuntimeSessionStatus.COMPLETED.getCode())
                .currentStep(STEP_COMPLETING)
                .currentIteration(existing.getCurrentIteration())
                .maxIterations(existing.getMaxIterations())
                .totalTokens(existing.getTotalTokens())
                .costUsd(existing.getCostUsd())
                .startTime(existing.getStartTime())
                .lastActiveTime(LocalDateTime.now())
                .source(existing.getSource())
                .build();
        sessionStore.save(completed);
        executionThreads.remove(executionId);
        log.info("Agent 执行完成: executionId={}, duration={}ms, tokens={}, cost=${}",
                executionId, completed.getElapsedMillis(), completed.getTotalTokens(),
                String.format("%.4f", completed.getCostUsd()));
    }

    /**
     * 标记会话执行失败。
     *
     * @param executionId 执行 ID
     * @param errorMessage 错误信息
     */
    public void markFailed(String executionId, String errorMessage) {
        RuntimeSession existing = sessionStore.findByExecutionId(executionId).orElse(null);
        if (existing == null) {
            return;
        }
        RuntimeSession failed = RuntimeSession.builder()
                .executionId(existing.getExecutionId())
                .conversationId(existing.getConversationId())
                .agentCode(existing.getAgentCode())
                .agentType(existing.getAgentType())
                .tenantId(existing.getTenantId())
                .userId(existing.getUserId())
                .model(existing.getModel())
                .status(RuntimeSessionStatus.FAILED.getCode())
                .currentStep(existing.getCurrentStep())
                .currentIteration(existing.getCurrentIteration())
                .maxIterations(existing.getMaxIterations())
                .totalTokens(existing.getTotalTokens())
                .costUsd(existing.getCostUsd())
                .startTime(existing.getStartTime())
                .lastActiveTime(LocalDateTime.now())
                .source(existing.getSource())
                .errorMessage(errorMessage)
                .build();
        sessionStore.save(failed);
        executionThreads.remove(executionId);
        log.warn("Agent 执行失败: executionId={}, error={}", executionId, errorMessage);
    }

    /**
     * 注册执行线程，用于强制回收。
     *
     * @param executionId 执行 ID
     * @param thread      执行线程
     */
    public void registerExecutionThread(String executionId, Thread thread) {
        if (executionId != null && thread != null) {
            executionThreads.put(executionId, thread);
        }
    }

    /**
     * 强制回收（中断）指定的执行会话。
     *
     * <p>通过中断执行线程来停止 Agent 运行。被中断的会话状态将变为 CANCELLED。
     * 此操作不可逆，请确保在必要时才使用。</p>
     *
     * @param executionId 执行 ID
     * @return true 如果成功回收，false 如果会话不存在或已结束
     */
    public boolean forceRecycle(String executionId) {
        RuntimeSession existing = sessionStore.findByExecutionId(executionId).orElse(null);
        if (existing == null) {
            log.warn("强制回收失败，会话不存在: executionId={}", executionId);
            return false;
        }
        if (!existing.isActive()) {
            log.warn("强制回收失败，会话已不活跃: executionId={}, status={}",
                    executionId, existing.getStatus());
            return false;
        }

        Thread thread = executionThreads.get(executionId);
        if (thread != null && thread.isAlive()) {
            thread.interrupt();
            log.info("已中断执行线程: executionId={}", executionId);
        }

        RuntimeSession cancelled = RuntimeSession.builder()
                .executionId(existing.getExecutionId())
                .conversationId(existing.getConversationId())
                .agentCode(existing.getAgentCode())
                .agentType(existing.getAgentType())
                .tenantId(existing.getTenantId())
                .userId(existing.getUserId())
                .model(existing.getModel())
                .status(RuntimeSessionStatus.CANCELLED.getCode())
                .currentStep(existing.getCurrentStep())
                .currentIteration(existing.getCurrentIteration())
                .maxIterations(existing.getMaxIterations())
                .totalTokens(existing.getTotalTokens())
                .costUsd(existing.getCostUsd())
                .startTime(existing.getStartTime())
                .lastActiveTime(LocalDateTime.now())
                .source(existing.getSource())
                .errorMessage("强制回收: 管理员手动取消")
                .build();
        sessionStore.save(cancelled);
        executionThreads.remove(executionId);
        log.info("强制回收成功: executionId={}, agentCode={}, duration={}ms",
                executionId, existing.getAgentCode(), cancelled.getElapsedMillis());
        return true;
    }

    /**
     * 查询所有活跃会话。
     *
     * @return 活跃会话列表
     */
    public List<RuntimeSession> getActiveSessions() {
        return sessionStore.findActiveSessions();
    }

    /**
     * 查询指定租户的活跃会话。
     *
     * @param tenantId 租户 ID
     * @return 该租户的活跃会话列表
     */
    public List<RuntimeSession> getActiveSessionsByTenant(String tenantId) {
        return sessionStore.findActiveSessionsByTenant(tenantId);
    }

    /**
     * 查询会话详情。
     *
     * @param executionId 执行 ID
     * @return 会话详情 Optional
     */
    public Optional<RuntimeSession> getSession(String executionId) {
        return sessionStore.findByExecutionId(executionId);
    }

    /**
     * 查询最近会话列表。
     *
     * @param limit 返回数量上限
     * @return 会话列表
     */
    public List<RuntimeSession> getRecentSessions(int limit) {
        return sessionStore.findAll(limit);
    }

    /**
     * 获取运行时概览统计。
     *
     * @return 统计信息 Map
     */
    public Map<String, Object> getOverviewStats() {
        long activeCount = sessionStore.countActive();
        List<RuntimeSession> allSessions = sessionStore.findAll(1000);

        long runningCount = allSessions.stream()
                .filter(s -> RuntimeSessionStatus.RUNNING.getCode().equals(s.getStatus()))
                .count();
        long waitingCount = allSessions.stream()
                .filter(s -> RuntimeSessionStatus.WAITING.getCode().equals(s.getStatus()))
                .count();
        long completedCount = allSessions.stream()
                .filter(s -> RuntimeSessionStatus.COMPLETED.getCode().equals(s.getStatus()))
                .count();
        long failedCount = allSessions.stream()
                .filter(s -> RuntimeSessionStatus.FAILED.getCode().equals(s.getStatus()))
                .count();

        double totalCost = allSessions.stream()
                .mapToDouble(RuntimeSession::getCostUsd)
                .sum();
        int totalTokens = allSessions.stream()
                .mapToInt(RuntimeSession::getTotalTokens)
                .sum();

        return Map.of(
                "activeSessions", activeCount,
                "runningSessions", runningCount,
                "waitingSessions", waitingCount,
                "completedSessions", completedCount,
                "failedSessions", failedCount,
                "totalCostUsd", String.format("%.4f", totalCost),
                "totalTokens", totalTokens,
                "registeredThreads", executionThreads.size()
        );
    }

    /**
     * 清理已完成的会话（定期调用，防止内存泄漏）。
     *
     * @param maxAgeMinutes 最大保留时长（分钟），超过此时长的非活跃会话将被清理
     * @return 清理的会话数量
     */
    public int cleanupStaleSessions(int maxAgeMinutes) {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(maxAgeMinutes);
        List<RuntimeSession> allSessions = sessionStore.findAll(10000);
        int cleaned = 0;

        for (RuntimeSession session : allSessions) {
            if (!session.isActive() && session.getLastActiveTime() != null
                    && session.getLastActiveTime().isBefore(cutoff)) {
                sessionStore.remove(session.getExecutionId());
                cleaned++;
            }
        }

        if (cleaned > 0) {
            log.info("清理过期会话: count={}, maxAgeMinutes={}", cleaned, maxAgeMinutes);
        }
        return cleaned;
    }
}
