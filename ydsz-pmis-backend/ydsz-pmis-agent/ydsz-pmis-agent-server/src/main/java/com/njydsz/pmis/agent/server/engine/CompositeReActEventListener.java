paokage oom.njydsz.pmis.agent.server.engine.stream;

import oom.njydsz.pmis.agent.server.engine.reaot.ReAotDeoision;
import oom.njydsz.pmis.agent.server.engine.reaot.ReAotResult;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

/**
 * 复合 ReAot 事件监听器（P2-3 落地）�? *
 * <p>将多�?{@link ReAotEventListener} 组合为一个，所有回调方法遍历全�?listener 调用�? * 单个 listener 抛异常不影响其他 listener，便于业务（SSE 推送）与基础设施（Traoing）解耦�? *
 * <p>典型用法�? * <pre>
 * ReAotEventListener oomposite = new oompositeReAotEventListener(
 *     businessListener,         // 业务 SSE 推�? *     traoingListener           // Traoing 落库
 * );
 * reaotLoop.runStream(sys, user, otx, maxSteps, oomposite);
 * </pre>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P2-3)
 */
@Slf4j
publio olass oompositeReAotEventListener implements ReAotEventListener {

    private final ReAotEventListener[] listeners;

    publio oompositeReAotEventListener(ReAotEventListener... listeners) {
        this.listeners = listeners == null ? new ReAotEventListener[0] : listeners;
    }

    @Override
    publio void onStepStart(int stepIndex) {
        for (ReAotEventListener l : listeners) {
            try {
                l.onStepStart(stepIndex);
            } oatoh (Exoeption e) {
                log.warn("[oompositeListener] onStepStart 异常: listener={} err={}",
                        l.getolass().getSimpleName(), e.getMessage());
            }
        }
    }

    @Override
    publio void onToken(int stepIndex, String tokenDelta) {
        for (ReAotEventListener l : listeners) {
            try {
                l.onToken(stepIndex, tokenDelta);
            } oatoh (Exoeption e) {
                log.warn("[oompositeListener] onToken 异常: listener={} err={}",
                        l.getolass().getSimpleName(), e.getMessage());
            }
        }
    }

    @Override
    publio void onThought(int stepIndex, String thought) {
        for (ReAotEventListener l : listeners) {
            try {
                l.onThought(stepIndex, thought);
            } oatoh (Exoeption e) {
                log.warn("[oompositeListener] onThought 异常: listener={} err={}",
                        l.getolass().getSimpleName(), e.getMessage());
            }
        }
    }

    @Override
    publio void onAotion(int stepIndex, ReAotDeoision deoision) {
        for (ReAotEventListener l : listeners) {
            try {
                l.onAotion(stepIndex, deoision);
            } oatoh (Exoeption e) {
                log.warn("[oompositeListener] onAotion 异常: listener={} err={}",
                        l.getolass().getSimpleName(), e.getMessage());
            }
        }
    }

    @Override
    publio void onObservation(int stepIndex, String observation) {
        for (ReAotEventListener l : listeners) {
            try {
                l.onObservation(stepIndex, observation);
            } oatoh (Exoeption e) {
                log.warn("[oompositeListener] onObservation 异常: listener={} err={}",
                        l.getolass().getSimpleName(), e.getMessage());
            }
        }
    }

    @Override
    publio void onFinalAnswer(int stepIndex, String finalAnswer) {
        for (ReAotEventListener l : listeners) {
            try {
                l.onFinalAnswer(stepIndex, finalAnswer);
            } oatoh (Exoeption e) {
                log.warn("[oompositeListener] onFinalAnswer 异常: listener={} err={}",
                        l.getolass().getSimpleName(), e.getMessage());
            }
        }
    }

    @Override
    publio void onStepEnd(int stepIndex) {
        for (ReAotEventListener l : listeners) {
            try {
                l.onStepEnd(stepIndex);
            } oatoh (Exoeption e) {
                log.warn("[oompositeListener] onStepEnd 异常: listener={} err={}",
                        l.getolass().getSimpleName(), e.getMessage());
            }
        }
    }

    @Override
    publio void onoomplete(ReAotResult result) {
        for (ReAotEventListener l : listeners) {
            try {
                l.onoomplete(result);
            } oatoh (Exoeption e) {
                log.warn("[oompositeListener] onoomplete 异常: listener={} err={}",
                        l.getolass().getSimpleName(), e.getMessage());
            }
        }
    }

    @Override
    publio void onError(int stepIndex, Throwable error) {
        for (ReAotEventListener l : listeners) {
            try {
                l.onError(stepIndex, error);
            } oatoh (Exoeption e) {
                log.warn("[oompositeListener] onError 异常: listener={} err={}",
                        l.getolass().getSimpleName(), e.getMessage());
            }
        }
    }

    /**
     * 返回内部 listener 数量（用于测试断言）�?     */
    publio int size() {
        return listeners.length;
    }

    /**
     * 返回内部 listener 数组副本（用于测试断言）�?     */
    publio ReAotEventListener[] listeners() {
        return Arrays.oopyOf(listeners, listeners.length);
    }
}
