package com.njydsz.common.feign.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import com.njydsz.common.util.string.StringUtils;

import feign.Logger;
import lombok.Getter;
import lombok.Setter;

/**
 * YdszFeign 模块配置属性类。
 *
 * <p>提供对 Feign 客户端各项行为的细粒度配置控制：
 * <ul>
 *   <li>{@code enabled}：模块总开关</li>
 *   <li>{@code logger-level}：日志级别</li>
 *   <li>{@code propagation}：请求头透传配置</li>
 *   <li>{@code retry}：重试策略配置</li>
 *   <li>{@code timeout}：超时配置</li>
 *   <li>{@code error}：错误处理配置</li>
 *   <li>{@code client}：HTTP 客户端配置</li>
 * </ul>
 *
 * <p>配置前缀：{@code ydsz.feign}
 *
 * <p>配置示例（YAML）：
 * <pre>
 * ydsz:
 *   feign:
 *     enabled: true
 *     logger-level: BASIC
 *     propagation:
 *       enabled: true
 *       headers:
 *         - X-Access-Token
 *         - X-User-Language
 *     retry:
 *       enabled: true
 *       max-attempts: 3
 *       backoff:
 *         delay: 100
 *         max-delay: 500
 *         multiplier: 2.0
 *     timeout:
 *       connect: 5000
 *       read: 10000
 *     error:
 *       include-body: true
 *       max-body-bytes: 4096
 *     client:
 *       max-connections: 200
 *       max-per-route: 50
 *       keep-alive: 30000
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * 
 * @see FeignConfiguration
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ydsz.feign")
public class FeignProperties {

    private static final String X_SERVICE_TYPE = "X-Service-Type";
    private static final String X_USER_LANGUAGE = "X-User-Language";
    private static final String X_DISTINCT_ID = "X-Distinct-Id";
    private static final String X_IDENTITY_TYPE = "X-Identity-Type";
    private static final String X_ACCESS_TOKEN = "X-Access-Token";
    private static final String X_DATA_SCOPE = "X-Data-Scope";
    private static final String X_COMPANY_IDS = "X-Company-Ids";
    private static final String X_DEPT_IDS = "X-Dept-Ids";
    private static final String X_UNIQUE_ID = "X-Unique-Id";
    private static final String X_TENANT_ID = "X-Tenant-Id";
    private static final String X_PROJECT_IDS = "X-Project-Ids";
    private static final String X_REGION_IDS = "X-Region-Ids";
    private static final String X_VISIBLE_COLUMNS = "X-Visible-Columns";
    private static final String X_EDITABLE_COLUMNS = "X-Editable-Columns";
    private static final String X_REQUEST_SOURCE = "X-Request-Source";
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";

    /**
     * 模块总开关。
     * 默认值：{@code true}
     */
    private boolean enabled = true;

    /**
     * Feign 日志级别。
     * <ul>
     *   <li>{@code NONE} - 不记录日志，性能最佳</li>
     *   <li>{@code BASIC} - 仅记录请求方法、URL 和响应状态（推荐生产环境）</li>
     *   <li>{@code HEADERS} - 记录基本信息和请求/响应头</li>
     *   <li>{@code FULL} - 记录完整请求和响应，包括主体和元数据（仅用于调试）</li>
     * </ul>
     * 默认值：{@code BASIC}
     */
    private String loggerLevel = "BASIC";

    /**
     * 请求头透传配置。
     */
    private final Propagation propagation = new Propagation();

    /**
     * 重试策略配置。
     */
    private final Retry retry = new Retry();

    /**
     * 超时配置（毫秒）。
     */
    private final Timeout timeout = new Timeout();

    /**
     * 错误处理配置。
     */
    private final Error error = new Error();

    /**
     * HTTP 客户端配置。
     */
    private final Client client = new Client();

    /**
     * GZIP 压缩配置。
     */
    private final Compress compress = new Compress();

    /**
     * Resilience4j 熔断器配置。
     */
    private final CircuitBreaker circuitBreaker = new CircuitBreaker();

    /**
     * 动态配置刷新配置。
     */
    private final Refresh refresh = new Refresh();

    /**
     * 响应拦截器配置。
     */
    private final ResponseInterceptor responseInterceptor = new ResponseInterceptor();

    /**
     * per-client 超时配置。
     * <p>Key 为 Feign 客户端名称（contextId 或 name），
     * Value 为该客户端的超时配置。未配置的客户端使用全局 {@link #timeout}。
     * <pre>
     * ydsz:
     *   feign:
     *     client-timeouts:
     *       notificationClient:
     *         connect: 3000
     *         read: 5000
     *       exportService:
     *         connect: 5000
     *         read: 60000
     * </pre>
     */
    private final Map<String, Timeout> clientTimeouts = new ConcurrentHashMap<>();

    /**
     * 解析日志级别为 Feign Logger.Level 枚举值。
     *
     * @return 日志级别枚举值，若解析失败则返回默认的 BASIC 级别
     */
    public Logger.Level resolvedLoggerLevel() {
        if (StringUtils.isBlank(loggerLevel)) {
            return Logger.Level.BASIC;
        }
        try {
            return Logger.Level.valueOf(loggerLevel.toUpperCase().trim());
        } catch (IllegalArgumentException ex) {
            return Logger.Level.BASIC;
        }
    }

    /**
     * 请求头透传配置。
     *
     * <p>控制哪些 HTTP 请求头需要从当前请求透传到下游 Feign 调用。
     * 透传的请求头包括身份认证信息、用户偏好、数据权限上下文等。
     */
    @Getter
@Setter
    public static class Propagation {

        /**
         * 是否启用请求头透传。
         * 默认值：{@code true}
         */
        private boolean enabled = true;

        /**
         * 需要透传的请求头名称集合。
         * <p>
         * 默认包含以下请求头：
         * <ul>
         *   <li>{@code X-Service-Type} - 服务类型</li>
         *   <li>{@code X-User-Language} - 用户语言偏好</li>
         *   <li>{@code X-Distinct-Id} - 用户设备唯一标识</li>
         *   <li>{@code X-Identity-Type} - 身份类型</li>
         *   <li>{@code X-Access-Token} - 认证令牌</li>
         *   <li>{@code X-Data-Scope} - 数据权限范围类型</li>
         *   <li>{@code X-Company-Ids} - 公司ID集合</li>
         *   <li>{@code X-Dept-Ids} - 部门ID集合</li>
         *   <li>{@code X-Unique-Id} - 当前登录人唯一ID</li>
         *   <li>{@code X-Tenant-Id} - 租户ID</li>
         *   <li>{@code X-Project-Ids} - 项目ID集合</li>
         *   <li>{@code X-Region-Ids} - 区域ID集合</li>
         *   <li>{@code X-Visible-Columns} - 列可见规则</li>
         *   <li>{@code X-Editable-Columns} - 列可编辑规则</li>
         *   <li>{@code X-Request-Source} - 请求来源标识</li>
         *   <li>{@code X-Forwarded-For} - 请求来源IP</li>
         * </ul>
         */
        private Set<String> headers = new LinkedHashSet<>(Arrays.asList(
                X_SERVICE_TYPE,
                X_USER_LANGUAGE,
                X_DISTINCT_ID,
                X_IDENTITY_TYPE,
                X_ACCESS_TOKEN,
                X_DATA_SCOPE,
                X_COMPANY_IDS,
                X_DEPT_IDS,
                X_UNIQUE_ID,
                X_TENANT_ID,
                X_PROJECT_IDS,
                X_REGION_IDS,
                X_VISIBLE_COLUMNS,
                X_EDITABLE_COLUMNS,
                X_REQUEST_SOURCE,
                X_FORWARDED_FOR
        ));
    }

    /**
     * 重试策略配置。
     *
     * <p>当 Feign 调用失败时，根据配置的重试策略自动进行重试。
     * 重试仅对特定类型的异常生效，如连接超时、服务器错误等。
     */
    @Getter
@Setter
    public static class Retry {

        /**
         * 是否启用重试机制。
         * 默认值：{@code true}
         */
        private boolean enabled = true;

        /**
         * 最大重试次数。
         * <p>包括首次调用，即总调用次数 = 1 + maxAttempts。
         * 默认值：{@code 3}
         */
        private int maxAttempts = 3;

        /**
         * 退避策略配置。
         */
        private final Backoff backoff = new Backoff();

        /**
         * 重试的 HTTP 方法。
         * <p>默认仅对 GET 请求进行重试。
         */
        private Set<String> retryOnMethods = new LinkedHashSet<>(Arrays.asList("GET"));

        @Getter
@Setter
        public static class Backoff {
            /**
             * 初始重试延迟时间（毫秒）。
             * 默认值：{@code 100}
             */
            private long delay = 100;

            /**
             * 最大重试延迟时间（毫秒）。
             * 默认值：{@code 500}
             */
            private long maxDelay = 500;

            /**
             * 延迟倍数。
             * <p>每次重试的延迟时间 = min(delay * multiplier^n, maxDelay)。
             * 默认值：{@code 2.0}
             */
            private double multiplier = 2.0;
        }
    }

    /**
     * 超时配置（毫秒）。
     *
     * <p>控制 Feign 客户端的连接超时和读取超时时间。
     */
    @Getter
@Setter
    public static class Timeout {

        /**
         * 连接超时时间（毫秒）。
         * <p>建立 TCP 连接的最大等待时间。
         * 默认值：{@code 5000}
         */
        private long connect = 5000;

        /**
         * 读取超时时间（毫秒）。
         * <p>从服务器读取数据的最大等待时间。
         * 默认值：{@code 10000}
         */
        private long read = 10000;
    }

    /**
     * 错误处理配置。
     *
     * <p>控制 Feign 调用异常时的错误信息构建策略。
     */
    @Getter
@Setter
    public static class Error {

        /**
         * 是否在异常信息中包含响应体。
         * <p>建议生产环境设为 {@code false} 或配合数据脱敏使用。
         * 默认值：{@code true}
         */
        private boolean includeBody = true;

        /**
         * 读取响应体的最大字节数。
         * <p>当 {@code includeBody} 为 {@code true} 时生效，
         * 用于防止响应体过大导致内存问题。
         * 默认值：{@code 4096}
         */
        private int maxBodyBytes = 4096;
    }

    /**
     * HTTP 客户端连接池配置。
     *
     * <p>用于优化高并发场景下的连接复用，提升性能。
     */
    @Getter
@Setter
    public static class Client {

        /**
         * 最大连接数。
         * <p>整个连接池的最大 TCP 连接数。
         * 默认值：{@code 200}
         */
        @Min(1)
        private int maxConnections = 200;

        /**
         * 每个路由的最大连接数。
         * <p>单个域名/路径的最大并发连接数。
         * 默认值：{@code 50}
         */
        @Min(1)
        private int maxPerRoute = 50;

        /**
         * 连接保持时间（毫秒）。
         * <p>连接被复用前的最大空闲时间。
         * 默认值：{@code 30000}
         */
        private int keepAlive = 30000;

        /**
         * 空闲连接校验时间（毫秒）。
         * <p>空闲超过此时间的连接在使用前会进行探活（stale check），
         * 防止使用到已被服务端关闭的"僵尸连接"。
         * 默认值：{@code 2000}。
         */
        @Min(0)
        private int validateAfterInactivity = 2000;

        /**
         * 连接最大生命周期（毫秒）。
         * <p>超过此时间的连接会被自动关闭回收，
         * 防止长时间复用同一连接导致连接老化问题。
         * 默认值：{@code 300000}（5 分钟）。
         */
        @Min(1000)
        private int connectionTimeToLive = 300000;
    }

    /**
     * GZIP 请求压缩配置。
     *
     * <p>控制 Feign 请求体是否启用 GZIP 压缩，减少网络传输量。
     */
    @Getter
@Setter
    public static class Compress {

        /**
         * 是否启用 GZIP 压缩。
         * <p>启用后，请求体大于 {@code min-size} 的请求将自动压缩。
         * 默认值：{@code false}
         */
        private boolean enabled = false;

        /**
         * 最小压缩阈值（字节）。
         * <p>请求体小于该值时不压缩，避免压缩后体积反而增大的情况。
         * 默认值：{@code 1024}
         */
        private int minSize = 1024;

        /**
         * 排除压缩的 Content-Type 列表。
         * <p>匹配这些 Content-Type 的请求不会被压缩，如图片、二进制流等。
         * 支持通配符，如 {@code image/*}。
         */
        private String[] excludedContentTypes = new String[]{
                "image/*",
                "video/*",
                "application/octet-stream",
                "application/zip",
                "application/gzip",
                "application/x-gzip"
        };
    }

    /**
     * Resilience4j 熔断器配置。
     *
     * <p>控制 Feign 客户端的熔断降级行为，基于 Resilience4j 实现。
     */
    @Getter
@Setter
    public static class CircuitBreaker {

        /**
         * 是否启用熔断器。
         * 默认值：{@code false}
         */
        private boolean enabled = false;

        /**
         * 失败率阈值（百分比）。
         * <p>当失败率达到此阈值时，熔断器切换到 OPEN 状态。
         * 取值范围：1~100。
         * 默认值：{@code 50}
         */
        private float failureRateThreshold = 50;

        /**
         * 慢调用率阈值（百分比）。
         * <p>当慢调用率达到此阈值时，熔断器切换到 OPEN 状态。
         * 取值范围：1~100。
         * 默认值：{@code 100}
         */
        private float slowCallRateThreshold = 100;

        /**
         * 慢调用阈值（毫秒）。
         * <p>调用耗时超过此值被视为慢调用。
         * 默认值：{@code 3000}
         */
        private int slowCallDurationThreshold = 3000;

        /**
         * 半开状态允许的最大调用数。
         * <p>熔断器处于半开状态时，允许的最大试探调用次数。
         * 默认值：{@code 10}
         */
        private int permittedNumberOfCallsInHalfOpenState = 10;

        /**
         * 滑动窗口大小。
         * <p>用于记录最近调用的样本数量或时间片段数量。
         * 默认值：{@code 100}
         */
        private int slidingWindowSize = 100;

        /**
         * 滑动窗口类型。
         * <ul>
         *   <li>{@code COUNT_BASED} - 基于调用次数的滑动窗口</li>
         *   <li>{@code TIME_BASED} - 基于时间的滑动窗口</li>
         * </ul>
         * 默认值：{@code COUNT_BASED}
         */
        private String slidingWindowType = "COUNT_BASED";

        /**
         * 开启状态等待时间（秒）。
         * <p>熔断器处于 OPEN 状态后，等待多长时间切换到 HALF_OPEN 状态。
         * 默认值：{@code 60}
         */
        private int waitDurationInOpenState = 60;

        /**
         * 熔断状态持久化 TTL（秒）。
         * <p>熔断状态写入 Redis 后的过期时间，超过此时间自动清除。
         * 默认值：{@code 3600}（1 小时）。
         */
        private int stateTtlSeconds = 3600;
    }

    /**
     * 动态配置刷新配置。
     *
     * <p>控制 Feign 客户端配置热更新行为。当配置中心下发新的 Feign 配置时，
     * 可自动重建 Feign 客户端并应用新配置，无需重启服务。
     */
    @Getter
@Setter
    public static class Refresh {

        /**
         * 是否启用动态配置刷新。
         * <p>启用后，当配置变更时会重新创建 Feign 客户端。
         * 默认值：{@code false}
         */
        private boolean enabled = false;

        /**
         * 排除的客户端名称列表。
         * <p>配置刷新时，这些 Feign 客户端不会被重建。
         */
        private List<String> exclude = new ArrayList<>();
    }

    /**
     * 响应拦截器配置。
     *
     * <p>控制 Feign 响应拦截器的行为，提供响应日志记录和指标采集能力。
     */
    @Getter
    @Setter
    public static class ResponseInterceptor {

        /**
         * 是否启用响应日志记录。
         * <p>启用后，会记录每个 Feign 响应的状态码、耗时等信息。
         * 默认值：{@code true}
         */
        private boolean logEnabled = true;

        /**
         * 是否启用响应指标采集。
         * <p>启用后，会将响应指标暴露到 Micrometer 监控系统。
         * 默认值：{@code true}
         */
        private boolean metricsEnabled = true;

        /**
         * 慢调用阈值（毫秒）。
         * <p>Feign 调用耗时超过此值时，将输出 WARN 级别日志并递增
         * {@code feign.request.slow} 指标计数器，便于告警规则配置。
         * <p>设为 0 表示禁用慢调用检测。
         * 默认值：{@code 3000}
         */
        private long slowCallThresholdMillis = 3000;
    }
}
