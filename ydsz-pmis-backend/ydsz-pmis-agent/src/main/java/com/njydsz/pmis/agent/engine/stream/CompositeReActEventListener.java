package com.njydsz.pmis.agent.engine.stream;

import com.njydsz.pmis.agent.engine.react.ReActDecision;
import com.njydsz.pmis.agent.engine.react.ReActResult;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

/**
 * 复合 ReAct 事件监听器（P2-3 落地）。
 *
 * <p>将多个 {@link ReActEventListener} 组合为一个，所有回调方法遍历全部 listener 调用。
 * 单个 listener 抛异常不影响其他 listener，便于业务（SSE 推送）与基础设施（Tracing）解耦。
 *
 * <p>典型用法：
 * <pre>
 * ReActEventListener composite = new CompositeReActEventListener(
 *     businessListener,         // 业务 SSE 推送
 *     tracingListener           // Tracing 落库
 * );
 * reactLoop.runStream(sys, user, ctx, maxSteps, composite);
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-3)
 */
@Slf4j
public class CompositeReActEventListener implements ReActEventListener {

    private final ReActEventListener[] listeners;

    public CompositeReActEventListener(ReActEventListener... listeners) {
        this.listeners = listeners == null ? new ReActEventListener[0] : listeners;
    }

    @Override
    public void onStepStart(int stepIndex) {
        for (ReActEventListener l : listeners) {
            try {
                l.onStepStart(stepIndex);
            } catch (Exception e) {
                log.warn("[CompositeListener] onStepStart 异常: listener={} err={}",
                        l.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    @Override
    public void onThought(int stepIndex, String thought) {
        for (ReActEventListener l : listeners) {
            try {
                l.onThought(stepIndex, thought);
            } catch (Exception e) {
                log.warn("[CompositeListener] onThought 异常: listener={} err={}",
                        l.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    @Override
    public void onAction(int stepIndex, ReActDecision decision) {
        for (ReActEventListener l : listeners) {
            try {
                l.onAction(stepIndex, decision);
            } catch (Exception e) {
                log.warn("[CompositeListener] onAction 异常: listener={} err={}",
                        l.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    @Override
    public void onObservation(int stepIndex, String observation) {
        for (ReActEventListener l : listeners) {
            try {
                l.onObservation(stepIndex, observation);
            } catch (Exception e) {
                log.warn("[CompositeListener] onObservation 异常: listener={} err={}",
                        l.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    @Override
    public void onFinalAnswer(int stepIndex, String finalAnswer) {
        for (ReActEventListener l : listeners) {
            try {
                l.onFinalAnswer(stepIndex, finalAnswer);
            } catch (Exception e) {
                log.warn("[CompositeListener] onFinalAnswer 异常: listener={} err={}",
                        l.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    @Override
    public void onStepEnd(int stepIndex) {
        for (ReActEventListener l : listeners) {
            try {
                l.onStepEnd(stepIndex);
            } catch (Exception e) {
                log.warn("[CompositeListener] onStepEnd 异常: listener={} err={}",
                        l.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    @Override
    public void onComplete(ReActResult result) {
        for (ReActEventListener l : listeners) {
            try {
                l.onComplete(result);
            } catch (Exception e) {
                log.warn("[CompositeListener] onComplete 异常: listener={} err={}",
                        l.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    @Override
    public void onError(int stepIndex, Throwable error) {
        for (ReActEventListener l : listeners) {
            try {
                l.onError(stepIndex, error);
            } catch (Exception e) {
                log.warn("[CompositeListener] onError 异常: listener={} err={}",
                        l.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    /**
     * 返回内部 listener 数量（用于测试断言）。
     */
    public int size() {
        return listeners.length;
    }

    /**
     * 返回内部 listener 数组副本（用于测试断言）。
     */
    public ReActEventListener[] listeners() {
        return Arrays.copyOf(listeners, listeners.length);
    }
}
