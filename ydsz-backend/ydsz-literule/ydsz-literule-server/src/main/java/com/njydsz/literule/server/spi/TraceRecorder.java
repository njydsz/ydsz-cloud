package com.njydsz.literule.server.spi;

import java.util.List;

import com.njydsz.literule.api.RuleExecutionTrace;

/**
 * 规则执行轨迹记录器（SPI）
 *
 * <p>由消费方提供实现，将执行轨迹写入 {@code ydsz_rule_execution_trace} 表。
 * literule 模块本身不依赖持久层，通过此接口反转依赖。
 *
 * <p>实现建议：
 * <ul>
 *   <li>使用异步批量写入（如 BlockingQueue + 后台线程）避免阻塞主流程</li>
 *   <li>factsSnapshot/resultSnapshot 应序列化为 JSONB</li>
 *   <li>支持按 traceId/ruleCode/scenario 查询</li>
 *   <li>支持历史 Trace 回放对比</li>
 * </ul>
 *
 * @since 1.0.0
 * @author ydsz-team
 */
public interface TraceRecorder {

    /**
     * 异步记录单条执行轨迹
     *
     * @param trace 轨迹记录
     */
    void record(RuleExecutionTrace trace);

    /**
     * 同步批量记录（用于批量评估场景）
     *
     * @param traces 轨迹列表
     */
    default void recordBatch(List<RuleExecutionTrace> traces) {
        for (RuleExecutionTrace trace : traces) {
            record(trace);
        }
    }

    /**
     * 按 traceId 查询全部规则执行轨迹
     *
     * @param traceId 追踪 ID
     * @return 轨迹列表
     */
    List<RuleExecutionTrace> getByTraceId(String traceId);

    /**
     * 按 ruleCode 查询历史执行轨迹
     *
     * @param ruleCode 规则编码
     * @param limit    最大返回数
     * @return 轨迹列表
     */
    List<RuleExecutionTrace> getByRuleCode(String ruleCode, int limit);

    /**
     * 查询最近的执行轨迹
     *
     * @param limit 最大返回数
     * @return 轨迹列表
     */
    List<RuleExecutionTrace> getRecentTraces(int limit);

    /**
     * 是否启用轨迹记录
     *
     * @return true=启用
     */
    default boolean isEnabled() {
        return true;
    }
}
