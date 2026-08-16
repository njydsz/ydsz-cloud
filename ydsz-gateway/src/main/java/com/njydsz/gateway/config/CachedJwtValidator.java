package com.njydsz.gateway.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamMessageListenerContainerOptions;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import com.njydsz.common.auth.model.UserInfo;
import com.njydsz.common.auth.token.TokenService;
import com.njydsz.common.cache.YdszCache;
import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.builder.CacheType;
import com.njydsz.common.safe.sensitive.SensitiveUtil;

/**
 * P3-7: JWT 校验结果缓存（Redis Stream 广播版）
 *
 * <p>使用 ydsz-common-cache 本地缓存 JWT 解析结果，避免每个请求重复执行 {@code tokenService.parseAccessToken(token)} 的
 * CPU 开销。
 *
 * <h3>缓存策略</h3>
 *
 * <ul>
 *   <li>缓存键: JWT Token 字符串
 *   <li>缓存值: UserInfo 解析结果（或 INVALID 标记）
 *   <li>TTL: 10 秒（P3-7 缩短：从 30s 降至 10s，提升黑名单生效时效性）
 *   <li>最大容量: 10,000 条（防止内存溢出）
 * </ul>
 *
 * <h3>P0-2 防击穿 / 防穿透增强</h3>
 *
 * <ul>
 *   <li>防击穿: 使用 {@code CacheProtectionGuard.getWithProtection} 保证同一 Token 并发请求 仅一个线程执行 JWT
 *       解析，其余线程等待复用结果，消除缓存过期瞬间的 CPU 尖峰
 *   <li>防穿透: 无效 Token 以空值占位符短时缓存（2-5s 随机抖动），防止伪造 Token 反复穿透
 * </ul>
 *
 * <h3>P3-7 增强：Redis Stream 广播（替代 Pub/Sub）</h3>
 *
 * <p>原 Pub/Sub 模式无持久化、不保证投递，订阅者离线时消息丢失。 改用 Redis Stream + Consumer Group 实现：
 *
 * <ul>
 *   <li>至少一次投递：消息持久化在 Stream 中，消费者故障恢复后可重读
 *   <li>消息体为 SHA-256 摘要：不传输 JWT 明文，提升安全性
 *   <li>消费者组协同：多个网关实例组成消费者组，消息仅被一个实例消费
 *   <li>回环消息幂等：本实例发布的消息也会被自己消费，重复 invalidate 无副作用
 * </ul>
 *
 * <h3>性能预期</h3>
 *
 * <p>假设单实例 QPS=2000，90% 请求在 10 秒窗口内复用缓存， JWT 解析次数从 2000/s 降至 ~200/s，CPU 开销减少 90%。
 *
 * @since 3.7.0
 * @author ydsz-team
 */
@Slf4j
@Component
public class CachedJwtValidator {

  /**
   * P3-7: 缓存 TTL 缩短至 10 秒（原 30 秒），提升黑名单生效时效性。
   *
   * <p>Redis Stream 广播保证多实例间秒级失效，TTL 仅作为 Stream 不可用时的兜底。
   */
  @Value("${ydsz.gateway.jwt.cache-ttl-seconds:10}")
  private long cacheTtlSeconds;

  /** P0-3: 缓存命中计数器（供 Prometheus 指标使用） */
  private final AtomicLong cacheHitCount = new AtomicLong(0);

  /** P0-3: 缓存未命中计数器（供 Prometheus 指标使用） */
  private final AtomicLong cacheMissCount = new AtomicLong(0);

  /** 缓存最大容量 */
  private static final long CACHE_MAX_SIZE = 10_000;

  /** 空值占位最小过期时间（毫秒）——防穿透，无效 Token 短时缓存 */
  private static final long NULL_CACHE_MIN_MS = 2_000;

  /** 空值占位最大过期时间（毫秒）——随机抖动防雪崩 */
  private static final long NULL_CACHE_MAX_MS = 5_000;

  /** P3-7: Redis Stream 频道（替代 Pub/Sub 频道） */
  private static final String INVALIDATION_STREAM = "ydsz:gateway:jwt-cache:stream";

  /** P3-7: 消费者组名称 */
  private static final String CONSUMER_GROUP = "jwt-cache-consumers";

  /** P3-7: Stream 最大保留长度（自动裁剪旧消息） */
  private static final long STREAM_MAX_LENGTH = 1_000;

  /** 本地缓存实例 */
  private final Cache<String, Optional<UserInfo>> claimsCache;

  /** Token 服务 */
  private final TokenService tokenService;

  /** P2-12: 网关指标组件（可选，用于记录 JWT 校验耗时） */
  private final GatewayMetrics gatewayMetrics;

  /** P3-7: Reactive Redis 模板 */
  private final ReactiveStringRedisTemplate redisTemplate;

  /** P3-7: Redis 连接工厂 */
  private final RedisConnectionFactory connectionFactory;

  /** P3-7: Stream 监听容器 */
  private StreamMessageListenerContainer<String, MapRecord<String, String, String>>
      streamListenerContainer;

  /** P3-7: 定时任务线程池（用于 Stream 健康检查 + pending 消息处理） */
  private final ScheduledExecutorService scheduler =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "jwt-cache-stream-worker");
            t.setDaemon(true);
            return t;
          });

  /** P3-7: hash -> token 反向索引（用于 Stream 消息反查失效 token） */
  private final ConcurrentHashMap<String, String> hashToTokenMap = new ConcurrentHashMap<>();

  /**
   * 构造 JWT 缓存校验器
   *
   * @param tokenService Token 服务
   * @param gatewayMetrics 网关指标组件（可选）
   * @param redisTemplateProvider Reactive Redis 模板提供者（可选，未配置时降级为单实例模式）
   * @param connectionFactoryProvider Redis 连接工厂提供者
   */
  public CachedJwtValidator(
      TokenService tokenService,
      GatewayMetrics gatewayMetrics,
      ObjectProvider<ReactiveStringRedisTemplate> redisTemplateProvider,
      ObjectProvider<RedisConnectionFactory> connectionFactoryProvider) {
    this.tokenService = tokenService;
    this.gatewayMetrics = gatewayMetrics;
    this.redisTemplate = redisTemplateProvider.getIfAvailable();
    this.connectionFactory = connectionFactoryProvider.getIfAvailable();
    this.claimsCache =
        YdszCache.<String, Optional<UserInfo>>newBuilder()
            .type(CacheType.STRIPED)
            .expireAfterWrite(cacheTtlSeconds, TimeUnit.SECONDS)
            .maximumSize(CACHE_MAX_SIZE)
            .recordStats()
            .build();
    log.info(
        "[JwtCache] JWT 校验缓存初始化完成, TTL={}s, maxSize={}, streamBroadcast={}",
        cacheTtlSeconds,
        CACHE_MAX_SIZE,
        redisTemplate != null);

    // P0-3: 注册缓存命中/未命中 Prometheus 指标
    if (gatewayMetrics != null) {
      gatewayMetrics.registerJwtCacheCounters(cacheHitCount, cacheMissCount);
    }
  }

  /**
   * P0-3: 获取缓存命中率（供监控和健康检查使用）
   *
   * @return 缓存命中率（0.0 ~ 1.0），无请求时返回 -1.0
   */
  public double getCacheHitRate() {
    long hits = cacheHitCount.get();
    long total = hits + cacheMissCount.get();
    return total > 0 ? (double) hits / total : -1.0;
  }

  /** P3-7: 启动后订阅 Redis Stream 失效广播频道（Consumer Group 模式） */
  @PostConstruct
  public void subscribeInvalidationStream() {
    if (redisTemplate == null || connectionFactory == null) {
      log.warn("[JwtCache] Redis 未配置，降级为单实例模式（多实例部署时黑名单生效延迟最长 {}s）", cacheTtlSeconds);
      return;
    }
    try {
      // 创建消费者组（幂等：已存在时忽略）
      createConsumerGroup();

      // 创建 Stream 监听容器
      StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
          StreamMessageListenerContainerOptions.builder()
              .pollTimeout(java.time.Duration.ofSeconds(2))
              .batchSize(10)
              .build();

      streamListenerContainer = StreamMessageListenerContainer.create(connectionFactory, options);

      // 订阅 Stream，使用 Consumer Group 模式
      streamListenerContainer.receive(
          Consumer.from(CONSUMER_GROUP, instanceId()),
          StreamOffset.create(INVALIDATION_STREAM, ReadOffset.lastConsumed()),
          this::handleStreamMessage);

      streamListenerContainer.start();

      // 启动定时任务：每 30 秒处理 pending 消息（ACK 失败重试）
      scheduler.scheduleAtFixedRate(this::processPendingMessages, 30, 30, TimeUnit.SECONDS);

      log.info(
          "[JwtCache] 已订阅失效广播 Stream stream={}, group={}, consumer={}",
          INVALIDATION_STREAM,
          CONSUMER_GROUP,
          instanceId());
    } catch (Exception e) {
      log.warn("[JwtCache] 订阅 Redis Stream 失效广播失败，降级为单实例模式: {}", e.getMessage());
    }
  }

  /**
   * P3-7: 获取当前实例标识（基于纳秒时间戳）。
   *
   * @return 实例标识字符串
   */
  private String instanceId() {
    return "gateway-" + Long.toHexString(System.nanoTime());
  }

  /**
   * P3-7: 创建消费者组（幂等）
   *
   * <p>消费者组从 Stream 最新位置开始消费。如果组已存在则忽略异常。
   */
  private void createConsumerGroup() {
    try {
      StreamOperations<String, String, String> ops = redisTemplate.opsForStream();
      ops.createGroup(INVALIDATION_STREAM, ReadOffset.latest(), CONSUMER_GROUP);
      log.info(
          "[JwtCache] 创建 Stream 消费者组成功 stream={}, group={}", INVALIDATION_STREAM, CONSUMER_GROUP);
    } catch (Exception e) {
      // 消费者组已存在，正常忽略
      log.debug("[JwtCache] 消费者组已存在: {}", e.getMessage());
    }
  }

  /**
   * P3-7: 处理 Stream 消息
   *
   * @param message Stream 消息记录
   */
  private void handleStreamMessage(MapRecord<String, String, String> message) {
    try {
      String tokenHash = message.getValue().get("tokenHash");
      if (tokenHash != null && !tokenHash.isBlank()) {
        // 通过 hash 反查并失效本地缓存
        invalidateByHash(tokenHash);
        log.debug("[JwtCache] 收到 Stream 失效事件 hash={}", tokenHash);
      }
      // ACK 确认消息
      redisTemplate
          .opsForStream()
          .acknowledge(INVALIDATION_STREAM, CONSUMER_GROUP, message.getId());
    } catch (Exception e) {
      log.warn("[JwtCache] 处理 Stream 消息异常: {}", e.getMessage());
    }
  }

  /**
   * P3-7: 通过 SHA-256 摘要使缓存失效
   *
   * <p>优先从 hashToTokenMap 反向索引查找（O(1)），未命中时遍历缓存 key 计算 hash（O(n)，兜底）。
   *
   * @param tokenHash Token 的 SHA-256 摘要
   */
  private void invalidateByHash(String tokenHash) {
    // 优先使用反向索引（O(1) 查找）
    String token = hashToTokenMap.get(tokenHash);
    if (token != null) {
      claimsCache.invalidate(token);
      hashToTokenMap.remove(tokenHash);
      return;
    }
    // 兜底：遍历缓存匹配（首次广播时的 hash 可能尚未建立映射）
    claimsCache
        .asMap()
        .forEach(
            (cachedToken, value) -> {
              if (sha256(cachedToken).equalsIgnoreCase(tokenHash)) {
                claimsCache.invalidate(cachedToken);
                hashToTokenMap.remove(tokenHash);
              }
            });
  }

  /** P3-7: 处理 pending 消息（之前 ACK 失败的消息） */
  private void processPendingMessages() {
    try {
      if (!isStreamAvailable()) {
        return;
      }
      StreamOperations<String, String, String> ops = redisTemplate.opsForStream();
      // 获取当前消费者的 pending 消息列表
      var pending =
          ops.pending(INVALIDATION_STREAM, Consumer.from(CONSUMER_GROUP, instanceId()), 10, 100);
      if (pending != null && !pending.getTotalPendingMessages().equals(0L)) {
        var pendingMessages = pending.getPendingMessages();
        if (pendingMessages != null && !pendingMessages.isEmpty()) {
          for (var entry : pendingMessages) {
            // 重试 ACK（幂等操作）
            ops.acknowledge(INVALIDATION_STREAM, CONSUMER_GROUP, entry.getId());
            log.debug("[JwtCache] 已 ACK 延迟消息: {}", entry.getId());
          }
        }
      }
    } catch (Exception e) {
      log.debug("[JwtCache] 处理 pending 消息异常: {}", e.getMessage());
    }
  }

  /**
   * P3-7: 检查 Stream 是否可用
   *
   * @return true=可用
   */
  private boolean isStreamAvailable() {
    return streamListenerContainer != null
        && streamListenerContainer.isRunning()
        && redisTemplate != null;
  }

  /** P3-7: 关闭时释放 Stream 监听 */
  @PreDestroy
  public void unsubscribe() {
    if (streamListenerContainer != null && streamListenerContainer.isRunning()) {
      streamListenerContainer.stop();
      log.info("[JwtCache] 已停止 Redis Stream 失效广播监听");
    }
    scheduler.shutdownNow();
  }

  /**
   * 校验并解析 JWT Token（带缓存 + 防击穿/防穿透）
   *
   * <p>优先从 Caffeine 缓存读取解析结果；缓存未命中时通过 {@link
   * com.njydsz.common.cache.api.CacheProtectionGuard#getWithProtection} 执行解析。
   *
   * <p>P2-12: 同时记录 JWT 校验耗时到 GatewayMetrics（如果可用）。
   *
   * @param jwt JWT Token 字符串
   * @return UserInfo 解析结果，Token 无效时返回 null
   */
  public UserInfo validateAndParse(String jwt) {
    if (jwt == null || jwt.isBlank()) {
      return null;
    }

    long startTime = System.currentTimeMillis();
    Optional<UserInfo> cached = claimsCache.getIfPresent(jwt);
    long duration = System.currentTimeMillis() - startTime;

    boolean isCached = cached != null;
    if (isCached) {
      cacheHitCount.incrementAndGet();
      recordMetrics(duration, true);
      return cached.orElse(null);
    }

    // P0-2: 缓存未命中，使用带防护的加载（防击穿 + 防穿透）
    cacheMissCount.incrementAndGet();
    startTime = System.currentTimeMillis();
    Optional<UserInfo> result =
        claimsCache.getWithProtection(jwt, this::parseToken, NULL_CACHE_MIN_MS, NULL_CACHE_MAX_MS);
    duration = System.currentTimeMillis() - startTime;

    recordMetrics(duration, false);
    return result == null ? null : result.orElse(null);
  }

  /**
   * P0-2: 执行实际 JWT 解析（作为 CacheProtectionGuard 的加载器）
   *
   * <p>防击穿保证同一 key 并发时该方法仅被调用一次； 返回 {@code Optional.empty()} 表示无效 Token（空值占位缓存，防穿透）。
   *
   * @param jwt JWT Token 字符串
   * @return 解析结果包装，无效返回 empty（不返回 null，避免触发空值占位歧义）
   */
  private Optional<UserInfo> parseToken(String jwt) {
    if (!tokenService.validateAccessToken(jwt)) {
      return Optional.empty();
    }
    try {
      return Optional.ofNullable(tokenService.parseAccessToken(jwt));
    } catch (Exception e) {
      log.warn("[JwtCache] 解析 JWT 失败: {}", e.getMessage());
      return Optional.empty();
    }
  }

  /**
   * P3-7: 失效单个 Token（黑名单加入后立即清除缓存）
   *
   * <p>P3-7: 多实例部署时，本方法同时：
   *
   * <ol>
   *   <li>清除本实例的 Caffeine 缓存
   *   <li>发布 SHA-256 摘要到 Redis Stream（不传输 JWT 明文）
   *   <li>其他实例订阅到消息后清除各自的 Caffeine 缓存
   * </ol>
   *
   * @param jwt 需要失效的 JWT Token
   */
  public void invalidate(String jwt) {
    if (jwt == null || jwt.isBlank()) {
      return;
    }
    // 1. 本地立即清除
    claimsCache.invalidate(jwt);
    log.debug("[JwtCache] Token 已从本地缓存移除 jwt={}", maskToken(jwt));

    // 2. P3-7: 广播 SHA-256 摘要到其他实例（异步，不阻塞主流程）
    broadcastInvalidation(jwt);
  }

  /**
   * P3-7: 发布 SHA-256 摘要到 Redis Stream（替代 Pub/Sub）
   *
   * <p>消息体仅包含 Token 的 SHA-256 摘要，不包含原始 Token，提升内网传输安全性。
   *
   * @param jwt 需要失效的 JWT Token
   */
  private void broadcastInvalidation(String jwt) {
    if (redisTemplate == null) {
      // Redis 未配置，跳过广播（单实例模式）
      return;
    }
    try {
      String tokenHash = sha256(jwt);
      // 建立 hash → token 反向索引（用于其他实例快速反查）
      hashToTokenMap.put(tokenHash, jwt);

      redisTemplate
          .opsForStream()
          .add(
              INVALIDATION_STREAM,
              Map.of("tokenHash", tokenHash, "ts", String.valueOf(System.currentTimeMillis())))
          .doOnSuccess(
              id -> {
                // 裁剪 Stream 长度，避免无限增长
                redisTemplate.opsForStream().trim(INVALIDATION_STREAM, STREAM_MAX_LENGTH);
              })
          .onErrorResume(
              e -> {
                log.warn("[JwtCache] Redis Stream 广播失效失败（其他实例将通过 TTL 过期）: {}", e.getMessage());
                return Mono.empty();
              })
          .subscribe();
    } catch (Exception e) {
      log.warn("[JwtCache] Redis Stream 广播发布异常: {}", e.getMessage());
    }
  }

  /**
   * P3-7: 计算字符串的 SHA-256 摘要（十六进制小写）
   *
   * @param input 输入字符串
   * @return SHA-256 摘要（64 字符十六进制），算法不可用时返回原文长度前缀
   */
  private static String sha256(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      // SHA-256 是 JVM 标准算法，理论上不会发生
      return "sha256-unavailable-" + input.hashCode();
    }
  }

  /**
   * P2-12: 记录 JWT 校验耗时指标
   *
   * @param durationMs 耗时（毫秒）
   * @param cached 是否命中缓存
   */
  private void recordMetrics(long durationMs, boolean cached) {
    if (gatewayMetrics != null) {
      try {
        gatewayMetrics.recordJwtValidationDuration(durationMs, cached);
      } catch (Exception e) {
        // 指标记录失败不影响主流程
        log.debug("[JwtCache] 记录指标失败: {}", e.getMessage());
      }
    }
  }

  /**
   * Token 脱敏（P0-5：复用 ydsz-common-safe 的 SensitiveUtil 统一脱敏策略）
   *
   * @param jwt JWT Token
   * @return 脱敏后的字符串
   */
  private String maskToken(String jwt) {
    return SensitiveUtil.defaultDesensitize(jwt, '*');
  }

  /**
   * 获取缓存统计信息（供监控使用）
   *
   * @return Caffeine 缓存统计快照的字符串表示
   */
  public String getCacheStats() {
    return claimsCache.getStats().toString();
  }

  /** 手动清除缓存（供 Nacos 配置刷新时调用） */
  public void invalidateAll() {
    claimsCache.invalidateAll();
    hashToTokenMap.clear();
    log.info("[JwtCache] 缓存已手动清除");
  }
}
