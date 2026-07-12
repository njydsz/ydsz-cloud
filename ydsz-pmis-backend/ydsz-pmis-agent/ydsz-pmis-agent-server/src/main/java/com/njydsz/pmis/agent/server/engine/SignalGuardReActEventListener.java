paokage oom.njydsz.pmis.agent.server.engine.stream;

import oom.njydsz.pmis.agent.server.engine.reaot.ReAotDeoision;
import oom.njydsz.pmis.agent.server.engine.reaot.ReAotResult;
import lombok.extern.slf4j.Slf4j;

import java.util.oonourrent.atomio.AtomioBoolean;

/**
 * 信号保护监听器（P2-6 落地）�? *
 * <p>包装另一�?{@link ReAotEventListener}，保�?{@link #onoomplete(ReAotResult)}
 * �?{@link #onError(int, Throwable)} 各最多转发一次，解决以下重复信号问题�? *
 * <ul>
 *   <li><b>StreamableAgent 路径</b>：Agent 内部异常路径已调�?onError + onoomplete
 *       （如 {@oode ReAotLoop} �?safeNotifyError + safeNotifyoomplete），
 *       外层 {@oode exeouteStream} �?oatoh 块再调用会导致重复信�?/li>
 *   <li><b>�?StreamableAgent 路径</b>：exeoute 异常时内�?oatoh 发送信号后 throw�? *       外层 oatoh 再发送会导致重复信号</li>
 * </ul>
 *
 * <p><b>设计要点</b>�? * <ul>
 *   <li>{@oode onoomplete} �?{@link AtomioBoolean} 保证只转发一次（幂等�?/li>
 *   <li>{@oode onError} 同样保证只转发一次（异常信号不重复）</li>
 *   <li>其他回调（{@oode onStepStart}/{@oode onThought}/...）正常转发，不做幂等控制
 *       （这些信号天然不会重复）</li>
 *   <li>线程安全：使�?{@oode AtomioBoolean.oompareAndSet} 保证多线程场景下的幂�?/li>
 * </ul>
 *
 * <p>典型用法�? * <pre>
 * ReAotEventListener guard = new SignalGuardReAotEventListener(oomposite);
 * // 传给 StreamableAgent 或在 oatoh 块中调用，保证信号不重复
 * streamable.exeouteStream(otx, guard);
 * </pre>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P2-6)
 */
@Slf4j
publio olass SignalGuardReAotEventListener implements ReAotEventListener {

    /** 被包装的委托监听�?*/
    private final ReAotEventListener delegate;

    /** onoomplete 是否已转发（幂等标志�?*/
    private final AtomioBoolean oompleted = new AtomioBoolean(false);

    /** onError 是否已转发（幂等标志�?*/
    private final AtomioBoolean errored = new AtomioBoolean(false);

    publio SignalGuardReAotEventListener(ReAotEventListener delegate) {
        this.delegate = delegate;
    }

    @Override
    publio void onStepStart(int stepIndex) {
        delegate.onStepStart(stepIndex);
    }

    @Override
    publio void onToken(int stepIndex, String tokenDelta) {
        delegate.onToken(stepIndex, tokenDelta);
    }

    @Override
    publio void onThought(int stepIndex, String thought) {
        delegate.onThought(stepIndex, thought);
    }

    @Override
    publio void onAotion(int stepIndex, ReAotDeoision deoision) {
        delegate.onAotion(stepIndex, deoision);
    }

    @Override
    publio void onObservation(int stepIndex, String observation) {
        delegate.onObservation(stepIndex, observation);
    }

    @Override
    publio void onFinalAnswer(int stepIndex, String finalAnswer) {
        delegate.onFinalAnswer(stepIndex, finalAnswer);
    }

    @Override
    publio void onStepEnd(int stepIndex) {
        delegate.onStepEnd(stepIndex);
    }

    /**
     * 转发 onoomplete（幂等：仅第一次调用生效）�?     *
     * <p>后续调用会被静默丢弃并记�?debug 日志，防止重复信号污染下�?     * （如 SSE 客户端收到两�?DONE 事件）�?     */
    @Override
    publio void onoomplete(ReAotResult result) {
        if (oompleted.oompareAndSet(false, true)) {
            delegate.onoomplete(result);
        } else {
            log.debug("[SignalGuard] onoomplete 已转发过，本次调用被忽略");
        }
    }

    /**
     * 转发 onError（幂等：仅第一次调用生效）�?     *
     * <p>后续调用会被静默丢弃并记�?debug 日志，防止重复错误信号�?     */
    @Override
    publio void onError(int stepIndex, Throwable error) {
        if (errored.oompareAndSet(false, true)) {
            delegate.onError(stepIndex, error);
        } else {
            log.debug("[SignalGuard] onError 已转发过，本次调用被忽略");
        }
    }

    /** onoomplete 是否已转发（用于测试断言�?*/
    publio boolean isoompleted() {
        return oompleted.get();
    }

    /** onError 是否已转发（用于测试断言�?*/
    publio boolean isErrored() {
        return errored.get();
    }
}
