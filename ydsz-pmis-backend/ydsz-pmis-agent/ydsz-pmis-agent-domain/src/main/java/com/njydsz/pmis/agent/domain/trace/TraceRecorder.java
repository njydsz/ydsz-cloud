package com.njydsz.pmis.agent.domain.trace;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent 执行链路记录器接口
 *
 * <p>记录 Agent 每一步的执行过程，用于调试和可观测性。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface TraceRecorder {

    /**
     * 开始一个新的执行链路
     *
     * @param conversationId 对话 ID
     * @param agentId        Agent ID
     * @return 链路 ID
     */
    String startTrace(String conversationId, String agentId);

    /**
     * 记录一个执行步骤
     *
     * @param traceId    链路 ID
     * @param stepType   步骤类型（LLM_CALL / TOOL_CALL / THOUGHT / OBSERVATION）
     * @param content    步骤内容
     * @param input      步骤输入
     * @param output     步骤输出
     * @param durationMs 耗时（毫秒）
     */
    void recordStep(String traceId, String stepType, String content,
                    Object input, Object output, long durationMs);

    /**
     * 结束执行链路
     *
     * @param traceId 链路 ID
     * @param status  最终状态（SUCCESS / FAILED / MAX_ITERATIONS）
     */
    void endTrace(String traceId, String status);

    /**
     * 获取链路步骤列表
     *
     * @param traceId 链路 ID
     * @return 步骤列表
     */
    List<TraceStep> getSteps(String traceId);

    /**
     * 执行步骤记录
     */
    final class TraceStep {
        private final String traceId;
        private final int stepIndex;
        private final String stepType;
        private final String content;
        private final Object input;
        private final Object output;
        private final long durationMs;
        private final LocalDateTime createdAt;

        public TraceStep(String traceId, int stepIndex, String stepType, String content,
                         Object input, Object output, long durationMs, LocalDateTime createdAt) {
            this.traceId = traceId;
            this.stepIndex = stepIndex;
            this.stepType = stepType;
            this.content = content;
            this.input = input;
            this.output = output;
            this.durationMs = durationMs;
            this.createdAt = createdAt;
        }

        public String getTraceId() { return traceId; }
        public int getStepIndex() { return stepIndex; }
        public String getStepType() { return stepType; }
        public String getContent() { return content; }
        public Object getInput() { return input; }
        public Object getOutput() { return output; }
        public long getDurationMs() { return durationMs; }
        public LocalDateTime getCreatedAt() { return createdAt; }
    }
}
