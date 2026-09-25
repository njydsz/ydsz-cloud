package com.njydsz.common.redis.config;

import java.time.Duration;
import java.util.Collection;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.validation.annotation.Validated;

import com.njydsz.common.redis.enums.FailOpenPolicy;

/**
 * Redis 配置属性类
 *
 * <p>提供对 Redis 配置的强类型访问，支持单机模式、集群模式和哨兵模式。 配置值通过 application.yml 中的 ydsz.redis 前缀注入。
 *
 * <p><b>配置示例（application.yml）：</b>
 *
 * <pre>{@code
 * ydsz:
 *   redis:
 *     # 单机模式
 *     host: localhost
 *     port: 6379
 *     password: xxx
 *     database: 0
 *     timeout: 3000
 *     lettuce:
 *       pool:
 *         max-active: 8
 *         max-wait: -1ms
 *         max-idle: 8
 *         min-idle: 0
 *
 *   # 或者集群模式
 *   redis:
 *     cluster:
 *       nodes: 192.168.1.1:6379,192.168.1.2:6379,192.168.1.3:6379
 *       max-redirects: 3
 *     password: xxx
 *
 *   # 或者哨兵模式
 *   redis:
 *     sentinel:
 *       master: mymaster
 *       nodes: 192.168.1.1:26379,192.168.1.2:26379,192.168.1.3:26379
 *     password: xxx
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@Validated
@ConfigurationProperties(prefix = "ydsz.redis")
public class RedisProperties {

  /**
   * 全局 Redis 故障处理策略
   *
   * <p>控制 Redis 操作失败时的统一行为，可选值：
   *
   * <ul>
   *   <li>{@code FAIL_OPEN} — 故障放行：返回 fallback 值，服务继续可用（默认）
   *   <li>{@code FAIL_CLOSED} — 故障拒绝：返回 fallback 值，由调用方判断拒绝
   *   <li>{@code FAIL_THROW} — 故障抛异常：抛出 {@code RedisOperationException}
   * </ul>
   *
   * <p>各子组件（如 rate-limiter、bloom-filter）可单独配置策略，覆盖此全局设置。 未单独配置的组件使用此全局策略。
   */
  private FailOpenPolicy failurePolicy = FailOpenPolicy.FAIL_OPEN;

  /** Redis 服务器地址（单机模式） */
  private String host = "localhost";

  /** Redis 服务器端口（单机模式） */
  private int port = 6379;

  /** Redis 密码 */
  private String password;

  /** 数据库索引（单机模式） */
  private int database = 0;

  /** 连接超时时间（毫秒） */
  private long timeout = 3000;

  /**
   * 连接超时时间（Duration 格式）
   *
   * <p>支持 Spring Boot 标准的 Duration 配置格式，如 PT3S、PT1.5S 等
   */
  private Duration timeoutDuration;

  /**
   * 获取连接超时时间
   *
   * <p>优先返回 timeoutDuration（Duration），若未设置则根据 timeout（毫秒）自动构建
   *
   * @return 连接超时时间
   */
  public Duration getTimeoutDuration() {
    if (timeoutDuration != null) {
      return timeoutDuration;
    }
    return timeout > 0 ? Duration.ofMillis(timeout) : null;
  }

  /**
   * 设置连接超时时间
   *
   * @param timeoutDuration 连接超时时间
   */
  public void setTimeoutDuration(Duration timeoutDuration) {
    this.timeoutDuration = timeoutDuration;
  }

  /** Lettuce 客户端配置 */
  private Lettuce lettuce = new Lettuce();

  /** 连接池配置 */
  private Pool pool = new Pool();

  /** 集群配置 */
  private Cluster cluster = new Cluster();

  /** 哨兵模式配置 */
  private Sentinel sentinel = new Sentinel();

  /** 重试配置 */
  private Retry retry = new Retry();

  /** 限流器配置 */
  private RateLimiter rateLimiter = new RateLimiter();

  /** Redis 用户名 */
  private String user;

  /** Redis Key 前缀，默认使用 spring.application.name 用于多应用共享 Redis 时避免 Key 冲突 */
  private String keyPrefix = "";

  /** 空值缓存 TTL（秒），用于缓存防护的空值占位过期时间 默认值：1800（30 分钟） */
  private int nullValueTtlSeconds = 1800;

  /** 内部随机数生成器（static 避免每次调用创建开销） */
  private static final java.util.Random TTL_RANDOM = new java.util.Random();

  /**
   * TTL 抖动幅度百分比（±ttlJitterPercent%），用于分散缓存过期时间、防缓存雪崩。
   *
   * <p>取值 0 表示不抖动。建议值 10（±10%），与业界主流 Redis 防雪崩策略（美团 Squirrel、阿里 Tair）一致。
   */
  private int ttlJitterPercent = 10;

  /**
   * 获取空值缓存 TTL（防缓存雪崩优化：施加 ±ttlJitterPercent% 随机抖动）。
   *
   * <p>当批量空值占位写入 Redis 时，加入随机抖动分散过期时刻，避免所有空值在同一秒集中失效
   * 导致 DB 压力骤增（缓存雪崩 / 惊群效应）。
   *
   * <p>示例：nullValueTtlSeconds=1800、ttlJitterPercent=10，返回值范围 [1620, 1980] 秒。
   *
   * @return 施加抖动后的 TTL（秒），最小值为 1
   */
  public int getNullValueTtlSeconds() {
    int base = nullValueTtlSeconds;
    if (ttlJitterPercent <= 0 || base <= 1) {
      return base;
    }
    int range = base * ttlJitterPercent / 100;
    if (range <= 0) {
      return base;
    }
    int jitter = TTL_RANDOM.nextInt(range * 2 + 1) - range;
    return Math.max(1, base + jitter);
  }

  /**
   * 获取原始空值 TTL（不施加抖动），用于需要精确过期的场景。
   *
   * @return 原始配置值（秒）
   */
  public int getNullValueTtlSecondsRaw() {
    return nullValueTtlSeconds;
  }

  /** 可观测性配置 */
  private Metrics metrics = new Metrics();

  /** 租户隔离配置 */
  private Tenant tenant = new Tenant();

  /** Key 过期事件监听配置 */
  private KeyExpiration keyExpiration = new KeyExpiration();

  /** 多级缓存配置（L1 Caffeine + L2 Redis） */
  private MultiLevel multilevel = new MultiLevel();

  /** Key 命名规范校验配置 */
  private KeyNaming keyNaming = new KeyNaming();

  /** 客户端配置（连接池、SSL、读策略等） */
  @NestedConfigurationProperty private Client client = new Client();

  /** Lettuce 客户端配置类 */
  @Data
  public static class Lettuce {
    /** 连接池配置 */
    private Pool pool = new Pool();

    /** 关闭超时时间 */
    private long shutdownTimeout = 100;
  }

  /** 连接池配置类 */
  @Data
  public static class Pool {
    /** 最大活跃连接数 */
    @Min(1)
    private int maxActive = 8;

    /** 最大等待时间（毫秒），-1 表示无限制 */
    private long maxWait = -1;

    /** 最大空闲连接数 */
    private int maxIdle = 8;

    /** 最小空闲连接数 */
    private int minIdle = 0;

    /** 是否启用连接池 */
    private boolean enabled = true;
  }

  /** 集群配置类 */
  @Data
  public static class Cluster {
    /** 集群节点列表 */
    private Collection<String> nodes;

    /** 最大重定向次数 */
    @Min(1)
    private int maxRedirects = 3;
  }

  /** 哨兵模式配置类 */
  @Data
  public static class Sentinel {
    /** 主节点名称 */
    private String master;

    /** 哨兵节点列表 */
    private Collection<String> nodes;

    /** 哨兵密码 */
    private String password;
  }

  /** 重试配置类 */
  @Data
  public static class Retry {

    /** 是否启用 Redis 重试拦截器（默认 true） */
    private boolean enabled = true;

    /** 最大重试次数（默认 3） */
    @Min(0)
    private int maxRetries = 3;

    /** 初始退避时间（毫秒，默认 100） */
    @Min(1)
    private long initialBackoffMs = 100;

    /** 最大退避时间（毫秒，默认 2000） */
    @Min(1)
    private long maxBackoffMs = 2000;

    /** 是否对写操作重试（默认 false，仅对读操作重试） */
    private boolean retryOnWrite = false;

    /**
     * 是否代理 RedisTemplate 以提供重试能力（默认 false）。
     *
     * <p>为保持 RedisTemplate 纯净，默认不再对其做 AOP 代理； 需要重试时可注入 {@code retryableRedisTemplate}，或开启此属性恢复旧行为。
     */
    private boolean proxyTemplate = false;
  }

  /** 限流器配置类 */
  @Data
  public static class RateLimiter {

    /**
     * 限流器故障处理策略（默认 FAIL_CLOSED）
     *
     * <p>当 Redis 不可用时的处理策略：
     *
     * <ul>
     *   <li>FAIL_OPEN: 放行所有请求（故障时允许访问）
     *   <li>FAIL_CLOSED: 拒绝所有请求（故障时拒绝访问，安全场景推荐）
     *   <li>FAIL_THROW: 抛出异常（由业务层处理）
     * </ul>
     */
    private FailOpenPolicy failOpenPolicy = FailOpenPolicy.FAIL_CLOSED;
  }

  /** 可观测性配置类 */
  @Data
  public static class Metrics {

    /**
     * 慢操作阈值（毫秒），0 表示禁用慢操作检测
     *
     * <p>超过此阈值的 Redis 操作将递增 {@code redis.operation.slow} 计数器
     */
    @Min(0)
    private long slowOperationThresholdMs = 100;
  }

  /** 租户隔离配置类 */
  @Data
  public static class Tenant {

    /**
     * 是否启用租户级 Redis Key 隔离
     *
     * <p>启用后，所有 Redis key 会自动添加 {tenantId}: 前缀，实现租户间数据隔离
     *
     * <p>默认：false（不启用）
     */
    private boolean enabled = false;
  }

  /** Key 过期事件监听配置类 */
  @Data
  public static class KeyExpiration {

    /**
     * 是否启用 Key 过期事件监听
     *
     * <p>需要 Redis 服务端配置 notify-keyspace-events Ex
     *
     * <p>默认：false（不启用）
     */
    private boolean enabled = false;
  }

  /**
   * 客户端配置类（对应 ydsz.redis.client.*）
   *
   * <p>包含客户端类型、连接池、SSL、读策略等配置。
   */
  @Data
  public static class Client {

    /** 客户端类型（默认 JEDIS） */
    private RedisClientType type = RedisClientType.JEDIS;

    /** 连接池配置 */
    private Pool pool = new Pool();

    /** SSL 配置 */
    private Ssl ssl = new Ssl();

    /**
     * 读策略（仅 Lettuce 客户端生效，用于读写分离场景）
     *
     * <p>可选值：MASTER、MASTER_PREFERRED、REPLICA_PREFERRED、REPLICA、NEAREST
     *
     * <p>默认值：MASTER（仅从主节点读取）
     */
    private ReadFrom readFrom = ReadFrom.MASTER;

    /** 连接池配置类 */
    @Data
    public static class Pool {
      /** 最大连接数（默认 16） */
      @Min(1)
      private int maxActive = 16;

      /** 最大空闲连接数（默认 8） */
      private int maxIdle = 8;

      /** 最小空闲连接数（默认 2） */
      private int minIdle = 2;

      /** 获取连接最大等待时间（毫秒），-1 表示无限制 */
      private long maxWait = -1;

      /** 是否启用连接池（默认启用） */
      private boolean enabled = true;
    }

    /** SSL 配置类 */
    @Data
    public static class Ssl {
      /** 是否启用 SSL（默认 false） */
      private boolean enabled = false;
    }

    /** Redis 读策略枚举（对应 Lettuce 的 io.lettuce.core.ReadFrom） */
    public enum ReadFrom {
      /** 仅从主节点读取 */
      MASTER,
      /** 优先从主节点读取，主节点不可用时从副本读取 */
      MASTER_PREFERRED,
      /** 仅从上游（主）节点读取 */
      UPSTREAM,
      /** 优先从上游（主）节点读取，主节点不可用时从副本读取 */
      UPSTREAM_PREFERRED,
      /** 优先从副本读取，副本不可用时从主节点读取 */
      REPLICA_PREFERRED,
      /** 仅从副本读取 */
      REPLICA,
      /** 从网络拓扑最近的节点读取 */
      NEAREST
    }
  }

  /** 多级缓存配置类（L1 Caffeine + L2 Redis） */
  @Data
  public static class MultiLevel {

    /**
     * 是否启用多级缓存
     *
     * <p>启用后，可通过 MultiLevelCacheProvider 提供 L1+L2 二级缓存能力。
     *
     * <p>默认：false
     */
    private boolean enabled = false;

    /**
     * L1 Caffeine 缓存最大条目数
     *
     * <p>超过该数量时将按 LRU 策略淘汰。
     *
     * <p>默认：1000
     */
    @Min(1)
    private long l1MaxSize = 1000L;

    /**
     * L1 Caffeine 缓存过期时间（秒）
     *
     * <p>建议为 L2 TTL 的 1/5 ~ 1/10，保证数据新鲜度。
     *
     * <p>默认：60（1 分钟）
     */
    @Min(1)
    private long l1TtlSeconds = 60L;
  }

  /** Key 命名规范校验配置类 */
  @Data
  public static class KeyNaming {

    /**
     * Key 命名校验模式
     *
     * <ul>
     *   <li>{@code WARN}（默认）：违规时打印 WARN 日志，不阻断操作
     *   <li>{@code STRICT}：违规时抛出 IllegalArgumentException
     *   <li>{@code DISABLED}：不做任何校验
     * </ul>
     *
     * <p>对应 {@link RedisKeyNamingConvention.ValidationMode} 枚举。
     * 推荐生产环境使用 WARN 模式避免误拦截，CI/CD 流水线可使用 STRICT 模式强制规范。
     *
     * <p>默认：WARN
     */
    private String mode = "WARN";
  }
}
