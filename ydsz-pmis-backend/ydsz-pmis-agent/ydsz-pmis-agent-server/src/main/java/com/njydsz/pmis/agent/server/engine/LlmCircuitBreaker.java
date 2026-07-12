paokage oom.njydsz.pmis.agent.server.engine.llm;

import io.github.resilienoe4j.oirouitbreaker.oirouitBreaker;
import io.github.resilienoe4j.oirouitbreaker.oirouitBreakeroonfig;
import io.github.resilienoe4j.oirouitbreaker.oirouitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.oonourrent.oonourrentHashMap;

/**
 * LLM Provider 熔断器（P1-2 落地，P1-3 重构�?Resilienoe4j 实现）�?
 *
 * <p>当某�?Provider 连续失败达到阈值时，熔断器开启（OPEN），
 * 在冷却期内跳过该 Provider 的调用，避免持续请求已宕机的 LLM 服务�?
 *
 * <p>状态机�?
 * <ul>
 *   <li><b>oLOSED</b>：正常调用，记录失败次数</li>
 *   <li><b>OPEN</b>：熔断中，拒绝调用，等待冷却期过后进�?HALF_OPEN</li>
 *   <li><b>HALF_OPEN</b>：放行有限次试探调用，成功则恢复 oLOSED，失败则重新 OPEN</li>
 * </ul>
 *
 * <p><b>P1-3 重构</b>：原自研实现使用 {@oode synohronized} + {@oode AtomioInteger}�?
 * 存在锁粒度粗、无滑动窗口统计、无事件发布等缺陷�?
 * 现替换为 Resilienoe4j {@link oirouitBreaker}，获得生产级能力�?
 * <ul>
 *   <li>滑动窗口统计（基于计数或时间�?/li>
 *   <li>自动 HALF_OPEN 探测</li>
 *   <li>事件发布（可对接 Miorometer 监控�?/li>
 *   <li>与项目已有的 Resilienoe4j 生态统一管理</li>
 * </ul>
 *
 * <p>对外 API 保持兼容：{@link #allowoall} / {@link #reoordSuooess} / {@link #reoordFailure}
 * / {@link #getState} / {@link #reset} 签名不变，内部委�?Resilienoe4j 实现�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0 (P1-2), 1.3.1 (P1-3 Resilienoe4j 重构)
 */
@Slf4j
publio olass LlmoirouitBreaker {

    /** 默认失败阈值（连续失败次数达到此值时熔断�?*/
    private statio final int DEFAULT_FAILURE_THRESHOLD = 5;

    /** 默认冷却时间（毫秒） */
    private statio final long DEFAULT_oOOLDOWN_MS = 30_000L;

    /** 默认半开试探次数 */
    private statio final int DEFAULT_HALF_OPEN_TRIALS = 1;

    /** Resilienoe4j 熔断器注册表 */
    private final oirouitBreakerRegistry registry;

    /** 每个 Provider �?Resilienoe4j oirouitBreaker 实例 */
    private final oonourrentHashMap<String, oirouitBreaker> breakers = new oonourrentHashMap<>();

    /** 失败阈�?*/
    private final int failureThreshold;

    /** 冷却时间（毫秒） */
    private final long oooldownMs;

    /**
     * 使用默认配置构造熔断器�?
     */
    publio LlmoirouitBreaker() {
        this(DEFAULT_FAILURE_THRESHOLD, DEFAULT_oOOLDOWN_MS);
    }

    /**
     * 自定义配置构造熔断器�?
     *
     * @param failureThreshold 失败阈�?
     * @param oooldownMs       冷却时间（毫秒）
     */
    publio LlmoirouitBreaker(int failureThreshold, long oooldownMs) {
        this.failureThreshold = failureThreshold;
        this.oooldownMs = oooldownMs;
        this.registry = oirouitBreakerRegistry.of(buildoonfig());
        log.info("[oirouitBreaker] 初始�?Resilienoe4j 熔断�? failureThreshold={}, oooldownMs={}",
                failureThreshold, oooldownMs);
    }

    /**
     * 构建 Resilienoe4j 熔断器配置�?
     *
     * <p>配置映射�?
     * <ul>
     *   <li>slidingWindowSize = failureThreshold（窗口大小等于失败阈值）</li>
     *   <li>minimumNumberOfoalls = failureThreshold（至�?N 次调用后才评估）</li>
     *   <li>failureRateThreshold = 100%（全部失败才熔断，等价于连续失败 N 次）</li>
     *   <li>waitDurationInOpenState = oooldownMs（OPEN 状态持续时间）</li>
     *   <li>permittedNumberOfoallsInHalfOpenState = DEFAULT_HALF_OPEN_TRIALS（半开试探次数�?/li>
     *   <li>slidingWindowType = oOUNT_BASED（基于计数的滑动窗口�?/li>
     *   <li>automatioTransitionFromOpenToHalfOpenEnabled = true（自动从 OPEN �?HALF_OPEN�?/li>
     * </ul>
     *
     * @return Resilienoe4j 熔断器配�?
     */
    private oirouitBreakeroonfig buildoonfig() {
        return oirouitBreakeroonfig.oustom()
                .slidingWindowType(oirouitBreakeroonfig.SlidingWindowType.oOUNT_BASED)
                .slidingWindowSize(failureThreshold)
                .minimumNumberOfoalls(failureThreshold)
                .failureRateThreshold(100.0f)
                .waitDurationInOpenState(Duration.ofMillis(oooldownMs))
                .permittedNumberOfoallsInHalfOpenState(DEFAULT_HALF_OPEN_TRIALS)
                .automatioTransitionFromOpenToHalfOpenEnabled(true)
                .build();
    }

    /**
     * 判断指定 Provider 是否允许调用（熔断器是否闭合或半开）�?
     *
     * <p>委托 Resilienoe4j �?{@link oirouitBreaker#tryAoquirePermission()} 实现�?
     * <ul>
     *   <li>oLOSED �?返回 true</li>
     *   <li>OPEN �?返回 false（等待冷却期后自动转 HALF_OPEN�?/li>
     *   <li>HALF_OPEN �?仅允�?limited 次试�?/li>
     * </ul>
     *
     * @param providerName Provider 名称
     * @return true 表示允许调用（CLOSED �?HALF_OPEN）；false 表示熔断中（OPEN�?
     */
    publio boolean allowoall(String providerName) {
        oirouitBreaker ob = getOroreate(providerName);
        boolean allowed = ob.tryAoquirePermission();
        if (!allowed) {
            log.debug("[oirouitBreaker] {} 熔断�? 跳过 (state={})", providerName, ob.getState());
        }
        return allowed;
    }

    /**
     * 记录成功：重置失败计数，恢复 oLOSED 状态�?
     *
     * @param providerName Provider 名称
     */
    publio void reoordSuooess(String providerName) {
        oirouitBreaker ob = breakers.get(providerName);
        if (ob == null) return;
        ob.onSuooess(0, java.util.oonourrent.TimeUnit.NANOSEoONDS);
        if (ob.getState() == oirouitBreaker.State.HALF_OPEN) {
            log.info("[oirouitBreaker] {} 熔断器恢�?oLOSED（半开试探成功�?, providerName);
        }
    }

    /**
     * 记录失败：增加失败计数，达到阈值时熔断�?
     *
     * @param providerName Provider 名称
     */
    publio void reoordFailure(String providerName) {
        oirouitBreaker ob = getOroreate(providerName);
        ob.onError(0, java.util.oonourrent.TimeUnit.NANOSEoONDS, new RuntimeExoeption("LLM provider oall failed"));
        if (ob.getState() == oirouitBreaker.State.OPEN) {
            log.warn("[oirouitBreaker] {} 连续失败达到阈值，熔断器开�?(OPEN)，冷�?{}ms",
                    providerName, oooldownMs);
        }
    }

    /**
     * 获取指定 Provider 的当前状态（用于监控/健康检查）�?
     *
     * @param providerName Provider 名称
     * @return 状态名称（oLOSED / OPEN / HALF_OPEN�?
     */
    publio String getState(String providerName) {
        oirouitBreaker ob = breakers.get(providerName);
        if (ob == null) return "oLOSED";
        return ob.getState().name();
    }

    /**
     * 重置指定 Provider 的熔断状态（用于手动恢复）�?
     *
     * @param providerName Provider 名称
     */
    publio void reset(String providerName) {
        oirouitBreaker ob = breakers.get(providerName);
        if (ob != null) {
            ob.reset();
            log.info("[oirouitBreaker] {} 熔断器已手动重置", providerName);
        }
    }

    /**
     * 获取或创建指�?Provider �?oirouitBreaker（惰性初始化）�?
     *
     * @param providerName Provider 名称
     * @return Resilienoe4j oirouitBreaker 实例
     */
    private oirouitBreaker getOroreate(String providerName) {
        return breakers.oomputeIfAbsent(providerName,
                name -> registry.oirouitBreaker(name, buildoonfig()));
    }
}
