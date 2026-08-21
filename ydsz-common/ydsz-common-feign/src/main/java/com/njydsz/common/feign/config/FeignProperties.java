package com.njydsz.common.feign.config;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import feign.Logger;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * YdszFeign 模块核心配置属性类
 *
 * <p>配置前缀：ydsz.feign，覆盖请求头透传、重试、超时、追踪、指标、熔断、隔离、压缩等全量能力。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ydsz.feign")
public class FeignProperties {

  /** 模块总开关，默认true */
  private boolean enabled = true;

  /** Feign日志级别，默认BASIC，可选NONE/HEADERS/FULL */
  private String loggerLevel = "BASIC";

  /** 核心请求头透传配置 */
  private final Propagation propagation = new Propagation();

  /** 请求重试配置 */
  private final Retry retry = new Retry();

  /** 超时配置（毫秒） */
  private final Timeout timeout = new Timeout();

  /** 链路追踪配置 */
  private final Trace trace = new Trace();

  /** 监控指标配置 */
  private final Metrics metrics = new Metrics();

  /** 熔断器开关配置，具体熔断规则使用Resilience4j原生配置 */
  private final CircuitBreaker circuitBreaker = new CircuitBreaker();

  /** 信号量隔离（Bulkhead）配置 */
  private final Bulkhead bulkhead = new Bulkhead();

  /** GZIP 请求压缩配置 */
  private final Compress compress = new Compress();

  /** HttpClient 连接池配置 */
  private final Client client = new Client();

  /** 响应拦截器配置 */
  private final ResponseInterceptor responseInterceptor = new ResponseInterceptor();

  /** 解析日志级别为Feign枚举值 */
  public Logger.Level resolvedLoggerLevel() {
    if (loggerLevel == null || loggerLevel.isBlank()) {
      return Logger.Level.BASIC;
    }
    try {
      return Logger.Level.valueOf(loggerLevel.toUpperCase().trim());
    } catch (IllegalArgumentException e) {
      return Logger.Level.BASIC;
    }
  }

  /** 请求头透传配置 */
  @Getter
  @Setter
  public static class Propagation {
    /** 是否启用请求头透传，默认true */
    private boolean enabled = true;

    /** 默认透传的13个核心业务头：覆盖链路追踪、身份鉴权、权限校验、租户隔离等所有业务场景 */
    private Set<String> headers =
        new LinkedHashSet<>(
            Arrays.asList(
                "traceparent", // W3C链路追踪头
                "X-Tenant-Id", // 租户ID
                "X-Access-Token", // 访问令牌
                "X-Request-Id", // 请求唯一ID
                "X-User-Userid", // 当前用户ID
                "X-User-Username", // 当前用户名
                "X-User-Locale", // 用户语言环境（国际化）
                "X-Request-Source", // 请求来源标识
                "X-Company-Ids", // 公司ID集合（权限校验）
                "X-Data-Scope", // 数据权限范围类型
                "X-Unique-Id", // 用户登录唯一ID
                "X-Dept-Ids", // 部门ID集合（权限校验）
                "X-Service-Type" // 服务类型标识
                ));
  }

  /** 请求重试配置 */
  @Getter
  @Setter
  public static class Retry {
    /** 是否启用重试，默认true */
    private boolean enabled = true;

    /** 最大重试次数（包含首次调用），默认3 */
    private int maxAttempts = 3;

    /** 退避策略配置 */
    private final Backoff backoff = new Backoff();

    /** 可重试的 HTTP 方法白名单，默认仅 GET */
    private Set<String> retryOnMethods = new LinkedHashSet<>(Arrays.asList("GET"));

    /** 退避策略 */
    @Getter
    @Setter
    public static class Backoff {
      /** 初始延迟（毫秒），默认 100 */
      private long delay = 100;

      /** 最大延迟（毫秒），默认 500 */
      private long maxDelay = 500;
    }
  }

  /** 超时配置 */
  @Getter
  @Setter
  public static class Timeout {
    /** 连接超时时间（毫秒），默认5000 */
    private long connect = 5000;

    /** 读取超时时间（毫秒），默认10000 */
    private long read = 10000;
  }

  /** 链路追踪配置 */
  @Getter
  @Setter
  public static class Trace {
    /** 是否启用W3C traceparent协议头透传，默认true */
    private boolean enabled = true;
  }

  /** 监控指标配置 */
  @Getter
  @Setter
  public static class Metrics {
    /** 是否启用Feign调用指标采集，默认true */
    private boolean enabled = true;
  }

  /** 熔断器开关配置 */
  @Getter
  @Setter
  public static class CircuitBreaker {
    /** 是否启用Resilience4j熔断能力，默认false */
    private boolean enabled = false;

    /** 熔断状态 Redis 持久化 TTL（秒），默认 3600 */
    private int stateTtlSeconds = 3600;

    /** 失败率阈值（百分比），达到该值触发熔断，默认 50 */
    private float failureRateThreshold = 50;

    /** 慢调用率阈值（百分比），默认 80 */
    private float slowCallRateThreshold = 80;

    /** 慢调用时长阈值（毫秒），默认 3000 */
    private long slowCallDurationMs = 3000;

    /** 熔断打开后自动恢复的等待时长（毫秒），默认 10000 */
    private long waitDurationMs = 10000;

    /** 滑动窗口内最小调用次数（低于该值不判定熔断），默认 10 */
    private int minimumNumberOfCalls = 10;

    /** 滑动窗口大小，默认 20 */
    private int slidingWindowSize = 20;
  }

  /** 信号量隔离（Bulkhead）配置 */
  @Getter
  @Setter
  public static class Bulkhead {
    /** 是否启用信号量隔离，默认false */
    private boolean enabled = false;

    /** 默认最大并发请求数，默认50 */
    private int defaultMaxConcurrent = 50;

    /** 获取许可超时时间（毫秒），默认100 */
    private long acquireTimeoutMs = 100;

    /** 按服务维度配置最大并发请求数 */
    private Map<String, Integer> serviceMaxConcurrent = new HashMap<>();
  }

  /** GZIP 请求压缩配置 */
  @Getter
  @Setter
  public static class Compress {
    /** 是否启用GZIP压缩，默认false */
    private boolean enabled = false;

    /** 压缩触发阈值（字节），默认 1024 */
    private int minSize = 1024;

    /** 排除压缩的 Content-Type 列表 */
    private Set<String> excludedContentTypes = new LinkedHashSet<>();
  }

  /** HttpClient 连接池配置 */
  @Getter
  @Setter
  public static class Client {
    /** 连接池最大连接数，默认 200 */
    private int maxConnections = 200;

    /** 每个路由的最大连接数，默认 50 */
    private int maxPerRoute = 50;

    /** 空闲连接保活时间（毫秒），默认 30000 */
    private long keepAlive = 30000;

    /** 连接空闲多久后校验（毫秒），默认 5000 */
    private long validateAfterInactivity = 5000;

    /** 连接最大存活时间（毫秒），默认 60000 */
    private long connectionTimeToLive = 60000;
  }

  /** 错误解码配置 */
  private final Error error = new Error();

  /** 错误解码配置 */
  @Getter
  @Setter
  public static class Error {
    /** 是否在错误信息中包含响应体，默认false */
    private boolean includeBody = false;

    /** 响应体最大字节数，默认4096 */
    private int maxBodyBytes = 4096;
  }

  /** 响应拦截器配置 */
  @Getter
  @Setter
  public static class ResponseInterceptor {
    /** 是否启用响应拦截器，默认true */
    private boolean enabled = true;

    /** 是否启用响应日志，默认false */
    private boolean logEnabled = false;

    /** 是否启用响应时间指标采集，默认true */
    private boolean metricsEnabled = true;

    /** 慢调用阈值（毫秒），默认 3000 */
    private long slowCallThresholdMillis = 3000;
  }
}
