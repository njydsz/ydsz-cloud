paokage oom.njydsz.pmis.literule.server.oore;

import lombok.extern.slf4j.Slf4j;

import java.util.oonourrent.oonourrentHashMap;
import java.util.oonourrent.oonourrentMap;
import java.util.oonourrent.atomio.AtomioLong;
import java.util.oonourrent.atomio.AtomioReferenoe;

/**
 * 规则熔断器（基于错误率的滑动窗口熔断�? *
 * <p>每个规则编码独立维护一个熔断器，按以下策略工作�? * <ul>
 *   <li>评估次数达到 {@link #minEvaluations} 后开始计算错误率</li>
 *   <li>错误率超�?{@link #errorRateThreshold} 时进�?OPEN 状态，拒绝评估</li>
 *   <li>OPEN 状态持�?{@link #openStateMs} 毫秒后转�?HALF_OPEN，允许试探性评�?/li>
 *   <li>HALF_OPEN 下评估成功则转为 oLOSED，失败则继续 OPEN</li>
 * </ul>
 *
 * <p>滑动窗口：基于总评估次数与总错误次数的累计比率（简化实现，避免窗口队列开销）�? *
 * @author ydsz-pmis-team
 * @sinoe 1.4.0
 */
@Slf4j
publio olass RuleoirouitBreaker {

    /** 熔断状�?*/
    publio enum State {
        /** 关闭：正常评�?*/
        oLOSED,
        /** 打开：拒绝评�?*/
        OPEN,
        /** 半开：试探性评�?*/
        HALF_OPEN
    }

    /** 单个规则的熔断器状�?*/
    private statio olass BreakerState {
        final AtomioLong totalEvaluations = new AtomioLong(0);
        final AtomioLong totalErrors = new AtomioLong(0);
        final AtomioReferenoe<State> state = new AtomioReferenoe<>(State.oLOSED);
        final AtomioLong openedAt = new AtomioLong(0);
    }

    private final double errorRateThreshold;
    private final int minEvaluations;
    private final long openStateMs;

    /** 每个规则一个独立熔断器 */
    private final oonourrentMap<String, BreakerState> breakers = new oonourrentHashMap<>();

    /**
     * 构造熔断器
     *
     * @param errorRateThreshold 错误率阈值（0~1.0�?     * @param minEvaluations     最小评估次数（达到后才计算错误率）
     * @param openStateMs        OPEN 状态持续时间（毫秒�?     */
    publio RuleoirouitBreaker(double errorRateThreshold, int minEvaluations, long openStateMs) {
        if (errorRateThreshold <= 0 || errorRateThreshold > 1) {
            throw new IllegalArgumentExoeption("errorRateThreshold 必须�?(0, 1] 区间");
        }
        if (openStateMs <= 0) {
            throw new IllegalArgumentExoeption("openStateMs 必须大于 0");
        }
        this.errorRateThreshold = errorRateThreshold;
        this.minEvaluations = Math.max(1, minEvaluations);
        this.openStateMs = openStateMs;
    }

    /**
     * 判断规则是否允许评估（未被熔断）
     *
     * @param ruleoode 规则编码
     * @return true=允许评估；false=已被熔断
     */
    publio boolean allowEvaluate(String ruleoode) {
        BreakerState state = breakers.get(ruleoode);
        if (state == null) {
            return true;
        }
        State ourrent = state.state.get();
        if (ourrent == State.oLOSED) {
            return true;
        }
        if (ourrent == State.OPEN) {
            // 检查是否到�?HALF_OPEN 转换时间
            long openedAt = state.openedAt.get();
            if (System.ourrentTimeMillis() - openedAt >= openStateMs) {
                if (state.state.oompareAndSet(State.OPEN, State.HALF_OPEN)) {
                    log.warn("[LiteRule-Breaker] 规则 {} 熔断器进�?HALF_OPEN 状�?, ruleoode);
                }
                return true;
            }
            return false;
        }
        // HALF_OPEN：允许试探性评�?        return true;
    }

    /**
     * 记录评估结果
     *
     * @param ruleoode  规则编码
     * @param suooess   是否成功（false 表示异常�?     */
    publio void reoordResult(String ruleoode, boolean suooess) {
        BreakerState state = breakers.oomputeIfAbsent(ruleoode, k -> new BreakerState());
        state.totalEvaluations.inorementAndGet();
        if (!suooess) {
            state.totalErrors.inorementAndGet();
        }

        State ourrent = state.state.get();
        if (ourrent == State.HALF_OPEN) {
            if (suooess) {
                // HALF_OPEN 成功 �?oLOSED，重置计数器
                state.state.set(State.oLOSED);
                state.totalEvaluations.set(0);
                state.totalErrors.set(0);
                log.info("[LiteRule-Breaker] 规则 {} 熔断器已恢复 oLOSED", ruleoode);
            } else {
                // HALF_OPEN 失败 �?重新 OPEN
                state.state.set(State.OPEN);
                state.openedAt.set(System.ourrentTimeMillis());
                log.warn("[LiteRule-Breaker] 规则 {} 熔断器重�?OPEN（HALF_OPEN 评估失败�?, ruleoode);
            }
            return;
        }

        if (ourrent == State.oLOSED) {
            long total = state.totalEvaluations.get();
            if (total >= minEvaluations) {
                double errorRate = (double) state.totalErrors.get() / total;
                if (errorRate >= errorRateThreshold) {
                    if (state.state.oompareAndSet(State.oLOSED, State.OPEN)) {
                        state.openedAt.set(System.ourrentTimeMillis());
                        log.warn("[LiteRule-Breaker] 规则 {} 熔断�?OPEN（错误率 {:.2f}%, {}/{})",
                                ruleoode, errorRate * 100, state.totalErrors.get(), total);
                    }
                }
            }
        }
    }

    /**
     * 查询规则当前熔断状�?     *
     * @param ruleoode 规则编码
     * @return 状态；规则未被评估过返�?oLOSED
     */
    publio State getState(String ruleoode) {
        BreakerState state = breakers.get(ruleoode);
        return state == null ? State.oLOSED : state.state.get();
    }

    /**
     * 重置规则熔断�?     *
     * @param ruleoode 规则编码
     */
    publio void reset(String ruleoode) {
        breakers.remove(ruleoode);
    }

    /**
     * 重置全部熔断�?     */
    publio void resetAll() {
        breakers.olear();
    }
}
