package com.njydsz.pmis.common.chaos;

import com.njydsz.pmis.common.featureflag.FeatureFlag;
import com.njydsz.pmis.common.featureflag.FeatureFlagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.ConnectException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 混沌工程服务 (批次 20 P3-1)
 *
 * <p>核心能力:
 * <ul>
 *   <li>注册 / 注销混沌实验 (admin 接口)</li>
 *   <li>在受保护的业务方法前调用 {@link #maybeInject(String)} 决定是否注入故障</li>
 *   <li>所有实验均受 FeatureFlag {@code CANARY_DEPLOY} 保护, 生产环境默认关闭</li>
 *   <li>注入统计 (触发次数 / 跳过次数) 用于复盘</li>
 * </ul>
 *
 * <h3>典型用法</h3>
 * <pre>{@code
 * @Autowired
 * private ChaosService chaosService;
 *
 * public ContractVO getContract(Long id) {
 *     chaosService.maybeInject("ContractService.getContract");
 *     // ... 正常业务逻辑
 * }
 * }</pre>
 *
 * <h3>注意事项</h3>
 * <ul>
 *   <li>生产环境必须 {@code CANARY_DEPLOY=false}, 由 admin 显式开启</li>
 *   <li>建议配合熔断器 (Sentinel) 使用, 防止故障扩散</li>
 *   <li>每次实验应有 owner / scope / rollback 计划</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (批次20)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChaosService {

    /** 实验注册表: key=target, value=实验配置 */
    private final Map<String, ChaosExperiment> experiments = new ConcurrentHashMap<>();

    /** 实验历史: 仅保留最近 100 条 */
    private final List<ChaosEvent> history = new CopyOnWriteArrayList<>();

    /** 特性开关服务，用于 CANARY_DEPLOY 二次保护 */
    private final FeatureFlagService featureFlagService;

    /**
     * 注册一个混沌实验.
     *
     * @param experiment 实验配置（target 必填，否则抛 IllegalArgumentException）
     */
    public void register(ChaosExperiment experiment) {
        if (experiment == null || experiment.getTarget() == null) {
            throw new IllegalArgumentException("混沌实验 target 必填");
        }
        experiments.put(experiment.getTarget(), experiment);
        log.info("[Chaos] 注册实验: target={} type={} createdBy={}",
                experiment.getTarget(), experiment.getType(), experiment.getCreatedBy());
    }

    /**
     * 注销一个实验.
     *
     * @param target 实验目标标识；为 null 时直接返回
     */
    public void unregister(String target) {
        if (target == null) return;
        ChaosExperiment removed = experiments.remove(target);
        if (removed != null) {
            log.info("[Chaos] 注销实验: target={}", target);
        }
    }

    /**
     * 列出当前所有实验.
     *
     * @return 当前已注册实验的不可变快照
     */
    public List<ChaosExperiment> list() {
        return List.copyOf(experiments.values());
    }

    /**
     * 注入决策: 业务代码在方法入口调用.
     *
     * @param target 当前方法/类标识
     * @return 实际注入的 outcome
     */
    public ChaosOutcome maybeInject(String target) {
        ChaosExperiment exp = experiments.get(target);
        if (exp == null || !exp.isEnabled()) {
            return ChaosOutcome.NOT_TRIGGERED;
        }
        // Feature flag 二次保护
        if (!featureFlagService.isEnabled(FeatureFlag.CANARY_DEPLOY)) {
            recordHistory(target, ChaosOutcome.BLOCKED_BY_FLAG, "Feature flag 关闭");
            return ChaosOutcome.BLOCKED_BY_FLAG;
        }
        // 概率判定
        if (exp.getErrorRate() != null && exp.getErrorRate() < 1.0) {
            double p = ThreadLocalRandom.current().nextDouble();
            if (p > exp.getErrorRate()) {
                recordHistory(target, ChaosOutcome.SKIPPED_PROBABILITY,
                        "未命中概率 " + p + " > " + exp.getErrorRate());
                return ChaosOutcome.SKIPPED_PROBABILITY;
            }
        }
        // 实际注入
        inject(exp);
        recordHistory(target, ChaosOutcome.INJECTED, "已注入 " + exp.getType());
        return ChaosOutcome.INJECTED;
    }

    /**
     * 实际执行故障注入：按实验类型分发到延迟 / 异常 / 网络分区 / 资源耗尽 / 错误率分支
     *
     * @param exp 混沌实验配置
     */
    private void inject(ChaosExperiment exp) {
        switch (exp.getType()) {
            case ChaosExperiment.TYPE_LATENCY -> {
                long ms = exp.getLatencyMs() != null ? exp.getLatencyMs() : 1000L;
                try {
                    Thread.sleep(ms);
                    log.warn("[Chaos] 注入延迟 {}ms @ {}", ms, exp.getTarget());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            case ChaosExperiment.TYPE_EXCEPTION -> {
                String cls = exp.getExceptionClass() != null ? exp.getExceptionClass()
                        : "java.lang.RuntimeException";
                log.warn("[Chaos] 注入异常 {} @ {}", cls, exp.getTarget());
                throw instantiate(cls, "Chaos injected: " + exp.getDescription());
            }
            case ChaosExperiment.TYPE_NETWORK_PARTITION -> {
                log.warn("[Chaos] 模拟网络分区 @ {}", exp.getTarget());
                throw new RuntimeException(
                        "Chaos: network partition at " + exp.getTarget(),
                        new ConnectException("simulated"));
            }
            case ChaosExperiment.TYPE_RESOURCE_EXHAUSTION -> {
                log.warn("[Chaos] 模拟资源耗尽 @ {}", exp.getTarget());
                throw new OutOfMemoryError("Chaos: simulated OOM at " + exp.getTarget());
            }
            case ChaosExperiment.TYPE_ERROR_RATE -> {
                // 概率型错误已在外层判定, 此分支理论上不会单独命中
                log.warn("[Chaos] 注入 error_rate @ {}", exp.getTarget());
                throw new RuntimeException("Chaos: error rate triggered at " + exp.getTarget());
            }
            default -> log.warn("[Chaos] 未知实验类型: {}", exp.getType());
        }
    }

    /**
     * 反射实例化异常类；非 RuntimeException 时回退为 RuntimeException 包装
     *
     * @param className 异常类全限定名
     * @param message   异常消息
     * @return 构造完成的 RuntimeException 实例
     */
    private static RuntimeException instantiate(String className, String message) {
        try {
            Class<?> cls = Class.forName(className);
            if (RuntimeException.class.isAssignableFrom(cls)) {
                return (RuntimeException) cls.getConstructor(String.class).newInstance(message);
            }
            // 非 RuntimeException 走 RuntimeException 包装
            return new RuntimeException(message + " (wrapped from " + className + ")");
        } catch (Exception e) {
            return new RuntimeException(message + " (fallback RuntimeException, original=" + className + ")", e);
        }
    }

    /**
     * 记录一条实验历史，超过 100 条时丢弃最旧的一条
     *
     * @param target  实验目标标识
     * @param outcome 实验结果
     * @param detail  附加说明
     */
    private void recordHistory(String target, ChaosOutcome outcome, String detail) {
        history.add(new ChaosEvent(System.currentTimeMillis(), target, outcome, detail));
        if (history.size() > 100) {
            history.remove(0);
        }
    }

    /**
     * 获取最近 100 条实验历史
     *
     * @return 实验历史的不可变快照
     */
    public List<ChaosEvent> recentHistory() {
        return List.copyOf(history);
    }

    /** 清空历史 */
    public void clearHistory() {
        history.clear();
    }

    /** 单条事件 */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class ChaosEvent {
        /** 事件时间戳（毫秒） */
        private final long timestamp;
        /** 实验目标标识 */
        private final String target;
        /** 实验结果 */
        private final ChaosOutcome outcome;
        /** 附加说明 */
        private final String detail;
    }
}
