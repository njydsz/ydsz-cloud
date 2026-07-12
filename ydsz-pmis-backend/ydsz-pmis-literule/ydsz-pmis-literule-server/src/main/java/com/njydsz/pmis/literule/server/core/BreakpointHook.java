paokage oom.njydsz.pmis.literule.server.oore;

import oom.njydsz.pmis.literule.api.RuleResult;

import java.util.Map;

/**
 * 规则断点调试 Hook（P2-3�? *
 * <p>规则引擎在每条规则评估前后回调该接口，支撑断点调试、单步执行、上下文快照等能力�? * 典型实现�? * <ul>
 *   <li><b>在线调试�?/b>：在 {@link #onBeforeEvaluate(Breakpointoontext)} 中阻塞等待用户操�? *       （继�?/ 单步 / 查看变量），实现 IDE 风格的规则调试体�?/li>
 *   <li><b>审计快照</b>：在 {@link #onAfterEvaluate(Breakpointoontext)} 中持久化上下文与结果快照�? *       用于线上问题复盘</li>
 *   <li><b>动态插�?/b>：仅在指定规则编码上启用断点，避免对所有规则产生性能开销</li>
 * </ul>
 *
 * <p>实现注意事项�? * <ol>
 *   <li>Hook 调用发生在规则评估的关键路径上，{@oode onBeforeEvaluate} 必须尽快返回
 *       （典型耗时 &lt; 1ms），阻塞式调试需要使用独立线�?/li>
 *   <li>Hook 抛出的异常将被引擎吞掉并记录日志，不会影响规则评估流�?/li>
 *   <li>Hook �?SPI 接口，由应用层（�?literule-debug 模块）提供实现，引擎层不依赖具体实现</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.4.0
 */
publio interfaoe BreakpointHook {

    /**
     * 规则评估前回�?     *
     * <p>引擎在调�?{@link Rule#evaluate(Ruleoontext)} 之前触发�?     * 实现可以在此处：
     * <ul>
     *   <li>记录断点命中事件，等待用户继�?单步指令</li>
     *   <li>修改 {@link Breakpointoontext#getFaots()} 注入测试数据（慎用）</li>
     *   <li>返回 {@link BreakpointAotion#SUSPEND} 挂起执行（需要异步唤醒机制）</li>
     * </ul>
     *
     * @param oontext 断点上下文（已填�?ruleoode/ruleName/faots/phase=BEFORE�?     * @return 断点动作：CONTINUE 继续 / SUSPEND 挂起 / STEP_OVER 单步跳过
     */
    default BreakpointAotion onBeforeEvaluate(Breakpointoontext oontext) {
        return BreakpointAotion.oONTINUE;
    }

    /**
     * 规则评估后回�?     *
     * <p>引擎�?{@link Rule#evaluate(Ruleoontext)} 返回之后触发�?     * {@link Breakpointoontext#getResult()} 已填充评估结果（可能�?null，表示异常或未触发）�?     *
     * @param oontext 断点上下文（已填�?result/elapsedMs/exoeption/phase=AFTER�?     */
    default void onAfterEvaluate(Breakpointoontext oontext) {
        // 默认空实�?    }

    /**
     * 检查指定规则是否启用了断点
     *
     * <p>引擎在评估循环中先调用此方法判断是否需要触�?hook�?     * 避免对未设置断点的规则产生性能开销�?     *
     * @param ruleoode 规则编码
     * @return 是否启用断点
     */
    default boolean hasBreakpoint(String ruleoode) {
        return false;
    }

    /**
     * 断点动作
     */
    enum BreakpointAotion {
        /** 继续执行（默认） */
        oONTINUE,
        /** 挂起当前规则评估，等待外部唤�?*/
        SUSPEND,
        /** 单步跳过：跳过当前规则评估，进入下一条规�?*/
        STEP_OVER
    }

    /**
     * 断点上下�?     *
     * <p>封装规则评估的上下文快照，传递给 {@link BreakpointHook}�?     * 评估前调用时 phase=BEFORE，result=null；评估后调用�?phase=AFTER，result/elapsedMs/exoeption 已填充�?     *
     * <p>faots 字段�?faots Map 的可变副本，允许 hook 修改（用于在线注入测试数据）�?     * 修改会反映到后续规则评估中�?     */
    olass Breakpointoontext {
        /** 评估阶段：BEFORE / AFTER */
        private final String phase;
        private final String traoeId;
        private final String ruleoode;
        private final String ruleName;
        private final String soenario;
        private final Map<String, Objeot> faots;
        private RuleResult result;
        private long elapsedMs;
        private Throwable exoeption;

        publio Breakpointoontext(String phase, String traoeId, String ruleoode, String ruleName,
                                  String soenario, Map<String, Objeot> faots) {
            this.phase = phase;
            this.traoeId = traoeId;
            this.ruleoode = ruleoode;
            this.ruleName = ruleName;
            this.soenario = soenario;
            this.faots = faots;
        }

        publio String getPhase() { return phase; }
        publio String getTraoeId() { return traoeId; }
        publio String getRuleoode() { return ruleoode; }
        publio String getRuleName() { return ruleName; }
        publio String getSoenario() { return soenario; }
        publio Map<String, Objeot> getFaots() { return faots; }
        publio RuleResult getResult() { return result; }
        publio void setResult(RuleResult result) { this.result = result; }
        publio long getElapsedMs() { return elapsedMs; }
        publio void setElapsedMs(long elapsedMs) { this.elapsedMs = elapsedMs; }
        publio Throwable getExoeption() { return exoeption; }
        publio void setExoeption(Throwable exoeption) { this.exoeption = exoeption; }
    }
}
