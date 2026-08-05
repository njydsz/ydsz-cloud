package com.remisoft.common.core.metrics;

import java.time.Duration;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

/**
 * Core 模块标准化 Micrometer 指标注册器。
 *
 * <p>在 Spring Boot Actuator 容器可用时（{@link MeterRegistry} 在 ApplicationContext 中），
 * 自动注册 core 模块的关键业务指标，供 Prometheus / SkyWalking / Grafana 等系统消费。</p>
 *
 * <p><b>当前注册的指标：</b></p>
 * <ul>
 *   <li>{@code core.api.response.count} — Counter，按 {@code result_code_prefix} (A/B/C/U) 标签分组，
 *       统计各错误码段的响应频率</li>
 *   <li>{@code core.request.context.hold_time} — Timer，统计请求上下文持有时间分布，
 *       用于诊断 TTL 泄漏与慢请求</li>
 * </ul>
 *
 * <p><b>设计决策：</b></p>
 * <ul>
 *   <li>采用"注册即用"模式：Bean 构造时即向 {@link MeterRegistry} 注册指标，
 *       后续通过静态方法上报</li>
 *   <li>指标值由调用方（如全局异常处理过滤器）显式上报，本类不做隐式 Hook，
 *       避免与业务链路耦合</li>
 *   <li>仅在 Micrometer 位于 classpath 且存在 {@link MeterRegistry} Bean 时实例化，
 *       不影响在非 Spring 或纯 JDK 环境下使用 core 模块</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.7.0
 * @see com.remisoft.common.core.config.CoreAutoConfiguration
 */
public class CoreMetrics {

    /** 响应计数指标名称（Counter，按 result_code_prefix 标签分组） */
    public static final String RESPONSE_COUNT = "core.api.response.count";

    /** 上下文持有时间指标名称（Timer） */
    public static final String CONTEXT_HOLD_TIME = "core.request.context.hold_time";

    /** 标签键：按错误码首字母段分组 */
    private static final String TAG_CODE_PREFIX = "result_code_prefix";

    /** 标签键：是否成功（基于 code 是否为 A00000 判定） */
    private static final String TAG_SUCCESS = "success";

    /** 单例引用（volatile 保证多线程可见性） */
    private static volatile CoreMetrics instance;

    private final MeterRegistry registry;
    private final Counter responseCounter;
    private final Timer contextHoldTimeTimer;

    public CoreMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.responseCounter = Counter.builder(RESPONSE_COUNT)
                .description("Core API response count grouped by result code prefix (A/B/C/U)")
                .tags(Tags.of("component", "remi-common-core"))
                .register(registry);
        this.contextHoldTimeTimer = Timer.builder(CONTEXT_HOLD_TIME)
                .description("Request context hold time distribution, for diagnosing TTL leaks")
                .tags(Tags.of("component", "remi-common-core"))
                .register(registry);
        instance = this;
    }

    /**
     * 上报一次响应结果。
     *
     * <p>由全局异常处理过滤器在请求结束时调用，统计错误码段分布。
     * 若当前 ApplicationContext 中未实例化本 Bean（Micrometer 不可用），
     * 调用为无操作（no-op）。</p>
     *
     * @param responseCode 响应码字符串（如 A00000、C99999）
     */
    public static void incrementResponse(String responseCode) {
        CoreMetrics m = instance;
        if (m == null) {
            return;
        }
        String prefix = extractPrefix(responseCode);
        String success = isSuccessCode(responseCode) ? "true" : "false";
        Counter.builder(RESPONSE_COUNT)
                .tags(Tags.of(TAG_CODE_PREFIX, prefix, TAG_SUCCESS, success))
                .register(m.registry)
                .increment();
        // also increment the base counter
        m.responseCounter.increment();
    }

    /**
     * 上报一次请求上下文的持有时间。
     *
     * <p>由过滤器的 finally 块在关闭 {@link com.remisoft.common.core.context.RequestContext.CleanupGuard} 前后调用，
     * 记录 distribution。若 Micrometer 不可用，则为 no-op。</p>
     *
     * @param holdTime 上下文持有时间（不为 null）
     */
    public static void recordHoldTime(Duration holdTime) {
        CoreMetrics m = instance;
        if (m == null || holdTime == null) {
            return;
        }
        m.contextHoldTimeTimer.record(holdTime);
    }

    /**
     * 从错误码提取首位字母分组（A/B/C/U 等），用于降低基数。
     *
     * <p>Micrometer 标签值应严格控制基数，细分每个具体错误码（如 A10101/A10102/...）
     * 会导致指标系列爆炸。按段分组（A=用户端错误、B=业务异常、C=第三方异常、U=未知）
     * 能在基数可控的前提下保留业务洞察力。</p>
     *
     * @param code 错误码字符串
     * @return 首位字母（大写）；空串返回 {@code "U"}
     */
    private static String extractPrefix(String code) {
        if (code == null || code.isEmpty()) {
            return "U";
        }
        return String.valueOf(Character.toUpperCase(code.charAt(0)));
    }

    /**
     * 判断响应码是否为成功码（A00000）。
     *
     * @param code 响应码
     * @return true=成功
     */
    private static boolean isSuccessCode(String code) {
        return "A00000".equals(code);
    }
}
