package com.njydsz.pmis.literule.server.core;

import java.util.Map;

import com.njydsz.pmis.literule.api.RuleResult;

/**
 * 规则断点调试 Hook（P2-3）
 *
 * <p>规则引擎在每条规则评估前后回调该接口，支撑断点调试、单步执行、上下文快照等能力。
 * 典型实现：
 * <ul>
 *   <li><b>在线调试器</b>：在 {@link #onBeforeEvaluate(BreakpointContext)} 中阻塞等待用户操作
 *       （继续 / 单步 / 查看变量），实现 IDE 风格的规则调试体验</li>
 *   <li><b>审计快照</b>：在 {@link #onAfterEvaluate(BreakpointContext)} 中持久化上下文与结果快照，
 *       用于线上问题复盘</li>
 *   <li><b>动态插桩</b>：仅在指定规则编码上启用断点，避免对所有规则产生性能开销</li>
 * </ul>
 *
 * <p>实现注意事项：
 * <ol>
 *   <li>Hook 调用发生在规则评估的关键路径上，{@code onBeforeEvaluate} 必须尽快返回
 *       （典型耗时 &lt; 1ms），阻塞式调试需要使用独立线程</li>
 *   <li>Hook 抛出的异常将被引擎吞掉并记录日志，不会影响规则评估流程</li>
 *   <li>Hook 是 SPI 接口，由应用层（如 literule-debug 模块）提供实现，引擎层不依赖具体实现</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
public interface BreakpointHook {

    /**
     * 规则评估前回调
     *
     * <p>引擎在调用 {@link Rule#evaluate(RuleContext)} 之前触发。
     * 实现可以在此处：
     * <ul>
     *   <li>记录断点命中事件，等待用户继续/单步指令</li>
     *   <li>修改 {@link BreakpointContext#getFacts()} 注入测试数据（慎用）</li>
     *   <li>返回 {@link BreakpointAction#SUSPEND} 挂起执行（需要异步唤醒机制）</li>
     * </ul>
     *
     * @param context 断点上下文（已填充 ruleCode/ruleName/facts/phase=BEFORE）
     * @return 断点动作：CONTINUE 继续 / SUSPEND 挂起 / STEP_OVER 单步跳过
     */
    default BreakpointAction onBeforeEvaluate(BreakpointContext context) {
        return BreakpointAction.CONTINUE;
    }

    /**
     * 规则评估后回调
     *
     * <p>引擎在 {@link Rule#evaluate(RuleContext)} 返回之后触发，
     * {@link BreakpointContext#getResult()} 已填充评估结果（可能为 null，表示异常或未触发）。
     *
     * @param context 断点上下文（已填充 result/elapsedMs/exception/phase=AFTER）
     */
    default void onAfterEvaluate(BreakpointContext context) {
        // 默认空实现
    }

    /**
     * 检查指定规则是否启用了断点
     *
     * <p>引擎在评估循环中先调用此方法判断是否需要触发 hook，
     * 避免对未设置断点的规则产生性能开销。
     *
     * @param ruleCode 规则编码
     * @return 是否启用断点
     */
    default boolean hasBreakpoint(String ruleCode) {
        return false;
    }

    /**
     * 断点动作
     */
    enum BreakpointAction {
        /** 继续执行（默认） */
        CONTINUE,
        /** 挂起当前规则评估，等待外部唤醒 */
        SUSPEND,
        /** 单步跳过：跳过当前规则评估，进入下一条规则 */
        STEP_OVER
    }

    /**
     * 断点上下文
     *
     * <p>封装规则评估的上下文快照，传递给 {@link BreakpointHook}。
     * 评估前调用时 phase=BEFORE，result=null；评估后调用时 phase=AFTER，result/elapsedMs/exception 已填充。
     *
     * <p>facts 字段是 facts Map 的可变副本，允许 hook 修改（用于在线注入测试数据），
     * 修改会反映到后续规则评估中。
     */
    class BreakpointContext {
        /** 评估阶段：BEFORE / AFTER */
        private final String phase;
        private final String traceId;
        private final String ruleCode;
        private final String ruleName;
        private final String scenario;
        private final Map<String, Object> facts;
        private RuleResult result;
        private long elapsedMs;
        private Throwable exception;

        public BreakpointContext(String phase, String traceId, String ruleCode, String ruleName,
                                  String scenario, Map<String, Object> facts) {
            this.phase = phase;
            this.traceId = traceId;
            this.ruleCode = ruleCode;
            this.ruleName = ruleName;
            this.scenario = scenario;
            this.facts = facts;
        }

        public String getPhase() { return phase; }
        public String getTraceId() { return traceId; }
        public String getRuleCode() { return ruleCode; }
        public String getRuleName() { return ruleName; }
        public String getScenario() { return scenario; }
        public Map<String, Object> getFacts() { return facts; }
        public RuleResult getResult() { return result; }
        public void setResult(RuleResult result) { this.result = result; }
        public long getElapsedMs() { return elapsedMs; }
        public void setElapsedMs(long elapsedMs) { this.elapsedMs = elapsedMs; }
        public Throwable getException() { return exception; }
        public void setException(Throwable exception) { this.exception = exception; }
    }
}
