package com.njydsz.message.server.config;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 消息引擎全局配置（prefix = {@code ydsz.message}）。 *
 * <p>绑定 {@code application.yml} 中 {@code ydsz.message.*} 配置项，包含通道开关、默认优先级、聚合 / 重试扫描间隔、全局频率上限、
 * 多维度限流等。
 *
 * <p><b>配置分级：</b>
 *
 * <ul>
 *   <li><b>核心配置</b>：channelEnabled、defaultPriority、aggregateScanIntervalMs、retryScanIntervalMs —
 *       启动必需，无默认值或默认值可能不适合生产环境
 *   <li><b>高级配置</b>：rateLimit、dedup、cost、sms、push 等 — 可选，有合理默认值，按需调整
 * </ul>
 *
 * <p>P0-3: 从 @Component 改为纯 @ConfigurationProperties， 由 {@link MessageAutoConfiguration}
 * 通过 @EnableConfigurationProperties 注册。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@Validated
@ConfigurationProperties(prefix = "ydsz.message")
public class MessageProperties {

  /** 默认aggregateScanIntervalMs值（可被配置文件覆盖） */
  private static final long DEFAULT_AGGREGATE_SCAN_INTERVAL_MS = 60000L;

  /** 默认retryScanIntervalMs值（可被配置文件覆盖） */
  private static final long DEFAULT_RETRY_SCAN_INTERVAL_MS = 30000L;

  /** 默认messageTtlSeconds值（可被配置文件覆盖） */
  private static final long DEFAULT_MESSAGE_TTL_SECONDS = 3600L;

  /** 默认markAllReadBatchSize值（可被配置文件覆盖） */
  private static final int DEFAULT_MARK_ALL_READ_BATCH_SIZE = 500;

  /** 默认receiptPullScanIntervalMs值（可被配置文件覆盖） */
  private static final long DEFAULT_RECEIPT_PULL_SCAN_INTERVAL_MS = 120000L;

  /** 默认receiptPullDelayMinutes值（可被配置文件覆盖） */
  private static final long DEFAULT_RECEIPT_PULL_DELAY_MINUTES = 5L;

  /** 默认receiptTimeoutMinutes值（可被配置文件覆盖） */
  private static final long DEFAULT_RECEIPT_TIMEOUT_MINUTES = 30L;

  /** 默认globalDailyLimit值（可被配置文件覆盖） */
  private static final int DEFAULT_GLOBAL_DAILY_LIMIT = 0;

  /** 默认globalHourlyLimit值（可被配置文件覆盖） */
  private static final int DEFAULT_GLOBAL_HOURLY_LIMIT = 0;

  /** 默认maxContentLength值（可被配置文件覆盖） */
  private static final int DEFAULT_MAX_CONTENT_LENGTH = 1048576;

  /** 默认receiverPermits值（可被配置文件覆盖） */
  private static final int DEFAULT_RECEIVER_PERMITS = 10;

  /** 默认templatePermits值（可被配置文件覆盖） */
  private static final int DEFAULT_TEMPLATE_PERMITS = 100;

  /** 默认tenantPermits值（可被配置文件覆盖） */
  private static final int DEFAULT_TENANT_PERMITS = 1000;

  /** 默认ttlSeconds值（可被配置文件覆盖） */
  private static final int DEFAULT_TTL_SECONDS = 60;

  /** 默认connectTimeout值（可被配置文件覆盖） */
  private static final int DEFAULT_CONNECT_TIMEOUT = 5000;

  /** 默认readTimeout值（可被配置文件覆盖） */
  private static final int DEFAULT_READ_TIMEOUT = 10000;

  /** 默认maxRetryCount值（可被配置文件覆盖） */
  private static final int DEFAULT_MAX_RETRY_COUNT = 3;

  /** 默认baseBackoffMs值（可被配置文件覆盖） */
  private static final long DEFAULT_BASE_BACKOFF_MS = 2000L;

  /** 默认backoffMultiplier值（可被配置文件覆盖） */
  private static final double DEFAULT_BACKOFF_MULTIPLIER = 2.0;

  /** 默认maxBackoffMs值（可被配置文件覆盖） */
  private static final long DEFAULT_MAX_BACKOFF_MS = 60000L;

  /** 默认threshold值（可被配置文件覆盖） */
  private static final int DEFAULT_THRESHOLD = 10;

  /** 默认windowMinutes值（可被配置文件覆盖） */
  private static final int DEFAULT_WINDOW_MINUTES = 60;

  /** 默认cooldownMinutes值（可被配置文件覆盖） */
  private static final int DEFAULT_COOLDOWN_MINUTES = 30;

  /** 默认failureRateThreshold值（可被配置文件覆盖） */
  private static final int DEFAULT_FAILURE_RATE_THRESHOLD = 50;

  /** 默认slowCallRateThreshold值（可被配置文件覆盖） */
  private static final int DEFAULT_SLOW_CALL_RATE_THRESHOLD = 80;

  /** 默认slowCallDurationSeconds值（可被配置文件覆盖） */
  private static final long DEFAULT_SLOW_CALL_DURATION_SECONDS = 5L;

  /** 默认waitDurationInOpenStateSeconds值（可被配置文件覆盖） */
  private static final long DEFAULT_WAIT_DURATION_IN_OPEN_STATE_SECONDS = 30L;

  /** 默认permittedNumberOfCallsInHalfOpenState值（可被配置文件覆盖） */
  private static final int DEFAULT_PERMITTED_NUMBER_OF_CALLS_IN_HALF_OPEN_STATE = 3;

  /** 默认slidingWindowSize值（可被配置文件覆盖） */
  private static final int DEFAULT_SLIDING_WINDOW_SIZE = 20;

  /** 默认minimumNumberOfCalls值（可被配置文件覆盖） */
  private static final int DEFAULT_MINIMUM_NUMBER_OF_CALLS = 10;

  /** 默认ttlDays值（可被配置文件覆盖） */
  private static final int DEFAULT_TTL_DAYS = 30;

  /** 默认dndBufferSeconds值（可被配置文件覆盖） */
  private static final long DEFAULT_DND_BUFFER_SECONDS = 60L;

  /** 默认maxDeferHours值（可被配置文件覆盖） */
  private static final long DEFAULT_MAX_DEFER_HOURS = 72L;

  /** 默认suppressWindowSeconds值（可被配置文件覆盖） */
  private static final long DEFAULT_SUPPRESS_WINDOW_SECONDS = 300L;

  /** 默认senderDailyLimit值（可被配置文件覆盖） */
  private static final long DEFAULT_SENDER_DAILY_LIMIT = 10000L;

  /** 默认senderHourlyLimit值（可被配置文件覆盖） */
  private static final long DEFAULT_SENDER_HOURLY_LIMIT = 1000L;

  /** 通道全局开关：key 为通道大写名（SMS/EMAIL/...），value 为是否启用 */
  private Map<String, Boolean> channelEnabled;

  /** 默认发送优先级 */
  private String defaultPriority = "NORMAL";

  /**
   * P1-A4: 默认异步发送开关。
   *
   * <p>开启后 {@code send()} 默认行为变为"先落库 PENDING → 写入 Outbox → 异步投递 MQ → 消费端异步发送"， 仅在请求中显式设置
   * {@code sync=true} 时走同步路径。
   *
   * <p>对标阿里消息中心发送入口 100% 异步化：API 仅落库 PENDING + 返回 msgId，实际发送由 Worker 池消费。 默认 false（保持向后兼容），高
   * 并发场景建议开启。
   */
  private boolean defaultAsync = false;

  /** 聚合扫描间隔（毫秒） */
  @Min(1000)
  private long aggregateScanIntervalMs = DEFAULT_AGGREGATE_SCAN_INTERVAL_MS;

  /** 重试扫描间隔（毫秒） */
  @Min(5000)
  private long retryScanIntervalMs = DEFAULT_RETRY_SCAN_INTERVAL_MS;

  /**
   * P2-5: 消息 TTL（秒），超过此时间的消息自动丢弃。
   *
   * <p>用于消费者侧判断定时消息是否错过发送窗口：若 {@code scheduledAt} 距今 超过此阈值，则视为过期消息直接跳过（如服务宕机恢复后积压的定时消息）。 默认 3600s（1
   * 小时），0 表示不检查 TTL。
   */
  private long messageTtlSeconds = DEFAULT_MESSAGE_TTL_SECONDS;

  /**
   * P2-6: markAllRead 分批处理单批大小。
   *
   * <p>用户「全部已读」操作时，若未读通知量巨大（如万级），单条 UPDATE 会导致 长事务与行锁堆积。改为按此批次大小循环 UPDATE，每批独立事务，避免长事务。 默认 500，范围建议
   * 200~1000。
   */
  private int markAllReadBatchSize = DEFAULT_MARK_ALL_READ_BATCH_SIZE;

  /** P2-9: 回执拉取开关（关闭后不再主动拉取回执，仅依赖服务商回调） */
  private boolean receiptPullEnabled = true;

  /** P2-9: 回执拉取扫描间隔（毫秒），默认 120s */
  private long receiptPullScanIntervalMs = DEFAULT_RECEIPT_PULL_SCAN_INTERVAL_MS;

  /** P2-9: 回执拉取延迟阈值（分钟）：发送成功后多少分钟才开始主动拉取 */
  private long receiptPullDelayMinutes = DEFAULT_RECEIPT_PULL_DELAY_MINUTES;

  /** P2-9: 回执超时阈值（分钟）：超过此时间仍未收到回执则标记为 TIMEOUT */
  private long receiptTimeoutMinutes = DEFAULT_RECEIPT_TIMEOUT_MINUTES;

  /** 全局每日发送上限（单用户单通道，0 表示不限） */
  private int globalDailyLimit = DEFAULT_GLOBAL_DAILY_LIMIT;

  /** 全局每小时发送上限（单用户单通道，0 表示不限） */
  private int globalHourlyLimit = DEFAULT_GLOBAL_HOURLY_LIMIT;

  /**
   * P1-7: 消息内容最大长度（字符），超过此长度拒绝发送。
   *
   * <p>防止超大消息体导致 DB 存储膨胀和内存压力。 默认 1MB（1,048,576 字符），0 表示不限制。
   */
  private int maxContentLength = DEFAULT_MAX_CONTENT_LENGTH;

  /** P2-5: 多维度限流配置 */
  private RateLimitConfig rateLimit = new RateLimitConfig();

  /** P2-1: 智能去重配置 */
  private DedupConfig dedup = new DedupConfig();

  /** P2-4: 成本看板配置 */
  private CostConfig cost = new CostConfig();

  /** P0-1: 短信服务商配置 */
  private SmsConfig sms = new SmsConfig();

  /**
   * 多维度限流配置（P2-5）。
   *
   * <p>支持 receiver / templateCode / tenant 三个维度的令牌桶限流， 各维度独立配置 permits（每秒令牌数），任一维度超限即拒绝发送。 维度间为 AND
   * 关系：所有启用的维度都通过才允许发送。
   */
  @Data
  public static class RateLimitConfig {
    /** receiver 维度限流开关（避免同一接收人被轰炸） */
    private boolean receiverEnabled = true;

    /** receiver 维度每秒令牌数（同一 receiver 每秒最多发送条数） */
    private int receiverPermits = DEFAULT_RECEIVER_PERMITS;

    /** templateCode 维度限流开关（避免单一模板占满配额） */
    private boolean templateEnabled = true;

    /** templateCode 维度每秒令牌数 */
    private int templatePermits = DEFAULT_TEMPLATE_PERMITS;

    /** tenant 维度限流开关（多租户配额隔离） */
    private boolean tenantEnabled = true;

    /** tenant 维度每秒令牌数 */
    private int tenantPermits = DEFAULT_TENANT_PERMITS;
  }

  /**
   * 智能去重配置（P2-1）。
   *
   * <p>基于 Redis {@code SET NX EX} 原子操作实现短窗口去重：相同 dedupKey 的消息 在 {@code ttlSeconds}
   * 秒内仅允许发送一次，超时后自动释放（允许补发）。 适用于网络重试、上游重复触发、MQ 重投等场景，避免用户收到重复通知。
   *
   * <p>降级策略：Redis 不可用时自动放行（fail-open），避免阻断业务。
   */
  @Data
  public static class DedupConfig {
    /** 去重总开关（关闭后所有消息直接放行，不检查 Redis） */
    private boolean enabled = true;

    /** 去重窗口（秒）：同一 dedupKey 在此时间内视为重复，默认 60s */
    private int ttlSeconds = DEFAULT_TTL_SECONDS;
  }

  /**
   * 成本看板配置（P2-4）。
   *
   * <p>按通道配置单条消息成本（元），用于发送成本统计与看板展示。 SMS/EMAIL/PUSH 有实际服务商计费,INAPP/WEBHOOK/IM 通道免费。 关闭后不记录成本字段（cost
   * 始终为 0）。
   */
  @Data
  public static class CostConfig {
    /** 成本追踪开关（关闭后 cost 始终为 0） */
    private boolean enabled = true;

    /** 通道单条成本（元），key 为通道大写名 */
    private Map<String, BigDecimal> unitPrices = defaultUnitPrices();

    private static Map<String, BigDecimal> defaultUnitPrices() {
      // 使用 LinkedHashMap 保持插入顺序,使成本看板输出顺序稳定且可测试
      Map<String, BigDecimal> m = new LinkedHashMap<>(16);
      m.put("SMS", new BigDecimal("0.0450"));
      m.put("EMAIL", new BigDecimal("0.0010"));
      m.put("PUSH", new BigDecimal("0.0001"));
      m.put("INAPP", BigDecimal.ZERO);
      m.put("WEBHOOK", BigDecimal.ZERO);
      m.put("DINGTALK", BigDecimal.ZERO);
      m.put("WECOM", BigDecimal.ZERO);
      m.put("WECOM_APP", BigDecimal.ZERO);
      m.put("FEISHU", BigDecimal.ZERO);
      m.put("WX_MINI", BigDecimal.ZERO);
      m.put("ALIPAY_MINI", BigDecimal.ZERO);
      return m;
    }
  }

  /**
   * P0-1: 短信服务商配置。
   *
   * <p>通过 {@code ydsz.message.sms.provider} 选择服务商（aliyun/mock）， 无凭证或选 mock 时降级为日志输出，保证开发环境可运行。
   *
   * <p>P2-15: 多服务商选择策略由 {@code strategy} + {@code weights} 控制， 由 {@link
   * com.njydsz.message.server.service.impl.SmsProviderStrategyServiceImpl} 消费。
   */
  @Data
  public static class SmsConfig {
    /** 服务商: aliyun / mock（默认 mock 降级） */
    private String provider = "mock";

    /** 阿里云 SMS 配置 */
    private AliyunSmsConfig aliyun = new AliyunSmsConfig();

    /**
     * P2-15: 多服务商选择策略。
     *
     * <p>可选值：ROUND_ROBIN（轮询）/ WEIGHTED（权重）/ COST_FIRST（成本优先）/ AVAILABILITY_FIRST（可用性优先）。 默认
     * ROUND_ROBIN。
     */
    private String strategy = "ROUND_ROBIN";

    /**
     * P2-15: 权重配置（provider:weight,provider:weight）。
     *
     * <p>仅当 {@code strategy=WEIGHTED} 时生效。默认 {@code aliyun:5,tencent:3}。
     */
    private String weights = "aliyun:5,tencent:3";
  }

  /** 阿里云 SMS 配置。 */
  @Data
  public static class AliyunSmsConfig {
    /** AccessKey ID */
    private String accessKeyId;

    /** AccessKey Secret */
    private String accessKeySecret;

    /** 默认签名（模板未配置时回退） */
    private String signName;

    /** 阿里云 SMS endpoint */
    private String endpoint = "dysmsapi.aliyuncs.com";

    /** 连接超时（毫秒） */
    private int connectTimeout = DEFAULT_CONNECT_TIMEOUT;

    /** 读取超时（毫秒） */
    private int readTimeout = DEFAULT_READ_TIMEOUT;
  }

  /** P0-2: APP 推送服务商配置 */
  private PushConfig push = new PushConfig();

  /** P0-1: 微信小程序订阅消息配置 */
  private WxMiniConfig wxMini = new WxMiniConfig();

  /** P0-1: 支付宝小程序模板消息配置 */
  private AlipayMiniConfig alipayMini = new AlipayMiniConfig();

  /**
   * P0-2: APP 推送服务商配置。
   *
   * <p>通过 {@code ydsz.message.push.provider} 选择服务商（getui/mock）， 无凭证或选 mock 时降级为日志输出。
   */
  @Data
  public static class PushConfig {
    /** 服务商: getui / mock（默认 mock 降级） */
    private String provider = "mock";

    /** 个推配置 */
    private GetuiPushConfig getui = new GetuiPushConfig();
  }

  /** 个推（GeTui）推送配置。 */
  @Data
  public static class GetuiPushConfig {
    /** 个推 AppID */
    private String appId;

    /** 个推 AppKey */
    private String appKey;

    /** 个推 MasterSecret */
    private String masterSecret;

    /** 个推 REST API base url */
    private String baseUrl = "https://restapi.getui.com";

    /** 连接超时（毫秒） */
    private int connectTimeout = DEFAULT_CONNECT_TIMEOUT;

    /** 读取超时（毫秒） */
    private int readTimeout = DEFAULT_READ_TIMEOUT;
  }

  /** P1-4: 死信告警配置 */
  private DeadLetterAlertConfig deadLetterAlert = new DeadLetterAlertConfig();

  /** P1-7: 默认重试策略（全局兜底） */
  private RetryPolicy defaultRetryPolicy = new RetryPolicy();

  /**
   * P1-7: 按通道覆盖的重试策略。
   *
   * <p>key 为通道大写名（SMS/EMAIL/PUSH/...），value 为该通道专属重试策略。 未命中的通道回退到 {@link #defaultRetryPolicy}。
   */
  private Map<String, RetryPolicy> channelRetryPolicies;

  /**
   * P1-7: 重试策略配置。
   *
   * <p>支持最大重试次数、基础退避、退避倍率、退避上限。退避公式： {@code backoff = min(baseBackoffMs *
   * backoffMultiplier^retryCount, maxBackoffMs)}。
   *
   * <p>默认值与原 {@code MessageConstants.MAX_RETRY_COUNT=3} / {@code RETRY_BASE_BACKOFF_MS=2000}
   * 保持等价（倍率 2.0，上限 60s）， 确保不配置时行为不变。
   */
  @Data
  public static class RetryPolicy {
    /** 最大重试次数（达到后转死信/失败） */
    private int maxRetryCount = DEFAULT_MAX_RETRY_COUNT;

    /** 基础退避（毫秒） */
    private long baseBackoffMs = DEFAULT_BASE_BACKOFF_MS;

    /** 退避倍率（指数退避底数，默认 2.0） */
    private double backoffMultiplier = DEFAULT_BACKOFF_MULTIPLIER;

    /** 退避上限（毫秒，防止单次退避过大） */
    private long maxBackoffMs = DEFAULT_MAX_BACKOFF_MS;
  }

  /**
   * P1-4: 死信告警配置。
   *
   * <p>当指定时间窗口内某通道死信数量达到阈值时触发告警事件 ({@link com.njydsz.message.server.event.DeadLetterAlertEvent})，
   * 通过 {@code cooldownMinutes} 控制同一通道告警冷却，避免告警风暴。
   */
  @Data
  public static class DeadLetterAlertConfig {
    /** 死信告警开关（关闭后仅落库不告警） */
    private boolean enabled = true;

    /** 告警阈值：窗口内死信数达到此值触发告警 */
    private int threshold = DEFAULT_THRESHOLD;

    /** 统计窗口（分钟） */
    private int windowMinutes = DEFAULT_WINDOW_MINUTES;

    /** 告警冷却（分钟）：同一通道告警后多久内不重复告警 */
    private int cooldownMinutes = DEFAULT_COOLDOWN_MINUTES;
  }

  /** P1-5: 退订中心配置 */
  private UnsubscribeConfig unsubscribe = new UnsubscribeConfig();

  /**
   * P0-1: 微信小程序订阅消息配置。
   *
   * <p>通过 {@code ydsz.message.wx-mini.provider} 选择服务商（wechat/mock）， 无凭证或选 mock 时降级为日志输出。
   * 微信小程序订阅消息需要用户在小程序端主动订阅后才能下发， 每次发送消耗一次订阅配额。
   */
  @Data
  public static class WxMiniConfig {
    /** 服务商: wechat / mock（默认 mock 降级） */
    private String provider = "mock";

    /** 微信小程序 AppID */
    private String appId;

    /** 微信小程序 AppSecret */
    private String appSecret;

    /** 微信 API base URL */
    private String baseUrl = "https://api.weixin.qq.com";

    /** 连接超时（毫秒） */
    private int connectTimeout = DEFAULT_CONNECT_TIMEOUT;

    /** 读取超时（毫秒） */
    private int readTimeout = DEFAULT_READ_TIMEOUT;
  }

  /**
   * P0-1: 支付宝小程序模板消息配置。
   *
   * <p>通过 {@code ydsz.message.alipay-mini.provider} 选择服务商（alipay/mock）， 无凭证或选 mock 时降级为日志输出。
   */
  @Data
  public static class AlipayMiniConfig {
    /** 服务商: alipay / mock（默认 mock 降级） */
    private String provider = "mock";

    /** 支付宝小程序 AppID */
    private String appId;

    /** 支付宝应用私钥 */
    private String privateKey;

    /** 支付宝公钥 */
    private String alipayPublicKey;

    /** 支付宝网关地址 */
    private String gateway = "https://openapi.alipay.com/gateway.do";

    /** 连接超时（毫秒） */
    private int connectTimeout = DEFAULT_CONNECT_TIMEOUT;

    /** 读取超时（毫秒） */
    private int readTimeout = DEFAULT_READ_TIMEOUT;
  }

  /** P2-8: 通道熔断器配置 */
  private CircuitBreakerConfig circuitBreaker = new CircuitBreakerConfig();

  /**
   * P2-8: 通道级熔断器配置。
   *
   * <p>不同场景可按通道配置不同阈值，未配置的通道使用默认值。
   */
  @Data
  public static class CircuitBreakerConfig {
    /** 失败率阈值（0-100），默认 50% */
    @Min(1)
    @Max(100)
    private int failureRateThreshold = DEFAULT_FAILURE_RATE_THRESHOLD;

    /** 慢调用率阈值（0-100），默认 80% */
    @Min(1)
    @Max(100)
    private int slowCallRateThreshold = DEFAULT_SLOW_CALL_RATE_THRESHOLD;

    /** 慢调用阈值（秒），默认 5s */
    @Min(1)
    private long slowCallDurationSeconds = DEFAULT_SLOW_CALL_DURATION_SECONDS;

    /** 熔断开启持续时间（秒），默认 30s */
    @Min(5)
    private long waitDurationInOpenStateSeconds = DEFAULT_WAIT_DURATION_IN_OPEN_STATE_SECONDS;

    /** 半开状态允许探测数，默认 3 */
    @Min(1)
    private int permittedNumberOfCallsInHalfOpenState = DEFAULT_PERMITTED_NUMBER_OF_CALLS_IN_HALF_OPEN_STATE;

    /** 滑动窗口大小，默认 20 */
    @Min(10)
    private int slidingWindowSize = DEFAULT_SLIDING_WINDOW_SIZE;

    /** 最小调用数，默认 10 */
    @Min(5)
    private int minimumNumberOfCalls = DEFAULT_MINIMUM_NUMBER_OF_CALLS;
  }

  /** P2-5: 智能定时配置 */
  private SmartTimingConfig smartTiming = new SmartTimingConfig();

  /**
   * GAP-6: 默认投递保证级别。
   *
   * <p>对标 AWS SNS+SQS 的 QoS 级别选择：
   *
   * <ul>
   *   <li>{@code AT_LEAST_ONCE}（默认）— 至少送达一次，可能重复（RocketMQ + 幂等）
   *   <li>{@code AT_MOST_ONCE} — 最多送达一次，允许丢失不允许重复（如营销消息）
   *   <li>{@code EXACTLY_ONCE} — 精确一次（需分布式锁 + DB 事务保证，性能较低）
   * </ul>
   *
   * 消息级覆盖：在 MessageRequest 中设置 deliveryGuarantee 字段优先于此全局默认值。
   */
  private String defaultDeliveryGuarantee = "AT_LEAST_ONCE";

  /**
   * P1-5: 退订中心配置。
   *
   * <p>支持 token-based 一键退订（RFC 8058 List-Unsubscribe-Post）， token 采用 HMAC-SHA256 签名，{@code ttlDays}
   * 控制链接有效期（默认 30 天， 符合邮件退订链接的最佳实践）。{@code secret} 必须配置为 ≥32 字节的随机串， 未配置时降级使用一个内置默认值（仅开发环境，生产必须覆盖）。
   */
  @Data
  public static class UnsubscribeConfig {
    /** 退订中心总开关（关闭后 token 一键退订接口拒绝执行） */
    private boolean enabled = true;

    /** token 签名密钥（Base64 编码，建议 ≥32 字节随机串；为空时使用内置默认值） */
    private String secret;

    /** token 有效期（天），默认 30 天 */
    private int ttlDays = DEFAULT_TTL_DAYS;

    /** 退订链接 base URL（如 https://ydsz.example.com/unsubscribe），用于拼接完整链接 */
    private String baseUrl;
  }

  /**
   * P2-5: 智能定时配置。
   *
   * <p>超越简单 DND 拦截的智能发送时机策略：
   *
   * <ul>
   *   <li>DND 命中时不再丢弃消息，而是<strong>延迟到 DND 结束后</strong>自动重发
   *   <li>URGENT 优先级消息可绕过 DND 立即发送
   *   <li>DND 仅对"打扰型"通道生效（SMS/PUSH/IM），EMAIL/INAPP/Webhook 不受 DND 限制
   * </ul>
   */
  @Data
  public static class SmartTimingConfig {
    /** 智能定时总开关（关闭后 DND 命中仍走旧的丢弃策略） */
    private boolean enabled = true;

    /** URGENT 优先级是否绕过 DND（默认 true，紧急消息必须立即送达） */
    private boolean urgentBypassDnd = true;

    /** DND 生效的打扰型通道列表（默认 SMS/PUSH/DINGTALK/WECOM/FEISHU） */
    private List<String> disruptiveChannels =
        Arrays.asList("SMS", "PUSH", "DINGTALK", "WECOM", "FEISHU", "WX_MINI", "ALIPAY_MINI");

    /** DND 延迟发送时附加的缓冲秒数（默认 60s，避免卡在 DND 结束瞬间的高峰） */
    private long dndBufferSeconds = DEFAULT_DND_BUFFER_SECONDS;

    /** DND 延迟消息最大延迟小时数（超过则降级为丢弃，防止消息过期太久失去意义，默认 72h） */
    private long maxDeferHours = DEFAULT_MAX_DEFER_HOURS;

    /**
     * 判断指定通道是否为打扰型通道（受 DND 约束）。
     *
     * @param channel 通道名称（大写）
     * @return true 表示该通道受 DND 约束
     */
    public boolean isDisruptive(String channel) {
      if (channel == null) {
        return false;
      }
      return disruptiveChannels.contains(channel.toUpperCase());
    }

    /**
     * 获取打扰型通道集合（用于测试与诊断）。
     *
     * @return 不可变副本
     */
    public Set<String> disruptiveChannelSet() {
      return new LinkedHashSet<>(disruptiveChannels);
    }
  }

  /** 敏感词过滤配置 */
  private SensitiveFilterConfig sensitiveFilter = new SensitiveFilterConfig();

  /** 消息归档配置 */
  private ArchiveConfig archive = new ArchiveConfig();

  /** 通道抑制窗口（秒），默认 300s */
  private long suppressWindowSeconds = DEFAULT_SUPPRESS_WINDOW_SECONDS;

  /** 单发送人每日发送上限（0 表示不限） */
  private long senderDailyLimit = DEFAULT_SENDER_DAILY_LIMIT;

  /** 单发送人每小时发送上限（0 表示不限） */
  private long senderHourlyLimit = DEFAULT_SENDER_HOURLY_LIMIT;

  /**
   * 敏感词过滤配置。
   *
   * <p>控制消息内容中的敏感词过滤行为，关闭后所有消息直接放行。
   */
  @Data
  public static class SensitiveFilterConfig {
    /** 敏感词过滤开关 */
    private boolean enabled = true;

    /** 敏感词列表（逗号分隔） */
    private String words = "";
  }

  /**
   * 消息归档配置。
   *
   * <p>控制消息是否同步归档到 Elasticsearch 以支持全文搜索， 未启用时仅落库 PostgreSQL。
   */
  @Data
  public static class ArchiveConfig {
    /** Elasticsearch 归档开关 */
    private boolean esEnabled = false;
  }

}
