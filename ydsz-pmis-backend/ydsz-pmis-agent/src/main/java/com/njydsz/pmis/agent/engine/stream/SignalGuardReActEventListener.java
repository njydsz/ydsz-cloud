package com.njydsz.pmis.agent.engine.stream;

import com.njydsz.pmis.agent.engine.react.ReActDecision;
import com.njydsz.pmis.agent.engine.react.ReActResult;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 信号保护监听器（P2-6 落地）。
 *
 * <p>包装另一个 {@link ReActEventListener}，保证 {@link #onComplete(ReActResult)}
 * 与 {@link #onError(int, Throwable)} 各最多转发一次，解决以下重复信号问题：
 *
 * <ul>
 *   <li><b>StreamableAgent 路径</b>：Agent 内部异常路径已调用 onError + onComplete
 *       （如 {@code ReActLoop} 的 safeNotifyError + safeNotifyComplete），
 *       外层 {@code executeStream} 的 catch 块再调用会导致重复信号</li>
 *   <li><b>非 StreamableAgent 路径</b>：execute 异常时内层 catch 发送信号后 throw，
 *       外层 catch 再发送会导致重复信号</li>
 * </ul>
 *
 * <p><b>设计要点</b>：
 * <ul>
 *   <li>{@code onComplete} 用 {@link AtomicBoolean} 保证只转发一次（幂等）</li>
 *   <li>{@code onError} 同样保证只转发一次（异常信号不重复）</li>
 *   <li>其他回调（{@code onStepStart}/{@code onThought}/...）正常转发，不做幂等控制
 *       （这些信号天然不会重复）</li>
 *   <li>线程安全：使用 {@code AtomicBoolean.compareAndSet} 保证多线程场景下的幂等</li>
 * </ul>
 *
 * <p>典型用法：
 * <pre>
 * ReActEventListener guard = new SignalGuardReActEventListener(composite);
 * // 传给 StreamableAgent 或在 catch 块中调用，保证信号不重复
 * streamable.executeStream(ctx, guard);
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-6)
 */
@Slf4j
public class SignalGuardReActEventListener implements ReActEventListener {

    /** 被包装的委托监听器 */
    private final ReActEventListener delegate;

    /** onComplete 是否已转发（幂等标志） */
    private final AtomicBoolean completed = new AtomicBoolean(false);

    /** onError 是否已转发（幂等标志） */
    private final AtomicBoolean errored = new AtomicBoolean(false);

    public SignalGuardReActEventListener(ReActEventListener delegate) {
        this.delegate = delegate;
    }

    @Override
    public void onStepStart(int stepIndex) {
        delegate.onStepStart(stepIndex);
    }

    @Override
    public void onToken(int stepIndex, String tokenDelta) {
        delegate.onToken(stepIndex, tokenDelta);
    }

    @Override
    public void onThought(int stepIndex, String thought) {
        delegate.onThought(stepIndex, thought);
    }

    @Override
    public void onAction(int stepIndex, ReActDecision decision) {
        delegate.onAction(stepIndex, decision);
    }

    @Override
    public void onObservation(int stepIndex, String observation) {
        delegate.onObservation(stepIndex, observation);
    }

    @Override
    public void onFinalAnswer(int stepIndex, String finalAnswer) {
        delegate.onFinalAnswer(stepIndex, finalAnswer);
    }

    @Override
    public void onStepEnd(int stepIndex) {
        delegate.onStepEnd(stepIndex);
    }

    /**
     * 转发 onComplete（幂等：仅第一次调用生效）。
     *
     * <p>后续调用会被静默丢弃并记录 debug 日志，防止重复信号污染下游
     * （如 SSE 客户端收到两次 DONE 事件）。
     */
    @Override
    public void onComplete(ReActResult result) {
        if (completed.compareAndSet(false, true)) {
            delegate.onComplete(result);
        } else {
            log.debug("[SignalGuard] onComplete 已转发过，本次调用被忽略");
        }
    }

    /**
     * 转发 onError（幂等：仅第一次调用生效）。
     *
     * <p>后续调用会被静默丢弃并记录 debug 日志，防止重复错误信号。
     */
    @Override
    public void onError(int stepIndex, Throwable error) {
        if (errored.compareAndSet(false, true)) {
            delegate.onError(stepIndex, error);
        } else {
            log.debug("[SignalGuard] onError 已转发过，本次调用被忽略");
        }
    }

    /** onComplete 是否已转发（用于测试断言） */
    public boolean isCompleted() {
        return completed.get();
    }

    /** onError 是否已转发（用于测试断言） */
    public boolean isErrored() {
        return errored.get();
    }
}
