package com.njydsz.common.exception.code;

import java.util.HashMap;
import java.util.Map;

import com.njydsz.common.exception.enums.ExceptionCode;
import com.njydsz.common.exception.enums.ExceptionCodeRegistry;

import lombok.Getter;

/**
 * 限流/熔断/降级类错误码枚举 (D)
 *
 * <p>涵盖 Sentinel 风格的限流、熔断、降级、热点参数限流等场景，
 * 配合 P0-3 的 ydsz-common-ratelimit 模块使用。
 *
 * <p><b>编码规范：</b>
 * <pre>
 *     D + 模块(2位) + 序号(3位)
 * </pre>
 *
 * <p><b>模块定义：</b>
 * <ul>
 *   <li>D01xxx - 全局限流（IP / 用户 / 租户维度）</li>
 *   <li>D02xxx - 接口粒度限流（QPS / 并发数）</li>
 *   <li>D03xxx - 热点参数限流（特定参数值的限流）</li>
 *   <li>D04xxx - 熔断器（Circuit Breaker）</li>
 *   <li>D05xxx - 服务降级（Service Degradation）</li>
 *   <li>D06xxx - 集群限流（分布式全局限流）</li>
 *   <li>D07xxx - 自适应限流（系统负载 / CPU / RT）</li>
 * </ul>
 *
 * <p><b>HTTP 状态码：</b>
 * <ul>
 *   <li>429 - 请求过于频繁（限流触发）</li>
 *   <li>503 - 服务不可用（熔断 / 降级 / 资源耗尽）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see ExceptionCode
 */
@Getter
public enum RateLimitExceptionCode implements ExceptionCode {

    // ==================== D01 全局限流 ====================

    /** 全局限流触发 */
    GLOBAL_RATE_LIMIT("D01001", "ratelimit.global.triggered", 429),
    /** IP 维度限流 */
    IP_RATE_LIMIT("D01002", "ratelimit.ip.triggered", 429),
    /** 用户维度限流 */
    USER_RATE_LIMIT("D01003", "ratelimit.user.triggered", 429),
    /** 租户维度限流 */
    TENANT_RATE_LIMIT("D01004", "ratelimit.tenant.triggered", 429),
    /** 设备维度限流 */
    DEVICE_RATE_LIMIT("D01005", "ratelimit.device.triggered", 429),
    /** 区域维度限流 */
    REGION_RATE_LIMIT("D01006", "ratelimit.region.triggered", 429),

    // ==================== D02 接口粒度限流 ====================

    /** 接口 QPS 限流 */
    API_QPS_LIMIT("D02001", "ratelimit.api.qps.triggered", 429),
    /** 接口并发数限流 */
    API_CONCURRENCY_LIMIT("D02002", "ratelimit.api.concurrency.triggered", 429),
    /** 接口平均响应时间限流 */
    API_AVG_RT_LIMIT("D02003", "ratelimit.api.rt.triggered", 429),
    /** 接口总数限流（按时间段） */
    API_TOTAL_LIMIT("D02004", "ratelimit.api.total.triggered", 429),
    /** 写接口限流 */
    API_WRITE_LIMIT("D02005", "ratelimit.api.write.triggered", 429),
    /** 读接口限流 */
    API_READ_LIMIT("D02006", "ratelimit.api.read.triggered", 429),
    /** 提交类操作限流 */
    API_SUBMIT_LIMIT("D02007", "ratelimit.api.submit.triggered", 429),

    // ==================== D03 热点参数限流 ====================

    /** 热点参数限流 */
    HOT_PARAM_LIMIT("D03001", "ratelimit.hot.param.triggered", 429),
    /** 热点用户限流（黑名单/重点用户） */
    HOT_USER_LIMIT("D03002", "ratelimit.hot.user.triggered", 429),
    /** 热点商品限流（秒杀场景） */
    HOT_GOODS_LIMIT("D03003", "ratelimit.hot.goods.triggered", 429),
    /** 热点 URL 限流 */
    HOT_URL_LIMIT("D03004", "ratelimit.hot.url.triggered", 429),
    /** 热点参数值缺失 */
    HOT_PARAM_MISSING("D03005", "ratelimit.hot.param.missing", 400),
    /** 热点参数非法 */
    HOT_PARAM_INVALID("D03006", "ratelimit.hot.param.invalid", 400),

    // ==================== D04 熔断器 ====================

    /** 熔断器开启 */
    CIRCUIT_BREAKER_OPEN("D04001", "ratelimit.circuit.open", 503),
    /** 熔断器半开启探测 */
    CIRCUIT_BREAKER_HALF_OPEN("D04002", "ratelimit.circuit.half.open", 503),
    /** 熔断器强制开启 */
    CIRCUIT_BREAKER_FORCED_OPEN("D04003", "ratelimit.circuit.forced.open", 503),
    /** 熔断器强制关闭 */
    CIRCUIT_BREAKER_FORCED_CLOSED("D04004", "ratelimit.circuit.forced.closed", 200),
    /** 错误率超过阈值 */
    ERROR_RATE_EXCEEDED("D04005", "ratelimit.circuit.error.rate.exceeded", 503),
    /** 慢调用比例超过阈值 */
    SLOW_CALL_RATIO_EXCEEDED("D04006", "ratelimit.circuit.slow.ratio.exceeded", 503),

    // ==================== D05 服务降级 ====================

    /** 服务降级 */
    SERVICE_DEGRADED("D05001", "ratelimit.degrade.service", 503),
    /** 接口降级 */
    API_DEGRADED("D05002", "ratelimit.degrade.api", 503),
    /** 资源降级 */
    RESOURCE_DEGRADED("D05003", "ratelimit.degrade.resource", 503),
    /** 降级超时 */
    DEGRADE_TIMEOUT("D05004", "ratelimit.degrade.timeout", 504),
    /** 手动降级 */
    MANUAL_DEGRADE("D05005", "ratelimit.degrade.manual", 503),
    /** 自动降级 */
    AUTO_DEGRADE("D05006", "ratelimit.degrade.auto", 503),

    // ==================== D06 集群限流 ====================

    /** 集群限流触发 */
    CLUSTER_RATE_LIMIT("D06001", "ratelimit.cluster.triggered", 429),
    /** 集群 QPS 超过阈值 */
    CLUSTER_QPS_EXCEEDED("D06002", "ratelimit.cluster.qps.exceeded", 429),
    /** 集群并发数超过阈值 */
    CLUSTER_CONCURRENCY_EXCEEDED("D06003", "ratelimit.cluster.concurrency.exceeded", 429),
    /** Token 申请失败 */
    CLUSTER_TOKEN_ACQUIRE_FAILED("D06004", "ratelimit.cluster.token.failed", 429),
    /** Token 桶耗尽 */
    CLUSTER_TOKEN_BUCKET_EMPTY("D06005", "ratelimit.cluster.token.empty", 429),

    // ==================== D07 自适应限流 ====================

    /** 系统负载过高 */
    SYSTEM_LOAD_HIGH("D07001", "ratelimit.adaptive.load.high", 503),
    /** CPU 使用率超过阈值 */
    CPU_USAGE_HIGH("D07002", "ratelimit.adaptive.cpu.high", 503),
    /** 内存使用率超过阈值 */
    MEMORY_USAGE_HIGH("D07003", "ratelimit.adaptive.memory.high", 503),
    /** 入口 QPS 超过阈值 */
    INGRESS_QPS_HIGH("D07004", "ratelimit.adaptive.ingress.qps.high", 503),
    /** 平均 RT 超过阈值 */
    AVG_RT_HIGH("D07005", "ratelimit.adaptive.rt.high", 503),
    /** 并发线程数超过阈值 */
    CONCURRENT_THREADS_HIGH("D07006", "ratelimit.adaptive.threads.high", 503);

    /** 异常错误码 */
    private final String code;
    /** 国际化消息键 */
    private final String key;
    /** HTTP 状态码 */
    private final int httpStatus;

    RateLimitExceptionCode(String code, String key, int httpStatus) {
        this.code = code;
        this.key = key;
        this.httpStatus = httpStatus;
    }

    @Override
    public int getHttpStatus() {
        return httpStatus;
    }

    private static final Map<String, RateLimitExceptionCode> CODE_MAP = new HashMap<>();

    static {
        Map<String, ExceptionCode> registryMap = new HashMap<>();
        for (RateLimitExceptionCode code : values()) {
            registryMap.put(code.getCode(), code);
            CODE_MAP.put(code.getCode(), code);
        }
        ExceptionCodeRegistry.register(registryMap);
    }

    public static RateLimitExceptionCode resolve(String code) {
        if (code == null) {
            return null;
        }
        return CODE_MAP.get(code);
    }
}
