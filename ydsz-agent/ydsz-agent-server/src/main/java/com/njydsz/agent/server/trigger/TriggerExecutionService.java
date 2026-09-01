package com.njydsz.agent.server.trigger;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.agent.domain.trigger.AgentTrigger;

/**
 * 触发器执行服务。
 *
 * <p>负责将匹配的触发器转换为实际的 Agent 执行调用。
 * 生成执行上下文、记录触发历史、处理执行结果。</p>
 *
 * @author ydsz-agent
 * @since 1.0.0
 */
@Slf4j
public class TriggerExecutionService {

    private final AgentExecutionDelegate executionDelegate;

    public TriggerExecutionService(AgentExecutionDelegate executionDelegate) {
        this.executionDelegate = Objects.requireNonNull(executionDelegate, "executionDelegate 不能为 null");
    }

    /**
     * 执行触发器。
     *
     * @param trigger 匹配的触发器
     * @param context 触发上下文
     */
    public void executeTrigger(AgentTrigger trigger, Map<String, Object> context) {
        String triggerId = trigger.getTriggerId();
        String executionId = generateExecutionId();

        log.info("[TriggerExecution] 开始执行触发器: triggerId={}, executionId={}, targetAgent={}",
                triggerId, executionId, trigger.getTargetAgentCode());

        try {
            // 构建执行请求
            AgentExecutionRequest request = buildExecutionRequest(trigger, context, executionId);

            // 委托执行
            executionDelegate.execute(request);

            log.info("[TriggerExecution] 触发器执行已提交: triggerId={}, executionId={}",
                    triggerId, executionId);

        } catch (Exception e) {
            log.error("[TriggerExecution] 触发器执行失败: triggerId={}, error={}",
                    triggerId, e.getMessage(), e);
            throw new TriggerExecutionException("触发器执行失败: " + triggerId, e);
        }
    }

    /**
     * 构建 Agent 执行请求。
     *
     * @param trigger     触发器配置
     * @param context     触发上下文
     * @param executionId 执行 ID
     * @return 执行请求
     */
    private AgentExecutionRequest buildExecutionRequest(AgentTrigger trigger,
                                                         Map<String, Object> context,
                                                         String executionId) {
        Map<String, Object> metadata = new HashMap<>(context);
        metadata.put("triggerId", trigger.getTriggerId());
        metadata.put("triggerName", trigger.getName());
        metadata.put("triggerType", trigger.getTriggerType().name());
        metadata.put("executionId", executionId);
        metadata.put("triggeredAt", LocalDateTime.now().toString());

        return new AgentExecutionRequest(
                executionId,
                trigger.getTenantId(),
                trigger.getTargetAgentCode(),
                trigger.getTargetAgentType(),
                metadata
        );
    }

    /**
     * 生成执行 ID。
     *
     * @return 唯一执行 ID
     */
    private String generateExecutionId() {
        return "trigger-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Agent 执行委托接口。
     *
     * <p>由 server 层实现，将触发器执行委托给 Agent 执行服务。</p>
     */
    public interface AgentExecutionDelegate {
        /**
         * 执行 Agent。
         *
         * @param request 执行请求
         */
        void execute(AgentExecutionRequest request);
    }

    /**
     * Agent 执行请求。
     */
    public record AgentExecutionRequest(
            String executionId,
            String tenantId,
            String agentCode,
            String agentType,
            Map<String, Object> metadata) {
    }

    /**
     * 触发器执行异常。
     */
    public static class TriggerExecutionException extends RuntimeException {
        public TriggerExecutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
